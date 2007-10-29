package org.exist.fulltext;

import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.*;
import static org.junit.Assert.*;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.StringReader;

/**
 * Low-level tests for fulltext index configuration and index updates.
 */
public class FTIndexTest {

    public final static String XML =
            "<content>" +
            "<figure>" +
            "<title>Location of equiment items on aircraft</title>" +
            "<img src='foo.jpg'/>" +
            "<img src='baz.jpg'/>" +
            "</figure>" +
            "<figure>" +
            "<title>Hydraulic Power<b>System</b></title>" +
            "<p>paragraphs with <span>mix</span><span>ed</span> content are <span>danger</span>ous.</p>" +
            "</figure>" +
            "</content>";

    private static String XML1 =
            "<test>" +
            "   <item id='1' attr='attribute'><description>Chair</description></item>" +
            "   <item id='2'><description>Table</description><price>892.25</price></item>" +
            "   <item id='3'><description>Cabinet</description><price>1525.00</price></item>" +
            "</test>";

    private static String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "           <include path=\"/content/figure/title\"/>" +
        "           <create qname=\"p\" content=\"mixed\"/>" +
        "           <create qname=\"content\"/>" +
        "		</fulltext>" +
        "	</index>" +
    	"</collection>";

    private static String COLLECTION_CONFIG2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "           <create qname=\"item\"/>" +
        "           <create qname=\"description\"/>" +
        "           <create qname=\"price\"/>" +
        "           <create qname=\"@attr\"/>" +
        "		</fulltext>" +
        "	</index>" +
    	"</collection>";

    private static String COLLECTION_CONFIG3 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
        "           <include path=\"/content/figure/title\" content=\"mixed\"/>" +
        "           <include path=\"/content//p\" content=\"mixed\"/>" +
        "		</fulltext>" +
        "	</index>" +
    	"</collection>";

    private static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    private static String XUPDATE_END =
        "</xu:modifications>";
    
    private static BrokerPool pool;
    private static Collection root;
    private static final QName[] QNDESC = new QName[]{ new QName("description", "", "") };
    private static final QName[] QNPRICE = new QName[]{ new QName("price", "", "") };
    private static final QName[] QNITEM = new QName[]{ new QName("item", "", "") };

    @Test
    public void defaultIndex() {
        DocumentSet docs = configureAndStore(null, XML, "defaultIndex.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            Occurrences[] occur = broker.getTextEngine().scanIndexTerms(docs, docs.toNodeSet(), "a", "ax");
            printOccurrences("Checking for 'a', 'ax'", occur);
            assertEquals(2, occur.length);
            assertEquals("aircraft", occur[0].getTerm());

            occur = checkIndex(docs, broker, null, "power", 1);
            assertEquals("power", occur[0].getTerm());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void mixedIndexes() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG3, XML, "mixedIndexes.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            Occurrences[] occur = checkIndex(docs, broker, null, "hydraulic", 1);
            assertEquals("hydraulic", occur[0].getTerm());

            checkIndex(docs, broker, null, "system", 0);
            occur = checkIndex(docs, broker, null, "powersystem", 1);
            assertEquals("powersystem", occur[0].getTerm());

            occur = checkIndex(docs, broker, null, "mix", 1);
            assertEquals("mixed", occur[0].getTerm());
            checkIndex(docs, broker, null, "ed", 0);
            occur = checkIndex(docs, broker, null, "mixed", 1);
            assertEquals("mixed", occur[0].getTerm());

            occur = checkIndex(docs, broker, null, "danger", 1);
            assertEquals("dangerous", occur[0].getTerm());
            occur = checkIndex(docs, broker, null, "dangerous", 1);
            assertEquals("dangerous", occur[0].getTerm());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void mixedQNameIndexes() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML, "mixedIndexes.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            Occurrences[] occur = checkIndex(docs, broker, null, "aircraft", 1);
            assertEquals("aircraft", occur[0].getTerm());

            occur = checkIndex(docs, broker, null, "system", 1);
            assertEquals("system", occur[0].getTerm());

            occur = checkIndex(docs, broker, null, "power", 1);
            assertEquals("power", occur[0].getTerm());
            
            QName qn[] = { new QName("p", "", "") };
            occur = checkIndex(docs, broker, qn, "mixed", 1);
            assertEquals("mixed", occur[0].getTerm());

            occur = checkIndex(docs, broker, qn, "dangerous", 1);
            assertEquals("dangerous", occur[0].getTerm());

            occur = checkIndex(docs, broker, qn, "content", 1);
            assertEquals("content", occur[0].getTerm());

            qn[0] = new QName("content", "", "");
            occur = checkIndex(docs, broker, qn, "aircraft", 1);
            assertEquals("aircraft", occur[0].getTerm());

            occur = checkIndex(docs, broker, qn, "hydraulic", 1);
            assertEquals("hydraulic", occur[0].getTerm());

            // not a mixed-content index
            checkIndex(docs, broker, qn, "dangerous", 0);
            occur = checkIndex(docs, broker, qn, "danger", 1);
            assertEquals("danger", occur[0].getTerm());

            occur = checkIndex(docs, broker, qn, "power", 1);
            assertEquals("power", occur[0].getTerm());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void dropDocument() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML, "dropDocument.xml");
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

            QName qn[] = { new QName("p", "", "") };
            checkIndex(docs, broker, qn, null, 0);

            qn[0] = new QName("content", "", "");
            checkIndex(docs, broker, qn, null, 0);

            checkIndex(docs, broker, null, null, 0);
            
            transact.commit(transaction);
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
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML, "dropDocument.xml");
        DBBroker broker = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            broker.reindexCollection(TestConstants.TEST_COLLECTION_URI);

