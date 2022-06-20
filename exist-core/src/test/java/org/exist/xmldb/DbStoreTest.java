package org.exist.xmldb;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.concurrent.DBUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import javax.print.URIException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DbStoreTest {

    @ClassRule(order = 1)
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @ClassRule(order = 2)
    public static final ExistXmldbEmbeddedServer existEmbeddedServerWithAnyURI = new ExistXmldbEmbeddedServer(false, true,
            true, getConfig(), UUID.randomUUID().toString());


    private final static String TEST_COLLECTION = "testAnyUri";

    private final static Path getConfig() {
        try {
            //org/exist/xmldb/any-uri-enabled.xml
            final URL path = DbStoreTest.class.getClassLoader().getResource("org/exist/xmldb/any-uri-enabled.xml");
            return Paths.get(path.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("unable to parse URI for resourse", e);
        }
    }

    @Test(expected = XMLDBException.class)
    public final void simpleTest() throws XMLDBException {
        final Collection rootCol = existEmbeddedServer.getRoot();
        Collection testCol = rootCol.getChildCollection(TEST_COLLECTION);
        if (testCol == null) {
            testCol = DBUtils.addCollection(rootCol, TEST_COLLECTION);
            assertNotNull(testCol);
        }

        final XPathQueryService xpqs =
                (XPathQueryService) testCol.getService("XPathQueryService", "1.0");
        final ResourceSet rs =
                xpqs.query(
                        "xmldb:store(\n" +
                                "        '/db',\n" +
                                "        'image.jpg',\n" +
                                "        xs:anyURI('https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png'),\n" +
                                "        'image/png'\n" +
                                "    )");
        assertNotNull(rs);

        assertTrue(true);
    }

    @Test
    public final void testWithAnyUriEnabled() throws XMLDBException {
        final Collection rootCol = existEmbeddedServerWithAnyURI.getRoot();
        Collection testCol = rootCol.getChildCollection(TEST_COLLECTION);
        if (testCol == null) {
            testCol = DBUtils.addCollection(rootCol, TEST_COLLECTION);
            assertNotNull(testCol);
        }

        final XPathQueryService xpqs =
                (XPathQueryService) testCol.getService("XPathQueryService", "1.0");
        final ResourceSet rs =
                xpqs.query(
                        "xmldb:store(\n" +
                                "        '/db',\n" +
                                "        'image.jpg',\n" +
                                "        xs:anyURI('https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png'),\n" +
                                "        'image/png'\n" +
                                "    )");
        assertNotNull(rs);

        assertTrue(true);
    }


}
