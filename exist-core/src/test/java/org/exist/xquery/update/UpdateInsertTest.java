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

import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import java.util.UUID;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class UpdateInsertTest extends AbstractTestUpdate {

    private static final String EOL = System.lineSeparator();

    @Test
    public void insertNamespacedAttribute() throws XMLDBException {
        final String docName = "pathNs2.xml";
        final XQueryService service =
            storeXMLStringAndGetQueryService(docName, "<test/>");

        queryResource(service, docName, "//t[@xml:id]", 0);

        String update = "update insert <t xml:id=\"id1\"/> into /test";
        queryResource(service, docName, update, 0);

        queryResource(service, docName, "//t[@xml:id eq 'id1']", 1);
        queryResource(service, docName, "/test/id('id1')", 1);

        update = "update value //t/@xml:id with 'id2'";
        queryResource(service, docName, update, 0);

        queryResource(service, docName, "//t[@xml:id eq 'id2']", 1);
        queryResource(service, docName, "id('id2', /test)", 1);
    }

    @Test
    public void insertPrecedingAttribute() throws XMLDBException {
        final String tempId = "tmp-1512257166656";
        final String doc =
        "<annotation-list>" + EOL +
        "    <annotation-item generator=\"earlyPrint\" status=\"pending\" visibility=\"public\" temp-id=\"" + tempId + "\" reason=\"\" creator=\"craig\" created=\"2017-12-02T23:26:06.656Z\" modified=\"2017-12-02T23:26:06.656Z\" generated=\"2017-12-02T23:26:06.656Z\" ticket=\"s-1512257166639\">" + EOL +
        "        <annotation-body subtype=\"update\" format=\"text/xml\" type=\"TEI\" original-value=\"Worthies\">" + EOL +
        "            <w>test123</w>" + EOL +
        "        </annotation-body>" + EOL +
        "        <annotation-target source=\"A00969\" version=\"\">" + EOL +
        "            <target-selector type=\"IdSelector\" value=\"A00969-001-b-0240\"/>" + EOL +
        "        </annotation-target>" + EOL +
        "    </annotation-item>" + EOL +
        "</annotation-list>";

        final String docName = "A00969_annotations.xml";
        final XQueryService service =
                storeXMLStringAndGetQueryService(docName, doc);

        queryResource(service, docName, "//annotation-item[@temp-id = '" + tempId + "']/@status", 1);
        queryResource(service, docName, "//annotation-item[@temp-id = '" + tempId + "']/@id", 0);

        final String uuid = UUID.randomUUID().toString();

        final String update = "update insert attribute id {'" + uuid + "'} preceding //annotation-item[@temp-id = '" + tempId + "']/@status";
        queryResource(service, docName, update, 0);

        queryResource(service, docName, "//annotation-item[@temp-id = '" + tempId + "']/@id", 1);
    }

    @Test
    public void insertInMemoryDocument() throws XMLDBException {
        final String doc = "<empty/>";

        final String docName = "empty.xml";
        final XQueryService service =
                storeXMLStringAndGetQueryService(docName, doc);

        queryResource(service, docName, "//empty/child::node()", 0);

        final String uuid = UUID.randomUUID().toString();

        final String update = "update insert document { <uuid>" + uuid + "</uuid> } into /empty";

        queryResource(service, docName, update, 0);

        queryResource(service, docName, "//empty/uuid", 1);
    }
}
