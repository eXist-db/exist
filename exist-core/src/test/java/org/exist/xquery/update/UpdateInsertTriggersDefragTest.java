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
package org.exist.xquery.update;

import org.exist.storage.DBBroker;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.test.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.Assert.assertEquals;

public class UpdateInsertTriggersDefragTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer exist = new ExistXmldbEmbeddedServer(false, true, true, propertiesBuilder().put(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR, -1).build());

    private Collection testCollection;
    private XQueryService queryService;
    private CollectionManagementService collectionService;

    /**
     * stores XML String and get Query Service
     *
     * @param documentName to be stored in the DB
     * @param content      to be stored in the DB
     * @throws XMLDBException if an error occurs storing the document
     */
    private void storeXML(final String documentName, final String content) throws XMLDBException {
        try (final XMLResource doc = testCollection.createResource(documentName, XMLResource.class)) {
            doc.setContent(content);
            testCollection.storeResource(doc);
        }
    }

    @Before
    public void setUp() throws Exception {
        collectionService = exist.getRoot().getService(CollectionManagementService.class);
        testCollection = collectionService.createCollection(TestConstants.TEST_COLLECTION_URI.toString());
        queryService = (XQueryService) testCollection.getService(XPathQueryService.class);
        storeXML(TestConstants.TEST_XML_URI.toString(), "<list><item>initial</item></list>");
    }

    @After
    public void tearDown() throws Exception {
        testCollection.close();
    }

    @Test
    public void triggerDefragAfterUpdate() throws Exception {
        final String update = "update insert <item>new node</item> into doc('" + TestConstants.TEST_COLLECTION_URI + "/" + TestConstants.TEST_XML_URI.toString() + "')//list";
        final ResourceSet updateResult = queryService.queryResource(TestConstants.TEST_XML_URI.toString(), update);
        assertEquals("Update expression returns an empty sequence", 0, updateResult.getSize());

        final ResourceSet itemResult = queryService.queryResource(TestConstants.TEST_XML_URI.toString(), "//item");
        assertEquals("Both items are returned", 2, itemResult.getSize());
    }

}
