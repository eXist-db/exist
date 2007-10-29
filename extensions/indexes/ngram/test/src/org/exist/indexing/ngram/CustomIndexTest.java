package org.exist.indexing.ngram;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.File;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import junit.framework.TestCase;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentSet;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.Occurrences;
import org.exist.util.ConfigurationHelper;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;

/**
 * 
 */
public class CustomIndexTest extends TestCase {

    private static String XML =
            "<test>" +
            "   <item id='1' attr='attribute'><description>Chair</description></item>" +
            "   <item id='2'><description>Table</description><price>892.25</price></item>" +
            "   <item id='3'><description>Cabinet</description><price>1525.00</price></item>" +
            "</test>";

    private static String XML2 =
            "<section>" +
            "   <para>01234</para>" +
            "   <para>56789</para>" +
            "</section>";
    
    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"all\">" +
    	"		</fulltext>" +
    	"		<ngram qname=\"item\"/>" +
        "		<ngram qname=\"@attr\"/>" +
        "        <ngram qname=\"para\"/>" +
        "	</index>" +
    	"</collection>";

    private static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    private static String XUPDATE_END =
        "</xu:modifications>";

    private BrokerPool pool;
    private DocumentSet docs;

    /**
     * Remove nodes from different levels of the tree and check if the index is
     * correctly updated.
     */
    public void testXUpdateRemove() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();
            
            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='2']/price\"/>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "892", 0);
            checkIndex(broker, docs, "tab", 1);
            checkIndex(broker, docs, "le8", 0);

            checkIndex(broker, docs, "cab", 1);

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

            checkIndex(broker, docs, "cab", 0);

            checkIndex(broker, docs, "att", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='1']/@attr\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "att", 0);

            checkIndex(broker, docs, "cha", 1);
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

            checkIndex(broker, docs, "cha", 0);
            
            transact.commit(transaction);
        } catch (Exception e) {
            transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    public void testXUpdateInsert() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:append select=\"/test\">" +
                    "       <item id='4'><description>Armchair</description><price>340</price></item>" +
                    "   </xu:append>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "arm", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']\">" +
                    "           <item id='0'><description>Wheelchair</description><price>1230</price></item>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "hee", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']\">" +
                    "           <item id='1.1'><description>refrigerator</description><price>777</price></item>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "ref", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']/description\">" +
                    "           <price>999</price>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "999", 1);
            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "ir9", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']/description\">" +
                    "           <price>888</price>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "999", 1);
            checkIndex(broker, docs, "888", 1);
            checkIndex(broker, docs, "88c", 1);

            checkIndex(broker, docs, "att", 1);
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
            checkIndex(broker, docs, "att", 0);
            checkIndex(broker, docs, "abc", 1);

            transact.commit(transaction);
        } catch (Exception e) {
            transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    public void testXUpdateUpdate() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

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

            checkIndex(broker, docs, "war", 1);
            checkIndex(broker, docs, "cha", 0);

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

            checkIndex(broker, docs, "whe", 1);

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
            checkIndex(broker, docs, "abc", 1);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    public void testXUpdateReplace() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:replace select=\"//item[@id = '1']\">" +
                    "       <item id='4'><description>Wheelchair</description><price>809.50</price></item>" +
                    "   </xu:replace>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "whe", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:replace select=\"//item[@id = '4']/description\">" +
                    "       <description>Armchair</description>" +
                    "   </xu:replace>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "whe", 0);
            checkIndex(broker, docs, "arm", 1);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }

    public void testXUpdateRename() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:rename select=\"//item[@id='2']\">renamed</xu:rename>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "tab", 0);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) {
                pool.release(broker);
            }
        }
    }
 
    public void testReindex() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            //Doh ! This reindexes *all* the collections for *every* index
            broker.reindexCollection(XmldbURI.xmldbUriFor("/db"));

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);
            
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[ngram:contains(para, '123')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[ngram:contains(para, '123')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    public void testDropIndex() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.WRITE_LOCK);
            assertNotNull(root);

            root.removeXMLResource(transaction, broker, XmldbURI.create("test_string.xml"));

            checkIndex(broker, docs, "cha", 0);

            seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    public void testQuery() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[ngram:contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[ngram:contains(*, '123')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[ngram:contains(para, '123')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//*[ngram:contains(., '567')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    public void testIndexKeys() {
        DBBroker broker = null;
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            
            Sequence seq = xquery.execute("util:index-key-occurrences(/test/item, 'cha', 'ngram-index')", null, AccessContext.TEST);
            //Sequence seq = xquery.execute("util:index-key-occurrences(/test/item, 'cha', 'org.exist.indexing.impl.NGramIndex')", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("util:index-key-occurrences(/test/item, 'le8', 'ngram-index')", null, AccessContext.TEST);
            //seq = xquery.execute("util:index-key-occurrences(/test/item, 'le8', 'org.exist.indexing.impl.NGramIndex')", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("util:index-key-documents(/test/item, 'le8', 'ngram-index')", null, AccessContext.TEST);
            //seq = xquery.execute("util:index-key-documents(/test/item, 'le8', 'org.exist.indexing.impl.NGramIndex')", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            
            seq = xquery.execute("util:index-key-documents(/test/item, 'le8', 'ngram-index')", null, AccessContext.TEST);
            //seq = xquery.execute("util:index-key-doucments(/test/item, 'le8', 'org.exist.indexing.impl.NGramIndex')", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            
            String queryBody =
                "declare function local:callback($key as item(), $data as xs:int+)\n" +
                "as element()+ {\n" + 
                "    <item>\n" + 
                "        <key>{$key}</key>\n" + 
                "        <frequency>{$data[1]}</frequency>\n" + 
                "    </item>\n" + 
                "};\n" + 
                "\n";
            
            String query = queryBody + "util:index-keys(/test/item, \'\', util:function(\'local:callback\', 2), 1000, 'ngram-index')";
            //String query = queryBody + "util:index-keys(/test/item, \'\', util:function(\'local:callback\', 2), 1000, 'org.exist.indexing.impl.NGramIndex')";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);
            //TODO : check cardinality
            StringWriter out = new StringWriter();
            Properties props = new Properties();
            props.setProperty(OutputKeys.INDENT, "yes");
            SAXSerializer serializer = new SAXSerializer(out, props);
            serializer.startDocument();
            for (SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                next.toSAX(broker, serializer, props);
            }
            serializer.endDocument();
            //TODO : check content
            System.out.println(out.toString());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    //TODO : could be replaced by an XQuery call to index-keys(). See above
    private void checkIndex(DBBroker broker, DocumentSet docs, String term, int count) {
        NGramIndexWorker index = (NGramIndexWorker) broker.getIndexController().getWorkerByIndexId(NGramIndex.ID);
        XQueryContext context = new XQueryContext(broker, AccessContext.TEST);
        Occurrences[] occurrences = index.scanIndex(context, docs, null, null);
        int found = 0;
        for (int i = 0; i < occurrences.length; i++) {
            Occurrences occurrence = occurrences[i];
            if (occurrence.getTerm().compareTo(term) == 0)
                found++;
        }        
        assertEquals(count, found);        
    }

    protected void setUp() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            System.out.printf("conf: " + confFile.getAbsolutePath());
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

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);

            docs = new DocumentSet();

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_string.xml"), XML);
            assertNotNull(info);
            root.store(transaction, broker, info, XML, false);

            docs.add(info.getDocument());

            info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_string2.xml"), XML2);
            assertNotNull(info);
            root.store(transaction, broker, info, XML2, false);

            docs.add(info.getDocument());

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            transact.abort(transaction);
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    protected void tearDown() {
        BrokerPool pool = null;
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
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
            broker.removeCollection(transaction, root);

            Collection config = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            assertNotNull(config);
            broker.removeCollection(transaction, config);
            
            transact.commit(transaction);
        } catch (Exception e) {
        	transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
        BrokerPool.stopAll(false);
    }
}
