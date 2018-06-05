package org.exist.xmldb;

import org.exist.Namespaces;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class SerializationTest {

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

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

	private Collection testCollection;

	@Test
	public void wrappedNsTest1() throws XMLDBException {
		final XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
		final ResourceSet result = service.query("declare namespace foo=\"http://foo.com\"; //foo:entry");
		assertEquals(2, result.getSize());

		final Resource resource = result.getMembersAsResource();

		final Source expected = Input.fromString(XML_EXPECTED1).build();
		final Source actual = Input.fromString(resource.getContent().toString()).build();
		final Diff diff = DiffBuilder.compare(expected).withTest(actual)
				.checkForIdentical()
				.build();
		assertFalse(diff.toString(), diff.hasDifferences());
	}

	//TODO : THIS IS BUGGY !
	@Test
	public void wrappedNsTest2() throws XMLDBException {
		final XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
		final ResourceSet result = service.query("declare namespace config='urn:config'; " +
				"declare namespace c='urn:content'; "  +
				"declare variable $config {<config xmlns='urn:config'>123</config>}; " +
				"declare variable $serverConfig {<serverconfig xmlns='urn:config'>123</serverconfig>}; " +
				"<c:Site xmlns='urn:content' xmlns:c='urn:content'> " +
				"{($config,$serverConfig)} " +
				"</c:Site>");
		assertEquals(1, result.getSize());

		final Resource resource = result.getMembersAsResource();

		final Source expected = Input.fromString(XML_EXPECTED2).build();
		final Source actual = Input.fromString(resource.getContent().toString()).build();
		final Diff diff = DiffBuilder.compare(expected).withTest(actual)
				.checkForIdentical()
				.build();
		assertFalse(diff.toString(), diff.hasDifferences());
	}

    @Before
	public void setUp() throws XMLDBException {
        final CollectionManagementService service =
            (CollectionManagementService) existEmbeddedServer.getRoot().getService(
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
        final CollectionManagementService service =
            (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                "CollectionManagementService",
                "1.0");
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }
}
