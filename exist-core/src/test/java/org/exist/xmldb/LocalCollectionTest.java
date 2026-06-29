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
import org.xmldb.api.base.Service;
import org.xmldb.api.base.ServiceProviderCache;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;
import org.xmldb.api.modules.XUpdateQueryService;
import org.xmldb.api.security.PermissionManagementService;
import org.xmldb.api.security.UserPrincipalLookupService;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.notNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

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
    public void getServices() throws XMLDBException {
        final List<Class<? extends Service>> expectedServiceTypes = Arrays.asList(CollectionManagementService.class,
                DatabaseInstanceManager.class, EXistCollectionManagementService.class, EXistRestoreService.class,
                EXistUserManagementService.class, IndexQueryService.class, UserManagementService.class,
                XPathQueryService.class, XQueryService.class, XUpdateQueryService.class,
                LocalXPathQueryService.class, LocalCollectionManagementService.class, LocalUserManagementService.class,
                LocalDatabaseInstanceManager.class, LocalIndexQueryService.class, LocalXUpdateQueryService.class,
                LocalUserPrincipalLookupService.class, LocalPermissionManagementService.class);
        for (Class<? extends Service> expectedServiceType : expectedServiceTypes) {
            assertThat(testCollection.hasService(expectedServiceType)).isTrue();
            assertThat(testCollection.getService(expectedServiceType)).isNotNull();
        }
    }

    @Test
    public void getChildCollectionCount() throws XMLDBException {
        assertThat(testCollection.getChildCollectionCount()).isZero();
    }

    @Test
    public void getPropertyWithDefault() throws XMLDBException {
        assertThat(testCollection.getProperty("myProperty", "theDefault")).isEqualTo("theDefault");
    }

    @Test
    public void hasService(){
        assertThat(testCollection.hasService(XPathQueryService.class)).isTrue();
    }

    @Test
    public void findService(){
        assertThat(testCollection.findService(XPathQueryService.class).get()).isNotNull();
    }

    @Test
    public void getService() throws XMLDBException {
        assertThat(testCollection.getService(XPathQueryService.class)).isNotNull();
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
        registry.add(eq(UserPrincipalLookupService.class), notNull());
        registry.add(eq(PermissionManagementService.class), notNull());

        replay(registry);
        localCollection.registerProvders(registry);
        verify(registry);
    }

    @Test
    public void listChildCollections() throws XMLDBException {
        assertThat(testCollection.listChildCollections()).isEmpty();
    }

    @Test
    public void getChildCollections() throws XMLDBException {
        LocalCollection localCollection = (LocalCollection)testCollection;
        assertThat(localCollection.getChildCollections()).isEmpty();
    }

    @Test
    public void listResources() throws XMLDBException {
        LocalCollection localCollection = (LocalCollection)testCollection;
        assertThat(localCollection.listResources()).isEmpty();
    }

    @Test
    public void getResources() throws XMLDBException {
        LocalCollection localCollection = (LocalCollection)testCollection;
        assertThat(localCollection.getResources()).isEmpty();
    }

    @Test
    public void getCreationTime() throws XMLDBException {
        assertThat(testCollection.getCreationTime()).isNotNull();
    }
}
