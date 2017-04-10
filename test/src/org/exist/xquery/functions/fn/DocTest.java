/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;

import static org.junit.Assert.*;

import org.exist.xmldb.EXistResource;
import org.exist.xmldb.LocalXMLResource;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Joe Wicentowski
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class DocTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    private Collection test = null;

    @Before
    public void setUp() throws XMLDBException {
        final CollectionManagementService cms = (CollectionManagementService)
        	existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        //Creates the 'test' collection
        test = cms.createCollection("test");
        assertNotNull(test);

        storeResource(test, "test.xq", "BinaryResource", "application/xquery", "doc('test.xml')");
        storeResource(test, "test1.xq", "BinaryResource", "application/xquery", "doc('/test.xml')");

        storeResource(existEmbeddedServer.getRoot(), "test.xml", "XMLResource", null, "<x/>");
        storeResource(test, "test.xml", "XMLResource", null, "<y/>");

    }

    @After
    public void tearDown() throws XMLDBException {
        final CollectionManagementService cms = (CollectionManagementService)
                existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        //Creates the 'test' collection
        cms.removeCollection("test");
        test = null;

        existEmbeddedServer.getRoot().removeResource(existEmbeddedServer.getRoot().getResource("test.xml"));
    }
    
    private void storeResource(final Collection col, final String fileName, final String type, final String mimeType, final String content) throws XMLDBException {
    	Resource res = col.createResource(fileName, type);
    	res.setContent(content);
    	
    	if (mimeType != null) {
            ((EXistResource) res).setMimeType(mimeType);
        }
        
    	col.storeResource(res);
    }

    @Test
    public void testURIResolveWithEval() throws XPathException, XMLDBException {
        String query = "util:eval(xs:anyURI('/db/test/test.xq'), false(), ())";
        ResourceSet result = existEmbeddedServer.executeQuery(query);

        LocalXMLResource res = (LocalXMLResource)result.getResource(0);
        assertNotNull(res);
        Node n = res.getContentAsDOM();
        assertEquals("y", n.getLocalName());

        query = "util:eval(xs:anyURI('/db/test/test1.xq'), false(), ())";
        result = existEmbeddedServer.executeQuery(query);

        res = (LocalXMLResource)result.getResource(0);
        assertNotNull(res);
        n = res.getContentAsDOM();
        assertEquals("x", n.getLocalName());
    }
}
