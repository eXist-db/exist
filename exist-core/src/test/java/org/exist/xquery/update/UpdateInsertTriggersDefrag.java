package org.exist.xquery.update;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.exist.storage.DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR;
import static org.exist.test.TestConstants.TEST_COLLECTION_URI;
import static org.exist.test.TestConstants.TEST_XML_URI;
import static org.junit.Assert.assertEquals;

public class UpdateInsertTriggersDefrag {
    @ClassRule
    public static final ExistXmldbEmbeddedServer exist = new ExistXmldbEmbeddedServer(false, true, true,
            propertiesBuilder().put(PROPERTY_XUPDATE_FRAGMENTATION_FACTOR, -1).build());
    private final String path = TEST_COLLECTION_URI + "/" + TEST_XML_URI.toString();
    private Collection testCollection;
    private CollectionManagementService collectionService;

    @Before
    public void setUp() throws Exception {
        collectionService = (CollectionManagementService) exist.getRoot().getService("CollectionManagementService","1.0");

        testCollection = collectionService.createCollection(TEST_COLLECTION_URI.lastSegment().toString());
        final XMLResource doc = (XMLResource) testCollection.createResource(TEST_XML_URI.toString(), XMLResource.RESOURCE_TYPE);

        doc.setContent("<list><item>initial</item></list>");
        testCollection.storeResource(doc);
    }

    @After
    public void tearDown() throws Exception {
        collectionService.removeCollection(testCollection.getName());
        testCollection.close();
    }

    @Test
    public void triggerDefragAfterUpdate() throws Exception {
        final XQueryService queryService = (XQueryService) testCollection.getService("XPathQueryService", "1.0");

        final String update = "update insert <item>new node</item> into doc('" + path + "')//list";
        final ResourceSet updateResult = queryService.queryResource(path, update);
        assertEquals("Update expression returns an empty sequence", 0, updateResult.getSize());

        final ResourceSet itemResult = queryService.queryResource(path, "//item");
        assertEquals("Both items are returned", 2, itemResult.getSize());
    }

}
