package org.exist.xquery;

import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import junit.framework.TestCase;

public class NamespaceUpdateTest extends TestCase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	private final static String namespaces =
		"<test xmlns='http://www.foo.com'>"
			+ "<section>"
			+ "<title>Test Document</title>"
			+ "<c:comment xmlns:c='http://www.other.com'>This is my comment</c:comment>"
			+ "</section>"
			+ "</test>";

	private Collection testCollection;
	
	public void testUpdateAttribute() {
		try {
			XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
			String query =
				"declare namespace t='http://www.foo.com';\n" +
				"<test xmlns='http://www.foo.com'>\n" +
				"{\n" +
				"	update insert attribute { 'ID' } { 'myid' } into /t:test\n" +
				"}\n" +
				"</test>";
			service.query(query);
			
			query =
				"declare namespace t='http://www.foo.com';\n" +
				"/t:test/@ID";
			ResourceSet result = service.query(query);
			assertEquals(1, result.getSize());
			assertEquals("myid", result.getResource(0).getContent().toString());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	protected void setUp() {
		try {
			// initialize driver
			Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			Collection root =
				DatabaseManager.getCollection(
					URI,
					"admin",
					null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			testCollection = service.createCollection("test");
			assertNotNull(testCollection);
			
			XMLResource doc =
				(XMLResource) root.createResource("namespace-updates.xml", "XMLResource");
			doc.setContent(namespaces);
			testCollection.storeResource(doc);
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(NamespaceUpdateTest.class);
	}
}
