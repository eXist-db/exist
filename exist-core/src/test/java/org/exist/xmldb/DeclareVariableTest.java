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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
public class DeclareVariableTest {

    private static final String TEST_COLLECTION_NAME = "xmldb-declare-variable-test";

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

    private Collection testCollection;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    private void setUp() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);
    }

    @AfterEach
    void tearDown() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void declareBigInteger(String apiName, String baseUri) throws XMLDBException {
        initDeclareVariableTest(apiName, baseUri);
        setUp();
        final Resource result = executeQueryWithExternalVariable(new BigInteger("123456789123456789123456789"));
        assertEquals("123456789123456789123456789", result.getContent());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void declareBigDecimal(String apiName, String baseUri) throws XMLDBException {
        initDeclareVariableTest(apiName, baseUri);
        setUp();
        final Resource result = executeQueryWithExternalVariable(new BigDecimal("1.1"));
        assertEquals("1.1", result.getContent());
    }

    private Resource executeQueryWithExternalVariable(final Object value) throws XMLDBException {
        final XQueryService xqueryService = testCollection.getService(XQueryService.class);
        xqueryService.declareVariable("x", value);

        final String query =
                """
                xquery version "3.1";
                declare variable $x external;
                $x""";

        final CompiledExpression compiled = xqueryService.compile(query);

        final ResourceSet resourceSet = xqueryService.execute(compiled);
        assertEquals(1, resourceSet.getSize());
        final Resource resource = resourceSet.getResource(0);
        assertNotNull(resource);
        return resource;
    }

    public void initDeclareVariableTest(String apiName, String baseUri) {
        this.apiName = apiName;
        this.baseUri = baseUri;
    }
}
