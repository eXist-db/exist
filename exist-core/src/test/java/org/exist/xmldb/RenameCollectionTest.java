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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.Arrays;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.ibm.icu.impl.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class RenameCollectionTest {

    @RegisterExtension
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "local", "xmldb:exist://" },
                { "remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc" }
        });
    }
    public String apiName;
    public String baseUri;

    private static final String TEST_COLLECTION_NAME = "testRename";
    private static final String ZERO_COLLECTION_NAME = "0";
    private static final String ONE_COLLECTION_NAME = "1";
    private static final String THREE_COLLECTION_NAME = "3";
    private Collection testCollection;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void rename_sameName(String apiName, String baseUri) throws XMLDBException {
        initRenameCollectionTest(apiName, baseUri);
        /*
         * Create the collections:
         *
         * /db/testRename/0
         * /db/testRename/1
         * /db/testRename/0/3
         */
        EXistCollectionManagementService service = testCollection.getService(EXistCollectionManagementService.class);
        final Collection zeroCollection = service.createCollection(ZERO_COLLECTION_NAME);
        assertNotNull(zeroCollection);

        final Collection oneCollection = service.createCollection(ONE_COLLECTION_NAME);
        assertNotNull(oneCollection);

        service = zeroCollection.getService(EXistCollectionManagementService.class);
        final Collection threeCollection = service.createCollection(THREE_COLLECTION_NAME);
        assertNotNull(threeCollection);

        // rename the collection /db/testRename/0 to /db/testRename/zero
        service = testCollection.getService(EXistCollectionManagementService.class);

        try {
            service.move(XmldbURI.create(ZERO_COLLECTION_NAME), null, XmldbURI.create(ZERO_COLLECTION_NAME));

            fail("It should be impossible to rename a collection to the same name");

        } catch (final XMLDBException e) {
            assertTrue(e.getMessage().indexOf("Cannot move collection to itself") > -1);
        }
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void rename_differentName(String apiName, String baseUri) throws XMLDBException {
        initRenameCollectionTest(apiName, baseUri);
        /*
         * Create the collections:
         *
         * /db/test/0
         * /db/test/1
         * /db/test/0/3
         */
        EXistCollectionManagementService service = testCollection.getService(EXistCollectionManagementService.class);
        final Collection zeroCollection = service.createCollection(ZERO_COLLECTION_NAME);
        assertNotNull(zeroCollection);

        final Collection oneCollection = service.createCollection(ONE_COLLECTION_NAME);
        assertNotNull(oneCollection);

        service = zeroCollection.getService(EXistCollectionManagementService.class);
        final Collection threeCollection = service.createCollection(THREE_COLLECTION_NAME);
        assertNotNull(threeCollection);

        // rename the collection /db/test/0 to /db/test/zero
        service = testCollection.getService(EXistCollectionManagementService.class);

        final XmldbURI newName = XmldbURI.create("zero");
        service.move(XmldbURI.create(ZERO_COLLECTION_NAME), null, newName);
    }

    @BeforeEach
    public void setUp() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);
    }

    @AfterEach
    public void tearDown() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }

    public void initRenameCollectionTest(String apiName, String baseUri) {
        this.apiName = apiName;
        this.baseUri = baseUri;
    }
}
