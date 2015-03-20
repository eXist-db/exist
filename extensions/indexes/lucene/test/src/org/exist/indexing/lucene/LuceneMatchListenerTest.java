/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.File;
import java.util.HashMap;
import java.util.Properties;

public class LuceneMatchListenerTest {

    private static String XML =
            "<root>" +
            "   <para>some paragraph with <hi>mixed</hi> content.</para>" +
            "   <para>another paragraph with <note><hi>nested</hi> inner</note> elements.</para>" +
            "   <para>a third paragraph with <term>term</term>.</para>" +
            "   <para>double match double match</para>" +
            "</root>";

    private static String XML1 =
            "<article>" +
            "   <head>The <b>title</b>of it</head>" +
            "   <p>A simple<note>sic</note> paragraph with <hi>highlighted</hi> text <note>and a note</note> to be ignored.</p>" +
            "   <p>Paragraphs with <s>mix</s><s>ed</s> content are <s>danger</s>ous.</p>" +
            "</article>";

    private static String CONF1 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "	<index>" +
            "		<fulltext default=\"none\">" +
            "		</fulltext>" +
            "   <text qname=\"para\"/>" +
            "	</index>" +
            "</collection>";

    private static String CONF2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "	<index>" +
        "		<fulltext default=\"none\">" +
        "		</fulltext>" +
        "       <text qname=\"para\"/>" +
        "       <text qname=\"term\"/>" +
        "	</index>" +
        "</collection>";

    private static String CONF3 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "	<index>" +
            "		<fulltext default=\"none\">" +
            "		</fulltext>" +
            "   <text qname=\"hi\"/>" +
            "	</index>" +
            "</collection>";

    private static String CONF4 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <fulltext default=\"none\" attributes=\"no\">" +
            "       </fulltext>" +
            "       <lucene>" +
            "           <text qname=\"p\">" +
            "               <ignore qname=\"note\"/>" +
            "           </text>" +
            "           <text qname=\"head\"/>" +
            "           <inline qname=\"s\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static String MATCH_START = "<exist:match xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
    private static String MATCH_END = "</exist:match>";

    private static BrokerPool pool;

    /**
     * Test match highlighting for index configured by QName, e.g.
     * &lt;create qname="a"/&gt;.
     */
    @Test
    public void indexByQName() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF2, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ft:query(., 'mixed')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                    MATCH_END + "</hi> content.</para>", result);

            seq = xquery.execute("//para[ft:query(., '+nested +inner +elements')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
                    MATCH_END + "</hi> " + MATCH_START +
                    "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para>", result);

            seq = xquery.execute("//para[ft:query(term, 'term')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END +
                    "</term>.</para>", result);

            seq = xquery.execute("//para[ft:query(., '+double +match')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>" + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + "</para>", result);

            seq = xquery.execute(
                    "for $para in //para[ft:query(., '+double +match')] return\n" +
                            "   <hit>{$para}</hit>", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<hit><para>" + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + "</para></hit>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void matchInAncestor() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ft:query(., 'mixed')]/hi", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute("//para[ft:query(., 'nested')]/note", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//hi/exist:match)", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void matchInDescendant() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF3, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//hi[ft:query(., 'mixed')]/ancestor::para", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute("//hi[ft:query(., 'nested')]/parent::note", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//hi/exist:match)", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void inlineNodes() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF4, XML1);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//p[ft:query(., 'mixed')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<p>Paragraphs with <s>" + MATCH_START + "mix" + MATCH_END +
                    "</s><s>ed</s> content are <s>danger</s>ous.</p>", result);

            seq = xquery.execute("//p[ft:query(., 'ignored')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<p>A simple<note>sic</note> paragraph with <hi>highlighted</hi> text <note>and a note</note> to be " +
                    MATCH_START + "ignored" + MATCH_END + ".</p>", result);

            seq = xquery.execute("//p[ft:query(., 'highlighted')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<p>A simple<note>sic</note> paragraph with <hi>" + MATCH_START +
                    "highlighted" + MATCH_END + "</hi> text <note>and a note</note> to be " +
                    "ignored.</p>", result);

            seq = xquery.execute("//p[ft:query(., 'highlighted')]/hi", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<hi>" + MATCH_START + "highlighted" + MATCH_END + "</hi>", result);
            
            seq = xquery.execute("//head[ft:query(., 'title')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<head>The <b>" + MATCH_START + "title" + MATCH_END + "</b>of it</head>",
                    result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @BeforeClass
    public static void startDB() throws DatabaseConfigurationException, EXistException {
        final File confFile = ConfigurationHelper.lookup("conf.xml");
        final Configuration config = new Configuration(confFile.getAbsolutePath());
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        HashMap<String, String> m = new HashMap<String, String>();
        m.put("exist", "http://exist.sourceforge.net/NS/exist");
        NamespaceContext ctx = new SimpleNamespaceContext(m);
        XMLUnit.setXpathNamespaceContext(ctx);
    }

    @AfterClass
    public static void closeDB() {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        pool = null;
    }

    private void configureAndStore(String config, String data) {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, config);

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_matches.xml"), XML);
            assertNotNull(info);
            root.store(transaction, broker, info, data, false);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private String queryResult2String(DBBroker broker, Sequence seq) throws SAXException, XPathException {
        Properties props = new Properties();
        props.setProperty(OutputKeys.INDENT, "no");
        props.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperties(props);
        return serializer.serialize((NodeValue) seq.itemAt(0));
    }
}
