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

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.xquery.util.URIUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.xmldb.api.base.ResourceType.BINARY_RESOURCE;
import static org.xmldb.api.base.ResourceType.XML_RESOURCE;

/** A test case for accessing collections remotely
 * @author <a href="mailto:pierrick.brihaye@free.fr">jmv
 * @author Pierrick Brihaye</a>
 */
public class RemoteCollectionTest extends RemoteDBTest {

    private final static String XML_CONTENT = "<xml/>";
    private final static String BINARY_CONTENT = "TEXT";

    @Before
    public void setUp() throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        setUpRemoteDatabase();
    }

    @After
    public void tearDown() {
        removeCollection();
    }

    @Test
    public void getServices() throws XMLDBException {
        final List<Class<? extends Service>> expectedServiceTypes = Arrays.asList(CollectionManagementService.class,
                DatabaseInstanceManager.class, EXistCollectionManagementService.class, EXistRestoreService.class,
                EXistUserManagementService.class, IndexQueryService.class, UserManagementService.class,
                XPathQueryService.class, XQueryService.class, XUpdateQueryService.class,
                RemoteXPathQueryService.class, RemoteCollectionManagementService.class, RemoteUserManagementService.class,
                RemoteDatabaseInstanceManager.class, RemoteIndexQueryService.class, RemoteXUpdateQueryService.class);
        RemoteCollection colTest = getCollection();
        for (Class<? extends Service> expectedServiceType : expectedServiceTypes) {
            assertTrue(colTest.hasService(expectedServiceType));
            assertNotNull(colTest.getService(expectedServiceType));
        }
    }

    @Test
    public void isRemoteCollection() throws XMLDBException {
        assertTrue(getCollection().isRemoteCollection());
    }

    @Test
    public void getPath() throws XMLDBException {
        assertEquals(XmldbURI.ROOT_COLLECTION + "/" + getTestCollectionName(), URIUtils.urlDecodeUtf8(getCollection().getPath()));
    }

    @Test
    public void createXmlResourceFromString() throws XMLDBException {
        final Collection collection = getCollection();
        final String resourceName = "testresource.xml";
        final Resource resource = collection.createResource(resourceName, XMLResource.class);
        assertNotNull(resource);
        assertEquals(collection, resource.getParentCollection());

        final String xml = "<?xml version='1.0'?><xml>" + System.currentTimeMillis() + "</xml>";
        resource.setContent(xml);
        collection.storeResource(resource);

        final Resource retrievedResource = collection.getResource(resourceName);
        assertNotNull(retrievedResource);
        assertEquals(XML_RESOURCE, retrievedResource.getResourceType());
        assertTrue(retrievedResource instanceof XMLResource);
        final String result = (String) retrievedResource.getContent();
        assertNotNull(result);

        final Source expected = Input.fromString(xml).build();
        final Source actual = Input.fromString(result).build();

        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void createBinaryResourceFromString() throws XMLDBException {
        final Collection collection = getCollection();
        final String resourceName = "testresource.bin";
        final Resource resource = collection.createResource(resourceName, BinaryResource.class);
        assertNotNull(resource);
        assertEquals(collection, resource.getParentCollection());

        final String bin = "binary data: " + System.currentTimeMillis();
        resource.setContent(bin);
        collection.storeResource(resource);

        final Resource retrievedResource = collection.getResource(resourceName);
        assertNotNull(retrievedResource);
        assertEquals(BINARY_RESOURCE, retrievedResource.getResourceType());
        assertTrue(retrievedResource instanceof BinaryResource);
        final byte[] result = (byte[]) retrievedResource.getContent();
        assertNotNull(result);
        assertEquals(bin, new String(result, UTF_8));
	}

	@Test
    public void createEmptyBinaryResource() throws XMLDBException, IOException {
        final Collection collection = getCollection();
        final String resourceName = "empty.dtd";
        final Resource resource = collection.createResource(resourceName, BinaryResource.class);
        ((EXistResource) resource).setMimeType("application/xml-dtd");

        final byte[] bin = new byte[0];
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(bin)) {
            final InputSource inputSource = new InputSource();
            inputSource.setByteStream(is);
            inputSource.setSystemId("empty.dtd");

            resource.setContent(inputSource);
            collection.storeResource(resource);
        }

        final Resource retrievedResource = collection.getResource(resourceName);
        assertNotNull(retrievedResource);
        assertEquals(BINARY_RESOURCE, retrievedResource.getResourceType());
        assertTrue(retrievedResource instanceof BinaryResource);
        final byte[] result = (byte[]) retrievedResource.getContent();
        assertNotNull(result);
        assertArrayEquals(bin, result);
    }


    @Test /* issue 1874 */
    public void createXMLFileResource() throws XMLDBException, IOException {
        Collection collection = getCollection();
        final Resource resource = collection.createResource("testresource", XMLResource.class);
        assertNotNull(resource);
        assertEquals(collection, resource.getParentCollection());

        final String sometxt = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        final Path path = Files.createTempFile("test-createXMLFileResource", ".xml");
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0'?><xml>");
        for (int i = 0; i < 5000; i++) {
            sb.append("<element>").append(sometxt).append("</element>");
        }
        sb.append("</xml>");
        Files.copy(new UnsynchronizedByteArrayInputStream(sb.toString().getBytes()), path, StandardCopyOption.REPLACE_EXISTING);
        resource.setContent(path);
        collection.storeResource(resource);
    }

    @Test
	public void getNonExistentResource() throws XMLDBException {
        Collection collection = getCollection();
        Resource resource = collection.getResource("unknown.xml");
        assertNull(resource);
	}

    @Test
	public void listResources() throws XMLDBException {
        ArrayList<String> xmlNames = new ArrayList<>();
        xmlNames.add("xml1");
        xmlNames.add("xml2");
        xmlNames.add("xml3");
        createResources(xmlNames, XMLResource.class);

        ArrayList<String> binaryNames = new ArrayList<>();
        binaryNames.add("b1");
        binaryNames.add("b2");
        createResources(binaryNames, BinaryResource.class);

        for (String resource : getCollection().listResources()) {
            xmlNames.remove(resource);
            binaryNames.remove(resource);
        }
        assertEquals(0, xmlNames.size());
        assertEquals(0, binaryNames.size());
	}

	/**
	 * Trying to access a collection where the parent collection does
	 * not exist caused NullPointerException on DatabaseManager.getCollection() method.
	 */
    @Test
	public void parent() throws XMLDBException {
        Collection c = DatabaseManager.getCollection(getUri() + XmldbURI.ROOT_COLLECTION, "admin", "");
        assertNull(c.getChildCollection("b"));

        String parentName = c.getName() + "/" + System.currentTimeMillis();
        String colName = parentName + "/a";
        c = DatabaseManager.getCollection(getUri() + parentName, "admin", "");
        assertNull(c);

        // following fails for XmlDb 20051203
        c = DatabaseManager.getCollection(getUri() + colName, "admin", "");
        assertNull(c);
	}

	@Test  /* issue 2743 */
    public void getLoadRemoteResourceContentBiggerThan16MB() throws XMLDBException, SAXException {
        Collection collection = getCollection();
        final RemoteXMLResource resource = (RemoteXMLResource)collection.createResource("testresource", XMLResource.class);
        prepareContent(resource);
        collection.storeResource(resource);
        // load stored content
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resource.getContentIntoAStream(outputStream);
        // compare size
        assertEquals(16777229, outputStream.size());
    }

    @Test
    public void isOpen() throws XMLDBException {
        Collection collection = getCollection();
        assertTrue(collection.isOpen());
        collection.close();
        assertFalse(collection.isOpen());
    }

    @Test
    public void getChildCollectionCount() throws XMLDBException {
        assertEquals(0, getCollection().getChildCollectionCount());
    }

    @Test
    public void getPropertyWithDefault() throws XMLDBException {
        assertEquals("theDefault", getCollection().getProperty("myProperty", "theDefault"));
    }

    @Test
    public void hasService(){
        assertTrue(getCollection().hasService(XPathQueryService.class));
    }

    @Test
    public void findService(){
        assertNotNull(getCollection().findService(XPathQueryService.class).get());
    }

    @Test
    public void getService() throws XMLDBException {
        assertNotNull(getCollection().getService(XPathQueryService.class));
    }

    @Test
    public void registerProvders() {
        RemoteCollection remoteCollection = (RemoteCollection)getCollection();
        ServiceProviderCache.ProviderRegistry registry = createMock(ServiceProviderCache.ProviderRegistry.class);

        registry.add(eq(XPathQueryService.class), notNull());
        registry.add(eq(XQueryService.class), notNull());
        registry.add(eq(CollectionManagementService.class), notNull());
        registry.add(eq(EXistCollectionManagementService.class), notNull());
        registry.add(eq(UserManagementService.class), notNull());
        registry.add(eq(EXistUserManagementService.class), notNull());
        registry.add(eq(DatabaseInstanceManager.class), notNull());
        registry.add(eq(XUpdateQueryService.class), notNull());
        registry.add(eq(IndexQueryService.class), notNull());
        registry.add(eq(EXistRestoreService.class), notNull());

        replay(registry);
        remoteCollection.registerProvders(registry);
        verify(registry);
    }

    @Test
    public void listChildCollections() throws XMLDBException {
        assertTrue(getCollection().listChildCollections().isEmpty());
    }

    @Test
    public void getChildCollections() throws XMLDBException {
        RemoteCollection remoteCollection = (RemoteCollection)getCollection();
        assertArrayEquals(new Collection[0], remoteCollection.getChildCollections());
    }

    @Test
    public void getResources() throws XMLDBException {
        RemoteCollection remoteCollection = (RemoteCollection)getCollection();
        assertArrayEquals(new org.exist.Resource[0], remoteCollection.getResources());
    }

    @Test
    public void getCreationTime() throws XMLDBException {
        assertNotNull(getCollection().getCreationTime());
    }

    private void prepareContent(RemoteXMLResource resource) throws XMLDBException, SAXException {
        final char[] buffer = new char[16 * 1024 * 1024];
        Arrays.fill(buffer, (char) 'x');
        ContentHandler content = resource.setContentAsSAX();
        content.startDocument();
        content.startElement("", "root", "root", new AttributesImpl());
        // writing 16 mb to resource
        content.characters(buffer, 0, buffer.length);
        content.endElement("", "root", "root");
        content.endDocument();
    }

    private void createResources(ArrayList<String> names, Class<? extends Resource> type) throws XMLDBException {
        for (String name : names) {
            Resource res = getCollection().createResource(name, type);
            if (res instanceof XMLResource) {
                res.setContent(XML_CONTENT);
            } else if (res instanceof BinaryResource) {
                res.setContent(BINARY_CONTENT);
            }
            getCollection().storeResource(res);
        }
    }
}
