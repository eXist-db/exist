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
package org.exist.indexing.range;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolConstants;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.util.FileUtils;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A range or full-text index written with other field names, or by a Lucene version too old to open, such as an
 * index of eXist-db 6, is rebuilt from the documents at startup; one in this format is not. See
 * https://github.com/eXist-db/exist/issues/6989.
 */
class IndexFormatRebuildTest {

    private static final String COLLECTION = "/db/test-index-format-rebuild";

    private static final String CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <range><create qname="item" type="xs:string"/></range>
                    <lucene><text qname="item"/></lucene>
                </index>
            </collection>""";

    /** An entry under a name this version neither writes nor removes. */
    private static final String STALE_FIELD = "staleProbe";

    @RegisterExtension
    final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeEach
    void storeDocuments() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, XmldbURI.create(COLLECTION));
            broker.saveCollection(txn, collection);
            final CollectionConfigurationManager configurations = pool.getConfigurationManager();
            configurations.addConfiguration(txn, broker, collection, CONFIG);
            for (int i = 1; i <= 3; i++) {
                broker.storeDocument(txn, XmldbURI.create("d" + i + ".xml"),
                        new StringInputSource("<wrap><item>" + (i <= 2 ? "hit" : "miss") + "</item></wrap>"),
                        MimeType.XML_TYPE, collection);
            }
            txn.commit();
        }
        assertMatches();
    }

    @Test
    void indexesInAnotherFormatAreRebuiltAtStartup() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        for (final String index : new String[] {"range", "lucene"}) {
            addEntry(dataDir.resolve(index), false);
        }

        existEmbeddedServer.startDb();

        for (final String index : new String[] {"range", "lucene"}) {
            try (final Directory directory = FSDirectory.open(dataDir.resolve(index))) {
                assertNull(fieldInfo(directory), index + " index still has the entries of the old format");
                assertEquals(LuceneIndex.FORMAT, SegmentInfos.readLatestCommit(directory).getUserData().get(LuceneIndex.FORMAT_KEY),
                        index + " index does not record its format");
            }
        }
        assertFalse(Files.exists(dataDir.resolve(BrokerPoolConstants.INDEX_REBUILD_ATTEMPTS_FILE)), "the finished rebuild is still counted");
        assertMatches();
    }

    /** A rebuild that did not finish at the last startups is tried again, up to the limit. */
    @Test
    void aRebuildIsTriedAgainBelowTheLimit() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        for (final String index : new String[] {"range", "lucene"}) {
            addEntry(dataDir.resolve(index), false);
        }
        Files.writeString(dataDir.resolve(BrokerPoolConstants.INDEX_REBUILD_ATTEMPTS_FILE), Integer.toString(BrokerPoolConstants.MAX_INDEX_REBUILD_ATTEMPTS - 1));

        existEmbeddedServer.startDb();

        for (final String index : new String[] {"range", "lucene"}) {
            try (final Directory directory = FSDirectory.open(dataDir.resolve(index))) {
                assertNull(fieldInfo(directory), index + " index was not rebuilt");
            }
        }
        assertFalse(Files.exists(dataDir.resolve(BrokerPoolConstants.INDEX_REBUILD_ATTEMPTS_FILE)), "the finished rebuild is still counted");
        assertMatches();
    }

    /** A rebuild that never finishes does not stop the database from starting: after the limit it is not tried. */
    @Test
    void aRebuildIsNotTriedAgainAtTheLimit() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        for (final String index : new String[] {"range", "lucene"}) {
            addEntry(dataDir.resolve(index), false);
        }
        final Path attemptsFile = dataDir.resolve(BrokerPoolConstants.INDEX_REBUILD_ATTEMPTS_FILE);
        Files.writeString(attemptsFile, Integer.toString(BrokerPoolConstants.MAX_INDEX_REBUILD_ATTEMPTS));

        existEmbeddedServer.startDb();

        for (final String index : new String[] {"range", "lucene"}) {
            try (final Directory directory = FSDirectory.open(dataDir.resolve(index))) {
                assertNotNull(fieldInfo(directory), index + " index was rebuilt after the limit");
            }
        }
        assertEquals(Integer.toString(BrokerPoolConstants.MAX_INDEX_REBUILD_ATTEMPTS), Files.readString(attemptsFile));
    }

    @Test
    void indexesInThisFormatAreNotRebuilt() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        for (final String index : new String[] {"range", "lucene"}) {
            addEntry(dataDir.resolve(index), true);
        }

        existEmbeddedServer.startDb();

        for (final String index : new String[] {"range", "lucene"}) {
            try (final Directory directory = FSDirectory.open(dataDir.resolve(index))) {
                assertNotNull(fieldInfo(directory), index + " index was rebuilt although it is in this format");
            }
        }
        assertMatches();
    }

    @Test
    void indexesTooOldToOpenAreRebuiltAtStartup() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        for (final String index : new String[] {"range", "lucene"}) {
            final Path indexDir = dataDir.resolve(index);
            clear(indexDir);
            try (final Directory directory = FSDirectory.open(indexDir);
                    final IndexOutput segments = directory.createOutput("segments_1", IOContext.DEFAULT)) {
                // the header of the segments file Lucene 4.10, and so eXist-db 6, writes: format 3, which Lucene 10
                // refuses with IndexFormatTooOldException
                CodecUtil.writeHeader(segments, "segments", 3);
            }
        }

        existEmbeddedServer.startDb();

        for (final String index : new String[] {"range", "lucene"}) {
            try (final Directory directory = FSDirectory.open(dataDir.resolve(index))) {
                final Map<String, String> userData = SegmentInfos.readLatestCommit(directory).getUserData();
                assertEquals(LuceneIndex.FORMAT, userData.get(LuceneIndex.FORMAT_KEY), index + " index does not record its format");
                assertNull(userData.get("exist.rebuild-pending"), index + " index still waits for a rebuild");
            }
        }
        assertMatches();
    }

    /** An index emptied because it was too old to open is rebuilt at a later startup if the first did not rebuild it. */
    @Test
    void anEmptiedIndexWaitingForARebuildIsRebuiltAtStartup() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        for (final String index : new String[] {"range", "lucene"}) {
            final Path indexDir = dataDir.resolve(index);
            clear(indexDir);
            Files.createDirectories(indexDir.resolve("taxonomy"));
            try (final Directory directory = FSDirectory.open(indexDir);
                    final IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
                writer.setLiveCommitData(Map.of("exist.rebuild-pending", "true").entrySet());
                writer.commit();
            }
        }

        existEmbeddedServer.startDb();

        // the index had no entries: only a rebuild finds the documents
        assertMatches();
        for (final String index : new String[] {"range", "lucene"}) {
            try (final Directory directory = FSDirectory.open(dataDir.resolve(index))) {
                assertTrue(SegmentInfos.readLatestCommit(directory).totalMaxDoc() > 0, index + " index was not rebuilt");
            }
        }
    }

    private static void clear(final Path indexDir) throws Exception {
        try (final Stream<Path> files = Files.list(indexDir)) {
            files.forEach(FileUtils::deleteQuietly);
        }
    }

    /**
     * Adds an entry under {@link #STALE_FIELD} to a stopped index, keeping the format of its last commit or, as an
     * index written before the format was recorded, removing it.
     */
    private static void addEntry(final Path indexDir, final boolean keepFormat) throws Exception {
        try (final Directory directory = FSDirectory.open(indexDir);
                final IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.APPEND))) {
            final Map<String, String> commitData = new HashMap<>();
            if (keepFormat) {
                writer.getLiveCommitData().forEach(entry -> commitData.put(entry.getKey(), entry.getValue()));
                assertEquals(LuceneIndex.FORMAT, commitData.get(LuceneIndex.FORMAT_KEY), indexDir + " does not record its format");
            }
            final Document document = new Document();
            document.add(new StringField(STALE_FIELD, "x", Field.Store.NO));
            writer.addDocument(document);
            writer.setLiveCommitData(commitData.entrySet());
            writer.commit();
        }
    }

    private static FieldInfo fieldInfo(final Directory directory) throws Exception {
        try (final DirectoryReader reader = DirectoryReader.open(directory)) {
            return FieldInfos.getMergedFieldInfos(reader).fieldInfo(STALE_FIELD);
        }
    }

    private void assertMatches() throws Exception {
        assertEquals(2, count("collection('" + COLLECTION + "')//item[range:eq(., 'hit')]"));
        assertEquals(2, count("collection('" + COLLECTION + "')//item[ft:query(., 'hit')]"));
    }

    private long count(final String path) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, xquery.compile(new XQueryContext(pool),
                    "import module namespace range='http://exist-db.org/xquery/range'; count(" + path + ")"), null);
            return result.itemAt(0).toJavaObject(Long.class);
        }
    }
}
