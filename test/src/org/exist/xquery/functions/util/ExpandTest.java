/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.functions.util;

import static org.junit.Assert.*;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Casey Jordan
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ExpandTest {

    private XQueryService service;
    private Collection root = null;
    private Database database = null;

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        service = (XQueryService) root.getService("XQueryService", "1.0");
    }

    @After
    public void tearDown() throws Exception {

        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

        // clear instance variables
        service = null;
        root = null;
    }

    @Test
    public void testExpandWithDefaultNS() throws XPathException {

    	String expected = "<ok xmlns=\"some\">\n    <concept xmlns=\"\"/>\n</ok>";
    	
        ResourceSet result = null;
        String r = null;
        try {
            String query = "" +
            		"let $doc-path := xmldb:store('/db', 'test.xml', <concept/>)\n" +
                    "let $doc := doc($doc-path)\n" +
                    "return\n" +
                    "<ok xmlns='some'>\n" +
                    "{util:expand($doc)}\n" +
            		"</ok>";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals(expected, r);

            query = "" +
            		"let $doc-path := xmldb:store('/db', 'test.xml', <concept/>)\n" +
                    "let $doc := doc($doc-path)\n" +
                    "return\n" +
                    "<ok xmlns='some'>\n" +
                    "{$doc}\n" +
            		"</ok>";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals(expected, r);

        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
}
