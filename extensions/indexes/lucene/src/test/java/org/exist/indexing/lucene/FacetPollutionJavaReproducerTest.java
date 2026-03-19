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
package org.exist.indexing.lucene;

import com.evolvedbinary.j8fu.function.FunctionE;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FacetPollutionJavaReproducerTest {

    private static final Logger LOG = LogManager.getLogger(FacetPollutionJavaReproducerTest.class);

    private static final String POLLUTER_COLL = "lucene-test-analyzers-index";
    private static final XmldbURI POLLUTER_COLL_URI = XmldbURI.create("/db/" + POLLUTER_COLL);

    private static final String FACETS_COLL = "lucene-test-facets";
    private static final XmldbURI FACETS_COLL_URI = XmldbURI.create("/db/" + FACETS_COLL);

    // Lucene facets delimiter is U+001F
    private static final char FACET_DELIM = (char) 0x1F;

    // Terms we expect for hierarchical facets:
    // subject/humanities/history => "subject\u001Fhumanities\u001Fhistory"
    private static final String EXPECT_SUBJECT = "subject" + FACET_DELIM + "humanities" + FACET_DELIM + "history";
    private static final String EXPECT_LOCATION = "location" + FACET_DELIM + "Germany" + FACET_DELIM + "Berlin";
    // ft-facets.xqm uses 2019-03-14 for the first letter
    private static final String EXPECT_DATE = "date" + FACET_DELIM + "2019" + FACET_DELIM + "03" + FACET_DELIM + "14";

    private static final String POLLUTER_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
            + "<index xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "  <lucene>"
            + "    <analyzer class=\"org.apache.lucene.analysis.standard.StandardAnalyzer\"/>"
            + "    <analyzer id=\"ws\" class=\"org.apache.lucene.analysis.core.WhitespaceAnalyzer\"/>"
            + "    <text qname=\"p\" analyzer=\"ws\"/>"
            + "  </lucene>"
            + "</index>"
            + "</collection>";

    private static final String POLLUTER_XML =
        "<text><body>"
            + "<p>Hello world from polluter</p>"
            + "<p>Another line for analyzers</p>"
            + "</body></text>";

    private static final String FACETS_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
            + "<index xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "  <lucene>"
            + "    <analyzer id=\"std\" class=\"org.apache.lucene.analysis.standard.StandardAnalyzer\"/>"
            + "    <text qname=\"letter\" analyzer=\"std\">"
            + "      <facet dimension=\"location\" hierarchical=\"yes\" expression=\""
            + "        if (string(place)='Berlin') then ('Germany','Berlin')"
            + "        else if (string(place)='Wrocław') then ('Poland','Wrocław')"
            + "        else ()\"/>"
            + "      <facet dimension=\"subject\" hierarchical=\"yes\" expression=\""
            + "        if (string(subject)='history') then ('humanities','history')"
            + "        else if (string(subject)='art') then ('humanities','art')"
            + "        else if (string(subject)='math') then ('science','math')"
            + "        else ()\"/>"
            + "      <facet dimension=\"date\" hierarchical=\"yes\" expression=\"tokenize(string(date), '-')\"/>"
            + "      <facet dimension=\"from\" expression=\"from\"/>"
            + "    </text>"
            + "  </lucene>"
            + "</index>"
            + "</collection>";

    private static final String FACETS_LETTERS_XML =
        "<letters>"
            + "<letter>"
            + "  <from>Susi</from>"
            + "  <to>Hans</to>"
            + "  <place>Berlin</place>"
            + "  <date>2019-03-08</date>"
            + "  <subject>history</subject>"
            + "</letter>"
            + "<letter>"
            + "  <from>Heinz</from>"
            + "  <to>Babsi</to>"
            + "  <place>Wrocław</place>"
            + "  <date>2017-06-22</date>"
            + "  <subject>art</subject>"
            + "</letter>"
            + "</letters>";

    @Test
    public void hierarchicalFacetTermsMissingAfterPolluterRemoval() throws Exception {
        final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);
        server.startDb();

        try {
            final BrokerPool pool = server.getBrokerPool();
            final TransactionManager transact = pool.getTransactionManager();

            final String tempModulesName = "lucene-test-facet-pollution-java-modules-" + System.nanoTime();
            final XmldbURI tempModulesUri = XmldbURI.create("/db/" + tempModulesName);

            final Path baseDir = Paths.get(System.getProperty("user.dir"));
            final Path zAnalyzersPath = resolveModulePath(baseDir,
                "src/test/xquery/lucene/analyzers-field.xqm",
                "extensions/indexes/lucene/src/test/xquery/lucene/analyzers-field.xqm");
            final Path ftFacetsPath = resolveModulePath(baseDir,
                "src/test/xquery/lucene/ft-facets.xqm",
                "extensions/indexes/lucene/src/test/xquery/lucene/ft-facets.xqm");

            LOG.info("Loading polluter module from {}", zAnalyzersPath);
            LOG.info("Loading facets module from {}", ftFacetsPath);

            final String zAnalyzersModule = Files.readString(zAnalyzersPath);
            final String ftFacetsModule = Files.readString(ftFacetsPath);

            try (DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                // Store both XQuery modules into a temporary collection so they can be imported.
                storeModule(broker, transact, tempModulesUri, "analyzers-field.xqm", zAnalyzersModule);
                storeModule(broker, transact, tempModulesUri, "ft-facets.xqm", ftFacetsModule);

                final String anixAt = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + tempModulesUri + "/analyzers-field.xqm";
                final String facetAt = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + tempModulesUri + "/ft-facets.xqm";

                // 1) Polluter setup
                executeXQuery(broker, transact,
                    "import module namespace anix=\"http://exist-db.org/xquery/lucene/analyzers-index/test\" at \"" + anixAt + "\";" +
                    "anix:setUp()");
                // 2) One representative polluter query (mirrors at least part of real XQSuite execution)
                executeXQuery(broker, transact,
                    "import module namespace anix=\"http://exist-db.org/xquery/lucene/analyzers-index/test\" at \"" + anixAt + "\";" +
                    "anix:german-standard-search()");
                // 3) Polluter tearDown
                executeXQuery(broker, transact,
                    "import module namespace anix=\"http://exist-db.org/xquery/lucene/analyzers-index/test\" at \"" + anixAt + "\";" +
                    "anix:tearDown()");
                // 4) Facets setup (indexes facets)
                executeXQuery(broker, transact,
                    "import module namespace facet=\"http://exist-db.org/xquery/lucene/test/facets\" at \"" + facetAt + "\";" +
                    "facet:store()");

                final String idxAt = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + "/db/lucene-test-facets/module.xql";

                // Store-time verification: external hierarchy sources should be readable.
                LOG.info("store-time idx:place-hierarchy('Berlin')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:place-hierarchy('Berlin'), '|')"));
                LOG.info("store-time idx:subject-hierarchy('history')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:subject-hierarchy('history'), '|')"));
                LOG.info("store-time idx:subject-hierarchy(letter[1]/subject)={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "let $l := doc('/db/lucene-test-facets/test.xml')//letter[1]" +
                        "return string-join(idx:subject-hierarchy($l/subject/string()), '|')"));

                // Explicitly run reindex after the store phase.
                executeXQueryNoOuterTxn(broker,
                    "(xmldb:reindex('/db/lucene-test-facets')," +
                    " xmldb:reindex('/db/lucene-test-facets/persons'))");

                LOG.info("polluted idx:place-hierarchy('Berlin')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:place-hierarchy('Berlin'), '|')"));
                LOG.info("polluted idx:subject-hierarchy('history')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:subject-hierarchy('history'), '|')"));
                LOG.info("polluted idx:subject-hierarchy(letter[1]/subject)={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "let $l := doc('/db/lucene-test-facets/test.xml')//letter[1]" +
                        "return string-join(idx:subject-hierarchy($l/subject/string()), '|')"));

                // Inspect Lucene $facets terms after facet indexing.
                final FacetsIndexTerms terms = readFacetsTerms(broker, EXPECT_SUBJECT, EXPECT_LOCATION, EXPECT_DATE);

                LOG.info("facet terms present? subject={} location={} date={}",
                    terms.subjectExists, terms.locationExists, terms.dateExists);
                LOG.info("sample $facets terms (up to 30): {}", terms.sampleTerms);
                LOG.info("subject-prefixed $facets terms (sample): {}", terms.subjectTerms);
                LOG.info("location-prefixed $facets terms (sample): {}", terms.locationTerms);
                LOG.info("date-prefixed $facets terms (sample): {}", terms.dateTerms);
                LOG.info("terms containing humanities/Germany/Berlin/Wroc (sample): {}", terms.termsWithHumanitiesOrGermany);

                assertTrue("Hierarchical facet terms expected but were missing after polluter run. " +
                    "subjectExists=" + terms.subjectExists + " locationExists=" + terms.locationExists + " dateExists=" + terms.dateExists,
                    terms.subjectExists && terms.locationExists && terms.dateExists);

                // Cleanup facets collection (keep temp modules only).
                final String facetTearDownQuery =
                    "import module namespace facet=\"http://exist-db.org/xquery/lucene/test/facets\" at \"" + facetAt + "\";" +
                    "facet:tearDown()";
                executeXQuery(broker, transact, facetTearDownQuery);
            }
        } finally {
            server.stopDb(true);
        }
    }

    @Test
    public void hierarchicalFacetTermsPresentWithoutPolluter() throws Exception {
        final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);
        server.startDb();

        try {
            final BrokerPool pool = server.getBrokerPool();
            final TransactionManager transact = pool.getTransactionManager();

            final String tempModulesName = "lucene-test-facet-pollution-java-modules-" + System.nanoTime();
            final XmldbURI tempModulesUri = XmldbURI.create("/db/" + tempModulesName);

            final Path baseDir = Paths.get(System.getProperty("user.dir"));
            final Path ftFacetsPath = resolveModulePath(baseDir,
                "src/test/xquery/lucene/ft-facets.xqm",
                "extensions/indexes/lucene/src/test/xquery/lucene/ft-facets.xqm");

            LOG.info("Loading facets module from {}", ftFacetsPath);

            final String ftFacetsModule = Files.readString(ftFacetsPath);

            try (DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                storeModule(broker, transact, tempModulesUri, "ft-facets.xqm", ftFacetsModule);

                final String facetAt = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + tempModulesUri + "/ft-facets.xqm";

                // Facets setup (indexes facets) without polluter steps.
                executeXQuery(broker, transact,
                    "import module namespace facet=\"http://exist-db.org/xquery/lucene/test/facets\" at \"" + facetAt + "\";" +
                    "facet:store()");

                final String idxAt = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + "/db/lucene-test-facets/module.xql";

                // Store-time verification: external hierarchy sources should be readable.
                LOG.info("store-time idx:place-hierarchy('Berlin')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:place-hierarchy('Berlin'), '|')"));
                LOG.info("store-time idx:subject-hierarchy('history')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:subject-hierarchy('history'), '|')"));
                LOG.info("store-time idx:subject-hierarchy(letter[1]/subject)={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "let $l := doc('/db/lucene-test-facets/test.xml')//letter[1]" +
                        "return string-join(idx:subject-hierarchy($l/subject/string()), '|')"));

                // Explicitly run reindex after the store phase.
                executeXQueryNoOuterTxn(broker,
                    "(xmldb:reindex('/db/lucene-test-facets')," +
                    " xmldb:reindex('/db/lucene-test-facets/persons'))");

                LOG.info("baseline idx:place-hierarchy('Berlin')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:place-hierarchy('Berlin'), '|')"));
                LOG.info("baseline idx:subject-hierarchy('history')={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "string-join(idx:subject-hierarchy('history'), '|')"));
                LOG.info("baseline idx:subject-hierarchy(letter[1]/subject)={}",
                    executeXQueryToString(broker, transact,
                        "import module namespace idx=\"http://exist-db.org/lucene/test/\" at \"" + idxAt + "\";" +
                        "let $l := doc('/db/lucene-test-facets/test.xml')//letter[1]" +
                        "return string-join(idx:subject-hierarchy($l/subject/string()), '|')"));

                final FacetsIndexTerms terms = readFacetsTerms(broker, EXPECT_SUBJECT, EXPECT_LOCATION, EXPECT_DATE);

                LOG.info("baseline facet terms present? subject={} location={} date={}",
                    terms.subjectExists, terms.locationExists, terms.dateExists);
                LOG.info("baseline subject-prefixed $facets terms (sample): {}", terms.subjectTerms);
                LOG.info("baseline location-prefixed $facets terms (sample): {}", terms.locationTerms);
                LOG.info("baseline date-prefixed $facets terms (sample): {}", terms.dateTerms);
                LOG.info("baseline terms containing humanities/Germany/Berlin/Wroc (sample): {}", terms.termsWithHumanitiesOrGermany);

                assertTrue("Hierarchical facet terms expected in clean state but were missing. " +
                    "subjectExists=" + terms.subjectExists + " locationExists=" + terms.locationExists + " dateExists=" + terms.dateExists,
                    terms.subjectExists && terms.locationExists && terms.dateExists);

                // Cleanup facets collection.
                final String facetTearDownQuery =
                    "import module namespace facet=\"http://exist-db.org/xquery/lucene/test/facets\" at \"" + facetAt + "\";" +
                    "facet:tearDown()";
                executeXQuery(broker, transact, facetTearDownQuery);
            }
        } finally {
            server.stopDb(true);
        }
    }

    private static void storeModule(
        final DBBroker broker,
        final TransactionManager transact,
        final XmldbURI moduleCollectionUri,
        final String moduleDocName,
        final String moduleContent
    ) throws Exception {
        try (Txn transaction = transact.beginTransaction()) {
            final Collection coll = broker.getOrCreateCollection(transaction, moduleCollectionUri);
            broker.saveCollection(transaction, coll);
            broker.storeDocument(transaction,
                XmldbURI.create(moduleDocName),
                new StringInputSource(moduleContent.getBytes(StandardCharsets.UTF_8)),
                MimeType.XQUERY_TYPE,
                coll);
            transaction.commit();
        }
    }

    private static void executeXQuery(final DBBroker broker, final TransactionManager transact, final String xquery)
        throws Exception {
        try (Txn transaction = transact.beginTransaction()) {
            final XQuery query = broker.getBrokerPool().getXQueryService();
            query.execute(broker, xquery, null);
            transaction.commit();
        }
    }

    private static void executeXQueryNoOuterTxn(final DBBroker broker, final String xquery)
        throws Exception {
        // Avoid wrapping in an explicit outer transaction; let xmldb:reindex manage its own boundaries.
        final XQuery query = broker.getBrokerPool().getXQueryService();
        query.execute(broker, xquery, null);
    }

    private static String executeXQueryToString(final DBBroker broker, final TransactionManager transact, final String xquery)
        throws Exception {
        try (Txn transaction = transact.beginTransaction()) {
            final XQuery query = broker.getBrokerPool().getXQueryService();
            final Sequence result = query.execute(broker, xquery, null);
            transaction.commit();
            return result == null ? "" : result.getStringValue();
        }
    }

    private static Path resolveModulePath(final Path baseDir, final String relativeFromBase1, final String relativeFromBase2)
        throws IOException {
        final Path candidate1 = baseDir.resolve(relativeFromBase1);
        if (Files.exists(candidate1)) {
            return candidate1;
        }
        final Path candidate2 = baseDir.resolve(relativeFromBase2);
        if (Files.exists(candidate2)) {
            return candidate2;
        }
        throw new IOException("Could not locate required XQuery module file under " + baseDir +
            ": tried [" + candidate1 + "] and [" + candidate2 + "]");
    }

    private static void createAndReindexCollection(
        final DBBroker broker,
        final TransactionManager transact,
        final XmldbURI collUri,
        final String configXml,
        final String docName,
        final String docXml
    ) throws Exception {
        final CollectionConfigurationManager mgr = broker.getBrokerPool().getConfigurationManager();
        final XmldbURI relativeDocUri = XmldbURI.create(docName);

        try (Txn transaction = transact.beginTransaction()) {
            final Collection coll = broker.getOrCreateCollection(transaction, collUri);
            broker.saveCollection(transaction, coll);

            mgr.addConfiguration(transaction, broker, coll, configXml);

            broker.storeDocument(transaction, relativeDocUri, new StringInputSource(docXml), MimeType.XML_TYPE, coll);
            transaction.commit();
        }

        try (Txn transaction = transact.beginTransaction()) {
            broker.reindexCollection(transaction, collUri);
            transaction.commit();
        }
    }

    private static void removeCollectionAndConfig(final DBBroker broker, final TransactionManager transact, final XmldbURI collUri)
        throws Exception {
        final String collName = collUri.lastSegment().toString();

        try (Txn transaction = transact.beginTransaction()) {
            // remove data collection
            final Collection data = broker.getOrCreateCollection(transaction, collUri);
            broker.removeCollection(transaction, data);

            // remove configuration collection (where <collectionName>/collection.xconf lives)
            final XmldbURI configUri = XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db/" + collName);
            final Collection config = broker.getOrCreateCollection(transaction, configUri);
            broker.removeCollection(transaction, config);

            transaction.commit();
        }
    }

    private static FacetsIndexTerms readFacetsTerms(final DBBroker broker, final String... termsToCheck) throws IOException {
        final LuceneIndexWorker indexWorker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        assertNotNull("Lucene index worker not available", indexWorker);

        final LuceneIndex luceneIndex = indexWorker.index;
        assertNotNull("Lucene index instance not available", luceneIndex);

        return luceneIndex.withReader(new FunctionE<IndexReader, FacetsIndexTerms, IOException>() {
            @Override
            public FacetsIndexTerms apply(final IndexReader reader) throws IOException {
                final Terms facetsTerms = org.apache.lucene.index.MultiTerms.getTerms(reader, FacetsConfig.DEFAULT_INDEX_FIELD_NAME);
                if (facetsTerms == null) {
                    LOG.warn("No terms found for field {}", FacetsConfig.DEFAULT_INDEX_FIELD_NAME);
                    return FacetsIndexTerms.empty();
                }

                final List<String> sample = new ArrayList<>();
                final List<String> subjectTerms = new ArrayList<>();
                final List<String> locationTerms = new ArrayList<>();
                final List<String> dateTerms = new ArrayList<>();

                // Sample the very first terms so log output isn't huge.
                final TermsEnum sampleEnum = facetsTerms.iterator();
                final int maxSample = 30;
                int sampleGuard = 0;
                while (sampleEnum.next() != null && sample.size() < maxSample && sampleGuard < 1000) {
                    sampleGuard++;
                    sample.add(sampleEnum.term().utf8ToString());
                }

                // Jump directly to dimension prefixes so we don't depend on lexicographic position.
                collectPrefixTerms(facetsTerms, "subject", subjectTerms, 50);
                collectPrefixTerms(facetsTerms, "location", locationTerms, 50);
                collectPrefixTerms(facetsTerms, "date", dateTerms, 50);

                final List<String> termsWithHumanitiesOrGermany = new ArrayList<>();
                final TermsEnum scanEnum = facetsTerms.iterator();
                while (scanEnum.next() != null && termsWithHumanitiesOrGermany.size() < 50) {
                    final String term = scanEnum.term().utf8ToString();
                    if (term.contains("humanities") || term.contains("Germany") || term.contains("Berlin") || term.contains("Wroc")) {
                        termsWithHumanitiesOrGermany.add(term);
                    }
                }

                // Also check Lucene taxonomy categories directly.
                // This tells us whether the pollution blocks category creation or only drill-down postings.
                boolean subjectTaxoExists;
                boolean locationTaxoExists;
                boolean dateTaxoExists;
                DirectoryTaxonomyReader taxoReader = null;
                try {
                    taxoReader = new DirectoryTaxonomyReader(luceneIndex.taxoDirectory);
                    subjectTaxoExists = taxoReader.getOrdinal(new FacetLabel("subject", "humanities", "history")) != -1;
                    locationTaxoExists = taxoReader.getOrdinal(new FacetLabel("location", "Germany", "Berlin")) != -1;
                    dateTaxoExists = taxoReader.getOrdinal(new FacetLabel("date", "2019", "03", "14")) != -1;
                } finally {
                    if (taxoReader != null) {
                        taxoReader.close();
                    }
                }

                LOG.info("taxonomy categories present? subject={} location={} date={}",
                    subjectTaxoExists, locationTaxoExists, dateTaxoExists);

                boolean subjectExists = hasTerm(facetsTerms, EXPECT_SUBJECT);
                boolean locationExists = hasTerm(facetsTerms, EXPECT_LOCATION);
                boolean dateExists = hasTerm(facetsTerms, EXPECT_DATE);

                return new FacetsIndexTerms(subjectExists, locationExists, dateExists, sample, subjectTerms, locationTerms, dateTerms, termsWithHumanitiesOrGermany);
            }
        });
    }

    private static void collectPrefixTerms(final Terms facetsTerms, final String prefix, final List<String> out, final int limit)
        throws IOException {
        final TermsEnum termsEnum = facetsTerms.iterator();
        final BytesRef seekRef = new BytesRef(prefix);
        final TermsEnum.SeekStatus status = termsEnum.seekCeil(seekRef);
        if (status == TermsEnum.SeekStatus.END) {
            return;
        }
        while (out.size() < limit) {
            final String term = termsEnum.term().utf8ToString();
            if (!term.startsWith(prefix)) {
                break;
            }
            out.add(term);
            if (termsEnum.next() == null) {
                break;
            }
        }
    }

    private static boolean hasTerm(final Terms facetsTerms, final String termText) throws IOException {
        final TermsEnum termsEnum = facetsTerms.iterator();
        final BytesRef seekRef = new BytesRef(termText);
        return termsEnum.seekExact(seekRef);
    }

    private static final class FacetsIndexTerms {
        final boolean subjectExists;
        final boolean locationExists;
        final boolean dateExists;
        final List<String> sampleTerms;
        final List<String> subjectTerms;
        final List<String> locationTerms;
        final List<String> dateTerms;
        final List<String> termsWithHumanitiesOrGermany;

        private FacetsIndexTerms(
            final boolean subjectExists,
            final boolean locationExists,
            final boolean dateExists,
            final List<String> sampleTerms,
            final List<String> subjectTerms,
            final List<String> locationTerms,
            final List<String> dateTerms,
            final List<String> termsWithHumanitiesOrGermany
        ) {
            this.subjectExists = subjectExists;
            this.locationExists = locationExists;
            this.dateExists = dateExists;
            this.sampleTerms = sampleTerms;
            this.subjectTerms = subjectTerms;
            this.locationTerms = locationTerms;
            this.dateTerms = dateTerms;
            this.termsWithHumanitiesOrGermany = termsWithHumanitiesOrGermany;
        }

        static FacetsIndexTerms empty() {
            return new FacetsIndexTerms(false, false, false, List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }
}

