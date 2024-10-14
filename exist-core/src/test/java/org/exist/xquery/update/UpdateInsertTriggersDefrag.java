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

import static org.exist.storage.DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR;
import static org.exist.test.TestConstants.TEST_COLLECTION_URI;
import static org.exist.test.TestConstants.TEST_XML_URI;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
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
        collectionService = exist.getRoot().getService(CollectionManagementService.class);
        testCollection = collectionService.createCollection(TEST_COLLECTION_URI.lastSegment().toString());

        try (final XMLResource doc = testCollection.createResource(TEST_XML_URI.toString(), XMLResource.class)) {
            doc.setContent("<list><item>initial</item></list>");
            testCollection.storeResource(doc);
        }
    }

    @After
    public void tearDown() throws Exception {
        collectionService.removeCollection(testCollection.getName());
        testCollection.close();
    }

    @Test
    public void triggerDefragAfterUpdate() throws Exception {
        final XQueryService queryService = testCollection.getService(XQueryService.class);

        final String update = "update insert <item>new node</item> into doc('" + path + "')//list";
        final ResourceSet updateResult = queryService.queryResource(path, update);
        assertEquals("Update expression returns an empty sequence", 0, updateResult.getSize());

        final ResourceSet itemResult = queryService.queryResource(path, "//item");
        assertEquals("Both items are returned", 2, itemResult.getSize());
    }

}
