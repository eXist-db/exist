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
package org.exist.xquery.update;

import org.exist.xmldb.EXistResource;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;

import static org.junit.Assert.*;

public class UpdateReplaceTest extends AbstractTestUpdate {

    @Test
    public void replaceOnlyChildWhereParentHasNoAttributes() throws XMLDBException {
        final String testDocName = "replaceOnlyChildWhereParentHasNoAttributes.xml";
        final String testDoc = "<Test><Content><A/></Content></Test>";

        final String updateQuery =
                "let $content := doc('/db/test/" + testDocName + "')/Test/Content\n" +
                        "    let $legacy := $content/A\n" +
                        "    return\n" +
                        "      update replace $legacy with <AA/>,\n" +
                        "    doc('/db/test/" + testDocName + "')/Test";

        final XQueryService xqueryService = storeXMLStringAndGetQueryService(testDocName, testDoc);
        final ResourceSet result = xqueryService.query(updateQuery);
        assertNotNull(result);
        assertEquals(1, result.getSize());

        final Resource res1 = result.getResource(0);
        assertNotNull(res1);
        assertEquals(XMLResource.RESOURCE_TYPE, res1.getResourceType());
        final Document doc = ((XMLResource) res1).getContentAsDOM().getOwnerDocument();

        final Source actual = Input.fromDocument(doc).build();
        final Source expected = Input.fromString("<Test><Content><AA/></Content></Test>").build();

        final Diff diff = DiffBuilder
                .compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void replaceFirstChildWhereParentHasNoAttributes() throws XMLDBException {
        final String testDocName = "replaceFirstChildWhereParentHasNoAttributes.xml";
        final String testDoc = "<Test><Content><A/><A/></Content></Test>";

        final String updateQuery =
                "let $content := doc('/db/test/" + testDocName + "')/Test/Content\n" +
                        "    let $legacy := $content/A[1]\n" +
                        "    return\n" +
                        "      update replace $legacy with <AA/>,\n" +
                        "    doc('/db/test/" + testDocName + "')/Test";

        final XQueryService xqueryService = storeXMLStringAndGetQueryService(testDocName, testDoc);
        final ResourceSet result = xqueryService.query(updateQuery);
        assertNotNull(result);
        assertEquals(1, result.getSize());

        final Resource res1 = result.getResource(0);
        assertNotNull(res1);
        assertEquals(XMLResource.RESOURCE_TYPE, res1.getResourceType());
        final Document doc = ((XMLResource) res1).getContentAsDOM().getOwnerDocument();

        final Source actual = Input.fromDocument(doc).build();
        final Source expected = Input.fromString("<Test><Content><AA/><A/></Content></Test>").build();

        final Diff diff = DiffBuilder
                .compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void replaceOnlyChildWhereParentHasAttribute() throws XMLDBException {
        final String testDocName = "replaceOnlyChildWhereParentHasAttribute.xml";
        final String testDoc = "<Test><Content Foo=\"bar\"><A/></Content></Test>";

        final String updateQuery =
                "let $content := doc('/db/test/" + testDocName + "')/Test/Content\n" +
                "    let $legacy := $content/A\n" +
                "    return\n" +
                "      update replace $legacy with <AA/>,\n" +
                "    doc('/db/test/" + testDocName + "')/Test";

        final XQueryService xqueryService = storeXMLStringAndGetQueryService(testDocName, testDoc);
        final ResourceSet result = xqueryService.query(updateQuery);
        assertNotNull(result);
        assertEquals(1, result.getSize());

        final Resource res1 = result.getResource(0);
        assertNotNull(res1);
        assertEquals(XMLResource.RESOURCE_TYPE, res1.getResourceType());
        final Document doc = ((XMLResource) res1).getContentAsDOM().getOwnerDocument();

        final Source actual = Input.fromDocument(doc).build();
        final Source expected = Input.fromString("<Test><Content Foo='bar'><AA/></Content></Test>").build();

        final Diff diff = DiffBuilder
                .compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void replaceFirstChildWhereParentHasAttribute() throws XMLDBException {
        final String testDocName = "replaceFirstChildWhereParentHasAttribute.xml";
        final String testDoc = "<Test><Content Foo=\"bar\"><A/><A/></Content></Test>";

        final String updateQuery =
                "let $content := doc('/db/test/" + testDocName + "')/Test/Content\n" +
                        "    let $legacy := $content/A[1]\n" +
                        "    return\n" +
                        "      update replace $legacy with <AA/>,\n" +
                        "    doc('/db/test/" + testDocName + "')/Test";

        final XQueryService xqueryService = storeXMLStringAndGetQueryService(testDocName, testDoc);
        final ResourceSet result = xqueryService.query(updateQuery);
        assertNotNull(result);
        assertEquals(1, result.getSize());

        final Resource res1 = result.getResource(0);
        assertNotNull(res1);
        assertEquals(XMLResource.RESOURCE_TYPE, res1.getResourceType());
        final Document doc = ((XMLResource) res1).getContentAsDOM().getOwnerDocument();

        final Source actual = Input.fromDocument(doc).build();
        final Source expected = Input.fromString("<Test><Content Foo='bar'><AA/><A/></Content></Test>").build();

        final Diff diff = DiffBuilder
                .compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }
}
