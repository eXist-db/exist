/*
 * Created on 20 juil. 2004
$Id$
 */
package org.exist.xmldb;

import org.exist.util.io.FastByteArrayInputStream;
import org.exist.xquery.util.URIUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

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
        Service[] services = getCollection().getServices();
        assertEquals(6, services.length);
        assertEquals(RemoteXPathQueryService.class, services[0].getClass());
        assertEquals(RemoteCollectionManagementService.class, services[1].getClass());
        assertEquals(RemoteUserManagementService.class, services[2].getClass());
        assertEquals(RemoteDatabaseInstanceManager.class, services[3].getClass());
        assertEquals(RemoteIndexQueryService.class, services[4].getClass());
        assertEquals(RemoteXUpdateQueryService.class, services[5].getClass());
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
        final Resource resource = collection.createResource(resourceName, XMLResource.RESOURCE_TYPE);
        assertNotNull(resource);
        assertEquals(collection, resource.getParentCollection());

        final String xml = "<?xml version='1.0'?><xml>" + System.currentTimeMillis() + "</xml>";
        resource.setContent(xml);
        collection.storeResource(resource);

        final Resource retrievedResource = collection.getResource(resourceName);
        assertNotNull(retrievedResource);
        assertEquals(XMLResource.RESOURCE_TYPE, retrievedResource.getResourceType());
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
        final Resource resource = collection.createResource(resourceName, BinaryResource.RESOURCE_TYPE);
        assertNotNull(resource);
        assertEquals(collection, resource.getParentCollection());

        final String bin = "binary data: " + System.currentTimeMillis();
        resource.setContent(bin);
        collection.storeResource(resource);

        final Resource retrievedResource = collection.getResource(resourceName);
        assertNotNull(retrievedResource);
        assertEquals(BinaryResource.RESOURCE_TYPE, retrievedResource.getResourceType());
        assertTrue(retrievedResource instanceof BinaryResource);
        final byte[] result = (byte[]) retrievedResource.getContent();
        assertNotNull(result);
        assertEquals(bin, new String(result, UTF_8));
	}

	@Test
    public void createEmptyBinaryResource() throws XMLDBException, IOException {
        final Collection collection = getCollection();
        final String resourceName = "empty.dtd";
        final Resource resource = collection.createResource(resourceName, BinaryResource.RESOURCE_TYPE);
        ((EXistResource) resource).setMimeType("application/xml-dtd");

        final byte[] bin = new byte[0];
        try (final InputStream is = new FastByteArrayInputStream(bin)) {
            final InputSource inputSource = new InputSource();
            inputSource.setByteStream(is);
            inputSource.setSystemId("empty.dtd");

            resource.setContent(inputSource);
            collection.storeResource(resource);
        }

        final Resource retrievedResource = collection.getResource(resourceName);
        assertNotNull(retrievedResource);
        assertEquals(BinaryResource.RESOURCE_TYPE, retrievedResource.getResourceType());
        assertTrue(retrievedResource instanceof BinaryResource);
        final byte[] result = (byte[]) retrievedResource.getContent();
        assertNotNull(result);
        assertArrayEquals(bin, result);
    }


    @Test /* issue 1874 */
    public void createXMLFileResource() throws XMLDBException, IOException {
        Collection collection = getCollection();
        final Resource resource = collection.createResource("testresource", "XMLResource");
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
        Files.copy(new FastByteArrayInputStream(sb.toString().getBytes()), path, StandardCopyOption.REPLACE_EXISTING);
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
        createResources(xmlNames, "XMLResource");

        ArrayList<String> binaryNames = new ArrayList<>();
        binaryNames.add("b1");
        binaryNames.add("b2");
        createResources(binaryNames, "BinaryResource");

        String[] actualContents = getCollection().listResources();
        for (int i = 0; i < actualContents.length; i++) {
            xmlNames.remove(actualContents[i]);
            binaryNames.remove(actualContents[i]);
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
        final RemoteXMLResource resource = (RemoteXMLResource)collection.createResource("testresource", "XMLResource");
        prepareContent(resource);
        collection.storeResource(resource);
        // load stored content
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resource.getContentIntoAStream(outputStream);
        // compare size
        assertEquals(16777229, outputStream.size());
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

    private void createResources(ArrayList<String> names, String type) throws XMLDBException {
        for (String name : names) {
            Resource res = getCollection().createResource(name, type);
            if(type.equals("XMLResource")) {
                res.setContent(XML_CONTENT);
            } else {
                res.setContent(BINARY_CONTENT);
            }
            getCollection().storeResource(res);
        }
    }
}
