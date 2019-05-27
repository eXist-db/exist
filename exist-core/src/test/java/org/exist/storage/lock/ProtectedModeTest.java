/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.storage.lock;

import org.exist.TestDataGenerator;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXPathQueryService;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.nio.file.Path;
import java.util.Random;

public class ProtectedModeTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);
    
    private final static int COLLECTION_COUNT = 20;
    private final static int DOCUMENT_COUNT = 20;

    private final static String generateXQ =
            "declare function local:random-sequence($length as xs:integer, $G as map(xs:string, item())) {\n"
            + "  if ($length eq 0)\n"
            + "  then ()\n"
            + "  else ($G?number, local:random-sequence($length - 1, $G?next()))\n"
            + "};\n"
            + "let $rnd := fn:random-number-generator() return"
            + "<book id=\"{$filename}\" n=\"{$count}\">"
            + "   <chapter xml:id=\"chapter{$count}\">"
            + "       <title>{local:random-sequence(7, $rnd)}</title>"
            + "       {"
            + "           for $section in 1 to 8 return"
            + "               <section id=\"sect{$section}\">"
            + "                   <title>{local:random-sequence(7, $rnd)}</title>"
            + "                   {"
            + "                       for $para in 1 to 10 return"
            + "                           <para>{local:random-sequence(120, $rnd)}</para>"
            + "                   }"
            + "               </section>"
            + "       }"
            + "   </chapter>"
            + "</book>";

    @Test
    public void queryCollection() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection("xmldb:exist:///db/protected", "admin", "");
        final EXistXPathQueryService service = (EXistXPathQueryService) root.getService("XQueryService", "1.0");
        try {
            service.beginProtected();
            final ResourceSet result = service.query("collection('/db/protected/test5')//book");
            assertEquals(DOCUMENT_COUNT, result.getSize());
        } finally {
            service.endProtected();
        }
    }

    @Test
    public void queryRoot() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection("xmldb:exist:///db/protected", "admin", "");
        final EXistXPathQueryService service = (EXistXPathQueryService) root.getService("XQueryService", "1.0");
        try {
            service.beginProtected();
            final ResourceSet result = service.query("//book");
            assertEquals(COLLECTION_COUNT * DOCUMENT_COUNT, result.getSize());
        } finally {
            service.endProtected();
        }
    }

    @Test
    public void queryDocs() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection("xmldb:exist:///db/protected", "admin", "");
        final EXistXPathQueryService service = (EXistXPathQueryService) root.getService("XQueryService", "1.0");
        final Random random = new Random();
        for (int i = 0; i < COLLECTION_COUNT; i++) {
            String docURI = "doc('/db/protected/test" + i + "/xdb" + random.nextInt(DOCUMENT_COUNT) + ".xml')";
            try {
                service.beginProtected();
                final ResourceSet result = service.query(docURI + "//book");
                assertEquals(1, result.getSize());
            } finally {
                service.endProtected();
            }
        }
    }

    @BeforeClass
    public static void setupDb() throws XMLDBException, SAXException {
        CollectionManagementService mgmt = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        final Collection collection = mgmt.createCollection("protected");

        mgmt = (CollectionManagementService) collection.getService("CollectionManagementService", "1.0");

        final TestDataGenerator generator = new TestDataGenerator("xdb", DOCUMENT_COUNT);
        for (int i = 0; i < COLLECTION_COUNT; i++) {
            Collection currentColl = mgmt.createCollection("test" + i);
            final Path[] files = generator.generate(currentColl, generateXQ);
            for (int j = 0; j < files.length; j++) {
                final XMLResource resource = (XMLResource) currentColl.createResource("xdb" + j + ".xml", "XMLResource");
                resource.setContent(files[j].toFile());
                currentColl.storeResource(resource);
            }
        }
    }

    @AfterClass
    public static void cleanupDb() throws XMLDBException {
        final CollectionManagementService cmgr = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        cmgr.removeCollection("protected");
    }
}
