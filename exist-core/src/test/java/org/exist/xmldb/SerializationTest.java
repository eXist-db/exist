/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb;

import org.exist.Namespaces;
import org.exist.TestUtils;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.test.ExistWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
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

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class SerializationTest {

	@ClassRule
	public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
	private static final String PORT_PLACEHOLDER = "${PORT}";

	private static final String EOL = System.lineSeparator();

	private static final String TEST_COLLECTION_NAME = "xmlrpc-serialization-test";

	private static final String XML_DOC_NAME = "defaultns.xml";
	private static final String XML =
		"<root xmlns=\"http://foo.com\">" +
		"	<entry>1</entry>" +
		"	<entry>2</entry>" +
		"</root>";

	private static final String XML_EXPECTED1 =
		"<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" hitCount=\"2\">" + EOL +
		"    <entry xmlns=\"http://foo.com\">1</entry>" + EOL +
		"    <entry xmlns=\"http://foo.com\">2</entry>" + EOL +
		"</exist:result>";

	private static final String XML_EXPECTED2 =
		"<exist:result xmlns:exist=\"" + Namespaces.EXIST_NS + "\" hitCount=\"1\">" + EOL +
		"    <c:Site xmlns:c=\"urn:content\" xmlns=\"urn:content\">" + EOL +
        "        <config xmlns=\"urn:config\">123</config>" + EOL +
        "        <serverconfig xmlns=\"urn:config\">123</serverconfig>" + EOL +
		"    </c:Site>" + EOL +
		"</exist:result>";

	private static final String XML_UPDATED_EXPECTED =
		"<root xmlns=\"http://foo.com\">" + EOL +
		"	<entry>1</entry>" + EOL +
		"	<entry>2</entry>" + EOL +
		"	<entry xmlns=\"\" xml:id=\"aargh\"/>" + EOL +
		"</root>";

	private static final XmldbURI TEST_XML_DOC_WITH_DOCTYPE_URI = XmldbURI.create("test-with-doctype.xml");

	private static final String XML_WITH_DOCTYPE =
			"<!DOCTYPE bookmap PUBLIC \"-//OASIS//DTD DITA BookMap//EN\" \"bookmap.dtd\">\n" +
			"<bookmap id=\"bookmap-1\"/>";

	private static final XmldbURI TEST_XML_DOC_WITH_XMLDECL_URI = XmldbURI.create("test-with-xmldecl.xml");

	private static final String XML_WITH_XMLDECL =
			"<?xml version=\"1.1\" encoding=\"ISO-8859-1\" standalone=\"yes\"?>\n" +
			"<bookmap id=\"bookmap-2\"/>";

	@Parameterized.Parameters(name = "{0}")
	public static java.util.Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
				{ "local", "xmldb:exist://" },
				{ "remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc" }
		});
	}

	@Parameterized.Parameter
	public String apiName;

	@Parameterized.Parameter(value = 1)
	public String baseUri;

	private Collection testCollection;

	private final String getBaseUri() {
		return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
	}

	@Test
	public void wrappedNsTest1() throws XMLDBException {
		final XQueryService service = testCollection.getService(XQueryService.class);
		final ResourceSet result = service.query("declare namespace foo=\"http://foo.com\"; //foo:entry");
		assertEquals(2, result.getSize());

		final Resource resource = result.getMembersAsResource();
		assertXMLEquals(XML_EXPECTED1, resource);
	}

	@Test
	public void wrappedNsTest2() throws XMLDBException {
		final XQueryService service = testCollection.getService(XQueryService.class);
		final ResourceSet result = service.query("declare namespace config='urn:config'; " +
				"declare namespace c='urn:content'; "  +
				"declare variable $config := <config xmlns='urn:config'>123</config>; " +
				"declare variable $serverConfig := <serverconfig xmlns='urn:config'>123</serverconfig>; " +
				"<c:Site xmlns='urn:content' xmlns:c='urn:content'> " +
				"{($config,$serverConfig)} " +
				"</c:Site>");
		assertEquals(1, result.getSize());

		final Resource resource = result.getMembersAsResource();
		assertXMLEquals(XML_EXPECTED2, resource);
	}

	@Test
	public void xqueryUpdateNsTest() throws XMLDBException {
		final XQueryService service = testCollection.getService(XQueryService.class);
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

	@Test
	public void getDocTypeDefault() throws XMLDBException {
		final Resource res = testCollection.getResource(TEST_XML_DOC_WITH_DOCTYPE_URI.lastSegmentString());
		assertEquals(XML_WITH_DOCTYPE, res.getContent());
	}

	@Test
	public void getDocTypeNo() throws XMLDBException {
		final String prevOutputDocType = testCollection.getProperty(EXistOutputKeys.OUTPUT_DOCTYPE);
		try {
			final Resource res = testCollection.getResource(TEST_XML_DOC_WITH_DOCTYPE_URI.lastSegmentString());
			testCollection.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "no");
			assertEquals("<bookmap id=\"bookmap-1\"/>", res.getContent());
		} finally {
			if (prevOutputDocType != null) {
				testCollection.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, prevOutputDocType);
			}
		}
	}

	@Test
	public void getDocTypeYes() throws XMLDBException {
		final String prevOutputDocType = testCollection.getProperty(EXistOutputKeys.OUTPUT_DOCTYPE);
		try {
			final Resource res = testCollection.getResource(TEST_XML_DOC_WITH_DOCTYPE_URI.lastSegmentString());
			testCollection.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "yes");
			assertEquals(XML_WITH_DOCTYPE, res.getContent());
		} finally {
			if (prevOutputDocType != null) {
				testCollection.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, prevOutputDocType);
			}
		}
	}

	@Test
	public void getXmlDeclDefault() throws XMLDBException {
		final Resource res = testCollection.getResource(TEST_XML_DOC_WITH_XMLDECL_URI.lastSegmentString());
		assertEquals(XML_WITH_XMLDECL, res.getContent());
	}

	@Test
	public void getXmlDeclNo() throws XMLDBException {
		final String prevOmitOriginalXmlDecl = testCollection.getProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION);
		try {
			final Resource res = testCollection.getResource(TEST_XML_DOC_WITH_XMLDECL_URI.lastSegmentString());
			testCollection.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "no");
			assertEquals(XML_WITH_XMLDECL, res.getContent());
		} finally {
			if (prevOmitOriginalXmlDecl != null) {
				testCollection.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, prevOmitOriginalXmlDecl);
			}
		}
	}

	@Test
	public void getXmlDeclYes() throws XMLDBException {
		final String prevOmitOriginalXmlDecl = testCollection.getProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION);
		try {
			final Resource res = testCollection.getResource(TEST_XML_DOC_WITH_XMLDECL_URI.lastSegmentString());
			testCollection.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "yes");
			assertEquals("<bookmap id=\"bookmap-2\"/>", res.getContent());
		} finally {
			if (prevOmitOriginalXmlDecl != null) {
				testCollection.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, prevOmitOriginalXmlDecl);
			}
		}
	}

	private static void assertXMLEquals(final String expected, final Resource actual) throws XMLDBException {
		final Source srcExpected = Input.fromString(expected).build();
		final Source srcActual = Input.fromString(actual.getContent().toString()).build();
		final Diff diff = DiffBuilder.compare(srcExpected)
				.withTest(srcActual)
				.checkForIdentical()
				.ignoreWhitespace()
				.build();
		assertFalse(diff.toString(), diff.hasDifferences());
	}

    @Before
	public void setUp() throws XMLDBException {
		final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);

        final XMLResource res = testCollection.createResource(XML_DOC_NAME, XMLResource.class);
        res.setContent(XML);
        testCollection.storeResource(res);

		final XMLResource res1 = testCollection.createResource(TEST_XML_DOC_WITH_DOCTYPE_URI.lastSegmentString(), XMLResource.class);
		res1.setContent(XML_WITH_DOCTYPE);
		testCollection.storeResource(res1);

		final XMLResource res2 = testCollection.createResource(TEST_XML_DOC_WITH_XMLDECL_URI.lastSegmentString(), XMLResource.class);
		res2.setContent(XML_WITH_XMLDECL);
		testCollection.storeResource(res2);
    }

    @After
    public void tearDown() throws XMLDBException {
		final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
		final CollectionManagementService service = root.getService(CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }
}
