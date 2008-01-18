package org.exist.fulltext;

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
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

public class FTMatchListenerTest {

    private static String XML =
        "<root>" +
        "   <para>some paragraph with <hi>mixed</hi> content.</para>" +
        "   <para>another paragraph with <note><hi>nested</hi> inner</note> elements.</para>" +
        "   <para>a third paragraph with <term>term</term>.</para>" +
        "   <para>double match double match</para>" +
        "</root>";

    private static String CONF1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "           <include path=\"//para\"/>" +
        "		</fulltext>" +
        "	</index>" +
    	"</collection>";

    private static String CONF2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "           <create qname=\"para\"/>" +
        "           <create qname=\"term\"/>" +
        "		</fulltext>" +
        "	</index>" +
    	"</collection>";

    private static String MATCH_START = "<exist:match xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
    private static String MATCH_END = "</exist:match>";

    private static BrokerPool pool;

    /**
     * Test match highlighting for index configured by path, e.g.
     * &lt;include path="//a/b"/&gt;.
     */
    @Test
    public void indexByPath() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1);

            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[. &= 'mixed']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                MATCH_END + "</hi> content.</para>", result);

            seq = xquery.execute("//para[hi &= 'mixed']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                MATCH_END + "</hi> content.</para>", result);

            seq = xquery.execute("//para[. &= 'another']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "another" + MATCH_END + " paragraph with <note><hi>nested</hi> " +
                "inner</note> elements.</para>", result);

            seq = xquery.execute("//para[. &= 'nested inner']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
                MATCH_END + "</hi> " + MATCH_START +
                "inner" + MATCH_END + "</note> elements.</para>", result);

            seq = xquery.execute("//para[. &= 'nested inner elements']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
                MATCH_END + "</hi> " + MATCH_START +
                "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para>", result);

            seq = xquery.execute(
                "for $para in //para[. &= 'nested inner elements'] return\n" +
                "   <hit>{$para}</hit>", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<hit><para>another paragraph with <note><hi>" + MATCH_START + "nested" +
                MATCH_END + "</hi> " + MATCH_START +
                "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para></hit>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void ancestorAxis() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1);

            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//hi[. &= 'mixed']/parent::para", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute("//hi[. &= 'mixed']/..", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            seq = xquery.execute("//hi[. &= 'nested']/ancestor::para", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);

            seq = xquery.execute("//hi[. &= 'nested']/ancestor::*[2]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            String query =
                    "let $results := for $m in //para[. &= 'mixed']/hi order by $m return $m/parent::para " +
                    "return $results";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);

            query =
                    "let $results := for $m in //hi[. &= 'nested'] return $m/ancestor::para " +
                    "return $results";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertXpathEvaluatesTo("1", "count(//exist:match)", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    /**
     * Test match highlighting for index configured by QName, e.g.
     * &lt;create qname="a"/&gt;.
     */
    @Test
    public void indexByQName() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF2);

            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[. &= 'mixed']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                MATCH_END + "</hi> content.</para>", result);

            seq = xquery.execute("//para[. &= 'nested inner elements']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
                MATCH_END + "</hi> " + MATCH_START +
                "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para>", result);

            seq = xquery.execute("//para[term &= 'term']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END +
                "</term>.</para>", result);

            seq = xquery.execute("//para[. &= 'double match']", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "double" + MATCH_END + " " +
                MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
                MATCH_START + "match" + MATCH_END + "</para>", result);

            seq = xquery.execute(
                    "for $para in //para[. &= 'double match'] return\n" +
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
        HashMap m = new HashMap();
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

    private void configureAndStore(String config) {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
            root.store(transaction, broker, info, XML, false);

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
}