            Occurrences[] occur = broker.getTextEngine().scanIndexTerms(docs, docs.toNodeSet(), "o", "ox");
            printOccurrences("o, ox", occur);
            assertEquals(2, occur.length);
            assertEquals("of", occur[0].getTerm());
            assertEquals("on", occur[1].getTerm());

            occur = checkIndex(docs, broker, null, "power", 1);
            assertEquals("power", occur[0].getTerm());

            QName qn[] = { new QName("p", "", "") };
            checkIndex(docs, broker, qn, "mixed", 1);
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
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML1, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	broker = pool.get(SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, QNDESC, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, QNITEM, null, 5);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[description &= 'chair']", null, AccessContext.TEST);
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

            checkIndex(docs, broker, QNPRICE, null, 1);
            checkIndex(docs, broker, QNITEM, null, 4);
            checkIndex(docs, broker, QNPRICE, "892", 0);
            checkIndex(docs, broker, QNITEM, "892", 0);
            Occurrences o[] = checkIndex(docs, broker, QNDESC, "table", 1);
            assertEquals("table", o[0].getTerm());
            o = checkIndex(docs, broker, QNDESC, "cabinet", 1);
            assertEquals("cabinet", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "table", 1);
            assertEquals("table", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "cabinet", 1);
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

            checkIndex(docs, broker, QNDESC, null, 2);
            checkIndex(docs, broker, QNITEM, null, 3);
            checkIndex(docs, broker, QNDESC, "cabinet", 0);
            checkIndex(docs, broker, QNITEM, "cabinet", 0);
            o = checkIndex(docs, broker, QNPRICE, "1525.00", 1);
            assertEquals("1525.00", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "1525.00", 1);
            assertEquals("1525.00", o[0].getTerm());

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

