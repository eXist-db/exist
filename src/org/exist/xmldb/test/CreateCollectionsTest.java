package org.exist.xmldb.test;

import java.io.File;
import java.io.IOException;

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
			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			assertNotNull(service);
			Collection testCollection = service.createCollection("test");
			assertNotNull(testCollection);
			File f = new File("samples/shakespeare");
			File files[] = f.listFiles(new XMLFilenameFilter());
			XMLResource res;
			String xml;
			for (int i = 0; i < files.length; i++) {
				res =
					(XMLResource) testCollection.createResource(
						files[i].getName(),
						"XMLResource");
				assertNotNull(res);
				xml = XMLUtil.readFile(files[i], "UTF-8");
				res.setContent(xml);
				testCollection.storeResource(res);
			}
		} catch (XMLDBException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}
}
