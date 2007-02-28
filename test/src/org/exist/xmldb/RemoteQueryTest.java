package org.exist.xmldb;

import java.io.File;
import java.net.BindException;
import java.util.Iterator;

import org.exist.StandaloneServer;
import org.exist.storage.DBBroker;
import org.mortbay.util.MultiException;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

import junit.framework.TestCase;
import junit.textui.TestRunner;

public class RemoteQueryTest extends TestCase {

	private static String uri = "xmldb:exist://localhost:8088/xmlrpc" + DBBroker.ROOT_COLLECTION;
	
	private static StandaloneServer server = null;
	
	private Collection testCollection;
	
	public void testResourceSet() {
		try {
			String query = "//SPEECH[SPEAKER = 'HAMLET']";
			XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
			service.setProperty("highlight-matches", "none");
			CompiledExpression compiled = service.compile(query);
			ResourceSet result = service.execute(compiled);
			
			assertEquals(result.getSize(), 359);
			
			for (int i = 0; i < result.getSize(); i++) {
				XMLResource r = (XMLResource) result.getResource(i);
				Node node = r.getContentAsDOM().getFirstChild();
				System.out.println(node.getNodeName());
				System.out.println(r.getContent());
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	protected void setUp() {
		if (uri.startsWith("xmldb:exist://localhost"))
			initServer();
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			Collection root =
				DatabaseManager.getCollection(
						uri,
						"admin",
						null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService(
						"CollectionManagementService",
				"1.0");
			testCollection = service.createCollection("test");
			assertNotNull(testCollection);
			
			Resource r = testCollection.createResource("hamlet.xml", "XMLResource");
                        String existHome = System.getProperty("exist.home");
                        File existDir = existHome==null ? new File(".") : new File(existHome);
			File f = new File(existDir,"samples/shakespeare/hamlet.xml");
			r.setContent(f);
			testCollection.storeResource(r);
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}
	
	private void initServer() {
		try {
			if (server == null) {
				server = new StandaloneServer();
				if (!server.isStarted()) {          
					try {               
						System.out.println("Starting standalone server...");
						String[] args = {};
						server.run(args);
						while (!server.isStarted()) {
							Thread.sleep(1000);
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
				}               
			}
		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}           
	}   
	
	protected void tearDown() throws Exception {
		try {
			if (!((CollectionImpl) testCollection).isRemoteCollection()) {
				DatabaseInstanceManager dim =
					(DatabaseInstanceManager) testCollection.getService(
							"DatabaseInstanceManager", "1.0");
				dim.shutdown();
			}
			testCollection = null;
			
			System.out.println("tearDown PASSED");
			
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public static void main(String[] args) {
		TestRunner.run(RemoteQueryTest.class);
	}
}
