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

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import org.exist.security.Permission;
import org.exist.security.Account;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;
import static org.exist.xmldb.XmldbLocalTests.*;

/**
 * Tests XMLResource.getContentAsDOM() for resources retrieved from
 * an XQuery.
 * 
 * @author wolf
 */
public class ContentAsDOMTest {
    
    private final static String XML =
        "<root><test>ABCDEF</test></root>";
    
    private final static String XQUERY =
        "let $t := /root/test " +
        "return (" +
        "<!-- Comment -->," +
        "<output>{$t}</output>)";

    private final static String TEST_COLLECTION = "testContentAsDOM";


    @Test
    public void getContentAsDOM() throws XMLDBException, TransformerConfigurationException, TransformerException {

        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
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
    }


    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        Collection testCollection = service.createCollection(TEST_COLLECTION);
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        // change ownership to guest
        Account guest = ums.getAccount(GUEST_UID);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod(Permission.DEFAULT_COLLECTION_PERM);

        Resource resource = testCollection.createResource("test.xml", "XMLResource");
        resource.setContent(XML);
        testCollection.storeResource(resource);
        ums.chown(resource, guest, GUEST_UID); //change resource ownership to guest
    }

    @After
    public void tearDown() throws XMLDBException {

        //delete the test collection
        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService service = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        service.removeCollection(TEST_COLLECTION);

        //shutdownDB the db
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
    }
}
