package org.exist.xmldb.test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import junit.framework.TestCase;
import org.exist.util.XMLFilenameFilter;
import org.exist.util.XMLUtil;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

public class CreateCollectionsTest extends TestCase {

	private final static String URI = "xmldb:exist:///db";
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	public Collection root = null;

	public CreateCollectionsTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName(DRIVER);
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

			// try to get collection
			root = DatabaseManager.getCollection(URI);
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}

	public void testCreateCollection() {
		assertNotNull(root);
		try {
			System.out.println(
				"Created Collection: "
					+ root.getName()
					+ "( "
					+ root.getClass()
					+ " )");

			Service[] services = root.getServices();
			System.out.println("services array: " + services);
			assertTrue(
				"Collection must provide at least one Service",
				services != null && services.length > 0);
			System.out.println("  number of services: " + services.length);
			for (int i = 0; i < services.length; i++) {
				System.out.println(
					"  Service: "
						+ services[i].getName()
						+ "( "
						+ services[i].getClass()
						+ " )");
			}

			Collection parentCollection = root.getParentCollection();
			System.out.println("root parentCollection: " + parentCollection);
			assertNull("root collection has no parent", parentCollection);

			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			assertNotNull(service);
			Collection testCollection = service.createCollection("test");

			assertNotNull(testCollection);
			int ccc = testCollection.getChildCollectionCount();
			assertTrue(
				"Collection just created: ChildCollectionCount==0",
				ccc == 0);
			assertTrue(
				"Collection state should be Open after creation",
				testCollection.isOpen());

			System.out.println("---------------------------------------");
			System.out.println("storing files ...");
			System.out.println("---------------------------------------");
			File f = new File("samples/shakespeare");
			File files[] = f.listFiles(new XMLFilenameFilter());

			for (int i = 0; i < files.length; i++) {
				// XMLResource storeResourceFromFile(File f, Collection col)
				storeResourceFromFile(files[i], testCollection);
			}

			HashSet fileNames = new HashSet();
			for (int i = 0; i < files.length; i++) {
				String file = files[i].toString();
				int lastSeparator = file.lastIndexOf(File.separatorChar);
				fileNames.add(file.substring(lastSeparator + 1));
			}
			System.out.println("fileNames: " + fileNames.toString());

			String[] resourcesNames = testCollection.listResources();
			int resourceCount = testCollection.getResourceCount();
			System.out.println(
				"testCollection.getResourceCount()=" + resourceCount);
			for (int i = 0; i < resourceCount; i++) {
				System.out.println("resourcesNames[i]=" + resourcesNames[i]);
				assertTrue(
					"resourcesNames must contain fileNames just stored",
					fileNames.contains(resourcesNames[i]));
			}

			String fileToRemove = "macbeth.xml";
			Resource resMacbeth = testCollection.getResource(fileToRemove);
			assertNotNull("getResource(" + fileToRemove + "\")", resMacbeth);
			testCollection.removeResource(resMacbeth);
			assertTrue(
				"After removal resource count must decrease",
				testCollection.getResourceCount() == resourceCount - 1);
			// restore the resource just removed :
			storeResourceFromFile(
				new File(
					"samples/shakespeare" + File.separatorChar + fileToRemove),
				testCollection);

		} catch (XMLDBException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}

	private XMLResource storeResourceFromFile(
		File file,
		Collection testCollection)
		throws XMLDBException, IOException {
		XMLResource res;
		String xml;
		res =
			(XMLResource) testCollection.createResource(
				file.getName(),
				"XMLResource");
		assertNotNull("storeResourceFromFile", res);
		xml = XMLUtil.readFile(file, "UTF-8");
		res.setContent(xml);
		testCollection.storeResource(res);
		return res;
	}
}
