/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import java.io.File;

import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.xmlrpc.XmlRpcTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RemoteQueryTest extends RemoteDBTest {
    // jetty.port.standalone
	private static String uri = "xmldb:exist://localhost:" + System.getProperty("jetty.port", "8088") + "/xmlrpc" + XmldbURI.ROOT_COLLECTION;

	private Collection testCollection;
	private Collection xmlrpcCollection;

	@Test
	public void resourceSet() throws XMLDBException {
		String query = "//SPEECH[SPEAKER = 'HAMLET']";
		XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
		service.setProperty("highlight-matches", "none");
		CompiledExpression compiled = service.compile(query);
		ResourceSet result = service.execute(compiled);

		assertEquals(result.getSize(), 359);

		for (int i = 0; i < result.getSize(); i++) {
			XMLResource r = (XMLResource) result.getResource(i);
			Node node = r.getContentAsDOM().getFirstChild();
		}
	}

	@Test
	public void externalVar() throws XMLDBException {
        String query = XmlRpcTest.QUERY_MODULE_DATA;
        XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
        service.setProperty("highlight-matches", "none");

        service.setNamespace("tm", "http://exist-db.org/test/module");
        service.setNamespace("tm-query", "http://exist-db.org/test/module/query");

        service.declareVariable("tm:imported-external-string", "imported-string-value");
        service.declareVariable("tm-query:local-external-string", "local-string-value");

        CompiledExpression compiled = service.compile(query);
        ResourceSet result = service.execute(compiled);

        assertEquals(result.getSize(), 2);

        for (int i = 0; i < result.getSize(); i++) {
            XMLResource r = (XMLResource) result.getResource(i);
        }
	}

	@Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
		if (uri.startsWith("xmldb:exist://localhost")) {
            initServer();
        }

        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root =
            DatabaseManager.getCollection(
                    uri,
                    "admin",
                    null);
        CollectionManagementService service =
            (CollectionManagementService) root.getService(
                    "CollectionManagementService",
            "1.0");
        testCollection = service.createCollection("test");
        assertNotNull(testCollection);

        Resource xr = testCollection.createResource("hamlet.xml", "XMLResource");
        String existHome = System.getProperty("exist.home");
        File existDir = existHome == null ? new File(".") : new File(existHome);
        File f = new File(existDir,"samples/shakespeare/hamlet.xml");
        xr.setContent(f);
        testCollection.storeResource(xr);

        xmlrpcCollection = service.createCollection("xmlrpc");
        assertNotNull(xmlrpcCollection);

        Resource br = xmlrpcCollection.createResource(TestConstants.TEST_MODULE_URI.toString(), "BinaryResource");
        ((EXistResource) br).setMimeType(MimeType.XQUERY_TYPE.getName());
        br.setContent(XmlRpcTest.MODULE_DATA);
        xmlrpcCollection.storeResource(br);
	}

	@After
	public void tearDown() throws Exception {
        if (!((CollectionImpl) testCollection).isRemoteCollection()) {
            DatabaseInstanceManager dim =
                (DatabaseInstanceManager) testCollection.getService(
                        "DatabaseInstanceManager", "1.0");
            dim.shutdown();
        }
        testCollection = null;
        xmlrpcCollection = null;
	}
}
