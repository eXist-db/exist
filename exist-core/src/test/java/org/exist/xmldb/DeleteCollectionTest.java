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
public class DeleteCollectionTest {

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

    private static final String TEST_COLLECTION_NAME = "testDelete";
    private static final String ZERO_COLLECTION_NAME = "00";
    private static final String ONE_COLLECTION_NAME = "11";
    private static final String THREE_COLLECTION_NAME = "33";
    private Collection testCollection;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Test
    public void delete() throws XMLDBException {
        /*
         * Create the collections:
         *
         * /db/testDelete/00
         * /db/testDelete/11
         * /db/testDelete/00/33
         */
        EXistCollectionManagementService service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        final Collection zeroCollection = service.createCollection(ZERO_COLLECTION_NAME);
        assertNotNull(zeroCollection);

        final Collection oneCollection = service.createCollection(ONE_COLLECTION_NAME);
        assertNotNull(oneCollection);

        service = (EXistCollectionManagementService) zeroCollection.getService("CollectionManagementService", "1.0");
        final Collection threeCollection = service.createCollection(THREE_COLLECTION_NAME);
        assertNotNull(threeCollection);

        // delete the collection /db/test/00
        service = (EXistCollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        service.removeCollection(ZERO_COLLECTION_NAME);
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
