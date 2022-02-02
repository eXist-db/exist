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

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class VariablesTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String MODULE =
            "module namespace mod1 = \"http://mod1\";\n" +
            "\n" +
            "declare variable $mod1:OPEN_GRAPH as map(xs:string, function(*)) := map {\n" +
            "    \"og:title\" : function($node, $model) {\n" +
            "    <meta property=\"og:title\" content=\"{map:get($mod1:PUBLICATIONS, 'some-id')}\"/>\n" +
            "    }\n" +
            "};\n" +
            "\n" +
            "declare variable $mod1:PUBLICATIONS := map {\n" +
            "  \"open-graph\" : map:merge((\n" +
            "    $mod1:OPEN_GRAPH,\n" +
            "    map {\n" +
            "      \"og:image\" : function($node, $model) {\n" +
            "        <meta property=\"og:image\" content=\"https://some/uri/some/image.png\"/>\n" +
            "      }\n" +
            "    }\n" +
            "  ))\n" +
            "};";

    @BeforeClass
    public static void setup() throws XMLDBException {
        final Collection c = createCollection("variables-test");
        writeModule(c, "mod1.xqm", MODULE);
    }

    @Test
    public void callModule() throws XMLDBException {
        final String query =
                "import module namespace mod1 = \"http://mod1\" at \"xmldb:exist:///db/variables-test/mod1.xqm\";\n" +
                "$mod1:PUBLICATIONS(\"open-graph\")";

        final ResourceSet rs = existEmbeddedServer.executeQuery(query);
        assertEquals(1, rs.getSize());
        assertEquals(XMLResource.RESOURCE_TYPE, rs.getResource(0).getResourceType());
    }

    private static Collection createCollection(final String collectionName) throws XMLDBException {
        Collection collection = existEmbeddedServer.getRoot().getChildCollection(collectionName);
        final CollectionManagementService cmService = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        if (collection == null) {
            //cmService.removeCollection(collectionName);
            cmService.createCollection(collectionName);
        }

        collection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + collectionName, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        assertNotNull(collection);
        return collection;
    }

    private static void writeModule(final Collection collection, final String modulename, final String module) throws XMLDBException {
        final BinaryResource res = (BinaryResource) collection.createResource(modulename, "BinaryResource");
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(module.getBytes());
        collection.storeResource(res);
        collection.close();
    }
}
