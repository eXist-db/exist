package org.exist.indexing;

import junit.framework.TestCase;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xupdate.XUpdateProcessor;
import org.exist.xupdate.Modification;
import org.exist.dom.DocumentSet;
import org.exist.indexing.impl.NGramIndexWorker;
import org.exist.indexing.impl.NGramIndex;
import org.xml.sax.InputSource;

import java.io.StringReader;

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
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
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
        try {
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
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
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
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
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
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
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
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

            broker.reindexCollection(XmldbURI.xmldbUriFor("/db"));

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);
            
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[text:ngram-contains(., '123')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[text:ngram-contains(para, '123')]", null, AccessContext.TEST);
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
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.WRITE_LOCK);
            assertNotNull(root);

            root.removeXMLResource(transaction, broker, XmldbURI.create("test_string.xml"));

            checkIndex(broker, docs, "cha", 0);

            seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
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
            Sequence seq = xquery.execute("//item[text:ngram-contains(., 'cha')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[text:ngram-contains(., '123')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//section[text:ngram-contains(para, '123')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute("//*[text:ngram-contains(., '567')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    private void checkIndex(DBBroker broker, DocumentSet docs, String term, int count) {
        NGramIndexWorker index = (NGramIndexWorker) broker.getIndexController().getIndexWorker(NGramIndex.ID);
        Occurrences[] occurrences = index.scanIndex(docs);
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
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        	assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
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
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    protected void tearDown() {
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.removeCollection(transaction, root);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
        BrokerPool.stopAll(false);
    }
}
