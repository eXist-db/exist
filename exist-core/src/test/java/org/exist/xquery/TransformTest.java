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
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class TransformTest {
    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String TEST_COLLECTION_NAME = "transform-test";

    private Collection testCollection;
    
    /**
     * Tests relative path resolution when parsing stylesheets in
     * the transform:transform function.
     */
    @Test
    public void transform() throws XMLDBException {
        String query =
            "import module namespace transform='http://exist-db.org/xquery/transform';\n" +
            "let $xml := <empty/>\n" +
            "let $xsl := 'xmldb:exist:///db/"+TEST_COLLECTION_NAME+"/xsl1/1.xsl'\n" +
            "return transform:transform($xml, $xsl, ())";
        String result = execQuery(query);
        assertEquals("<doc>" +
                "<p>Start Template 1</p>" +
                "<p>Start Template 2</p>" +
                "<p>Template 3</p>" +
                "<p>End Template 2</p>" +
                "<p>Template 3</p>" +
                "<p>End Template 1</p>" +
                "</doc>", result);
    }
    
    
    private String execQuery(String query) throws XMLDBException {
    	XQueryService service = testCollection.getService(XQueryService.class);
        service.setProperty("indent", "no");
    	ResourceSet result = service.query(query);
    	assertEquals(result.getSize(), 1);
    	return result.getResource(0).getContent().toString();
    }
    
    private void addXMLDocument(Collection c, String doc, String id) throws XMLDBException {
    	Resource r = c.createResource(id, XMLResource.class);
    	r.setContent(doc);
    	((EXistResource) r).setMimeType("application/xml");
    	c.storeResource(r);
    }

    @Before
    public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        CollectionManagementService service =
                existEmbeddedServer.getRoot().getService(
                    CollectionManagementService.class);
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);

        service =
                testCollection.getService(
                    CollectionManagementService.class);

        Collection xsl1 = service.createCollection("xsl1");
        assertNotNull(xsl1);

        Collection xsl3 = service.createCollection("xsl3");
        assertNotNull(xsl3);

        service =
                xsl1.getService(
                    CollectionManagementService.class);

        Collection xsl2 = service.createCollection("xsl2");
        assertNotNull(xsl2);


        String	doc1 = "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>\n"+
        "<xsl:import href='xsl2/2.xsl' />\n" +
        "<xsl:template match='/'>\n" +
        "<doc>" +
        "<p>Start Template 1</p>" +
        "<xsl:call-template name='template-2' />" +
        "<xsl:call-template name='template-3' />" +
        "<p>End Template 1</p>" +
        "</doc>" +
        "</xsl:template>" +
        "</xsl:stylesheet>";

        String doc2 = "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>\n"+
        "<xsl:import href='../../xsl3/3.xsl' />\n" +
        "<xsl:template name='template-2'>\n" +
        "<p>Start Template 2</p>" +
        "<xsl:call-template name='template-3' />" +
        "<p>End Template 2</p>" +
        "</xsl:template>" +
        "</xsl:stylesheet>";

        String	doc3 = "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>\n"+
        "<xsl:template name='template-3'>\n" +
        "<p>Template 3</p>" +
        "</xsl:template>" +
        "</xsl:stylesheet>";

        addXMLDocument(xsl1, doc1, "1.xsl");
        addXMLDocument(xsl2, doc2, "2.xsl");
        addXMLDocument(xsl3, doc3, "3.xsl");
    }

    @After
    public void tearDown() throws XMLDBException {
        Collection root =
            DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        CollectionManagementService service =
                root.getService(
                    CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }
}
