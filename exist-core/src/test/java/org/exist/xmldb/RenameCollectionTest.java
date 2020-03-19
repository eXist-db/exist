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

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.Arrays;

import static com.ibm.icu.impl.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class RenameCollectionTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

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

    private static final String TEST_COLLECTION_NAME = "testRename";
    private static final String ZERO_COLLECTION_NAME = "0";
    private static final String ONE_COLLECTION_NAME = "1";
    private static final String THREE_COLLECTION_NAME = "3";
    private Collection testCollection;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Test
    public void rename_sameName() throws XMLDBException {
        /*
         * Create the collections:
         *
         * /db/testRename/0
         * /db/testRename/1
         * /db/testRename/0/3
         */
        EXistCollectionManagementService service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        final Collection zeroCollection = service.createCollection(ZERO_COLLECTION_NAME);
        assertNotNull(zeroCollection);

        final Collection oneCollection = service.createCollection(ONE_COLLECTION_NAME);
        assertNotNull(oneCollection);

        service = (EXistCollectionManagementService) zeroCollection.getService("CollectionManagementService", "1.0");
        final Collection threeCollection = service.createCollection(THREE_COLLECTION_NAME);
        assertNotNull(threeCollection);

        // rename the collection /db/testRename/0 to /db/testRename/zero
        service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");

        try {
            service.move(XmldbURI.create(ZERO_COLLECTION_NAME), null, XmldbURI.create(ZERO_COLLECTION_NAME));

            fail("It should be impossible to rename a collection to the same name");

        } catch (final XMLDBException e) {
            assertTrue(e.getMessage().indexOf("Cannot move collection to itself") > -1);
        }
    }

    @Test
    public void rename_differentName() throws XMLDBException {
        /*
         * Create the collections:
         *
         * /db/test/0
         * /db/test/1
         * /db/test/0/3
         */
        EXistCollectionManagementService service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        final Collection zeroCollection = service.createCollection(ZERO_COLLECTION_NAME);
        assertNotNull(zeroCollection);

        final Collection oneCollection = service.createCollection(ONE_COLLECTION_NAME);
        assertNotNull(oneCollection);

        service = (EXistCollectionManagementService) zeroCollection.getService("CollectionManagementService", "1.0");
        final Collection threeCollection = service.createCollection(THREE_COLLECTION_NAME);
        assertNotNull(threeCollection);

        // rename the collection /db/test/0 to /db/test/zero
        service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");

        final XmldbURI newName = XmldbURI.create("zero");
        service.move(XmldbURI.create(ZERO_COLLECTION_NAME), null, newName);
    }

    @Before
    public void setUp() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);
    }

    @After
    public void tearDown() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }
}
