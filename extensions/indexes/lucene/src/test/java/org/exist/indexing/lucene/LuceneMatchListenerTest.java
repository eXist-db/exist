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
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;

import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    private static String XML2 =
            "<p xmlns=\"http://www.tei-c.org/ns/1.0\">\n" +
            "    <s type=\"combo\"><w lemma=\"из\">из</w>\n" +
            "        <w>новина</w>\n" +
            "        <w lemma=\"и\">и</w>\n" +
            "        <w lemma=\"од\">од</w>\n" +
            "        <lb/>\n" +
            "        <pb n=\"32\"/>\n" +
            "        <w>других</w>\n" +
            "        <w lemma=\"човек\">људи</w>\n" +
            "        <w>дознајем</w>, <w xml:id=\"VSK.P13.t1.p4.w205\" lemma=\"ма\">ма</w>\n" +
            "        <w>се</w>\n" +
            "        <w lemma=\"не\">не</w>\n" +
            "        <w>прорезује</w>\n" +
            "        <w>право</w>\n" +
            "        <w lemma=\"по\">по</w>\n" +
            "        <w>имућству</w>, <w xml:id=\"VSK.P13.t1.p4.w219\" lemma=\"те\">те</w>\n" +
            "        <w>се</w>\n" +
            "        <w>на</w>\n" +
            "        <w lemma=\"то\">то</w>\n" +
            "        <w>видим</w>\n" +
            "        <w>многи</w>\n" +
            "        <w>љуте</w>.</s>\n" +
            "</p>";

    private static String CONF1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "   <index>" +
        "       <text qname=\"para\"/>" +
        "   </index>" +
        "</collection>";

    private static String CONF2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "   <index>" +
        "       <text qname=\"para\"/>" +
        "       <text qname=\"term\"/>" +
        "   </index>" +
        "</collection>";

    private static String CONF3 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "   <index>" +
        "       <text qname=\"hi\"/>" +
        "   </index>" +
        "</collection>";

    private static String CONF4 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
        "       <lucene>" +
        "           <text qname=\"p\">" +
        "               <ignore qname=\"note\"/>" +
        "           </text>" +
        "           <text qname=\"head\"/>" +
        "           <inline qname=\"s\"/>" +
        "       </lucene>" +
        "   </index>" +
        "</collection>";


    private static String CONF5 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
            "    <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
            "        <lucene>" +
            "            <text qname=\"tei:p\"/>" +
            "            <text qname=\"tei:w\"/>" +
            "            <text qname=\"@lemma\"/>" +
            "        </lucene>" +
            "    </index>" +
            "</collection>";

    private static String MATCH_START = "<exist:match xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
    private static String MATCH_END = "</exist:match>";

    /**
     * Test match highlighting for index configured by QName, e.g.
     * &lt;create qname="a"/&gt;.
     */
    @Test
    public void indexByQName() throws EXistException, PermissionDeniedException, XPathException, SAXException, CollectionConfigurationException, LockException, IOException {

        configureAndStore(CONF2, XML);

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//para[ft:query(., 'mixed')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                    MATCH_END + "</hi> content.</para>", result);

            seq = xquery.execute(broker, "//para[ft:query(., '+nested +inner +elements')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
                    MATCH_END + "</hi> " + MATCH_START +
                    "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para>", result);

            seq = xquery.execute(broker, "//para[ft:query(term, 'term')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END +
                    "</term>.</para>", result);

            seq = xquery.execute(broker, "//para[ft:query(., '+double +match')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<para>" + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + "</para>", result);

            seq = xquery.execute(broker,
                    "for $para in //para[ft:query(., '+double +match')] return\n" +
                            "   <hit>{$para}</hit>", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<hit><para>" + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + "</para></hit>", result);
        }
    }

    @Test
    public void matchInAncestor() throws EXistException, PermissionDeniedException, XPathException, SAXException, IOException, XpathException, LockException, CollectionConfigurationException {
        configureAndStore(CONF1, XML);
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//para[ft:query(., 'mixed')]/hi", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute(broker, "//para[ft:query(., 'nested')]/note", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//hi/exist:match)", result);
        }
    }

    @Test
    public void matchInDescendant() throws EXistException, PermissionDeniedException, XPathException, SAXException, IOException, XpathException, LockException, CollectionConfigurationException {
        configureAndStore(CONF3, XML);
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//hi[ft:query(., 'mixed')]/ancestor::para", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute(broker, "//hi[ft:query(., 'nested')]/parent::note", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//hi/exist:match)", result);
        }
    }

    @Test
    public void inlineNodes_whenNotIndenting() throws EXistException, PermissionDeniedException, XPathException, SAXException, CollectionConfigurationException, LockException, IOException {
        configureAndStore(CONF4, XML1);

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//p[ft:query(., 'mixed')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<p>Paragraphs with <s>" + MATCH_START + "mix" + MATCH_END +
                    "</s><s>ed</s> content are <s>danger</s>ous.</p>", result);

            seq = xquery.execute(broker, "//p[ft:query(., 'ignored')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<p>A simple<note>sic</note> paragraph with <hi>highlighted</hi> text <note>and a note</note> to be " +
                    MATCH_START + "ignored" + MATCH_END + ".</p>", result);

            seq = xquery.execute(broker, "//p[ft:query(., 'highlighted')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<p>A simple<note>sic</note> paragraph with <hi>" + MATCH_START +
                    "highlighted" + MATCH_END + "</hi> text <note>and a note</note> to be " +
                    "ignored.</p>", result);

            seq = xquery.execute(broker, "//p[ft:query(., 'highlighted')]/hi", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<hi>" + MATCH_START + "highlighted" + MATCH_END + "</hi>", result);
            
            seq = xquery.execute(broker, "//head[ft:query(., 'title')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            XMLAssert.assertEquals("<head>The <b>" + MATCH_START + "title" + MATCH_END + "</b>of it</head>",
                    result);
        }
    }

    @Test
    public void inlineMatchNodes_whenIndenting() throws EXistException, PermissionDeniedException, XPathException, SAXException, CollectionConfigurationException, LockException, IOException {
        configureAndStore(CONF5, XML2);

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            final String query =  "declare namespace tei=\"http://www.tei-c.org/ns/1.0\";" +
                    "//tei:p[.//tei:w[ft:query(., <query><bool><term>дознајем</term></bool></query>)]] ! util:expand(.)";
            final Sequence seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            final String result = queryResult2String(broker, seq, true);

            final String expected =
            "<p xmlns=\"http://www.tei-c.org/ns/1.0\">\n" +
            "    <s type=\"combo\">\n" +
            "        <w lemma=\"из\">из</w>\n" +
            "        <w>новина</w>\n" +
            "        <w lemma=\"и\">и</w>\n" +
            "        <w lemma=\"од\">од</w>\n" +
            "        <lb/>\n" +
            "        <pb n=\"32\"/>\n" +
            "        <w>других</w>\n" +
            "        <w lemma=\"човек\">људи</w>\n" +
            "        <w>" + MATCH_START + "дознајем" + MATCH_END + "</w>, <w xml:id=\"VSK.P13.t1.p4.w205\" lemma=\"ма\">ма</w>\n" +
            "        <w>се</w>\n" +
            "        <w lemma=\"не\">не</w>\n" +
            "        <w>прорезује</w>\n" +
            "        <w>право</w>\n" +
            "        <w lemma=\"по\">по</w>\n" +
            "        <w>имућству</w>, <w xml:id=\"VSK.P13.t1.p4.w219\" lemma=\"те\">те</w>\n" +
            "        <w>се</w>\n" +
            "        <w>на</w>\n" +
            "        <w lemma=\"то\">то</w>\n" +
            "        <w>видим</w>\n" +
            "        <w>многи</w>\n" +
            "        <w>љуте</w>.</s>\n" +
            "</p>";

            XMLAssert.assertEquals(expected, result);
        }
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void startDB() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);
        }

        final Map<String, String> m = new HashMap<>();
        m.put(Namespaces.EXIST_NS_PREFIX, Namespaces.EXIST_NS);
        final NamespaceContext ctx = new SimpleNamespaceContext(m);
        XMLUnit.setXpathNamespaceContext(ctx);
    }

    @AfterClass
    public static void closeDB() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }

    private void configureAndStore(final String config, final String data) throws EXistException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, config);

            final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_matches.xml"), XML);
            assertNotNull(info);
            root.store(transaction, broker, info, data);

            transact.commit(transaction);
        }
    }

    private String queryResult2String(final DBBroker broker, final Sequence seq) throws SAXException, XPathException {
        return queryResult2String(broker, seq, false);
    }

    private String queryResult2String(final DBBroker broker, final Sequence seq, final boolean indent) throws SAXException, XPathException {
        final Properties props = new Properties();
        props.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        props.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperties(props);
        return serializer.serialize((NodeValue) seq.itemAt(0));
    }
}
