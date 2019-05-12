/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.collections.triggers;

import java.io.IOException;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.IndexQueryService;
import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import org.junit.runners.Parameterized.Parameters;

/**
 * Test proper configuration of triggers in collection.xconf, in particular if there's
 * only a configuration for the parent collection, but not the child. The trigger should
 * be created with the correct base collection.
 */
@RunWith(Parameterized.class)
public class TriggerConfigTest {

    private static final Logger LOG = LogManager.getLogger(TriggerConfigTest.class);

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "/db/triggers" },
            { "/db/triggers/sub1" },
            { "/db/triggers/sub1/sub2" }
        });
    }

    private static final String COLLECTION_CONFIG =
    	"<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>" +
	    "  <exist:triggers>" +
		"     <exist:trigger class='org.exist.collections.triggers.TestTrigger'/>" +
        "  </exist:triggers>" +
        "</exist:collection>";

    private static final String EMPTY_COLLECTION_CONFIG =
    	"<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>" +
        "</exist:collection>";

    private final static String DOCUMENT_CONTENT =
		  "<test>"
		+ "<item id='1'><price>5.6</price><stock>22</stock></item>"
		+ "<item id='2'><price>7.4</price><stock>43</stock></item>"
		+ "<item id='3'><price>18.4</price><stock>5</stock></item>"
		+ "<item id='4'><price>65.54</price><stock>16</stock></item>"
		+ "</test>";

    private final static String BASE_URI = "xmldb:exist://";

    @Parameter
    public String testCollection;

    @Test
    public void storeDocument() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(COLLECTION_CONFIG);
            
            Resource resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);
            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.queryResource("messages.xml", "string(//event[last()]/@collection)");
            assertEquals(1, result.getSize());
            assertEquals(testCollection, result.getResource(0).getContent());
        } catch (XMLDBException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @Test
    public void removeDocument() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(COLLECTION_CONFIG);

            Resource resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);

            root.removeResource(resource);

            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.queryResource("messages.xml", "string(//event[last()]/@collection)");
            assertEquals(1, result.getSize());
            assertEquals(testCollection, result.getResource(0).getContent());
        } catch (XMLDBException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @Test
    public void removeTriggers() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(EMPTY_COLLECTION_CONFIG);

            Resource resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);

            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.query("if (doc-available('" + testCollection + "/messages.xml')) then doc('" + testCollection + "/messages.xml')/events/event[@id = 'STORE-DOCUMENT'] else ()");
            assertEquals("No trigger should have fired. Configuration was removed", 0, result.getSize());
        } catch (XMLDBException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @Test
    public void updateTriggers() {
        try {
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");
            IndexQueryService iqs = (IndexQueryService) root.getService("IndexQueryService", "1.0");
            iqs.configureCollection(EMPTY_COLLECTION_CONFIG);

            Collection configCol =  DatabaseManager.getCollection(BASE_URI + "/db/system/config" + testCollection, "admin", "");
            Resource resource = configCol.createResource("collection.xconf", "XMLResource");
            resource.setContent(COLLECTION_CONFIG);
            configCol.storeResource(resource);

            resource = root.createResource("data.xml", "XMLResource");
            resource.setContent(DOCUMENT_CONTENT);
            root.storeResource(resource);

            XQueryService qs = (XQueryService) root.getService("XQueryService", "1.0");
            ResourceSet result = qs.query("if (doc-available('" + testCollection + "/messages.xml')) then doc('" + testCollection + "/messages.xml')/events/event[@id = 'STORE-DOCUMENT']/string(@collection) else ()");
            assertEquals(1, result.getSize());
            assertEquals(testCollection, result.getResource(0).getContent());
        } catch (XMLDBException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @After
    public void cleanDB() {
        try {
            Collection config = DatabaseManager.getCollection(BASE_URI + "/db/system/config" + testCollection, "admin", "");
            if (config != null) {
                CollectionManagementService mgmt = (CollectionManagementService) config.getService("CollectionManagementService", "1.0");
                mgmt.removeCollection(".");
            }
            Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");
            Resource resource = root.getResource("messages.xml");
            if (resource != null) {
                root.removeResource(resource);
            }
            resource = root.getResource("data.xml");
            if (resource != null) {
                root.removeResource(resource);
            }
        } catch (XMLDBException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @BeforeClass
    public static void initDB() throws XMLDBException {
        CollectionManagementService mgmt = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        Collection testCol = mgmt.createCollection("triggers");

        for (int i = 1; i <= 2; i++) {
            mgmt = (CollectionManagementService) testCol.getService("CollectionManagementService", "1.0");
            testCol = mgmt.createCollection("sub" + i);
        }
    }

    @AfterClass
    public static void closeDB() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }
}