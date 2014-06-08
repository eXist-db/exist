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

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.NodeProxy;
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
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
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
import java.io.StringWriter;
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
    
    private static String XML2 =
            "<root>" +
            "   <para>bla bla content bla bla bla bla content bla bla</para>" +
            "</root>";

    private static String XML3 =
            "<root>" +
            "   <para>blabla</para>" +
            "   <para>bla bla content bla bla bla bla content bla bla</para>" +
            "   <para>blabla</para>" +
            "</root>";

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

    private static String NS = "xmlns:exist=\"http://exist.sourceforge.net/NS/exist\"";
    private static String _MATCH_START = "<exist:match>";
    private static String MATCH_START = "<exist:match xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
    private static String MATCH_END = "</exist:match>";
    
    private static String CUTOFF = "<exist:cutoff/>";
//    private static String CUTOFF = "<exist:cutoff xmlns:exist=\"http://exist.sourceforge.net/NS/exist\"/>";

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
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                    MATCH_END + "</hi> content.</para>", result);

            seq = xquery.execute("//para[ft:query(., '+nested +inner +elements')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
                    MATCH_END + "</hi> " + MATCH_START +
                    "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para>", result);

            seq = xquery.execute("//para[ft:query(term, 'term')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END +
                    "</term>.</para>", result);

            seq = xquery.execute("//para[ft:query(., '+double +match')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
                    MATCH_START + "match" + MATCH_END + "</para>", result);

            seq = xquery.execute(
                    "for $para in //para[ft:query(., '+double +match')] return\n" +
                            "   <hit>{$para}</hit>", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
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
            System.out.println("RESULT: " + result);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute("//para[ft:query(., 'nested')]/note", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
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
            System.out.println("RESULT: " + result);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute("//hi[ft:query(., 'nested')]/parent::note", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
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
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<p>Paragraphs with <s>" + MATCH_START + "mix" + MATCH_END +
                    "</s><s>ed</s> content are <s>danger</s>ous.</p>", result);

            seq = xquery.execute("//p[ft:query(., 'ignored')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<p>A simple<note>sic</note> paragraph with <hi>highlighted</hi> text <note>and a note</note> to be " +
                    MATCH_START + "ignored" + MATCH_END + ".</p>", result);

            seq = xquery.execute("//p[ft:query(., 'highlighted')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<p>A simple<note>sic</note> paragraph with <hi>" + MATCH_START +
                    "highlighted" + MATCH_END + "</hi> text <note>and a note</note> to be " +
                    "ignored.</p>", result);

            seq = xquery.execute("//p[ft:query(., 'highlighted')]/hi", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<hi>" + MATCH_START + "highlighted" + MATCH_END + "</hi>", result);
            
            seq = xquery.execute("//head[ft:query(., 'title')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<head>The <b>" + MATCH_START + "title" + MATCH_END + "</b>of it</head>",
                    result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    @Test
    public void chunkTests() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF2, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            Sequence seq = xquery.execute("//para[ft:query(., 'mixed')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, (NodeProxy)seq.itemAt(0), 5, LuceneMatchChunkListener.CHUNK);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals(
        		"<para "+NS+">" 
					+ CUTOFF + "with <hi>"
						+ _MATCH_START + "mixed" + MATCH_END
					+ "</hi> cont" + CUTOFF
				+"</para>",
				result);
            
            

//            seq = xquery.execute("//para[ft:query(., 'mixed')]", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, 100, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
//                    MATCH_END + "</hi> content.</para>", result);
//
//            seq = xquery.execute("//para[ft:query(., '+nested +inner +elements')]", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
//                    MATCH_END + "</hi> " + MATCH_START +
//                    "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para>", result);
//
//            seq = xquery.execute("//para[ft:query(term, 'term')]", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END +
//                    "</term>.</para>", result);
//
//            seq = xquery.execute("//para[ft:query(., '+double +match')]", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<para>" + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + "</para>", result);
//
//            seq = xquery.execute(
//                    "for $para in //para[ft:query(., '+double +match')] return\n" +
//                            "   <hit>{$para}</hit>", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<hit><para>" + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + "</para></hit>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void chunkMultiMatchTest() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF2, XML2);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            Sequence seq = xquery.execute("//para[ft:query(., 'content')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, (NodeProxy)seq.itemAt(0), 5, LuceneMatchChunkListener.CHUNK);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals(
        		"<para "+NS+">" +
					CUTOFF + " bla " +
					_MATCH_START + "content" + MATCH_END +
					" bla bla bla bla " + 
//					" bla " + CUTOFF + " bla " +
					_MATCH_START + "content" + MATCH_END +
					" bla b" + CUTOFF +
//					" bla " + CUTOFF +
				"</para>",
				result);

            result = queryResult2String(broker, (NodeProxy)seq.itemAt(0), 5, LuceneMatchChunkListener.DO_NOT_CHUNK_NODE);
            XMLAssert.assertEquals(
        		"<para "+NS+">bla bla " +
					_MATCH_START + "content" + MATCH_END +
					" bla bla bla bla " + 
					_MATCH_START + "content" + MATCH_END +
					" bla bla" +
				"</para>",
				result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    @Test
    public void chunkCutNode() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF2, XML3);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            Sequence seq = xquery.execute("//para[ft:query(., 'content')]/parent::*", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, (NodeProxy)seq.itemAt(0), 5, LuceneMatchChunkListener.DO_NOT_CHUNK_NODE);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals(
        		"<root "+NS+">" +
    				CUTOFF + 
    				"<para>" +
	    				 "bla bla " +
						_MATCH_START + "content" + MATCH_END +
						" bla bla bla bla " + 
						_MATCH_START + "content" + MATCH_END +
						" bla bla" + 
					"</para>" +
					CUTOFF +
				"</root>",
				result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void chunkAroundWS() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF2, XML2);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            Sequence seq = xquery.execute("//para[ft:query(., 'content')]/parent::*", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, (NodeProxy)seq.itemAt(0), 6, LuceneMatchChunkListener.CHUNK_TILL_WS);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals(
        		"<root "+NS+">" +
    				"<para>" +
    					CUTOFF + 
		    				 " bla " +
							_MATCH_START + "content" + MATCH_END +
							" bla bla bla bla " + 
							_MATCH_START + "content" + MATCH_END +
							" bla " + 
						CUTOFF +
					"</para>" +
				"</root>",
				result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @BeforeClass
    public static void startDB() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
        HashMap<String, String> m = new HashMap<String, String>();
        m.put("exist", "http://exist.sourceforge.net/NS/exist");
        NamespaceContext ctx = new SimpleNamespaceContext(m);
        XMLUnit.setXpathNamespaceContext(ctx);
    }

    @AfterClass
    public static void closeDB() {
        //TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        pool = null;
    }

    private void configureAndStore(String config, String data) {
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

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, config);

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_matches.xml"), XML);
            assertNotNull(info);
            root.store(transaction, broker, info, data, false);

            transact.commit(transaction);
        } catch (Exception e) {
            transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
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
    
    private String queryResult2String(DBBroker broker, NodeProxy proxy, int chunkOffset, byte mode) throws SAXException, XPathException {
        Properties props = new Properties();
        props.setProperty(OutputKeys.INDENT, "no");
        
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        
        LuceneMatchChunkListener highlighter = new LuceneMatchChunkListener(getLuceneIndex(), chunkOffset, mode);
        highlighter.reset(broker, proxy);

        final StringWriter writer = new StringWriter();
        
        SerializerPool serializerPool = SerializerPool.getInstance();
        SAXSerializer xmlout = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
        try {
        	//setup pipes
			xmlout.setOutput(writer, props);
			
			highlighter.setNextInChain(xmlout);
			
			serializer.setReceiver(highlighter);
			
			//serialize
	        serializer.toReceiver(proxy, false, true);
	        
	        //get result
	        return writer.toString();
        } finally {
        	serializerPool.returnObject(xmlout);
        }
    }
    
    private LuceneIndex getLuceneIndex() {
        return (LuceneIndex) pool.getIndexManager().getIndexById(LuceneIndex.ID);
    }
}
