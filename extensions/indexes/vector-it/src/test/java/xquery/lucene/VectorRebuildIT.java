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
package xquery.lucene;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolConstants;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.ConfigurationHelper;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A rebuild at startup of a full-text index in another format embeds the vectors of a field with embedding="local"
 * again and logs it, and finishes and is not repeated even if the model cannot be loaded. See
 * https://github.com/eXist-db/exist/issues/6989.
 * <p>
 * The test that embeds skips when the model is not available. Run with:
 * <pre>
 *   mvn verify -pl extensions/indexes/vector-it -Ponnx-model
 * </pre>
 */
@SuppressWarnings("PMD.ClassNamingConventions") // Failsafe runs the classes named *IT
public class VectorRebuildIT {

    private static final String MODEL = "all-MiniLM-L6-v2";
    private static final String MODEL_PATH = "target/onnx-models/all-MiniLM-L6-v2";
    private static final String COLLECTION = "/db/vector-rebuild-it";

    /** The loggers of the full-text index and of the broker pool, which runs the rebuild. */
    private static final String[] CAPTURED = {"org.exist.indexing.lucene.LuceneIndexWorker", BrokerPool.class.getName()};

    @RegisterExtension
    final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender = new AbstractAppender("vector-rebuild-capture", null, null, false, Property.EMPTY_ARRAY) {
        @Override
        public void append(final LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    };

    @BeforeEach
    void attachAppender() {
        appender.start();
        // the test configuration turns logging off: give the captured loggers their own configuration
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        configuration.addAppender(appender);
        for (final String logger : CAPTURED) {
            final LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                    .withLoggerName(logger)
                    .withLevel(Level.INFO)
                    .withAdditivity(false)
                    .withConfig(configuration)
                    .build();
            loggerConfig.addAppender(appender, Level.INFO, null);
            configuration.addLogger(logger, loggerConfig);
        }
        context.updateLoggers();
    }

    @AfterEach
    void detachAppender() {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        for (final String logger : CAPTURED) {
            context.getConfiguration().removeLogger(logger);
        }
        context.updateLoggers();
        appender.stop();
    }

    @Test
    void aRebuildEmbedsTheVectorsAgainAndLogsThem() throws Exception {
        assumeTrue(hasModel(), "ONNX model not found — run with -Ponnx-model");
        storeDocuments(MODEL_PATH);
        assertEquals(3, countVectorMatches());

        restartWithIndexInAnotherFormat();

        assertEquals(1, count("Reindex: embedding the vectors of field 'embedding' again with model " + MODEL), messages.toString());
        assertEquals(1, count("Reindex: embedded the vectors of 3 nodes again in "), messages.toString());
        assertEquals(1, count("(embedding 3)"), messages.toString());
        assertEquals(3, countVectorMatches());
        assertRebuildNotRepeated();
    }

    /** A model that cannot be loaded leaves the field without vectors, but the rebuild finishes and is not repeated. */
    @Test
    void aRebuildWithoutTheModelFinishes() throws Exception {
        storeDocuments("target/onnx-models/no-such-model");

        restartWithIndexInAnotherFormat();

        assertEquals(0, count("Reindex:"), "nothing was embedded: " + messages);
        assertEquals(3, countArticles());
        assertRebuildNotRepeated();
    }

    private void storeDocuments(final String modelPath) throws Exception {
        final String config = """
                <collection xmlns="http://exist-db.org/collection-config/1.0">
                    <index>
                        <lucene>
                            <text qname="article">
                                <vector-field name="embedding" expression="title" dimension="384" similarity="cosine"
                                    embedding="local" model="%s" model-path="%s"/>
                            </text>
                        </lucene>
                    </index>
                </collection>""".formatted(MODEL, modelPath);
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, XmldbURI.create(COLLECTION));
            broker.saveCollection(txn, collection);
            pool.getConfigurationManager().addConfiguration(txn, broker, collection, config);
            final String[] titles = {"Hello world", "Machine learning", "Quantum physics"};
            for (int i = 0; i < titles.length; i++) {
                broker.storeDocument(txn, XmldbURI.create("d" + i + ".xml"),
                        new StringInputSource("<article><title>" + titles[i] + "</title></article>"),
                        MimeType.XML_TYPE, collection);
            }
            txn.commit();
        }
    }

    /** Stops the database, adds an entry to the full-text index in a commit without its format, and starts it again. */
    private void restartWithIndexInAnotherFormat() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        try (final Directory directory = FSDirectory.open(dataDir.resolve("lucene"));
                final IndexWriter writer = new IndexWriter(directory,
                        new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.APPEND))) {
            final Document document = new Document();
            document.add(new StringField("staleProbe", "x", Field.Store.NO));
            writer.addDocument(document);
            writer.setLiveCommitData(Map.<String, String>of().entrySet());
            writer.commit();
        }
        messages.clear();
        existEmbeddedServer.startDb();
        assertEquals(1, count("Rebuilding indexes written in another format: [lucene-index]"), messages.toString());
        assertFalse(Files.exists(dataDir.resolve(BrokerPoolConstants.INDEX_REBUILD_ATTEMPTS_FILE)),
                "the finished rebuild is still counted");
    }

    /** The next startup finds the index in this format: it neither rebuilds nor embeds again. */
    private void assertRebuildNotRepeated() throws Exception {
        final Path dataDir = existEmbeddedServer.getTemporaryStorage().orElseThrow();
        existEmbeddedServer.stopDb(false);
        messages.clear();
        existEmbeddedServer.startDb();
        assertEquals(0, count("Rebuilding indexes"), messages.toString());
        assertEquals(0, count("Reindex:"), messages.toString());
        assertFalse(Files.exists(dataDir.resolve(BrokerPoolConstants.INDEX_REBUILD_ATTEMPTS_FILE)));
    }

    private long count(final String fragment) {
        return messages.stream().filter(message -> message.contains(fragment)).count();
    }

    private long countVectorMatches() throws Exception {
        return query("""
                import module namespace ft="http://exist-db.org/xquery/lucene";
                import module namespace vector="http://exist-db.org/xquery/vector";
                count(collection("%s")//article[ft:query-vector(., vector:embed("Hello world", "%s", "%s"), 3)])
                """.formatted(COLLECTION, MODEL, MODEL_PATH));
    }

    private long countArticles() throws Exception {
        return query("count(collection('" + COLLECTION + "')//article)");
    }

    private long query(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, query, null);
            return result.itemAt(0).toJavaObject(Long.class);
        }
    }

    private static boolean hasModel() {
        final Path modelDir = ConfigurationHelper.getExistHome()
                .orElse(Path.of(System.getProperty("user.dir", ".")))
                .resolve(MODEL_PATH);
        return Files.isRegularFile(modelDir.resolve("model.onnx")) && Files.isRegularFile(modelDir.resolve("tokenizer.json"));
    }
}
