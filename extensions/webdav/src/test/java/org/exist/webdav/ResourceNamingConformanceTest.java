/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.webdav;

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

/**
 * Cross-surface resource-naming conformance harness (PR A).
 *
 * For a corpus of "awkward" resource names, this stores each name through one API surface and probes
 * how it round-trips and what name actually lands in storage, across eXist's remote surfaces. It
 * prints a matrix of the current behavior (visible in the CI log) and enforces a <b>ratchet</b>:
 * the set of names that fail to round-trip cross-surface must exactly equal {@link #KNOWN_FAILURES}.
 * So merging this guards every already-correct name against regression immediately, and as the naming
 * fixes land you remove entries from {@code KNOWN_FAILURES} to lock in each improvement — the test
 * tells you exactly which ones when they start passing.
 *
 * Surfaces covered in this first increment:
 * <ul>
 *   <li><b>WebDAV</b> (Apache Jackrabbit server since PR #6364) — probed via raw HTTP PUT/GET so the
 *       on-the-wire encoding is explicit.</li>
 *   <li><b>REST</b> — via {@link HttpURLConnection}.</li>
 *   <li>an <b>oracle</b> for the stored name — eXist's native REST collection listing (no XQuery module
 *       needed; the WebDAV module's test conf.xml registers no XQuery builtin-modules), reporting what
 *       name actually got stored under the test collection.</li>
 * </ul>
 *
 * <p>The harness is module- and WebDAV-client-independent: the test collection is created by a REST PUT
 * (which auto-creates it), and all probes are raw HTTP. Probe content is valid XML because the corpus
 * names end in {@code .xml} and eXist parses {@code .xml} resources on store.</p>
 *
 * TODO (follow-up increments, see the resource-naming tasking):
 * <ul>
 *   <li>XML-RPC surface (create/read via {@code org.apache.xmlrpc} client against {@code /xmlrpc}).</li>
 *   <li>Full N×N cross-surface matrix (create-via-X then read-via-every-Y).</li>
 *   <li>existdb-openapi lives in a separate repo; its special-char suite is PR F there.</li>
 * </ul>
 */
