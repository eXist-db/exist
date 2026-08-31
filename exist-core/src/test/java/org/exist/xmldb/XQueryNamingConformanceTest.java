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
package org.exist.xmldb;

import static org.junit.Assert.fail;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * XQuery-accessor resource-naming conformance — the XQuery-side companion to the cross-surface HTTP harness
 * ({@code org.exist.webdav.ResourceNamingConformanceTest}) and the backup/restore round-trip
 * ({@code org.exist.backup.BackupRestoreNamingConformanceTest}). Same awkward-name corpus, same ratchet
 * discipline; see issue #6463.
 *
 * <p>For every corpus name it stores/creates via {@code xmldb:store} / {@code xmldb:create-collection} in
 * two modes and reads back <b>by the requested name</b> via the XQuery accessors:</p>
 * <ul>
 *   <li><b>resources</b> — read via {@code doc()}, {@code collection()}, {@code xmldb:get-child-resources()};</li>
 *   <li><b>collections</b> — read via {@code xmldb:get-child-collections()}.</li>
 * </ul>
 * <p>and in two <b>modes</b>: <b>raw</b> (the naive {@code xmldb:store($col, $name, …)}) and <b>encode</b>
 * (the eXide/TEI {@code xmldb:encode-uri($name)} workaround applied on both the store and the read).</p>
 *
 * <p>Characterized behavior, pinned by {@link #KNOWN_FAILURES} exactly (same ratchet as the sibling tests):
 * {@code doc()}, {@code collection()} and {@code xmldb:get-child-resources()} agree in every case — they are
 * one behavior, not three. Naive {@code xmldb:store}/{@code create-collection} reject a space, a literal
 * {@code %} and a {@code #} outright (FORG0001) and, for non-ASCII names, store an encoded key that a
 * read-by-requested-name then misses (the store&harr;doc disagreement). The {@code encode-uri} workaround,
 * applied consistently on store and read, makes every name round-trip. Entries drop out of the allowlist as
 * the naming contract lands.</p>
 */
public class XQueryNamingConformanceTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String RES_COLL = "/db/naming-xq-res";
    private static final String COLL_PARENT = "/db/naming-xq-coll";

    /** Awkward human-intended names. {@code /} is excluded (path separator); the literal-{@code %} cases are included. */
    private static final List<String> CORPUS = List.of(
            "plain.xml",            // control: must always work
            "with space.xml",
            "a+b.xml",
            "a%b.xml",              // literal percent
            "a%20b.xml",            // literal "%20"
            "a#b.xml",              // hash
            "a@b.xml",
            "a&b.xml",
            "report(2024).xml",
            "o'brien.xml",
            "café.xml",
            "Привет.xml",
            "文書.xml");

    /**
     * Ratchet allowlist: the {@code kind:mode:name:reader} cells that do NOT round-trip by the requested name.
     * The set of failing cells must equal this exactly. Documented current behavior; entries drop out as fixes land.
     * All failures are in <b>raw</b> mode — the {@code encode} workaround round-trips every name — and every reader
     * of a given (kind, name) fails together, confirming the accessors agree.
     */
    private static final Set<String> KNOWN_FAILURES = Set.of(
            // All failures are in RAW mode, and the same six names fail across every accessor — confirming
            // doc()/collection()/get-child-resources() agree, and that get-child-collections mirrors them for
            // collections. The encode-uri workaround round-trips every name (no encode-mode entries here).
            // 'with space', a%b and a#b are rejected on create (FORG0001); the three non-ASCII names store an
            // encoded key that a read-by-requested-name then misses (the store<->doc disagreement).

            // resources — xmldb:store, read by requested name via doc() / collection() / get-child-resources():
            "res:raw:with space.xml:doc", "res:raw:with space.xml:collection", "res:raw:with space.xml:get-child-resources",
            "res:raw:a%b.xml:doc", "res:raw:a%b.xml:collection", "res:raw:a%b.xml:get-child-resources",
            "res:raw:a#b.xml:doc", "res:raw:a#b.xml:collection", "res:raw:a#b.xml:get-child-resources",
            "res:raw:café.xml:doc", "res:raw:café.xml:collection", "res:raw:café.xml:get-child-resources",
            "res:raw:Привет.xml:doc", "res:raw:Привет.xml:collection", "res:raw:Привет.xml:get-child-resources",
            "res:raw:文書.xml:doc", "res:raw:文書.xml:collection", "res:raw:文書.xml:get-child-resources",

            // collections — xmldb:create-collection, read by requested name via get-child-collections():
            "coll:raw:with space.xml:get-child-collections",
            "coll:raw:a%b.xml:get-child-collections",
            "coll:raw:a#b.xml:get-child-collections",
            "coll:raw:café.xml:get-child-collections",
            "coll:raw:Привет.xml:get-child-collections",
            "coll:raw:文書.xml:get-child-collections"
    );

    @Test
    public void xqueryAccessorNamingConformance() {
        final StringBuilder matrix = new StringBuilder("\n=== XQuery-accessor resource-naming matrix (read back by requested name) ===\n");
        final Set<String> failing = new LinkedHashSet<>();

        for (final String mode : List.of("raw", "encode")) {
            matrix.append("\n-- resources, xmldb:store (").append(mode).append(") --\n");
            matrix.append(String.format("%-16s %-8s %-26s %-8s %-8s %-18s%n",
                    "requested", "created", "stored-key", "doc()", "coll()", "get-child-res"));
            for (final String name : CORPUS) {
                freshResources();
                final String nameExpr = "encode".equals(mode) ? "xmldb:encode-uri(" + lit(name) + ")" : lit(name);
                final boolean created = xqStr("xmldb:store(" + lit(RES_COLL) + ", " + nameExpr + ", <probe>M</probe>)").startsWith("/db/");
                final String stored = created ? xqStr("string-join(xmldb:get-child-resources(" + lit(RES_COLL) + "), ',')") : "-";
                final boolean doc = created && xqBool("exists(doc(concat(" + lit(RES_COLL + "/") + ", " + nameExpr + ")))");
                final boolean coll = created && xqBool("exists(collection(" + lit(RES_COLL) + ")[ends-with(document-uri(.), concat('/', " + nameExpr + "))])");
                final boolean gcr = created && xqBool("xmldb:get-child-resources(" + lit(RES_COLL) + ") = " + nameExpr);

                record(failing, "res", mode, name, "doc", doc);
                record(failing, "res", mode, name, "collection", coll);
                record(failing, "res", mode, name, "get-child-resources", gcr);
                matrix.append(String.format("%-16s %-8s %-26s %-8s %-8s %-18s%n",
                        name, created ? "ok" : "FAIL", stored,
                        doc ? "PASS" : "FAIL", coll ? "PASS" : "FAIL", gcr ? "PASS" : "FAIL"));
            }

            matrix.append("\n-- collections, xmldb:create-collection (").append(mode).append(") --\n");
            matrix.append(String.format("%-16s %-8s %-26s %-18s%n", "requested", "created", "stored-key", "get-child-coll"));
            for (final String name : CORPUS) {
                freshCollections();
                final String nameExpr = "encode".equals(mode) ? "xmldb:encode-uri(" + lit(name) + ")" : lit(name);
                final boolean created = xqStr("xmldb:create-collection(" + lit(COLL_PARENT) + ", " + nameExpr + ")").startsWith("/db/");
                final String stored = created ? xqStr("string-join(xmldb:get-child-collections(" + lit(COLL_PARENT) + "), ',')") : "-";
                final boolean gcc = created && xqBool("xmldb:get-child-collections(" + lit(COLL_PARENT) + ") = " + nameExpr);

                record(failing, "coll", mode, name, "get-child-collections", gcc);
                matrix.append(String.format("%-16s %-8s %-26s %-18s%n", name, created ? "ok" : "FAIL", stored, gcc ? "PASS" : "FAIL"));
            }
        }
        System.out.println(matrix);

        final Set<String> regressions = new LinkedHashSet<>(failing);
        regressions.removeAll(KNOWN_FAILURES);
        final Set<String> nowFixed = new LinkedHashSet<>(KNOWN_FAILURES);
        nowFixed.removeAll(failing);

        final StringBuilder msg = new StringBuilder();
        if (!regressions.isEmpty()) {
            msg.append(regressions.size()).append(" accessor cell(s) regressed (kind:mode:name:reader):\n    ")
                    .append(String.join("\n    ", regressions)).append('\n');
        }
        if (!nowFixed.isEmpty()) {
            msg.append(nowFixed.size()).append(" cell(s) now round-trip but are still listed as known failures.\n")
                    .append("Remove them from KNOWN_FAILURES so they become regression-guarded:\n    ")
                    .append(String.join("\n    ", nowFixed)).append('\n');
        }
        if (msg.length() > 0) {
            fail(msg.append("--- current matrix ---").append(matrix).toString());
        }
    }

    private static void record(final Set<String> failing, final String kind, final String mode, final String name, final String reader, final boolean ok) {
        if (!ok) {
            failing.add(kind + ":" + mode + ":" + name + ":" + reader);
        }
    }

    /** An XQuery string literal (double-quoted) for an arbitrary name; only {@code &} and {@code <} need escaping. */
    private static String lit(final String s) {
        return '"' + s.replace("&", "&amp;").replace("<", "&lt;") + '"';
    }

    private void freshResources() {
        xqStr("if (xmldb:collection-available(" + lit(RES_COLL) + ")) then xmldb:remove(" + lit(RES_COLL) + ") else (), "
                + "xmldb:create-collection('/db', 'naming-xq-res')");
    }

    private void freshCollections() {
        xqStr("if (xmldb:collection-available(" + lit(COLL_PARENT) + ")) then xmldb:remove(" + lit(COLL_PARENT) + ") else (), "
                + "xmldb:create-collection('/db', 'naming-xq-coll')");
    }

    private boolean xqBool(final String query) {
        return "true".equals(xqStr(query));
    }

    private String xqStr(final String query) {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, query, null);
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.getItemCount(); i++) {
                if (i > 0) {
                    sb.append('|');
                }
                sb.append(result.itemAt(i).getStringValue());
            }
            return sb.toString();
        } catch (final Exception e) {
            // a create that the naming layer rejects (e.g. FORG0001) surfaces here; treated as "not created".
            return "ERR:" + e.getClass().getSimpleName();
        }
    }
}
