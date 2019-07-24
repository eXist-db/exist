/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
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
        EXistXQueryService service = (EXistXQueryService) testCollection.getService("XQueryService", "1.0");
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
            (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                "CollectionManagementService",
                "1.0");
        testCollection = service.createCollection("test-pool");
        assertNotNull(testCollection);

        final XMLResource doc =
          (XMLResource) testCollection.createResource("large_list.xml", "XMLResource");
        doc.setContent("<test id='t1'/>");
        testCollection.storeResource(doc);
    }

    @After
    public void tearDown() throws Exception {
        final CollectionManagementService service = (CollectionManagementService)
            testCollection.getService("CollectionManagementService", "1.0");
        service.removeCollection("/db/test-pool");
        testCollection.close();
    }
}
