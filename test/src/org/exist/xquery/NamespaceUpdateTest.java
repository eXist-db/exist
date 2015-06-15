package org.exist.xquery;

import org.exist.xmldb.XmldbURI;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NamespaceUpdateTest {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	private final static String namespaces =
		"<test xmlns='http://www.foo.com'>"
			+ "<section>"
			+ "<title>Test Document</title>"
			+ "<c:comment xmlns:c='http://www.other.com'>This is my comment</c:comment>"
			+ "</section>"
			+ "</test>";

	private Collection testCollection;

	@Test
	public void updateAttribute() throws XMLDBException {
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
	}

    @Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
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
	}
}
