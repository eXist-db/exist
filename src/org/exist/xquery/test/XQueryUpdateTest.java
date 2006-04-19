package org.exist.xquery.test;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;

public class XQueryUpdateTest extends TestCase {

	public static void main(String[] args) {
        TestRunner.run(XQueryUpdateTest.class);
    }

    protected static String TEST_COLLECTION = DBBroker.ROOT_COLLECTION + "/test";
    protected static String TEST_XML = 
        "<?xml version=\"1.0\"?>" +
        "<products/>";
    
    protected final static int ITEMS_TO_APPEND = 70;
    
    private BrokerPool pool;
    
//	public void testAppend() {
//        DBBroker broker = null;
//        try {
//        	System.out.println("testAppend() ...\n");
//            broker = pool.get(SecurityManager.SYSTEM_USER);
//            
//            XQuery xquery = broker.getXQueryService();
//            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
//                String query =
//                	"	update insert\n" +
//                	"		<product id='id{$i}'>\n" +
//                	"			<description>Description {$i}</description>\n" +
//                	"			<price>{$i + 1.0}</price>\n" +
//                	"			<stock>{$i * 10}</stock>\n" +
//                	"		</product>\n" +
//                	"	into /products";
//                XQueryContext context = xquery.newContext(AccessContext.TEST);
//                context.declareVariable("i", new Integer(i));
//                CompiledXQuery compiled = xquery.compile(context, query);
//                xquery.execute(compiled, null);
//                
//                Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
//                assertEquals(seq.getLength(), 1);
//                
//                Serializer serializer = broker.getSerializer();
//                System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
//            }
//            
//            Sequence seq = xquery.execute("//product", null, AccessContext.TEST);
//            assertEquals(ITEMS_TO_APPEND, seq.getLength());
//            
//            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
//            assertEquals(ITEMS_TO_APPEND, seq.getLength());
//            System.out.println("testAppend: PASS");
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail(e.getMessage());
//        } finally {
//            pool.release(broker);
//        }
//	}
    
//    public void testInsertBefore() {
//        DBBroker broker = null;
//        try {
//            System.out.println("testInsertBefore() ...\n");
//            broker = pool.get(SecurityManager.SYSTEM_USER);
//            
//            String query =
//                "   update insert\n" +
//                "       <product id='original'>\n" +
//                "           <description>Description</description>\n" +
//                "           <price>0</price>\n" +
//                "           <stock>10</stock>\n" +
//                "       </product>\n" +
//                "   into /products";
//            
//            XQuery xquery = broker.getXQueryService();
//            xquery.execute(query, null, AccessContext.TEST);
//            
//            query =
//                "   declare variable $i external;\n" +
//                "   update insert\n" +
//                "       <product id='id{$i}'>\n" +
//                "           <description>Description {$i}</description>\n" +
//                "           <price>{$i + 1.0}</price>\n" +
//                "           <stock>{$i * 10}</stock>\n" +
//                "       </product>\n" +
//                "   preceding /products/product[1]";
//            XQueryContext context = xquery.newContext(AccessContext.TEST);
//            CompiledXQuery compiled = xquery.compile(context, query);
//            for (int i = 0; i < ITEMS_TO_APPEND; i++) {
//                context.declareVariable("i", new Integer(i));
//                xquery.execute(compiled, null);
//                
//                Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
//                assertEquals(seq.getLength(), 1);
//                
//                Serializer serializer = broker.getSerializer();
//                System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
//            }
//            
//            Sequence seq = xquery.execute("//product", null, AccessContext.TEST);
//            assertEquals(ITEMS_TO_APPEND + 1, seq.getLength());
//
//            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
//            assertEquals(ITEMS_TO_APPEND, seq.getLength());
//            System.out.println("testInsertBefore: PASS");
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail(e.getMessage());
//        } finally {
//            pool.release(broker);
//        }
//    }
    
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
                
                Sequence seq = xquery.execute("/products", null, AccessContext.TEST);
                assertEquals(seq.getLength(), 1);
                
                Serializer serializer = broker.getSerializer();
                System.out.println(serializer.serialize((NodeValue) seq.itemAt(0)));
            }
            
            Sequence seq = xquery.execute("//product", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND + 1, seq.getLength());

            seq = xquery.execute("//product[price > 0.0]", null, AccessContext.TEST);
            assertEquals(ITEMS_TO_APPEND, seq.getLength());
            System.out.println("testInsertAfter: PASS");
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
            TransactionManager mgr = pool.getTransactionManager();
            broker = pool.get(SecurityManager.SYSTEM_USER);
            Txn transaction = mgr.beginTransaction();        
            System.out.println("Transaction started ...");
            
            Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.saveCollection(transaction, root);
            
            IndexInfo info = root.validateXMLResource(transaction, broker, "test.xml", TEST_XML);
            root.store(transaction, broker, info, TEST_XML, false);
    
            mgr.commit(transaction);    
            System.out.println("Transaction commited ...");
            
        } catch (Exception e) {
            fail(e.getMessage());
        }  finally {
            pool.release(broker);
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
         try {
             BrokerPool.stopAll(false);
         } catch (Exception e) {            
             fail(e.getMessage());
         }
     }
}
