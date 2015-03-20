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

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.LocalXMLResource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Joe Wicentowski
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class DocTest {

    private XPathQueryService service;
    private Collection root = null;
    private Collection test = null;

    private Database database = null;
    private org.exist.start.Main runner = null;

    public DocTest() {
    }

    @Before
    public void setUp() throws Exception {
        runner = new org.exist.start.Main("jetty");
		runner.run(new String[]{"jetty"});

		// initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        service = (XPathQueryService) root.getService("XQueryService", "1.0");

        CollectionManagementService cms = (CollectionManagementService) 
        	root.getService("CollectionManagementService", "1.0");
        //Creates the 'test' collection
        test = cms.createCollection("test");
        assertNotNull(test);

        storeResource(test, "test.xq", "BinaryResource", "application/xquery", "doc('test.xml')");
        storeResource(test, "test1.xq", "BinaryResource", "application/xquery", "doc('/test.xml')");

        storeResource(root, "test.xml", "XMLResource", null, "<x/>");
        storeResource(test, "test.xml", "XMLResource", null, "<y/>");

    }
    
    private void storeResource(Collection col, String fileName, String type, String mimeType, String content) throws XMLDBException {
    	Resource res = col.createResource(fileName, type);
    	res.setContent(content);
    	
    	if (mimeType != null)
    		((EXistResource) res).setMimeType(mimeType);
        
    	col.storeResource(res);
    }

    @After
    public void tearDown() throws Exception {

        //root.removeResource(invokableQuery);

        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        
        runner.shutdown();

        // clear instance variables
        service = null;
        root = null;
    }

    @Test
    public void testURIResolveWithEval() throws XPathException {
        ResourceSet result = null;
        try {
            String query = "util:eval(xs:anyURI('/db/test/test.xq'), false(), ())";
            result = service.query(query);

            LocalXMLResource res = (LocalXMLResource)result.getResource(0);
            assertNotNull(res);
            Node n = res.getContentAsDOM();
            assertEquals("y", n.getLocalName());

            query = "util:eval(xs:anyURI('/db/test/test1.xq'), false(), ())";
            result = service.query(query);

            res = (LocalXMLResource)result.getResource(0);
            assertNotNull(res);
            n = res.getContentAsDOM();
            assertEquals("x", n.getLocalName());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    @Ignore
    @Test
    public void testURLRewriter() throws XPathException, HttpException, IOException {

		HttpClient client = new HttpClient();

		// connect to a login page to retrieve session ID
		// jetty.port.jetty
		PostMethod method = new PostMethod("http://localhost:" + System.getProperty("jetty.port") + "/exist/rest/test/text.xq");

    	client.executeMethod(method);

    }
}
