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
package org.exist.collections.triggers;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.IndexQueryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
public class SAXTriggerTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String DOCUMENT1_CONTENT = 
        "<test>"
        + "<item id='1'><price>5.6</price><stock>22</stock></item>"
        + "<item id='2'><price>7.4</price><stock>43</stock></item>"
        + "<item id='3'><price>18.4</price><stock>5</stock></item>"
        + "<item id='4'><price>65.54</price><stock>16</stock></item>"
        + "</test>";

    private final static String DOCUMENT2_CONTENT = 
            "<test>"
            + "<item id='1'><price>5.6</price><stock>22</stock></item>"
            + "</test>";

    private final static String DOCUMENT3_CONTENT = 
        """
        <test test="valueTest">
            <item id="1" test="valueTest">
                <price test="valueTest">5.6</price>
                <stock test="valueTest">22</stock>
            </item>
        </test>""";
    
    private final static String COLLECTION_CONFIG = 
            "<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>"
            + "  <exist:triggers>"
            + "     <exist:trigger class='org.exist.collections.triggers.StoreTrigger'/>"
            + "  </exist:triggers>"
            + "</exist:collection>";


    private final static String BASE_URI = "xmldb:exist://";

    private final static String testCollection = "/db/triggers";

    @org.junit.jupiter.api.Test
    void test() throws EXistException, XMLDBException {

        final BrokerPool db = BrokerPool.getInstance();
        db.registerDocumentTrigger(AnotherTrigger.class);

        final Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");

        final Resource resource = root.createResource("data.xml", XMLResource.class);
        resource.setContent(DOCUMENT1_CONTENT);
        root.storeResource(resource);

        assertEquals(3, AnotherTrigger.createDocumentEvents);

        assertEquals(26, AnotherTrigger.count);

        assertEquals(DOCUMENT1_CONTENT, AnotherTrigger.sb.toString());
    }

    @org.junit.jupiter.api.Test
    void saxEventModifications() throws EXistException, XMLDBException {

        final BrokerPool db = BrokerPool.getInstance();
        db.registerDocumentTrigger(StoreTrigger.class);

        final Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");

        Resource resource = root.createResource("data.xml", XMLResource.class);
        resource.setContent(DOCUMENT2_CONTENT);
        root.storeResource(resource);

        resource = root.createResource("data.xml", XMLResource.class);

        assertEquals(DOCUMENT3_CONTENT, resource.getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void saxEventModificationsAtXConf() throws EXistException, XMLDBException {
        final Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");

        final IndexQueryService idxConf = root.getService(IndexQueryService.class);
        idxConf.configureCollection(COLLECTION_CONFIG);

        Resource resource = root.createResource("data.xml", XMLResource.class);
        resource.setContent(DOCUMENT2_CONTENT);
        root.storeResource(resource);

        resource = root.createResource("data.xml", XMLResource.class);

        assertEquals(DOCUMENT3_CONTENT, resource.getContent().toString());
    }

    @AfterEach
    void cleanDB() throws XMLDBException {
        final Collection config = DatabaseManager.getCollection(BASE_URI + "/db/system/config" + testCollection, "admin", "");
        if (config != null) {
            CollectionManagementService mgmt = config.getService(CollectionManagementService.class);
            mgmt.removeCollection(".");
        }
        final Collection root = DatabaseManager.getCollection(BASE_URI + testCollection, "admin", "");
        
        Resource resource = root.getResource("messages.xml");
        if (resource != null) {
            root.removeResource(resource);
        }
        
        resource = root.getResource("data.xml");
        if (resource != null) {
            root.removeResource(resource);
        }
    }

    @BeforeAll
    static void initDB() throws ClassNotFoundException, XMLDBException, InstantiationException, IllegalAccessException {
        CollectionManagementService mgmt = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        Collection testCol = mgmt.createCollection("triggers");

        for (int i = 1; i <= 2; i++) {
            mgmt = testCol.getService(CollectionManagementService.class);
            testCol = mgmt.createCollection("sub" + i);
        }
    }

    @AfterAll
    static void closeDB() throws XMLDBException, LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }
}
