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
package org.exist.xquery;

import org.exist.source.StringSource;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXQueryService;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import static org.junit.Assert.assertNotNull;

public class QueryPoolTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private Collection testCollection;

    @Test
    public void differentQueries() throws XMLDBException {
        EXistXQueryService service = testCollection.getService(EXistXQueryService.class);
        for (int i = 0; i < 1000; i++) {
            String query = "update insert <node id='id" + Integer.toHexString(i) + "'>" +
                    "Some longer text <b>content</b> in this node. Some longer text <b>content</b> in this node. " +
                    "Some longer text <b>content</b> in this node. Some longer text <b>content</b> in this node." +
                    "</node> " +
                    "into //test[@id = 't1']";
            service.execute(new StringSource(query));
        }
    }

    @Test
    public void read() throws XMLDBException {
        XMLResource res = (XMLResource) testCollection.getResource("large_list.xml");
        assertNotNull(res);
    }

    @Before
    public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        final CollectionManagementService service =
                existEmbeddedServer.getRoot().getService(
                    CollectionManagementService.class);
        testCollection = service.createCollection("test-pool");
        assertNotNull(testCollection);

        final XMLResource doc = testCollection.createResource("large_list.xml", XMLResource.class);
        doc.setContent("<test id='t1'/>");
        testCollection.storeResource(doc);
    }

    @After
    public void tearDown() throws Exception {
        final CollectionManagementService service = testCollection.getService(CollectionManagementService.class);
        service.removeCollection("/db/test-pool");
        testCollection.close();
    }
}
