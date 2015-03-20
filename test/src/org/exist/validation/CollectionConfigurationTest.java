/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 * $Id: CollectionConfigurationValidationModeTest.java 6709 2007-10-12 20:58:52Z dizzzz $
 */
package org.exist.validation;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.*;

/**
 *  Some tests regarding invalid collection.xconf documents.
 * 
 * @author wessels
 */
public class CollectionConfigurationTest {

    String invalidConfig = "<invalid/>";
    private static final Logger LOG = LogManager.getLogger(CollectionConfigurationValidationModeTest.class);
    private static XPathQueryService xpqservice;
    private static Collection root = null;
    private static Database database = null;
    private static CollectionManagementService cmservice = null;

    public CollectionConfigurationTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        startDatabase();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
                (DatabaseInstanceManager) root.getService(
                "DatabaseInstanceManager", "1.0");
        dim.shutdown();
        database = null;
    }


    // =============
    
    private static void startDatabase() throws XMLDBException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        xpqservice = (XPathQueryService) root.getService("XQueryService", "1.0");
        cmservice = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");

    }

    private void createCollection(String collection) throws XMLDBException {
        Collection testCollection = cmservice.createCollection(collection);
        assertNotNull(testCollection);

        testCollection = cmservice.createCollection("/db/system/config" + collection);
        assertNotNull(testCollection);
    }

    private void storeCollectionXconf(String collection, String document) throws XMLDBException {
        ResourceSet result = xpqservice.query("xmldb:store(\"" + collection + "\", \"collection.xconf\", " + document + ")");
        String r = (String) result.getResource(0).getContent();
        assertEquals("Store xconf", collection + "/collection.xconf", r);
    }

    @SuppressWarnings("unused")
	private void storeDocument(String collection, String name, String document) throws XMLDBException {
        ResourceSet result = xpqservice.query("xmldb:store(\"" + collection + "\", \"" + name + "\", " + document + ")");
        String r = (String) result.getResource(0).getContent();
        assertEquals("Store doc", collection + "/" + name, r);
    }

    // ==========
    
    @Test
    public void insertInvalidCollectionXconf() {

        try {
            createCollection("/db/system/config/db/foobar");
            storeCollectionXconf("/db/system/config/db/foobar", invalidConfig);
        } catch (XMLDBException ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }

        try {
            createCollection("/db/system/config/db/foobar");
            storeCollectionXconf("/db/system/config/db/foobar", invalidConfig);
        } catch (XMLDBException ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }

    }
}
