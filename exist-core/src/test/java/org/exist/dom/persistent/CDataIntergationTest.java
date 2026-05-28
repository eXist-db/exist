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

package org.exist.dom.persistent;

import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.exist.TestUtils;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hc.core5.http.HttpStatus.SC_CREATED;
import static org.apache.hc.core5.http.HttpStatus.SC_OK;
import static org.junit.Assert.*;

/**
 * Tests for retrieving a document containing CDATA via
 * various APIs.
 */
public class CDataIntergationTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    @ClassRule
    public static final TemporaryFolder tempFolder = new TemporaryFolder();

    private final static String cdata_content = "Hello there \"Bob?\"";
    private final static String cdata_xml = "<elem1><![CDATA[" + cdata_content + "]]></elem1>";

    @Test
    public void cdataRestApi() throws IOException {
        final String uri = "http://localhost:" + existWebServer.getPort() + "/exist/rest/db";
        final String docUri = uri + "/rest-cdata-test.xml";

        final Executor executor = AbstractHttpTest.createAuthenticatedExecutor(
                existWebServer, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);

        // store document
        assertEquals(SC_CREATED, executeForStatus(executor,
                Request
                        .put(docUri)
                        .addHeader("Content-Type", "application/xml")
                        .bodyByteArray(cdata_xml.getBytes(UTF_8))
        ));

        // retrieve document
        final HttpResponseResult retrieved = executeForStatusAndBody(executor, Request.get(docUri));
        assertEquals(SC_OK, retrieved.statusCode());
        assertEquals(cdata_xml, retrieved.body());
    }

    @Test
    public void cdataXmlDbApi() throws XMLDBException {
        final String docName = "xmldb-cdata-test.xml";
        final Database database = new DatabaseImpl();
        DatabaseManager.registerDatabase(database);

        // store document
        try (final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)) {
            final Resource resource = root.createResource(docName, XMLResource.class);
            resource.setContent(cdata_xml);
            root.storeResource(resource);
        }

        // retrieve document
        try (final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)) {
            final Resource resource = root.getResource(docName);
            assertNotNull(resource);
            assertEquals(cdata_xml, resource.getContent().toString());
        }
    }
}