public class ResourceNamingConformanceTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String TEST_COLLECTION = "/db/naming-conformance-test";
    // valid XML, because the corpus names end in .xml and eXist parses .xml resources on store
    private static final String MARKER = "naming-probe-content";
    private static final String CONTENT = "<probe>" + MARKER + "</probe>";

    /**
     * The corpus of "awkward" leaf resource names. Each is the human-intended name the user
     * believes they are creating. {@code /} is excluded (path separator); a {@code .xml}/{@code .bin}
     * suffix is kept so MIME detection behaves normally.
     */
    private static final Map<String, String> CORPUS = new LinkedHashMap<>();
    static {
        CORPUS.put("ascii-control",   "plain.xml");        // control: must always work
        CORPUS.put("space",           "with space.xml");
        CORPUS.put("plus",            "a+b.xml");
        CORPUS.put("literal-percent", "a%b.xml");
        CORPUS.put("encoded-space",   "a%20b.xml");        // literal "%20" in the name
        CORPUS.put("hash",            "a#b.xml");
        CORPUS.put("at",              "a@b.xml");
        CORPUS.put("ampersand",       "a&b.xml");
        CORPUS.put("parens",          "report(2024).xml");
        CORPUS.put("apostrophe",      "o'brien.xml");
        CORPUS.put("non-ascii",       "café.xml");
        CORPUS.put("cyrillic",        "Привет.xml");
        CORPUS.put("cjk",             "文書.xml");
    }

    /**
     * The ratchet allowlist: corpus labels that do NOT yet round-trip cross-surface (stored via
     * WebDAV, then read back by the requested name via both WebDAV and REST). This set must match
     * the names that currently fail exactly.
     *
     * <p>When a naming fix lands and one of these starts round-tripping, the test fails and tells you
     * to <b>remove</b> it from this set — at which point that name becomes regression-guarded. If a
     * name that is <em>not</em> listed here ever stops round-tripping, the test fails as a regression.
     * So the matrix can neither silently drift nor silently regress. See the resource-naming tasking
     * (issues #3795, #3665, #1824, #5299, #1612).</p>
     */
    private static final Set<String> KNOWN_FAILURES = Set.of(
            "plus",        // a+b.xml         — '+' stored as %2B; REST read by requested name misses
            "at",          // a@b.xml
            "ampersand",   // a&b.xml
            "parens",      // report(2024).xml
            "apostrophe"   // o'brien.xml
    );

    @BeforeClass
    public static void createTestCollection() {
        freshCollection();
    }

    @AfterClass
    public static void removeTestCollection() {
        restDelete(TEST_COLLECTION);
    }

    /**
     * Resets the test collection to empty: delete it, then recreate it by REST-PUTting a seed
     * document (which auto-creates the collection) and removing the seed. Wiping the whole
     * collection avoids depending on the stored name, which is what this test characterizes.
     */
    private static void freshCollection() {
        restDelete(TEST_COLLECTION);
        restPut(TEST_COLLECTION + "/__seed.xml", CONTENT);
        restDelete(TEST_COLLECTION + "/__seed.xml");
    }

    @Test
    public void crossSurfaceNamingConformance() throws Exception {
        final List<Row> rows = new ArrayList<>();

        for (final Map.Entry<String, String> probe : CORPUS.entrySet()) {
            final String label = probe.getKey();
            final String name = probe.getValue();
            final Row row = new Row(label, name);

            // start each probe from an empty collection (deleting by the requested name is unreliable,
            // since the stored name may differ — which is exactly what this test characterizes)
            freshCollection();

            try {
                // CREATE via WebDAV — raw HTTP PUT with RFC 3986 path-segment encoding, so the
                // test controls exactly what is on the wire (no client library obscuring it)
                final StringBuilder putBody = new StringBuilder();
                final int putCode = webdavPut(TEST_COLLECTION + "/" + name, CONTENT, putBody);
                row.created = putCode == 200 || putCode == 201 || putCode == 204;
                if (!row.created) {
                    row.error = "PUT " + putCode + ": " + putBody.toString().replaceAll("\\s+", " ").trim();
                }

                // ORACLE: what name actually landed in storage?
                row.storedName = soleStoredName();

                // READ-BACK via WebDAV (self round-trip, raw HTTP GET) — by the name the user PUT
                row.webdavReadBack = webdavGet(TEST_COLLECTION + "/" + name);

                // READ via REST by the name the user PUT (cross-surface WebDAV -> REST): can a REST
                // client retrieve, by the requested name, what a WebDAV client just stored?
                row.restReadBack = restGet(TEST_COLLECTION + "/" + name);
            } catch (final Throwable t) {
                row.error = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            }
            rows.add(row);
        }

        final String matrix = renderMatrix("WebDAV create -> {oracle, WebDAV read, REST read}", rows);
        System.out.println(matrix);

        // A name is "conformant" when it stores and its content round-trips by the requested name
        // via BOTH WebDAV and REST.
        final Set<String> failing = new LinkedHashSet<>();
        for (final Row r : rows) {
            final boolean conformant = r.created
                    && Boolean.TRUE.equals(r.webdavReadBack)
                    && Boolean.TRUE.equals(r.restReadBack);
            if (!conformant) {
                failing.add(r.label);
            }
        }

        // Ratchet: the set of failing names must match KNOWN_FAILURES exactly.
        final Set<String> regressions = new LinkedHashSet<>(failing);
        regressions.removeAll(KNOWN_FAILURES);              // failing but expected to pass -> regression
        final Set<String> nowFixed = new LinkedHashSet<>(KNOWN_FAILURES);
        nowFixed.removeAll(failing);                        // listed as broken but now passing -> tighten the list

        final StringBuilder msg = new StringBuilder();
        if (!regressions.isEmpty()) {
            msg.append(regressions.size())
                    .append(" name(s) regressed — expected to round-trip cross-surface but failed:")
                    .append(describe(regressions, rows)).append('\n');
        }
        if (!nowFixed.isEmpty()) {
            msg.append(nowFixed.size())
                    .append(" name(s) now round-trip cross-surface but are still listed as known failures.\n")
                    .append("Remove them from KNOWN_FAILURES so they become regression-guarded:")
                    .append(describe(nowFixed, rows)).append('\n');
        }
        if (msg.length() > 0) {
            fail(msg.append("--- current matrix ---\n").append(matrix).toString());
        }
    }

    /** Render a set of corpus labels as "    - label (requested-name)" lines for failure messages. */
    private static String describe(final Set<String> labels, final List<Row> rows) {
        final StringBuilder sb = new StringBuilder();
        for (final Row r : rows) {
            if (labels.contains(r.label)) {
                sb.append("\n    - ").append(r.label).append(" (").append(r.requestedName).append(')');
            }
        }
        return sb.toString();
    }

    // ---- WebDAV helpers (raw HTTP, so the on-the-wire encoding is explicit) ----

    /** Raw HTTP PUT to the WebDAV endpoint; returns the HTTP status (or -1), appending any body. */
    private static int webdavPut(final String dbPath, final String content, final StringBuilder bodyOut) {
        try {
            final URL url = URI.create(webdavBase() + encodePathSegments(dbPath)).toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/xml");
            conn.setDoOutput(true);
            try (final OutputStream os = conn.getOutputStream()) {
                os.write(content.getBytes(UTF_8));
            }
            final int code = conn.getResponseCode();
            if (bodyOut != null) {
                bodyOut.append(readBody(conn));
            }
            conn.disconnect();
            return code;
        } catch (final Exception e) {
            if (bodyOut != null) {
                bodyOut.append(e);
            }
            return -1;
        }
    }

    /** Raw HTTP GET from the WebDAV endpoint; true if 200 and content matches. */
    private static Boolean webdavGet(final String dbPath) {
        try {
            final URL url = URI.create(webdavBase() + encodePathSegments(dbPath)).toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestMethod("GET");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                conn.disconnect();
                return false;
            }
            final String body = readBody(conn);
            conn.disconnect();
            return body.contains(MARKER);
        } catch (final Exception e) {
            return false;
        }
    }

    private static String webdavBase() {
        return "http://localhost:" + existWebServer.getPort() + "/webdav";
    }

    // ---- REST helpers ----

    /** GET a db path via the REST interface; returns true if 200 and content matches. */
    private Boolean restGet(final String dbPath) {
        try {
            final String encoded = encodePathSegments(dbPath);
            final URL url = URI.create(restBase() + encoded).toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestMethod("GET");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                conn.disconnect();
                return false;
            }
            final String body = readBody(conn);
            conn.disconnect();
            return body.contains(MARKER);
        } catch (final Exception e) {
            return false;
        }
    }

    /** Raw HTTP PUT to the REST endpoint (auto-creates parent collections); returns the HTTP status. */
    private static int restPut(final String dbPath, final String content) {
        try {
            final URL url = URI.create(restBase() + encodePathSegments(dbPath)).toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/xml");
            conn.setDoOutput(true);
            try (final OutputStream os = conn.getOutputStream()) {
                os.write(content.getBytes(UTF_8));
            }
            final int code = conn.getResponseCode();
            conn.disconnect();
            return code;
        } catch (final Exception e) {
            return -1;
        }
    }


    /**
     * The single stored child resource name under the test collection (null if zero, "?multiple:..." if >1).
     *
     * Uses eXist's native REST collection listing (a built-in of the REST servlet) rather than an
     * {@code xmldb:*} query, because the WebDAV module's test {@code conf.xml} registers no XQuery
     * builtin-modules — so this harness must not depend on any XQuery module.
     */
    private String soleStoredName() {
        final List<String> names = restListChildResources(TEST_COLLECTION);
        if (names.isEmpty()) {
            return null;
        }
        return names.size() == 1 ? names.get(0) : "?multiple:" + String.join(",", names);
    }

    /** GET a collection via REST and extract the child {@code <exist:resource name="..."/>} names. */
    private List<String> restListChildResources(final String collection) {
        final List<String> names = new ArrayList<>();
        try {
            final URL url = URI.create(restBase() + encodePathSegments(collection)).toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestMethod("GET");
            conn.connect();
            final String body = readBody(conn);
            conn.disconnect();
            final Matcher m = Pattern.compile("<exist:resource[^>]*\\sname=\"([^\"]*)\"").matcher(body);
            while (m.find()) {
                names.add(unescapeXml(m.group(1)));
            }
        } catch (final Exception ignored) {
            // treat as empty listing
        }
        return names;
    }

    /** Raw HTTP DELETE against the REST endpoint (built-in; needs no XQuery module). */
    private static void restDelete(final String dbPath) {
        try {
            final URL url = URI.create(restBase() + encodePathSegments(dbPath)).toURL();
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", basicAuth());
            conn.setRequestMethod("DELETE");
            conn.connect();
            conn.getResponseCode();
            conn.disconnect();
        } catch (final Exception ignored) {
            // best-effort cleanup
        }
    }

    private static String unescapeXml(final String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    private static String restBase() {
        return "http://localhost:" + existWebServer.getPort() + "/rest";
    }

    private static String basicAuth() {
        return "Basic " + java.util.Base64.getEncoder().encodeToString(
                (TestUtils.ADMIN_DB_USER + ":" + TestUtils.ADMIN_DB_PWD).getBytes(UTF_8));
    }

    private static String readBody(final HttpURLConnection conn) throws IOException {
        final java.io.InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            return "";
        }
        try (is; final OutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return baos.toString();
        }
    }

    /** Percent-encode each path segment (between slashes) per RFC 3986, leaving '/' as separators. */
    private static String encodePathSegments(final String path) {
        final String[] segs = path.split("/", -1);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            // URLEncoder is form-encoding; convert '+' to %20 to get RFC 3986 path-segment encoding
            sb.append(java.net.URLEncoder.encode(segs[i], UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    // ---- matrix rendering ----

    private static final class Row {
        final String label;
        final String requestedName;
        boolean created;
        String storedName;
        Boolean webdavReadBack;
        Boolean restReadBack;
        String error;

        Row(final String label, final String requestedName) {
            this.label = label;
            this.requestedName = requestedName;
        }
    }

    private static String renderMatrix(final String title, final List<Row> rows) {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n=== Resource-naming conformance: ").append(title).append(" ===\n");
        sb.append(String.format("%-16s %-18s %-8s %-22s %-10s %-9s %s%n",
                "probe", "requested", "created", "stored-as", "dav-read", "rest-read", "error"));
        for (final Row r : rows) {
            final boolean stored = r.storedName != null && r.storedName.equals(r.requestedName);
            sb.append(String.format("%-16s %-18s %-8s %-22s %-10s %-9s %s%n",
                    r.label,
                    r.requestedName,
                    r.created ? "ok" : "FAIL",
                    r.storedName == null ? "-" : (r.storedName + (stored ? "" : " (≠)")),
                    cell(r.webdavReadBack),
                    cell(r.restReadBack),
                    r.error == null ? "" : r.error));
        }
        sb.append("Legend: stored-as '(≠)' = stored name differs from requested; ")
                .append("dav-read/rest-read = content round-tripped via that surface.\n");
        return sb.toString();
    }

    private static String cell(final Boolean b) {
        if (b == null) {
            return "-";
        }
        return b ? "PASS" : "FAIL";
    }
}
