package org.exist.indexing.lucene;

import org.exist.TestUtils;
import org.exist.Indexer;
import org.exist.xupdate.XUpdateProcessor;
import org.exist.xupdate.Modification;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.xacml.AccessContext;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.Occurrences;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.StringReader;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class LuceneIndexTest {

    private static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    private static String XUPDATE_END =
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

    private static String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "		</fulltext>" +
        "       <lucene>" +
        "           <text qname=\"p\"/>" +
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
    	"		<fulltext default=\"none\">" +
        "           <create qname=\"item\"/>" +
        "           <create qname=\"description\"/>" +
        "           <create qname=\"condition\"/>" +
        "           <create qname=\"@attr\"/>" +
        "		</fulltext>" +
        "       <lucene>" +
        "           <text qname=\"item\"/>" +
        "           <text qname=\"description\"/>" +
        "           <text qname=\"condition\"/>" +
        "           <text qname=\"@attr\"/>" +
        "       </lucene>" +
        "	</index>" +
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            checkIndex(docs, broker, new QName[] { new QName("head", "") }, "title", 1);
            Occurrences[] o = checkIndex(docs, broker, new QName[]{new QName("p", "")}, "with", 1);
            assertEquals(2, o[0].getOccurrences());
            checkIndex(docs, broker, new QName[] { new QName("hi", "") }, "just", 1);
            checkIndex(docs, broker, null, "in", 2);

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

            System.out.println("Test PASSED.");
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, "samples/shakespeare");
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            broker.reindexCollection(TestConstants.TEST_COLLECTION_URI);

            checkIndex(docs, broker, new QName[] { new QName("head", "") }, "title", 1);
            Occurrences[] o = checkIndex(docs, broker, new QName[]{new QName("p", "")}, "with", 1);
            assertEquals(2, o[0].getOccurrences());
            checkIndex(docs, broker, new QName[] { new QName("hi", "") }, "just", 1);
            checkIndex(docs, broker, null, "in", 2);

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
        	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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

            checkIndex(docs, broker, new QName[] { new QName("description", "") }, null, 2);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("description", "") }, "cabinet", 0);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "cabinet", 0);
            checkIndex(docs, broker, new QName[] { new QName("condition", "") }, "bad", 1);
            checkIndex(docs, broker, new QName[] { new QName("item", "") }, "bad", 1);

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

            QName qnattr[] = { new QName("attr", "", "") };
            qnattr[0].setNameType(ElementValue.ATTRIBUTE);
            checkIndex(docs, broker, qnattr, null, 0);

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
        	broker = pool.get(SecurityManager.SYSTEM_USER);
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
            broker = pool.get(SecurityManager.SYSTEM_USER);
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
            e.printStackTrace();
            transact.abort(transaction);
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
            e.printStackTrace();
            transact.abort(transaction);
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
        return docs;
    }

    private Occurrences[] checkIndex(DocumentSet docs, DBBroker broker, QName[] qn, String term, int expected) throws PermissionDeniedException {
        LuceneIndexWorker index = (LuceneIndexWorker)
            broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        Map hints = new HashMap();
        if (term != null)
            hints.put(OrderedValuesIndex.START_VALUE, term);
        if (qn != null && qn.length > 0) {
            List qnames = new ArrayList();
            for (int i = 0; i < qn.length; i++) {
                qnames.add(qn[i]);
            }
            hints.put(QNamedKeysIndex.QNAMES_KEY, qnames);
        }
        XQueryContext context = new XQueryContext(broker, AccessContext.TEST);
        Occurrences[] occur = index.scanIndex(context, docs, docs.docsToNodeSet(), hints);
        if (expected != occur.length) {
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
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
            e.printStackTrace();
            transact.abort(transaction);
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
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection collConfig = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
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
//        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        pool = null;
        root = null;
    }
}

