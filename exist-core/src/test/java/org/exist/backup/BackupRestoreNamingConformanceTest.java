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
package org.exist.backup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.exist.TestUtils;
import org.exist.backup.restore.listener.LogRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Backup &rarr; restore round-trip conformance for awkward resource names.
 *
 * <p>Extends the resource-naming conformance net (see {@code ResourceNamingConformanceTest} in the webdav
 * module, and issue #6463) to the backup/restore path @line-o asked about on PR #6508: for a corpus of
 * awkward leaf names (spaces, sub-delimiters, non-ASCII, CJK, Cyrillic, and the literal-{@code %} cases
 * Decision 2 flags), it stores each resource, snapshots the exact stored key and content, exports a full
 * backup, wipes the collection, restores, and asserts the snapshot is reproduced <em>byte-for-byte</em> —
 * no resource lost, renamed, or collided by the round-trip. That is the guard against "even theoretical
 * data loss" through backup/restore.</p>
 *
 * <p><b>Scope.</b> This guards the <em>restore</em> path. Resources are stored under the same leaf keys a
 * WebDAV/REST store lands them under (verified in the printed mapping: {@code café.xml -> caf%C3%A9.xml},
 * {@code a+b.xml -> a%2Bb.xml}, {@code with space.xml -> with%20space.xml}, …), via eXist's own
 * {@code encodeXmldbUriFor}. Two distinct names collapsing onto one key <em>on the way in</em> — the
 * Decision 2 store-time collision — rides a different store path (the persistent layer's non-escaping
 * encoding) and is out of scope here; this encoder escapes a literal {@code %} to {@code %25}, so the
 * corpus stores injectively and the {@code before}-snapshot size check is a setup-integrity guard, not a
 * claim about that other path.</p>
 *
 * <p>Parameterized over {@link SystemExport}'s direct / non-direct modes and plain / zip, mirroring
 * {@link SystemExportImportTest}.</p>
 */
@RunWith(Parameterized.class)
public class BackupRestoreNamingConformanceTest {

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/backup-naming-conformance");
    private static final String MARKER = "backup-naming-probe";

    /** Awkward human-intended leaf names. {@code /} is excluded (path separator); the literal-{@code %} cases are included. */
    private static final List<String> XML_NAMES = List.of(
            "plain.xml",            // control: must always survive
            "with space.xml",
            "a+b.xml",
            "a%b.xml",              // literal percent (not a valid %XX escape)
            "a%20b.xml",            // the literal text "%20", not a space
            "a@b.xml",
            "a&b.xml",
            "report(2024).xml",
            "o'brien.xml",
            "café.xml",
            "Привет.xml",
            "文書.xml");

    private static final String BINARY_NAME = "résumé.bin";
    private static final byte[] BINARY_CONTENT = "binary résumé payload".getBytes(UTF_8);

    @Parameter
    public String apiName;

    @Parameter(value = 1)
    public boolean direct;

    @Parameter(value = 2)
    public boolean zip;

    @Parameters(name = "{0} zip:{2}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"direct", true, false},
                {"non-direct", false, false},
                {"direct", true, true},
                {"non-direct", false, true}
        });
    }

    /** Unique XML content per resource, so the byte-for-byte comparison is meaningful and any collision shows. */
    private static String contentFor(final String humanName) {
        final String escaped = humanName
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        return "<probe name=\"" + escaped + "\">" + MARKER + "</probe>";
    }

    private static XmldbURI storedKeyFor(final String humanName) throws Exception {
        // eXist's own encoder: the leaf key a WebDAV/REST store would land this name under.
        return URIUtils.encodeXmldbUriFor(TEST_COLLECTION.toString() + "/" + humanName).lastSegment();
    }

    @Before
    public void storeCorpus() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {

            final Collection existing = broker.getCollection(TEST_COLLECTION);
            if (existing != null) {
                broker.removeCollection(txn, existing);
            }
            final Collection test = broker.getOrCreateCollection(txn, TEST_COLLECTION);
            broker.saveCollection(txn, test);

            for (final String name : XML_NAMES) {
                broker.storeDocument(txn, storedKeyFor(name), new StringInputSource(contentFor(name)), MimeType.XML_TYPE, test);
            }
            broker.storeDocument(txn, storedKeyFor(BINARY_NAME), new StringInputSource(BINARY_CONTENT), MimeType.BINARY_TYPE, test);

            txn.commit();
        }
    }

    @After
    public void removeCorpus() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection test = broker.getCollection(TEST_COLLECTION);
            if (test != null) {
                broker.removeCollection(txn, test);
            }
            txn.commit();
        }
    }

    @Test
    public void awkwardNamesSurviveBackupRestore() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        // snapshot the stored (key -> content) state BEFORE the round-trip
        final Map<String, String> before = snapshot(pool);
        printMapping("before backup", before);

        // setup-integrity: every corpus name must have stored as its own distinct key in this path, so the
        // round-trip below genuinely exercises all of them (not fewer after an accidental store-time clash).
        assertEquals("setup stored fewer resources than the corpus — a name clashed on the way in; see the "
                        + "printed mapping", XML_NAMES.size() + 1, before.size());

        // export a full backup
        final Path backup;
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final SystemExport export = new SystemExport(broker, txn, null, null, direct);
            backup = export.export(temporaryFolder.newFolder().getAbsolutePath(), false, zip, null);
            txn.commit();
        }

        // wipe the collection
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection test = broker.getCollection(TEST_COLLECTION);
            assertNotNull(test);
            broker.removeCollection(txn, test);
            txn.commit();
        }

        // restore from the backup
        final RestoreListener listener = new LogRestoreListener();
        new SystemImport(pool).restore(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD, null, backup, listener);

        // snapshot AFTER the round-trip
        final Map<String, String> after = snapshot(pool);
        printMapping("after restore", after);

        // the round-trip must be faithful: the same stored keys, each with byte-for-byte identical content,
        // none dropped, renamed, or collided.
        assertEquals("backup -> restore changed the set of stored resource names", before.keySet(), after.keySet());
        assertEquals("backup -> restore did not reproduce every resource's content byte-for-byte", before, after);
    }

    /** stored-key -&gt; content for every document under the test collection (XML serialized; binary as a UTF-8 string). */
    private Map<String, String> snapshot(final BrokerPool pool) throws Exception {
        final Map<String, String> out = new TreeMap<>();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection test = broker.getCollection(TEST_COLLECTION);
            assertNotNull("test collection missing", test);
            for (final Iterator<DocumentImpl> it = test.iterator(broker); it.hasNext(); ) {
                final DocumentImpl doc = it.next();
                final String key = doc.getFileURI().toString();       // the exact stored key
                final String content;
                if (doc instanceof BinaryDocument bin) {
                    try (final InputStream is = broker.getBinaryResource(txn, bin)) {
                        content = "binary:" + InputStreamUtil.readString(is, UTF_8);
                    }
                } else {
                    content = serialize(broker, doc);
                }
                out.put(key, content);
            }
            txn.commit();
        }
        return out;
    }

    private String serialize(final DBBroker broker, final DocumentImpl doc) throws Exception {
        final Serializer serializer = broker.borrowSerializer();
        try {
            serializer.setUser(broker.getCurrentSubject());
            return serializer.serialize(doc);
        } finally {
            broker.returnSerializer(serializer);
        }
    }

    private void printMapping(final String phase, final Map<String, String> snapshot) {
        final StringBuilder sb = new StringBuilder("\n=== Backup/restore naming conformance (")
                .append(apiName).append(", zip:").append(zip).append(") — ").append(phase).append(" ===\n");
        for (final String key : snapshot.keySet()) {
            sb.append("    ").append(key).append('\n');
        }
        sb.append("  ").append(snapshot.size()).append(" stored resource(s)\n");
        System.out.println(sb);
    }
}
