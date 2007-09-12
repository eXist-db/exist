package org.exist.xquery;

import java.io.IOException;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;

public class XQueryUpdateTest extends TestCase {

	public static void main(String[] args) {
        TestRunner.run(XQueryUpdateTest.class);
    }

    protected static XmldbURI TEST_COLLECTION = XmldbURI.create(DBBroker.ROOT_COLLECTION + "/test");
    
    protected static String TEST_XML = 
        "<?xml version=\"1.0\"?>" +
        "<products/>";
    
    protected static String UPDATE_XML =
        "<progress total=\"100\" done=\"0\" failed=\"0\" passed=\"0\"/>";
    
    protected final static int ITEMS_TO_APPEND = 1000;
    
    private BrokerPool pool;
    
	public void testAppend() {
        DBBroker broker = null;
        try {
        	System.out.println("testAppend() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            String query =
            	"   declare variable $i external;\n" +
            	"	update insert\n" +
            	"		<product id='id{$i}' num='{$i}'>\n" +
            	"			<description>Description {$i}</description>\n" +
            	"			<price>{$i + 1.0}</price>\n" +
            	"			<stock>{$i * 10}</stock>\n" +
            	"		</product>\n" +
            	"	into /products";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", new Integer(i));
                xquery.execute(compiled, null);
            }
            
            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);
            
            Serializer serializer = broker.getSerializer();
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
            
            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
            
            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
            System.out.println("testAppend: PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
	}
    
	public void testAppendAttributes() {
		testAppend();
        DBBroker broker = null;
        try {
        	System.out.println("testAppendAttributes() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            String query =
            	"   declare variable $i external;\n" +
            	"	update insert\n" +
            	"		attribute name { concat('n', $i) }\n" +
            	"	into //product[@num = $i]";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", new Integer(i));
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());

            seq = xquery.execute("//product[@name = 'n20']", null, AccessContext.TEST);
            assertEquals(1, seq.getItemCount());

            store(broker, "attribs.xml", "<test attr1='aaa' attr2='bbb'>ccc</test>");
            query = "update insert attribute attr1 { 'eee' } into /test";

            System.out.println("testing duplicate attribute ...");
            xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("xmldb:document('" + TEST_COLLECTION + "/attribs.xml')/test[@attr1 = 'eee']", null, AccessContext.TEST);
            assertEquals(1, seq.getItemCount());
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));

            System.out.println("testAppendAttributes: PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
	}

    public void testInsertBefore() {
        DBBroker broker = null;
        try {
            System.out.println("testInsertBefore() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            String query =
                "   update insert\n" +
                "       <product id='original'>\n" +
                "           <description>Description</description>\n" +
                "           <price>0</price>\n" +
                "           <stock>10</stock>\n" +
                "       </product>\n" +
                "   into /products";

            XQuery xquery = broker.getXQueryService();
            xquery.execute(query, null, AccessContext.TEST);

            query =
                "   declare variable $i external;\n" +
                "   update insert\n" +
                "       <product id='id{$i}'>\n" +
                "           <description>Description {$i}</description>\n" +
                "           <price>{$i + 1.0}</price>\n" +
                "           <stock>{$i * 10}</stock>\n" +
                "       </product>\n" +
                "   preceding /products/product[1]";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", new Integer(i));
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND + 1, seq.getItemCount());

            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
            System.out.println("testInsertBefore: PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    public void testInsertAfter() {
        DBBroker broker = null;
        try {
            System.out.println("testInsertAfter() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            String query =
                "   update insert\n" +
                "       <product id='original'>\n" +
                "           <description>Description</description>\n" +
                "           <price>0</price>\n" +
                "           <stock>10</stock>\n" +
                "       </product>\n" +
                "   into /products";

            XQuery xquery = broker.getXQueryService();
            xquery.execute(query, null, AccessContext.TEST);

            query =
                "   declare variable $i external;\n" +
                "   update insert\n" +
                "       <product id='id{$i}'>\n" +
                "           <description>Description {$i}</description>\n" +
                "           <price>{$i + 1.0}</price>\n" +
                "           <stock>{$i * 10}</stock>\n" +
                "       </product>\n" +
                "   following /products/product[1]";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                context.declareVariable("i", new Integer(i));
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND + 1, seq.getItemCount());

            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());
            System.out.println("testInsertAfter: PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    public void testUpdate() {
    	testAppend();
        DBBroker broker = null;
        try {
            System.out.println("testUpdate() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);
            
            XQuery xquery = broker.getXQueryService();
            
            String query =
            	"declare option exist:output-size-limit '-1';\n" +
            	"for $prod at $i in //product return\n" +
                "	update value $prod/description\n" +
                "	with concat('Updated Description', $i)";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product[starts-with(description, 'Updated')]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            Serializer serializer = broker.getSerializer();
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));

            for (int i = 1; i <= ITEMS_TO_APPEND; i++) {
                seq = xquery.execute("//product[description &= 'Description" + i + "']", null, AccessContext.TEST);
                assertEquals(1, seq.getItemCount());
                System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
            }
            seq = xquery.execute("//product[description &= 'Updated']", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
            
            query =
            	"declare option exist:output-size-limit '-1';\n" +
            	"for $prod at $count in //product return\n" +
                "	update value $prod/stock/text()\n" +
                "	with (400 + $count)";
            seq = xquery.execute(query, null, AccessContext.TEST);
            
            seq = xquery.execute("//product[stock > 400]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);
            seq = xquery.execute("//product[stock &= '401']", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
            
            query =
            	"declare option exist:output-size-limit '-1';\n" +
            	"for $prod in //product return\n" +
                "	update value $prod/@num\n" +
                "	with xs:int($prod/@num) * 3";
            seq = xquery.execute(query, null, AccessContext.TEST);
            
            seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);
            
            seq = xquery.execute("//product[@num = 3]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);
            
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
            
            query =
            	"declare option exist:output-size-limit '-1';\n" +
            	"for $prod in //product return\n" +
                "	update value $prod/stock\n" +
                "	with (<local>10</local>,<external>1</external>)";
            seq = xquery.execute(query, null, AccessContext.TEST);
            
            seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);
            
            seq = xquery.execute("//product/stock/external[. = 1]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);
            
            System.out.println("testUpdate: PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    public void testRemove() {
    	testAppend();

        DBBroker broker = null;
        try {
        	broker = pool.get(SecurityManager.SYSTEM_USER);

        	XQuery xquery = broker.getXQueryService();

        	String query =
        		"for $prod in //product return\n" +
        		"	update delete $prod\n";
        	Sequence seq = xquery.execute(query, null, AccessContext.TEST);

        	seq = xquery.execute("//product", null, AccessContext.TEST);
        	assertEquals(seq.getItemCount(), 0);

        	System.out.println("testRemove: PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
	}

    public void testRename() {
    	testAppend();
        DBBroker broker = null;
        try {
            System.out.println("testUpdate() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();

            String query =
            	"for $prod in //product return\n" +
            	"	update rename $prod/description as 'desc'\n";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/desc", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	update rename $prod/@num as 'count'\n";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/@count", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            System.out.println("testUpdate: PASS");
        } catch (Exception e) {
        	e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    public void testReplace() {
    	testAppend();
        DBBroker broker = null;
        try {
            System.out.println("testReplace() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();

            String query =
            	"for $prod in //product return\n" +
            	"	update replace $prod/description with <desc>An updated description.</desc>\n";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/desc", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	update replace $prod/@num with '1'\n";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product/@num", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            query =
            	"for $prod in //product return\n" +
            	"	update replace $prod/desc/text() with 'A new update'\n";
            seq = xquery.execute(query, null, AccessContext.TEST);

            seq = xquery.execute("//product[starts-with(desc, 'A new')]", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), ITEMS_TO_APPEND);

            System.out.println("testUpdate: PASS");
        } catch (Exception e) {
        	e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    public void testAttrUpdate() {
        DBBroker broker = null;
        try {
            System.out.println("testAttrUpdate() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);
            store(broker, "test.xml", UPDATE_XML);

            String query =
                    "let $progress := /progress\n" +
                    "for $i in 1 to 100\n" +
                    "let $done := $progress/@done\n" +
                    "return (\n" +
                    "   update value $done with xs:int($done + 1),\n" +
                    "   xs:int(/progress/@done)\n" +
                    ")";
            XQuery xquery = broker.getXQueryService();
            Sequence result = xquery.execute(query, null, AccessContext.TEST);

            System.out.println("testAttrUpdate(): PASSED\n");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    public void testAppendCDATA() {
        DBBroker broker = null;
        try {
        	System.out.println("testAppendCDATA() ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            XQuery xquery = broker.getXQueryService();
            String query =
            	"   declare variable $i external;\n" +
            	"	update insert\n" +
            	"		<product>\n" +
            	"			<description><![CDATA[me & you <>]]></description>\n" +
            	"		</product>\n" +
            	"	into /products";
            XQueryContext context = xquery.newContext(AccessContext.TEST);
            CompiledXQuery compiled = xquery.compile(context, query);
            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
                xquery.execute(compiled, null);
            }

            Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
            assertEquals(seq.getItemCount(), 1);

            Serializer serializer = broker.getSerializer();
            System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));

            seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getItemCount());

            System.out.println("testAppendCDATA: PASS");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    public void bugtestInsertAttribDoc_1730726() {
        DBBroker broker = null;
        try {
            System.out.println(this.getName()+" ...\n");
            broker = pool.get(SecurityManager.SYSTEM_USER);

            String query =
                "declare namespace xmldb = \"http://exist-db.org/xquery/xmldb\"; "+
                "let $uri := xmldb:store(\"/db\", \"insertAttribDoc.xml\", <C/>) "+
                "let $node := doc($uri)/element() "+
                "let $attrib := <Value f=\"ATTRIB VALUE\"/>/@* "+
                "return update insert $attrib into $node";
            
            XQuery xquery = broker.getXQueryService();
            Sequence result = xquery.execute(query, null, AccessContext.TEST);

            System.out.println(this.getName()+"(): PASSED\n");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    


    protected void setUp() throws Exception {
        this.pool = startDB();
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            store(broker, "test.xml", TEST_XML);
        } catch (Exception e) {
        	e.printStackTrace();
            fail(e.getMessage());
        }  finally {
            pool.release(broker);
        }
    }

	private void store(DBBroker broker, String docName, String data) throws PermissionDeniedException, EXistException, TriggerException, SAXException, LockException, TransactionException {
		TransactionManager mgr = pool.getTransactionManager();
		Txn transaction = mgr.beginTransaction();        
		System.out.println("Transaction started ...");
		
		try {
			Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
			broker.saveCollection(transaction, root);
			
			IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
			//TODO : unlock the collection here ?
			root.store(transaction, broker, info, data, false);
	   
			mgr.commit(transaction);
			
			DocumentImpl doc = root.getDocument(broker, XmldbURI.create(docName));
		    broker.getSerializer().serialize(doc);
		} catch (IOException e) {
			mgr.abort(transaction);
			fail();
		}
	}
    
    protected BrokerPool startDB() {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;
    }
    
     protected void tearDown() {
         pool = null;
         try {
             BrokerPool.stopAll(false);
         } catch (Exception e) {            
             fail(e.getMessage());
         }
     }
}
