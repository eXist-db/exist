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
import org.exist.xquery.util.URIUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 *   <li><b>WebDAV</b> (Apache Jackrabbit server since PR #6364) — probed via {@link HttpClient}.</li>
 *   <li><b>REST</b> — via {@link HttpClient}.</li>
 *   <li>an <b>oracle</b> for the stored name — eXist's native REST collection listing (no XQuery module
 *       needed; the WebDAV module's test conf.xml registers no XQuery builtin-modules), reporting what
 *       name actually got stored under the test collection.</li>
 * </ul>
 *
 * <p>The harness is module- and WebDAV-client-independent: the test collection is created by a REST PUT
 * (which auto-creates it), and all probes go over {@link HttpClient}. Request URLs are built with the
 * multi-argument {@link URI} constructor, which percent-encodes the path; note this leaves RFC 3986
 * sub-delimiters ({@code + @ & ( ) '}) literal on the wire, which is itself part of the encoding
 * behavior this harness characterizes. Probe content is valid XML because the corpus names end in
 * {@code .xml} and eXist parses {@code .xml} resources on store.</p>
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

    private static HttpClient http;

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
        // characters macOS/Windows reject in filenames (eXist-db/exist#6463 follow-up): ':' and '*'
        // are RFC 3986 pchar; the rest are not. Probes whether REST/WebDAV agree with the canonical
        // codec on these (the stored-form oracle), and whether they store at all.
        CORPUS.put("colon",           "a:b.xml");
        CORPUS.put("asterisk",        "a*b.xml");
        CORPUS.put("angle-brackets",  "a<b>.xml");
        CORPUS.put("pipe",            "a|b.xml");
        CORPUS.put("dquote",          "a\"b.xml");
    }

    /**
     * The ratchet allowlist: corpus labels that do NOT round-trip cross-surface (stored via WebDAV,
     * then read back by the requested name via both WebDAV and REST). The set of names that currently
     * fail must equal this set exactly.
     *
     * <p>It is <b>empty</b>: with request URLs built the standard way (the multi-argument
     * {@link URI} constructor, which leaves RFC 3986 sub-delimiters such as {@code + @ & ( ) '} literal
     * in the path, exactly as a browser or {@code curl} sends them), every corpus name — including
     * those with sub-delimiters and non-ASCII characters — round-trips between WebDAV and REST. eXist
     * may store a percent-encoded form (e.g. a space as {@code %20}; see the {@code (≠)} markers in the
     * printed matrix), but both surfaces agree on read, so the content is reachable by the requested
     * name. An earlier revision of this harness percent-encoded sub-delimiters in the request URL and
     * saw five "failures" ({@code + @ & ( ) '}); those were artifacts of that encoding choice, not of
     * eXist's storage, and disappear once the URL is encoded conventionally.</p>
     *
     * <p>The ratchet therefore guards the <em>good</em> state: if any corpus name ever stops
     * round-tripping cross-surface, the test fails as a regression. If a future change makes a new,
     * not-yet-covered name fail, add it here with a comment. See the resource-naming tasking
     * (issues #3795, #3665, #1824, #5299, #1612).</p>
     */
    private static final Set<String> KNOWN_FAILURES = Set.of(
            // A literal ':' cannot round-trip on any surface. eXist's XmldbURI wraps java.net.URI,
            // and a ':' makes the segment look like it carries a scheme -- xmldb:store rejects it,
            // and a WebDAV PUT of ".../a:b.xml" returns HTTP 500 (verified). The canonical codec
            // therefore encodes ':' to %3A (URIUtils.encodeForURILenient); a client must likewise
            // send %3A to address such a name. Tracked under eXist-db/exist#6463. (Separately, the
            // 500 should arguably be a 4xx -- a pre-existing WebDAV error-mapping nit.)
            "colon");

    @BeforeClass
    public static void createTestCollection() {
        // HttpClient is AutoCloseable (Java 21); created here and closed in @AfterClass below.
        http = HttpClient.newHttpClient();
        freshCollection();
    }

    @AfterClass
    public static void removeTestCollection() {
        restDelete(TEST_COLLECTION);
        http.close();
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
                // CREATE via WebDAV — HTTP PUT with the URI constructor encoding the path
                final StringBuilder putBody = new StringBuilder();
                final int putCode = webdavPut(TEST_COLLECTION + "/" + name, CONTENT, putBody);
                row.created = putCode == 200 || putCode == 201 || putCode == 204;
                if (!row.created) {
                    row.error = "PUT " + putCode + ": " + putBody.toString().replaceAll("\\s+", " ").trim();
                }

                // ORACLE: what name actually landed in storage?
                row.storedName = soleStoredName();

                // READ-BACK via WebDAV (self round-trip, HTTP GET) — by the name the user PUT
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

        // Cross-surface storage convergence (eXist-db/exist#6463): the form REST/WebDAV actually
        // store must equal the canonical lenient codec URIUtils.encodeForURILenient(requestedName)
        // -- the same encoder xmldb:store/create-collection now apply. This proves the HTTP surfaces
        // and the xmldb: layer agree on one stored key for every name, including the literal-percent
        // cases (a%b -> a%25b, a%20b -> a%2520b) which must stay distinct.
        final StringBuilder storeMismatch = new StringBuilder();
        for (final Row r : rows) {
            if (r.created && r.storedName != null) {
                final String canonical = URIUtils.encodeForURILenient(r.requestedName);
                if (!canonical.equals(r.storedName)) {
                    storeMismatch.append("\n    - ").append(r.label)
                            .append(": stored '").append(r.storedName)
                            .append("' but canonical lenient codec is '").append(canonical).append('\'');
                }
            }
        }
        if (storeMismatch.length() > 0) {
            fail("REST/WebDAV stored form diverges from the canonical lenient codec "
                    + "(URIUtils.encodeForURILenient):" + storeMismatch
                    + "\n--- current matrix ---\n" + matrix);
        }

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

    // ---- WebDAV helpers (java.net.http.HttpClient; the multi-arg URI constructor encodes the path) ----

    /** HTTP PUT to the WebDAV endpoint; returns the HTTP status (or -1), appending any body. */
    private static int webdavPut(final String dbPath, final String content, final StringBuilder bodyOut) {
        try {
            final HttpRequest req = authed(endpointUri("/webdav", dbPath))
                    .header("Content-Type", "application/xml")
                    .PUT(HttpRequest.BodyPublishers.ofString(content, UTF_8))
                    .build();
            final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
            if (bodyOut != null) {
                bodyOut.append(resp.body());
            }
            return resp.statusCode();
        } catch (final Exception e) {
            restoreInterrupt(e);
            if (bodyOut != null) {
                bodyOut.append(e);
            }
            return -1;
        }
    }

    /** HTTP GET from the WebDAV endpoint; true if 200 and content matches. */
    private static Boolean webdavGet(final String dbPath) {
        return getMatchesMarker(endpointUri("/webdav", dbPath));
    }

    // ---- REST helpers ----

    /** GET a db path via the REST interface; returns true if 200 and content matches. */
    private Boolean restGet(final String dbPath) {
        return getMatchesMarker(endpointUri("/rest", dbPath));
    }

    /** HTTP PUT to the REST endpoint (auto-creates parent collections); returns the HTTP status. */
    private static int restPut(final String dbPath, final String content) {
        try {
            final HttpRequest req = authed(endpointUri("/rest", dbPath))
                    .header("Content-Type", "application/xml")
                    .PUT(HttpRequest.BodyPublishers.ofString(content, UTF_8))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (final Exception e) {
            restoreInterrupt(e);
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
            final HttpRequest req = authed(endpointUri("/rest", collection)).GET().build();
            final String body = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8)).body();
            final Matcher m = Pattern.compile("<exist:resource[^>]*\\sname=\"([^\"]*)\"").matcher(body);
            while (m.find()) {
                names.add(unescapeXml(m.group(1)));
            }
        } catch (final Exception e) {
            restoreInterrupt(e);
            // treat as empty listing
        }
        return names;
    }

    /** HTTP DELETE against the REST endpoint (built-in; needs no XQuery module). */
    private static void restDelete(final String dbPath) {
        try {
            final HttpRequest req = authed(endpointUri("/rest", dbPath)).DELETE().build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (final Exception e) {
            restoreInterrupt(e);
            // best-effort cleanup
        }
    }

    /** Send a GET and report whether the response is 200 and its body contains the probe marker. */
    private static Boolean getMatchesMarker(final URI uri) {
        try {
            final HttpResponse<String> resp = http.send(authed(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString(UTF_8));
            return resp.statusCode() == 200 && resp.body().contains(MARKER);
        } catch (final Exception e) {
            restoreInterrupt(e);
            return false;
        }
    }

    /**
     * Build the request URI for an endpoint and db path. The multi-argument {@link URI} constructor
     * percent-encodes the path component (and, per RFC 3986, leaves sub-delimiters such as
     * {@code + @ & ( ) '} literal — which is itself part of what this harness characterizes).
     */
    private static URI endpointUri(final String endpoint, final String dbPath) {
        try {
            return new URI("http", null, "localhost", existWebServer.getPort(), endpoint + dbPath, null, null);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("cannot build URI for " + endpoint + dbPath, e);
        }
    }

    private static HttpRequest.Builder authed(final URI uri) {
        return HttpRequest.newBuilder(uri).header("Authorization", basicAuth());
    }

    /** Re-assert the interrupt flag if a blocking HttpClient call was interrupted. */
    private static void restoreInterrupt(final Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String unescapeXml(final String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    private static String basicAuth() {
        return "Basic " + java.util.Base64.getEncoder().encodeToString(
                (TestUtils.ADMIN_DB_USER + ":" + TestUtils.ADMIN_DB_PWD).getBytes(UTF_8));
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
