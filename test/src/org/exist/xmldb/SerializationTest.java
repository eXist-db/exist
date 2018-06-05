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

	private static final String EOL = System.getProperty("line.separator");

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	private static final String TEST_COLLECTION_NAME = "test";

	private static final String XML_DOC_NAME = "defaultns.xml";
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

	private static final String XML_UPDATED_EXPECTED =
		"<root xmlns=\"http://foo.com\">" +
		"	<entry>1</entry>" +
		"	<entry>2</entry>" +
		"	<entry xmlns=\"\" xml:id=\"aargh\"/>" +
		"</root>";

	private Collection testCollection;

	@Test
	public void wrappedNsTest1() throws XMLDBException {
		final XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
		final ResourceSet result = service.query("declare namespace foo=\"http://foo.com\"; //foo:entry");
		assertEquals(2, result.getSize());

		final Resource resource = result.getMembersAsResource();
		assertXMLEquals(XML_EXPECTED1, resource);
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
		assertXMLEquals(XML_EXPECTED2, resource);
	}

	@Test
	public void xqueryUpdateNsTest() throws XMLDBException {
		final XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
		final ResourceSet result = service.query(
				"xquery version \"1.0\";" + EOL +
				"declare namespace foo=\"http://foo.com\";" + EOL +
				"let $in-memory :=" + EOL + XML + EOL +
				"let $on-disk := doc('/db/" + TEST_COLLECTION_NAME + '/' + XML_DOC_NAME + "')" + EOL +
				"let $new-node := <entry xml:id='aargh'/>" + EOL +
				"let $update := update insert $new-node into $on-disk/foo:root" + EOL +
				"return" + EOL +
				"    (" + EOL +
				"        $in-memory," + EOL +
				"        $on-disk" + EOL +
				"    )" + EOL
		);

		assertEquals(2, result.getSize());

		final Resource inMemoryResource = result.getResource(0);
		assertXMLEquals(XML, inMemoryResource);

		final Resource onDiskResource = result.getResource(1);
		assertXMLEquals(XML_UPDATED_EXPECTED, onDiskResource);
	}

	private static void assertXMLEquals(final String expected, final Resource actual) throws XMLDBException {
		final Source srcExpected = Input.fromString(expected).build();
		final Source srcActual = Input.fromString(actual.getContent().toString()).build();
		final Diff diff = DiffBuilder.compare(srcExpected).withTest(srcActual)
				.checkForIdentical()
				.ignoreWhitespace()
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

        final XMLResource res = (XMLResource) testCollection.createResource(XML_DOC_NAME, "XMLResource");
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
