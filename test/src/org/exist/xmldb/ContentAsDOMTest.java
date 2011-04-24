/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

/**
 * Tests XMLResource.getContentAsDOM() for resources retrieved from
 * an XQuery.
 * 
 * @author wolf
 */
public class ContentAsDOMTest extends TestCase {

    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private final static String ROOT_URI = "xmldb:exist://" + XmldbURI.ROOT_COLLECTION;
    
    private final static String XML =
        "<root><test>ABCDEF</test></root>";
    
    private final static String XQUERY =
        "let $t := /root/test " +
        "return (" +
        "<!-- Comment -->," +
        "<output>{$t}</output>)";
    
    private Collection root;
    
    public ContentAsDOMTest(String name) {
        super(name);
    }
    
    public void testGetContentAsDOM() {
        try {
        	XQueryService service = (XQueryService) root.getService("XQueryService", "1.0");        
	        ResourceSet result = service.query(XQUERY);
	        for(long i = 0; i < result.getSize(); i++) {
	            XMLResource r = (XMLResource) result.getResource(i);
	            
	            System.out.println("Output of getContent():");
	            System.out.println(r.getContent());
	            
	            System.out.println("Output of getContentAsDOM():");
	            Node node = r.getContentAsDOM();
	            Transformer t = TransformerFactory.newInstance().newTransformer();
	            t.setOutputProperty(OutputKeys.INDENT, "yes");
	            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
	            DOMSource source = new DOMSource(node);
	            StreamResult output = new StreamResult(System.out);
	            t.transform(source, output);
	        }
        } catch (Exception e) {
        	e.printStackTrace();
            fail(e.getMessage());            
        }
    }
    
    /*
     * @see TestCase#setUp()
     */
    protected void setUp() {
        try {
            // initialize driver
            Class<?> cl = Class.forName(DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            
            root = DatabaseManager.getCollection(ROOT_URI, "admin", null);
            Resource resource = root.createResource("test.xml", "XMLResource");
            resource.setContent(XML);
            root.storeResource(resource);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() {
    	try {
	        Resource resource = root.getResource("test.xml");
	        assertNotNull("text.xml not found", resource);
	        root.removeResource(resource);
	        DatabaseInstanceManager mgr = (DatabaseInstanceManager)
	            root.getService("DatabaseInstanceManager", "1.0");
	        mgr.shutdown();
            
            root = null;
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        TestRunner.run(ContentAsDOMTest.class);
    }
}
