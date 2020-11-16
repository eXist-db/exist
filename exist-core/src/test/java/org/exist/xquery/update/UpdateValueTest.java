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


/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class UpdateValueTest extends AbstractTestUpdate {

    @Test
    public void updateNamespacedAttribute() throws XMLDBException {
        final String docName = "pathNs.xml";
        final XQueryService service =
            storeXMLStringAndGetQueryService(docName, "<test><t xml:id=\"id1\"/></test>");

        queryResource(service, docName, "//t[@xml:id eq 'id1']", 1);

        queryResource(service, docName, "update value //t/@xml:id with 'id2'", 0);

        queryResource(service, docName, "//t[@xml:id eq 'id2']", 1);
        queryResource(service, docName, "id('id2', /test)", 1);
    }

    @Test
    public void updateAttributeInNamespacedElement() throws XMLDBException {
        final String docName = "docNs.xml";
        final XQueryService service =
            storeXMLStringAndGetQueryService(docName, "<test xmlns=\"http://test.com\" id=\"id1\"/>");

        queryResource(service, docName, "declare namespace t=\"http://test.com\"; update value /t:test/@id with " +
                "'id2'", 0);
        queryResource(service, docName, "declare namespace t=\"http://test.com\"; /t:test[@id = 'id2']", 1);
    }
}