            o = checkIndex(docs, broker, QNDESC, null, 1);
            assertEquals("table", o[0].getTerm());
            checkIndex(docs, broker, QNDESC, "chair", 0);
            checkIndex(docs, broker, QNITEM, null, 2);
            assertEquals("table", o[0].getTerm());

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
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML1, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	broker = pool.get(SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, QNDESC, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, QNITEM, null, 5);
            
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[description &= 'chair']", null, AccessContext.TEST);
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
                "       <item id='4'><description>Armchair</description><price>340</price></item>" +
                "   </xu:append>" +
                XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, QNPRICE, null, 3);
            checkIndex(docs, broker, QNDESC, null, 4);
            checkIndex(docs, broker, QNITEM, null, 7);
            Occurrences o[] = checkIndex(docs, broker, QNPRICE, "340", 1);
            assertEquals("340", o[0].getTerm());
            o = checkIndex(docs, broker, QNDESC, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "340", 1);
            assertEquals("340", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());

            // Insert before top element
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

            checkIndex(docs, broker, QNPRICE, null, 4);
            checkIndex(docs, broker, QNDESC, null, 5);
            checkIndex(docs, broker, QNITEM, null, 9);
            o = checkIndex(docs, broker, QNPRICE, "1230", 1);
            assertEquals("1230", o[0].getTerm());
            o = checkIndex(docs, broker, QNDESC, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "1230", 1);
            assertEquals("1230", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());

            // Insert after element
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

            checkIndex(docs, broker, QNPRICE, null, 5);
            checkIndex(docs, broker, QNDESC, null, 6);
            checkIndex(docs, broker, QNITEM, null, 11);
            o = checkIndex(docs, broker, QNPRICE, "777", 1);
            assertEquals("777", o[0].getTerm());
            o = checkIndex(docs, broker, QNDESC, "refrigerator", 1);
            assertEquals("refrigerator", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "777", 1);
            assertEquals("777", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "refrigerator", 1);
            assertEquals("refrigerator", o[0].getTerm());

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

            checkIndex(docs, broker, QNPRICE, null, 6);
            checkIndex(docs, broker, QNITEM, null, 12);
            o = checkIndex(docs, broker, QNPRICE, "999", 1);
            assertEquals("999", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "999", 1);
            assertEquals("999", o[0].getTerm());

            // Insert before nested element
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

            checkIndex(docs, broker, QNPRICE, null, 7);
            checkIndex(docs, broker, QNITEM, null, 13);
            o = checkIndex(docs, broker, QNPRICE, "999", 1);
            assertEquals("999", o[0].getTerm());
            o = checkIndex(docs, broker, QNPRICE, "888", 1);
            assertEquals("888", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "999", 1);
            assertEquals("999", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "888", 1);
            assertEquals("888", o[0].getTerm());

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
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML1, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, QNDESC, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, QNITEM, null, 5);
            
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[description &= 'chair']", null, AccessContext.TEST);
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

            checkIndex(docs, broker, QNDESC, null, 3);
            checkIndex(docs, broker, QNITEM, null, 5);
            checkIndex(docs, broker, QNDESC, "chair", 0);
            checkIndex(docs, broker, QNITEM, "chair", 0);
            Occurrences o[] = checkIndex(docs, broker, QNDESC, "wardrobe", 1);
            assertEquals("wardrobe", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "wardrobe", 1);
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

            checkIndex(docs, broker, QNDESC, null, 3);
            checkIndex(docs, broker, QNITEM, null, 5);
            checkIndex(docs, broker, QNDESC, "wardrobe", 0);
            checkIndex(docs, broker, QNITEM, "wardrobe", 0);
            o = checkIndex(docs, broker, QNDESC, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "wheelchair", 1);
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
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML1, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, QNDESC, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, QNITEM, null, 5);
            
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[description &= 'chair']", null, AccessContext.TEST);
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

            checkIndex(docs, broker, QNDESC, null, 3);
            checkIndex(docs, broker, QNPRICE, null, 3);
            checkIndex(docs, broker, QNITEM, null, 6);
            checkIndex(docs, broker, QNDESC, "chair", 0);
            checkIndex(docs, broker, QNITEM, "chair", 0);
            Occurrences o[] = checkIndex(docs, broker, QNDESC, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, QNPRICE, "809.50", 1);
            assertEquals("809.50", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, QNITEM, "809.50", 1);
            assertEquals("809.50", o[0].getTerm());

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

            checkIndex(docs, broker, QNDESC, null, 3);
            checkIndex(docs, broker, QNITEM, null, 6);
            checkIndex(docs, broker, QNDESC, "wheelchair", 0);
            o = checkIndex(docs, broker, QNDESC, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());
            checkIndex(docs, broker, QNITEM, "wheelchair", 0);
            o = checkIndex(docs, broker, QNITEM, "armchair", 1);
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

    @Test
    public void xupdateRename() {
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML1, "xupdate.xml");
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            transact = pool.getTransactionManager();
            transaction = transact.beginTransaction();

            Occurrences occur[] = checkIndex(docs, broker, QNDESC, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, QNITEM, null, 5);

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//item[description &= 'chair']", null, AccessContext.TEST);
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

            checkIndex(docs, broker, QNDESC, null, 3);
            checkIndex(docs, broker, QNPRICE, null, 2);
            checkIndex(docs, broker, QNITEM, null, 3);
            Occurrences o[] = checkIndex(docs, broker, QNDESC, "table", 1);
            assertEquals("table", o[0].getTerm());
            checkIndex(docs, broker, QNITEM, "table", 0);

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
    
    private Occurrences[] checkIndex(DocumentSet docs, DBBroker broker, QName[] qn, String term, int expected) throws PermissionDeniedException {
        Occurrences[] occur;
        if (qn == null)
            occur = broker.getTextEngine().scanIndexTerms(docs, docs.toNodeSet(), term, null);
        else
            occur = broker.getTextEngine().scanIndexTerms(docs, docs.toNodeSet(), qn, term, null);
        printOccurrences(term, occur);
        assertEquals(expected, occur.length);
        return occur;
    }

    private void printOccurrences(String msg, Occurrences[] occur) {
        StringBuilder buf = new StringBuilder();
        if (msg != null)
            buf.append(msg).append(": ");
        for (int i = 0; i < occur.length; i++) {
            Occurrences occurrences = occur[i];
            if (i > 0)
                buf.append(", ");
            buf.append(occurrences.getTerm()).append(":\t").append(occurrences.getOccurrences());
        }
        System.out.println(buf.toString());
    }

    private DocumentSet configureAndStore(String configuration, String data, String docName) {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        DocumentSet docs = new DocumentSet();
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
            System.out.println("Transaction started ...");

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

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

            Collection config = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            assertNotNull(config);
            broker.removeCollection(transaction, config);

            assertNotNull(root);
            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
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
