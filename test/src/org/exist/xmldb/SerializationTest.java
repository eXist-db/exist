package org.exist.xmldb;

import org.exist.Namespaces;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.io.IOException;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertNotNull;

public class SerializationTest {

	private static final String TEST_COLLECTION_NAME = "test";

	private static final String XML =
		"<root xmlns=\"http://foo.com\">" +
		"	<entry>1</entry>" +
		"	<entry>2</entry>" +
		"</root>";
	
	private static final String XML_EXPECTED1 =
		"<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" hitCount=\"2\">\n" + 
		"    <entry xmlns=\"http://foo.com\">1</entry>\n" + 
		"    <entry xmlns=\"http://foo.com\">2</entry>\n" + 
		"</exist:result>";
	
	private static final String XML_EXPECTED2 =
		"<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" hitCount=\"1\">\n" +
		"    <c:Site xmlns:c=\"urn:content\" xmlns=\"urn:content\">\n"+
		//BUG : we should have
		//<config xmlns="urn:config">123</config>
        "        <config>123</config>\n" +
        //BUG : we should have 
        //<serverconfig xmlns="urn:config">123</serverconfig>
        "        <serverconfig>123</serverconfig>\n" +
		"    </c:Site>\n" +
		"</exist:result>";

	private Database database;
	private Collection testCollection;

	@Test
	public void queryResults() throws XMLDBException, IOException, SAXException {
		XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
		ResourceSet result = service.query("declare namespace foo=\"http://foo.com\"; //foo:entry");
		Resource resource = result.getMembersAsResource();
		String str = resource.getContent().toString();
		assertXMLEqual(XML_EXPECTED1, str);

		//TODO : THIS IS BUGGY !
		result = service.query("declare namespace config='urn:config'; " +
				"declare namespace c='urn:content'; "  +
				"declare variable $config {<config xmlns='urn:config'>123</config>}; " +
				"declare variable $serverConfig {<serverconfig xmlns='urn:config'>123</serverconfig>}; " +
				"<c:Site xmlns='urn:content' xmlns:c='urn:content'> " +
				"{($config,$serverConfig)} " +
				"</c:Site>");
		resource = result.getMembersAsResource();
		str = resource.getContent().toString();
		assertXMLEqual(XML_EXPECTED2, str);
	}

    @Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root =
            DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        CollectionManagementService service =
            (CollectionManagementService) root.getService(
                "CollectionManagementService",
                "1.0");
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);

        XMLResource res = (XMLResource)
            testCollection.createResource("defaultns.xml", "XMLResource");
        res.setContent(XML);
        testCollection.storeResource(res);
    }

    @After
    public void tearDown() throws XMLDBException {
        Collection root =
            DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        CollectionManagementService service =
            (CollectionManagementService) root.getService(
                "CollectionManagementService",
                "1.0");
        service.removeCollection(TEST_COLLECTION_NAME);

        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
            (DatabaseInstanceManager) testCollection.getService(
                "DatabaseInstanceManager", "1.0");
        dim.shutdown();
        database = null;
        testCollection = null;
    }
}