package org.exist.xmlrpc;

import org.exist.StandaloneServer;
import org.exist.TestDataGenerator;
import org.exist.xmldb.IndexQueryService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mortbay.util.MultiException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import java.io.File;
import java.net.BindException;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 */
public class QuerySessionTest {

    private final static String generateXQ = "<book id=\"{$filename}\" n=\"{$count}\">"
			+ "   <chapter xml:id=\"chapter{$count}\">"
			+ "       <title>{pt:random-text(7)}</title>"
			+ "       {"
			+ "           for $section in 1 to 8 return"
			+ "               <section id=\"sect{$section}\">"
			+ "                   <title>{pt:random-text(7)}</title>"
			+ "                   {"
			+ "                       for $para in 1 to 10 return"
			+ "                           <para>{pt:random-text(40)}</para>"
			+ "                   }"
			+ "               </section>"
			+ "       }"
			+ "   </chapter>" + "</book>";

    private final static String COLLECTION_CONFIG =
		"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		"	<index>" +
		"		<fulltext default=\"all\" attributes=\"false\"/>" +
		"		<create qname=\"@xml:id\" type=\"xs:string\"/>" +
		"	</index>" +
		"</collection>";

    private final static String QUERY =
            "declare variable $n external;" +
            "//chapter[@xml:id = $n]";

    private final static String baseURI = "xmldb:exist://localhost:8088/xmlrpc";

    private final static int N_THREADS = 10;

    private final static int DOC_COUNT = 100;
    
    private static StandaloneServer server;

    private Random random = new Random();

    @Test (expected=XMLDBException.class)
    public void manualRelease() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(baseURI + "/db/rpctest", "admin", "");
        XQueryService service = (XQueryService) test.getService("XQueryService", "1.0");
        ResourceSet result = service.query("//chapter[@xml:id = 'chapter1']");
        Assert.assertEquals(1, result.getSize());

        // clear should release the query result on the server
        result.clear();

        // the result has been cleared already. we should get an exception here
        Resource members = result.getMembersAsResource();
        System.out.println("members: " + members.getContent().toString());
    }

    @Test
    public void runTasks() {
        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        for (int i = 0; i < 1000; i++) {
            executor.submit(new QueryTask(QUERY));
        }

        executor.shutdown();
		boolean terminated = false;
		try {
			terminated = executor.awaitTermination(60 * 60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		}
		Assert.assertTrue(terminated);
    }

    private class QueryTask implements Runnable {

        private String query;

        private QueryTask(String query) {
            this.query = query;
        }

        public void run() {
            try {
                Collection test = DatabaseManager.getCollection(baseURI + "/db/rpctest", "admin", "");
                XQueryService service = (XQueryService) test.getService("XQueryService", "1.0");
                int n = random.nextInt(DOC_COUNT) + 1;
                service.declareVariable("n", "chapter" + n);
                ResourceSet result = service.query(query);
                Assert.assertEquals(1, result.getSize());
            } catch (XMLDBException e) {
                e.printStackTrace();
                Assert.fail(e.getMessage());
            }
        }
    }

    @BeforeClass
    public static void startServer() throws Exception {
        try {
            server = new StandaloneServer();
            System.out.println("Starting standalone server...");
            String[] args = {};
            System.out.println("Waiting for server to start...");
            server.run(args);
            while (!server.isStarted()) {
                synchronized (server) {
                    server.wait(10000);
                }
            }
        } catch (MultiException e) {
            boolean rethrow = true;
            Iterator i = e.getExceptions().iterator();
            while (i.hasNext()) {
                Exception e0 = (Exception)i.next();
                if (e0 instanceof BindException) {
                    System.out.println("A server is running already !");
                    rethrow = false;
                    break;
                }
            }
            if (rethrow) throw e;
        }

        // initialize XML:DB driver
        Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(baseURI + "/db", "admin", "");
        
        CollectionManagementService mgmt =
                (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        Collection test = mgmt.createCollection("rpctest");
        IndexQueryService idxs = (IndexQueryService) test.getService("IndexQueryService", "1.0");
        idxs.configureCollection(COLLECTION_CONFIG);

        Resource resource = test.createResource("strings.xml", "XMLResource");
        resource.setContent(new File("samples/shakespeare/macbeth.xml"));
        test.storeResource(resource);
        
        TestDataGenerator generator = new TestDataGenerator("xdb", DOC_COUNT);
        File[] files = generator.generate(test, generateXQ);
        for (int i = 0; i < files.length; i++) {
            resource = test.createResource(files[i].getName(), "XMLResource");
            resource.setContent(files[i]);
            test.storeResource(resource);
        }
        generator.releaseAll();
    }

    @AfterClass
    public static void stopServer() {
        try {
            Collection root = DatabaseManager.getCollection(baseURI + "/db", "admin", "");
            CollectionManagementService mgmt =
                (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            mgmt.removeCollection("rpctest");

            Collection config = DatabaseManager.getCollection(baseURI + "/db/system/config/db", "admin", "");
            mgmt =
                (CollectionManagementService) config.getService("CollectionManagementService", "1.0");
            mgmt.removeCollection("rpctest");
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
        server.shutdown();
        server = null;
    }
}
