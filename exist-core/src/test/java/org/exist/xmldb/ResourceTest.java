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

import java.io.*;
import java.util.List;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;

import org.custommonkey.xmlunit.exceptions.XpathException;
import org.exist.dom.QName;
import org.exist.security.Account;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.StringInputSource;
import org.exist.util.io.InputStreamUtil;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.SAXSerializer;
import org.junit.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.TestUtils.GUEST_DB_USER;
import static org.exist.xmldb.AbstractLocal.PROP_JOIN_TRANSACTION_IF_PRESENT;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.exist.samples.Samples.SAMPLES;
import static org.junit.Assert.*;

public class ResourceTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String TEST_COLLECTION = "testResource";

    @BeforeClass
    public static void prepareXmldbJoinTransactions() {
        System.setProperty(PROP_JOIN_TRANSACTION_IF_PRESENT, "true");
    }

    @AfterClass
    public static void releaseXmldbJoinTransactions() {
        System.clearProperty(PROP_JOIN_TRANSACTION_IF_PRESENT);
    }

    @Test
    public void readNonExistingResource() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);
        Resource nonExistent = testCollection.getResource("12345.xml");
        assertNull(nonExistent);
    }

    @Test
    public void readResource() throws XMLDBException, IOException {
        final Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);
        final List<String> resources = testCollection.listResources();
        assertEquals(resources.size(), testCollection.getResourceCount());

        final XMLResource doc = (XMLResource) testCollection.getResource(resources.getFirst());
        assertNotNull(doc);

        try(final StringWriter sout = new StringWriter()) {
            final Properties outputProperties = new Properties();
            outputProperties.put(OutputKeys.METHOD, "xml");
            outputProperties.put(OutputKeys.ENCODING, "ISO-8859-1");
            outputProperties.put(OutputKeys.INDENT, "yes");
            outputProperties.put(OutputKeys.OMIT_XML_DECLARATION, "no");

            final ContentHandler xmlout = new SAXSerializer(sout, outputProperties);
            doc.getContentAsSAX(xmlout);
        }
    }

    @Test
    public void testRecursiveSerailization() throws XMLDBException, IOException {
        final String xmlDoc1 = "<test><title>Title</title>"
                + "<import href=\"recurseSer2.xml\"></import>"
                + "<para>Paragraph2</para>"
                + "</test>";
        final String xmlDoc2 = "<test2><title>Title2</title></test2>";

        final String doc1Name = "recurseSer1.xml";
        final String doc2Name = "recurseSer2.xml";
        final XMLResource resource1 = addResource(doc1Name, xmlDoc1);
        final XMLResource resource2 = addResource(doc2Name, xmlDoc2);

        final Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);

        try(final StringWriter sout = new StringWriter()) {
            final Properties outputProperties = new Properties();
            outputProperties.put(OutputKeys.METHOD, "xml");
            outputProperties.put(OutputKeys.ENCODING, "UTF-8");
            outputProperties.put(OutputKeys.INDENT, "no");
            outputProperties.put(OutputKeys.OMIT_XML_DECLARATION, "yes");

            final ContentHandler importHandler = new ImportingContentHandler(sout, outputProperties);
            resource1.getContentAsSAX(importHandler);

            final String result = sout.getBuffer().toString();
            assertEquals(
                    "<test>" +
                    "<title>Title</title>" +
                    "<test2>" +
                    "<title>Title2</title>" +
                    "</test2>" +
                    "<para>Paragraph2</para>" +
                    "</test>"
            , result.trim());
        }
    }

    @Test
    public void readDOM() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);

        XMLResource doc = (XMLResource) testCollection.getResource("r_and_j.xml");
        assertNotNull(doc);
        Node n = doc.getContentAsDOM();
        Element elem=null;
        if (n instanceof Element) {
            elem = (Element)n;
        } else if (n instanceof Document) {
            elem = ((Document)n).getDocumentElement();
        }
        assertNotNull(elem);
        assertEquals(elem.getNodeName(), "PLAY");
        NodeList children = elem.getChildNodes();
        Node node;
        for(int i = 0; i < children.getLength(); i++) {
            node = children.item(i);
            assertNotNull(node);
            node = node.getFirstChild();
            while(node != null) {
                node = node.getNextSibling();
            }
        }
    }

    @Test
    public void setContentAsSAX() throws SAXException, ParserConfigurationException, XMLDBException, IOException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);

        XMLResource doc = testCollection.createResource("test.xml", XMLResource.class);
        String xml =
                "<test><title>Title</title>"
                        + "<para>Paragraph1</para>"
                        + "<para>Paragraph2</para>"
                        + "</test>";
        ContentHandler handler = doc.setContentAsSAX();
        SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
        saxFactory.setNamespaceAware(true);
        saxFactory.setValidating(false);
        SAXParser sax = saxFactory.newSAXParser();
        XMLReader reader = sax.getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new StringReader(xml)));
        testCollection.storeResource(doc);
    }

    @Test
    public void setContentAsDOM() throws XMLDBException, ParserConfigurationException, SAXException, IOException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);

        XMLResource doc = testCollection.createResource("dom.xml", XMLResource.class);
        String xml =
                "<test><title>Title</title>"
                        + "<para>Paragraph1</para>"
                        + "<para>Paragraph2</para>"
                        + "</test>";
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = docFactory.newDocumentBuilder();
        Document dom = builder.parse(new InputSource(new StringReader(xml)));
        doc.setContentAsDOM(dom.getDocumentElement());
        testCollection.storeResource(doc);
    }
    
    @Test
    public void setContentAsSourceXml() throws XMLDBException, SAXException, IOException, XpathException {
        final Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);

        final XMLResource doc = testCollection.createResource("source.xml", XMLResource.class);
        final String xml =
                "<test><title>Title1</title>"
                        + "<para>Paragraph3</para>"
                        + "<para>Paragraph4</para>"
                        + "</test>";
        

        doc.setContent(new StringInputSource(xml));
        testCollection.storeResource(doc);
        
        final XMLResource newDoc = (XMLResource) testCollection.getResource("source.xml");
        final String newDocXml = (String) newDoc.getContent();
        
        assertXpathEvaluatesTo("Title1", "/test/title/text()", newDocXml);
        assertXpathEvaluatesTo("2", "count(/test/para)", newDocXml);
        assertXpathEvaluatesTo("Paragraph3", "/test/para[1]/text()", newDocXml);
        assertXpathEvaluatesTo("Paragraph4", "/test/para[2]/text()", newDocXml);
    }

    @Test
    public void setContentAsSourceBinary() throws XMLDBException {
        final Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        assertNotNull(testCollection);

        final BinaryResource doc = testCollection.createResource("source.bin", BinaryResource.class);
        final byte[] bin = "Stuff And Things".getBytes(UTF_8);

        doc.setContent(new StringInputSource(bin));
        testCollection.storeResource(doc);

        final BinaryResource newDoc = (BinaryResource) testCollection.getResource("source.bin");
        final byte[] newDocBin = (byte[]) newDoc.getContent();

        assertArrayEquals(bin, newDocBin);
    }

    @Test
    public void queryRemoveResource() throws XMLDBException {
        Resource resource = null;
        
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
                    assertNotNull(testCollection);
        String resourceName = "QueryTestPerson.xml";
        String id = "test." + System.currentTimeMillis();
        String content = "<?xml version='1.0'?><person id=\"" + id + "\"><name>Jason</name></person>";
        resource = testCollection.createResource(resourceName, XMLResource.class);
        resource.setContent(content);
        testCollection.storeResource(resource);

        XPathQueryService service = testCollection.getService(XPathQueryService.class);
        ResourceSet rs = service.query("/person[@id='" + id + "']");

        for (ResourceIterator iterator = rs.getIterator(); iterator.hasMoreResources();) {
            Resource r = iterator.nextResource();
            testCollection.removeResource(r);
            resource = null;
        }
    }

    @Test
    public void addRemove() throws XMLDBException {

        final String resourceID = "addremove.xml";

        XMLResource created = addResource(resourceID, xmlForTest());
        assertNotNull(created);
        // need to test documents xml structure

        XMLResource located = resourceForId(resourceID);
        assertNotNull(located);
        //assertEquals((String) created.getContent(), (String) located.getContent());

        removeDocument(resourceID);
        XMLResource locatedAfterRemove = resourceForId(resourceID);
        assertNull(locatedAfterRemove);
    }

    @Test
    public void addRemoveAddWithIds() throws XMLDBException {

        final String resourceID = "removeWithIds;1.xml";

        addResource(resourceID, "<foo1 xml:id='f'/>");
        removeDocument(resourceID);
        addResource(resourceID, "<foo xml:id='f'/>");
    }

    private void removeDocument(String id) throws XMLDBException {

        XMLResource resource = resourceForId(id);

        if (null != resource) {
            Collection collection = null;

            try {
                collection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
                collection.removeResource(resource);
            } finally {
                closeCollection(collection);
            }
        }
    }

    private XMLResource addResource(String id, String content) throws XMLDBException {
        Collection collection = null;
        XMLResource result = null;

        try {
            collection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
            result = collection.createResource(id, XMLResource.class);
            result.setContent(content);
            collection.storeResource(result);
        } finally {
            closeCollection(collection);
        }
        return result;
    }

    private XMLResource resourceForId(String id) throws XMLDBException {
        Collection collection = null;
        XMLResource result = null;

        try {
            collection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
            result = (XMLResource) collection.getResource(id);
        } finally {
            closeCollection(collection);
        }

        return result;
    }

    private void closeCollection(Collection collection) throws XMLDBException {
        if(null != collection) {
            collection.close();
        }
    }

    private String xmlForTest() {
            return "<test><title>Title</title>"
                    + "<para>Paragraph1</para>"
                    + "<para>Paragraph2</para>"
                    + "</test>";
    }

    @Before
    public void setUp() throws XMLDBException, IOException {
        //create a test collection
        final CollectionManagementService cms = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        final Collection testCollection = cms.createCollection(TEST_COLLECTION);
        final UserManagementService ums = testCollection.getService(UserManagementService.class);
        // change ownership to guest
        final Account guest = ums.getAccount(GUEST_DB_USER);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod("rwxr-xr-x");

        //store sample files as guest
        final Collection testCollectionAsGuest = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        for (final String sampleName : SAMPLES.getShakespeareXmlSampleNames()) {
            final XMLResource res = testCollectionAsGuest.createResource(sampleName, XMLResource.class);
            try (final InputStream is = SAMPLES.getShakespeareSample(sampleName)) {
                res.setContent(InputStreamUtil.readString(is, UTF_8));
            }
            testCollectionAsGuest.storeResource(res);
        }
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        CollectionManagementService cms = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        cms.removeCollection(TEST_COLLECTION);
    }

    private class ImportingContentHandler extends SAXSerializer {
        private static final String IMPORT_ELEM_NAME = "import";
        private static final String HREF_ATTR_NAME = "href";
        private final Writer writer;
        private final Properties outputProperties;

        ImportingContentHandler(final Writer writer, final Properties outputProperties) {
            super(writer, outputProperties);
            this.writer = writer;
            this.outputProperties = outputProperties;
        }

        @Override
        public void startElement(final QName qname, final AttrList attribs) throws SAXException {
            if(qname.getLocalPart().equals(IMPORT_ELEM_NAME)) {
                importDoc(attribs.getValue(new QName(HREF_ATTR_NAME, XMLConstants.NULL_NS_URI)));
            } else {
                super.startElement(qname, attribs);
            }
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                final Attributes attributes) throws SAXException {
            if(localName.equals(IMPORT_ELEM_NAME)) {
                importDoc(attributes.getValue(HREF_ATTR_NAME));
            } else {
                super.startElement(uri, localName, qName, attributes);
            }
        }

        private void importDoc(final String href) throws SAXException {
            try {
                final XMLResource resource = resourceForId(href);
                resource.getContentAsSAX(new ImportingContentHandler(writer, outputProperties));
            } catch (final XMLDBException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endElement(final QName qname) throws SAXException {
            if(!qname.getLocalPart().equals(IMPORT_ELEM_NAME)) {
                super.endElement(qname);
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) throws SAXException {
            if(!localName.equals(IMPORT_ELEM_NAME)) {
                super.endElement(uri, localName, qName);
            }
        }
    }
}
