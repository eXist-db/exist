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
package org.exist.xquery;

import com.googlecode.junittoolbox.ParallelRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;

@RunWith(ParallelRunner.class)
public class DuplicateAttributesTest {

    private static final Logger LOG = LogManager.getLogger(DuplicateAttributesTest.class);

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static Collection testCollection;

    private static String STORED_DOC1 = "<node attr='ab'/>";

    private static String STORED_DOC2 = "<node attr2='ab'/>";

    private static String DOC_WITH_DTD =
        "<!DOCTYPE IDS [\n" +
        "<!ELEMENT IDS (elementwithid-1+, elementwithid-2+,\n" +
        "               elementwithidrefattr-1+,elementwithidrefattr-2+)>\n" +
        "<!ELEMENT elementwithid-1 (#PCDATA)>\n" +
        "<!ELEMENT elementwithid-2 (#PCDATA)>\n" +
        "<!ELEMENT elementwithidrefattr-1 (#PCDATA)>\n" +
        "<!ELEMENT elementwithidrefattr-2 (#PCDATA)>\n" +
        "<!ATTLIST elementwithid-1 anId  ID #REQUIRED>\n" +
        "<!ATTLIST elementwithid-2 anId  ID #REQUIRED>\n" +
        "<!ATTLIST elementwithidrefattr-1 anIdRef IDREF #REQUIRED>  \n" +
        "<!ATTLIST elementwithidrefattr-2 anIdRef IDREF #REQUIRED>\n" +
        "]>\n" +
        " <IDS>\n" +
        "  <elementwithid-1 anId = \"id1\"/>\n" +
        "  <elementwithid-2 anId = \"id2\"/>\n" +
        "  <elementwithidrefattr-1 anIdRef = \"id1\"/>\n" +
        "  <elementwithidrefattr-2 anIdRef = \"id2\"/> \n" +
        " </IDS>";

    /**
     * Add attribute to element which already has an attribute of that name.
     */
    @Test (expected=XMLDBException.class)
    public void appendStoredAttrFail() throws XMLDBException {
        XQueryService xqs = (XQueryService) testCollection.getService("XQueryService", "1.0");
        String query =
            "let $a := \n" +
            "<node attr=\"a\" b=\"c\">{doc(\"/db/test/stored1.xml\")//@attr}</node>" +
            "return $a";
        xqs.query(query);
    }

    /**
     * Add attribute to element which has no conflicting attributes.
     */
    @Test
    public void appendStoredAttrOK() {
        try {
            XQueryService xqs = (XQueryService) testCollection.getService("XQueryService", "1.0");
            String query =
                "let $a := \n" +
                "<node attr=\"a\" b=\"c\">{doc(\"/db/test/stored2.xml\")//@attr2}</node>" +
                "return $a";
            ResourceSet result = xqs.query(query);
            assertEquals(1, result.getSize());
            assertEquals("<node attr=\"a\" b=\"c\" attr2=\"ab\"/>", result.getResource(0).getContent());
        } catch (XMLDBException e) {
            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    /**
     * Add constructed in-memory attribute to element which already has an
     * attribute of that name.
     */
    @Test (expected=XMLDBException.class)
    public void appendConstrAttr() throws XMLDBException {
        XQueryService xqs = (XQueryService) testCollection.getService("XQueryService", "1.0");
        String query =
            "let $a := <root attr=\"ab\"/>" +
            "let $b := \n" +
            "   <node attr=\"a\" b=\"c\">{$a//@attr}</node>" +
            "return $a";
        xqs.query(query);
    }

    /**
     * Add attribute to element which already has an
     * attribute of that name (using idref).
     */
    @Test (expected=XMLDBException.class)
    public void appendIdref() throws XMLDBException {
        XQueryService xqs = (XQueryService) testCollection.getService("XQueryService", "1.0");
        String query =
            "<results>{fn:idref(('id1', 'id2'), doc('/db/test/docdtd.xml')/IDS)}</results>";
        ResourceSet result = xqs.query(query);
        result.getResource(0).getContent();
    }

    @BeforeClass
    public static void setup() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService)
                existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection("test");
        assertNotNull(testCollection);

        Resource resource = testCollection.createResource("stored1.xml", "XMLResource");
        resource.setContent(STORED_DOC1);
        testCollection.storeResource(resource);

        resource = testCollection.createResource("stored2.xml", "XMLResource");
        resource.setContent(STORED_DOC2);
        testCollection.storeResource(resource);

        resource = testCollection.createResource("docdtd.xml", "XMLResource");
        resource.setContent(DOC_WITH_DTD);
        testCollection.storeResource(resource);
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService)
            existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        service.removeCollection("test");
    }
}