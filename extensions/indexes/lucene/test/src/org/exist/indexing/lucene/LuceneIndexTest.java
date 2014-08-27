/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.StringReader;
import java.util.*;

import org.exist.Indexer;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.InputSource;

public class LuceneIndexTest {

    protected static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    protected static String XUPDATE_END =
        "</xu:modifications>";
    
    private static String XML1 =
            "<section>" +
            "   <head>The title in big letters</head>" +
            "   <p rend=\"center\">A simple paragraph with <hi>just</hi> text in it.</p>" +
            "   <p rend=\"right\">paragraphs with <span>mix</span><span>ed</span> content are <span>danger</span>ous.</p>" +
            "</section>";

    private static String XML2 =
            "<test>" +
            "   <item id='1' attr='attribute'><description>Chair</description></item>" +
            "   <item id='2'><description>Table</description>\n<condition>good</condition></item>" +
            "   <item id='3'><description>Cabinet</description>\n<condition>bad</condition></item>" +
            "</test>";

    private static String XML3 =
            "<section>" +
            "   <head>TITLE IN UPPERCASE LETTERS</head>" +
            "   <p>UPPERCASE PARAGRAPH</p>" +
            "</section>";

    private static String XML4 =
            "<test><a>A X</a><b><c>B X</c> C</b></test>";

    private static String XML5 =
            "<article>" +
            "   <head>The <b>title</b>of it</head>" +
            "   <p>A simple paragraph with <hi>highlighted</hi> text <note>and a note</note> " +
            "       in it.</p>" +
            "   <p>Paragraphs with <s>mix</s><s>ed</s> content are <s>danger</s>ous.</p>" +
            "   <p><note1>ignore</note1> <s2>warn</s2>ings</p>" +
            "</article>";

    private static String XML6 =
            "<a>" +
            "   <b>AAA</b>" +
            "   <c>AAA</c>" +
            "   <b>AAA</b>" +
            "</a>";

    private static String XML7 =
        "<section>" +
        "   <head>Query Test</head>" +
        "   <p>Eine wunderbare Heiterkeit hat meine ganze Seele eingenommen, gleich den " +
        "   süßen Frühlingsmorgen, die ich mit ganzem Herzen genieße. Ich bin allein und " +
        "   freue mich meines Lebens in dieser Gegend, die für solche Seelen geschaffen " +
        "   ist wie die meine. Ich bin so glücklich, mein Bester, so ganz in dem Gefühle " +
        "   von ruhigem Dasein versunken, daß meine Kunst darunter leidet.</p>" +
        "</section>";

    private static String XML8 =
            "<a>" +
            "   <b class=' class title '>AAA</b>" +
            "   <c class=' element title '>AAA</c>" +
            "   <b class=' element '>AAA</b>" +
            "</a>";

    private static String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <analyzer class=\"org.apache.lucene.analysis.core.SimpleAnalyzer\"/>" +
        "           <text match=\"/section/p\"/>" +
        "           <text qname=\"head\"/>" +
        "           <text qname=\"@rend\"/>" +
        "           <text qname=\"hi\"/>" +
        "           <text qname=\"LINE\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";

    private static String COLLECTION_CONFIG2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <text qname=\"item\"/>" +
        "           <text match=\"//description\"/>" +
        "           <text qname=\"condition\"/>" +
        "           <text qname=\"@attr\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";

    private static String COLLECTION_CONFIG3 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "	<index>" +
        "       <lucene>" +
        "           <analyzer id=\"whitespace\" class=\"org.apache.lucene.analysis.core.WhitespaceAnalyzer\"/>" +
        "           <text match=\"/section/head\" analyzer=\"whitespace\"/>" +
        "           <text match=\"//p\"/>" +
        "       </lucene>" +
        "	</index>" +
        "</collection>";

    private static String COLLECTION_CONFIG4 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "	<index>" +
            "       <lucene>" +
            "           <text match=\"/test/a\"/>" +
            "           <text match=\"/test/b/*\"/>" +
            "       </lucene>" +
            "	</index>" +
            "</collection>";

