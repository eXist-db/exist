/*
 * Created on Sep 13, 2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.collections.test;

import java.io.File;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import junit.framework.TestCase;

/**
 * @author wolf
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class CollectionTest extends TestCase {

	public static void main(String[] args) {
		junit.textui.TestRunner.run(CollectionTest.class);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Class to test for void addCollection(Collection)
	 */
	public void testAddCollection() throws Exception {
		Worker1 w1 = new Worker1();
		w1.start();
		/*Collection root =
			DatabaseManager.getCollection("xmldb:exist:///db", "admin", "");
		DatabaseInstanceManager mgr =
			(DatabaseInstanceManager) root.getService(
				"DatabaseInstanceManager",
				"1.0");
		mgr.shutdown();*/
	}

	public class Worker1 extends Thread {

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			try {
				Collection root =
					DatabaseManager.getCollection(
						"xmldb:exist:///db",
						"admin",
						"");
				CollectionManagementService service =
					(CollectionManagementService) root.getService(
						"CollectionManagementService",
						"1.0");
				Collection test = service.createCollection("test");
				assertNotNull("created collection test != null", test);
				XMLResource res =
					(XMLResource) test.createResource(
						"hamlet.xml",
						"XMLResource");
				assertNotNull("created resource != null", res);
				res.setContent(new File("samples/shakespeare/hamlet.xml"));
				test.storeResource(res);

				res = (XMLResource) test.getResource("hamlet.xml");
				assertNotNull("stored resource != null", res);
				System.out.println((String) res.getContent());

				test.removeResource(res);
				test.close();
			} catch (XMLDBException e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}
}
