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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.test.TestConstants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ServiceProviderCache;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalCollectionTest {
    static Collection testCollection;

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void setup() throws XMLDBException {
        final CollectionManagementService cms = existEmbeddedServer
                .getRoot()
                .getService(CollectionManagementService.class);

        testCollection = cms.createCollection(TestConstants.TEST_COLLECTION_URI.lastSegment().toString());
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        final CollectionManagementService cms = existEmbeddedServer
                .getRoot()
                .getService(CollectionManagementService.class);

        cms.removeCollection(TestConstants.TEST_COLLECTION_URI.getRawCollectionPath());
    }

    @Test
    public void getChildCollectionCount() throws XMLDBException {
        assertEquals(0, testCollection.getChildCollectionCount());
    }

    @Test
    public void getPropertyWithDefault() throws XMLDBException {
        assertEquals("theDefault", testCollection.getProperty("myProperty", "theDefault"));
    }

    @Test
    public void hasService(){
        assertTrue(testCollection.hasService(XPathQueryService.class));
    }

    @Test
    public void findService(){
        assertNotNull(testCollection.findService(XPathQueryService.class).get());
    }

    @Test
    public void getService() throws XMLDBException {
        assertNotNull(testCollection.getService(XPathQueryService.class));
    }

    @Test
    public void registerProvders() {
        LocalCollection localCollection = (LocalCollection)testCollection;
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
        localCollection.registerProvders(registry);
        verify(registry);
    }

    @Test
    public void listChildCollections() throws XMLDBException {
        assertTrue(testCollection.listChildCollections().isEmpty());
    }

    @Test
    public void getChildCollections() throws XMLDBException {
        LocalCollection localCollection = (LocalCollection)testCollection;
        assertArrayEquals(new Collection[0], localCollection.getChildCollections());
    }

    @Test
    public void listResources() throws XMLDBException {
        LocalCollection localCollection = (LocalCollection)testCollection;
        assertTrue(localCollection.listResources().isEmpty());
    }

    @Test
    public void getResources() throws XMLDBException {
        LocalCollection localCollection = (LocalCollection)testCollection;
        assertArrayEquals(new org.exist.Resource[0], localCollection.getResources());
    }

    @Test
    public void getCreationTime() throws XMLDBException {
        assertNotNull(testCollection.getCreationTime());
    }
}