    private static String COLLECTION_CONFIG5 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <lucene>" +
            "           <text qname=\"article\">" +
            "               <ignore qname=\"note\"/>" +
            "               <inline qname=\"s\"/>" +
            "           </text>" +
            "           <text qname=\"p\">" +
            "               <ignore qname=\"note\"/>" +
            "               <inline qname=\"s\"/>" +
            "           </text>" +
            "           <text qname=\"head\"/>" +
            "           <ignore qname=\"note1\"/>" +
            "           <inline qname=\"s2\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static String COLLECTION_CONFIG6 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <lucene>" +
            "           <text qname=\"b\"/>" +
            "           <text qname=\"c\" boost=\"2.0\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static String COLLECTION_CONFIG7 =
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
            "   <index xmlns:tei='http://www.tei-c.org/ns/1.0'>" +
            "       <lucene>" +
            "           <text match='//*' attribute='class=.*title.*'/>" +
            "           <text qname='c' attribute='class=.*title.*' boost=\"2.0\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static BrokerPool pool;
    private static Collection root;
    private Boolean savedConfig;

    @Test
    public void simpleQueries() {
        System.out.println("Test simple queries ...");
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            checkIndex(docs, broker, new QName[] { new QName("head", "") }, "title", 1);
            Occurrences[] o = checkIndex(docs, broker, new QName[]{new QName("p", "")}, "with", 1);
            assertEquals(2, o[0].getOccurrences());
            checkIndex(docs, broker, new QName[] { new QName("hi", "") }, "just", 1);
            checkIndex(docs, broker, null, "in", 1);

            QName attrQN = new QName("rend", "");
            attrQN.setNameType(ElementValue.ATTRIBUTE);
            checkIndex(docs, broker, new QName[] { attrQN }, null, 2);
            checkIndex(docs, broker, new QName[] { attrQN }, "center", 1);
            checkIndex(docs, broker, new QName[] { attrQN }, "right", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("/section[ft:query(p, 'content')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/section[ft:query(p/@rend, 'center')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/section[ft:query(hi, 'just')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute("/section[ft:query(p/*, 'just')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/section[ft:query(head/*, 'just')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            System.out.println("Test PASSED.");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void configuration() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG4, XML4, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            checkIndex(docs, broker, new QName[] { new QName("a", "") }, "x", 1);
            checkIndex(docs, broker, new QName[] { new QName("c", "") }, "x", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("/test[ft:query(a, 'x')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/test[ft:query(.//c, 'x')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/test[ft:query(b, 'x')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void inlineAndIgnore() {
        System.out.println("Test simple queries ...");
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG5, XML5, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            checkIndex(docs, broker, new QName[] { new QName("head", "") }, "title", 1);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "simple", 1);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "mixed", 1);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "dangerous", 1);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "note", 0);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "ignore", 0);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "warnings", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("/article[ft:query(head, 'title')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(p, 'highlighted')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(p, 'mixed')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(p, 'mix')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(p, 'dangerous')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(p, 'ous')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(p, 'danger')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(p, 'note')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(., 'highlighted')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(., 'mixed')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(., 'dangerous')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(., 'warnings')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(., 'danger')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute("/article[ft:query(., 'note')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            
            seq = xquery.execute("/article[ft:query(., 'ignore')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void boosts() {
        configureAndStore(COLLECTION_CONFIG6, XML6, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("for $a in ft:query((//b|//c), 'AAA') " +
                    "order by ft:score($a) descending return $a/local-name(.)", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(3, seq.getItemCount());
            assertEquals("c", seq.getStringValue());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void attributePattern() {
        configureAndStore(COLLECTION_CONFIG7, XML8, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("for $a in ft:query((//b|//c), 'AAA') " +
                    "order by ft:score($a) descending return $a/local-name(.)", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(2, seq.getItemCount());
            assertEquals("c", seq.itemAt(0).getStringValue());
            assertEquals("b", seq.itemAt(1).getStringValue());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void queryTranslation() {
        configureAndStore(COLLECTION_CONFIG1, XML7, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            XQueryContext context = new XQueryContext(broker.getBrokerPool(), AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, "declare variable $q external; " +
                    "ft:query(//p, util:parse($q)/query)");

            context.declareVariable("q", "<query><term>heiterkeit</term></query>");
            Sequence seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <term>heiterkeit</term><term>blablabla</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <term occur='should'>heiterkeit</term><term occur='should'>blablabla</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <term occur='must'>heiterkeit</term><term occur='must'>blablabla</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <term occur='must'>heiterkeit</term><term occur='not'>herzen</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <phrase occur='must'>wunderbare heiterkeit</phrase><term occur='must'>herzen</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                    "<query>" +
                    "   <phrase slop='5'>heiterkeit seele eingenommen</phrase>" +
                    "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            // phrase with wildcards
            context.declareVariable("q",
                "<query>" +
                "   <phrase slop='5'><term>heiter*</term><term>se?nnnle*</term></phrase>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <wildcard>?eiter*</wildcard>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <fuzzy max-edits='2'>selee</fuzzy>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <fuzzy occur='must' max-edits='2'>selee</fuzzy>" +
                "       <wildcard occur='should'>bla*</wildcard>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <regex>heit.*keit</regex>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <phrase><term>wunderbare</term><regex>heit.*keit</regex></phrase>" +
                "</query>");
            seq = xquery.execute(compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void analyzers() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG3, XML3, "test.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            checkIndex(docs, broker, new QName[] { new QName("head", "") }, "TITLE", 1);
            checkIndex(docs, broker, new QName[] { new QName("p", "") }, "uppercase", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("/section[ft:query(p, 'UPPERCASE')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/section[ft:query(head, 'TITLE')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("/section[ft:query(head, 'title')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void dropSingleDoc() {
        System.out.println("Test removal of single document ...");
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "dropDocument.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            System.out.println("Removing document dropDocument.xml");
            root.removeXMLResource(transaction, broker, XmldbURI.create("dropDocument.xml"));
            transact.commit(transaction);

            checkIndex(docs, broker, null, null, 0);

            System.out.println("Test PASSED.");
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void dropDocuments() {
        System.out.println("Test removal of multiple documents ...");
        configureAndStore(COLLECTION_CONFIG1, "samples/shakespeare");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//LINE[ft:query(., 'bark')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(6, seq.getItemCount());

            System.out.println("Removing document r_and_j.xml");
            root.removeXMLResource(transaction, broker, XmldbURI.create("r_and_j.xml"));
            transact.commit(transaction);

            seq = xquery.execute("//LINE[ft:query(., 'bark')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(3, seq.getItemCount());

            transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Removing document hamlet.xml");
            root.removeXMLResource(transaction, broker, XmldbURI.create("hamlet.xml"));
            transact.commit(transaction);

            seq = xquery.execute("//LINE[ft:query(., 'bark')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            System.out.println("Test PASSED.");
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void removeCollection() {
        System.out.println("Test removal of collection ...");
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, "samples/shakespeare");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//SPEECH[ft:query(LINE, 'love')]", null, AccessContext.TEST);
            assertNotNull(seq);
            System.out.println("Found: " + seq.getItemCount());
            assertEquals(166, seq.getItemCount());

            System.out.println("Removing collection");
            broker.removeCollection(transaction, root);

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            transact.commit(transaction);

            root = null;
            
            checkIndex(docs, broker, null, null, 0);

            System.out.println("Test PASSED.");
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void reindex() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "dropDocument.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            broker.reindexCollection(TestConstants.TEST_COLLECTION_URI);

            checkIndex(docs, broker, new QName[] { new QName("head", "") }, "title", 1);
            Occurrences[] o = checkIndex(docs, broker, new QName[]{new QName("p", "")}, "with", 1);
            assertEquals(2, o[0].getOccurrences());
            checkIndex(docs, broker, new QName[] { new QName("hi", "") }, "just", 1);
            checkIndex(docs, broker, null, "in", 1);

            QName attrQN = new QName("rend", "");
            attrQN.setNameType(ElementValue.ATTRIBUTE);
            checkIndex(docs, broker, new QName[] { attrQN }, null, 2);
            checkIndex(docs, broker, new QName[] { attrQN }, "center", 1);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    /**
     * Remove nodes from different levels of the tree and check if the index is
     * correctly updated.
     */
    @Test
    public void xupdateRemove() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	broker = pool.get(pool.getSecurityManager().getSystemSubject());
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            checkIndex(docs, broker, new QName[] { new QName("description", "") }, "chair", 1);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 2);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ft:query(description, 'chair')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='2']/condition\"/>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 1);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 4);
            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "good", 0);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "good", 0);
            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "table", 1);
            assertEquals("table", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "cabinet", 1);
            assertEquals("cabinet", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "table", 1);
            assertEquals("table", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "cabinet", 1);
            assertEquals("cabinet", o[0].getTerm());

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='3']/description/text()\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='1']\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            o = checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 1);
            assertEquals("table", o[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, "chair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "chair", 0);

            transact.commit(transaction);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    /**
     * Remove nodes from different levels of the tree and check if the index is
     * correctly updated.
     */
    @Test
    public void xupdateInsert() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	broker = pool.get(pool.getSecurityManager().getSystemSubject());
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 5);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ft:query(description, 'chair')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            // Append to root node
            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                XUPDATE_START +
                "   <xu:append select=\"/test\">" +
                "       <item id='4'><description>Armchair</description> <condition>bad</condition></item>" +
                "   </xu:append>" +
                XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 2);
            System.out.println("prices: " + o.length);
            for (int i = 0; i < o.length; i++) {
                System.out.println("occurance: " + o[i].getTerm() + ": " + o[i].getOccurrences());
            }
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 4);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 6);

            o = checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "bad", 1);
            assertEquals("bad", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "bad", 1);
            assertEquals("bad", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());

            // Insert before top element
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']\">" +
                    "           <item id='0'><description>Wheelchair</description> <condition>poor</condition></item>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 8);

            o = checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());

            // Insert after element
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']\">" +
                    "           <item id='1.1'><description>refrigerator</description> <condition>perfect</condition></item>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 4);
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 10);

            o = checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "perfect", 1);
            assertEquals("perfect", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "refrigerator", 1);
            assertEquals("refrigerator", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "perfect", 1);
            assertEquals("perfect", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "refrigerator", 1);
            assertEquals("refrigerator", o[0].getTerm());

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']/description\">" +
                    "           <condition>average</condition>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 11);
            o = checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "average", 1);
            assertEquals("average", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "average", 1);
            assertEquals("average", o[0].getTerm());

            // Insert before nested element
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']/description\">" +
                    "           <condition>awesome</condition>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 12);
            o = checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "awesome", 1);
            assertEquals("awesome", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "awesome", 1);
            assertEquals("awesome", o[0].getTerm());

            // Overwrite attribute
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:append select=\"//item[@id = '1']\">" +
                    "           <xu:attribute name=\"attr\">abc</xu:attribute>" +
                    "       </xu:append>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            QName qnattr[] = { new QName("attr", "", "") };
            qnattr[0].setNameType(ElementValue.ATTRIBUTE);
            o = checkIndex(docs, broker, qnattr, null, 1);
            assertEquals("abc", o[0].getTerm());
            checkIndex(docs, broker, qnattr, "attribute", 0);

            transact.commit(transaction);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    @Test
    public void xupdateUpdate() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 5);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ft:query(description, 'chair')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            // Update element content
            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description\">wardrobe</xu:update>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, "chair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "chair", 0);
            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "wardrobe", 1);
            assertEquals("wardrobe", o[0].getTerm());

            // Update text node
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description/text()\">Wheelchair</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, "wardrobe", 0);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "wardrobe", 0);
            o = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());

            // Update attribute value
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/@attr\">abc</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            QName qnattr[] = { new QName("attr", "", "") };
            qnattr[0].setNameType(ElementValue.ATTRIBUTE);
            o = checkIndex(docs, broker, qnattr, null, 1);
            assertEquals("abc", o[0].getTerm());
            checkIndex(docs, broker, qnattr, "attribute", 0);

            transact.commit(transaction);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    @Test
    public void xupdateReplace() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 5);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ft:query(description, 'chair')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "<xu:replace select=\"//item[@id = '1']\">" +
                    "<item id='4'><description>Wheelchair</description> <condition>poor</condition></item>" +
                    "</xu:replace>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, "chair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "chair", 0);
            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "<xu:replace select=\"//item[@id = '4']/description\">" +
                    "<description>Armchair</description>" +
                    "</xu:replace>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, "wheelchair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "wheelchair", 0);
            o = checkIndex(docs, broker, new QName[] { new QName("description", "") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item", "") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());

            transact.commit(transaction);
         } catch (Exception e) {
             if (transact != null)
                 transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    private DocumentSet configureAndStore(String configuration, String data, String docName) {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        MutableDocumentSet docs = new DefaultDocumentSet();
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            if (configuration != null) {
                CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
            assertNotNull(info);
            root.store(transaction, broker, info, data, false);

            docs.add(info.getDocument());
            transact.commit(transaction);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
        return docs;
    }

    private DocumentSet configureAndStore(String configuration, String directory) {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        MutableDocumentSet docs = new DefaultDocumentSet();
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            if (configuration != null) {
                CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }

            File file = new File(directory);
            File[] files = file.listFiles();
            MimeTable mimeTab = MimeTable.getInstance();
            for (int j = 0; j < files.length; j++) {
                MimeType mime = mimeTab.getContentTypeFor(files[j].getName());
                if(mime != null && mime.isXMLType()) {
                    System.out.println("Storing document " + files[j].getName());
                    InputSource is = new InputSource(files[j].getAbsolutePath());
                    IndexInfo info =
                            root.validateXMLResource(transaction, broker, XmldbURI.create(files[j].getName()), is);
                    assertNotNull(info);
                    is = new InputSource(files[j].getAbsolutePath());
                    root.store(transaction, broker, info, is, false);
                    docs.add(info.getDocument());
                }
            }
            transact.commit(transaction);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
        return docs;
    }

    private Occurrences[] checkIndex(DocumentSet docs, DBBroker broker, QName[] qn, 
            String term, int expected) {
        LuceneIndexWorker index = (LuceneIndexWorker)
            broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        Map<String, Object> hints = new HashMap<String, Object>();
        if (term != null)
            hints.put(OrderedValuesIndex.START_VALUE, term);
        if (qn != null && qn.length > 0) {
            List<QName> qnlist = new ArrayList<QName>(qn.length);
            for (int i = 0; i < qn.length; i++)
                qnlist.add(qn[i]);
            hints.put(QNamedKeysIndex.QNAMES_KEY, qnlist);
        }
        XQueryContext context = new XQueryContext(broker.getBrokerPool(), AccessContext.TEST);
        Occurrences[] occur = index.scanIndex(context, docs, null, hints);
        if (occur != null && expected != occur.length) {
            for (int i = 0; i < occur.length; i++) {
                System.out.println("term: " + occur[i].getTerm());
            }
        }
        assertEquals(expected, occur.length);
        return occur;
    }

    @Before
    public void setup() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);

            Configuration config = BrokerPool.getInstance().getConfiguration();
            savedConfig = (Boolean) config.getProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT);
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, Boolean.TRUE);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    @After
    public void cleanup() {
        BrokerPool pool = null;
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection collConfig = broker.getOrCreateCollection(transaction,
                XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"));
            assertNotNull(collConfig);
            broker.removeCollection(transaction, collConfig);

            if (root != null) {
                assertNotNull(root);
                broker.removeCollection(transaction, root);
            }
            transact.commit(transaction);

            Configuration config = BrokerPool.getInstance().getConfiguration();
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, savedConfig);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    }

    @BeforeClass
    public static void startDB() {
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            config.setProperty(Indexer.PROPERTY_SUPPRESS_WHITESPACE, "none");
            config.setProperty(Indexer.PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE, Boolean.TRUE);
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopDB() {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        pool = null;
        root = null;
    }
}

