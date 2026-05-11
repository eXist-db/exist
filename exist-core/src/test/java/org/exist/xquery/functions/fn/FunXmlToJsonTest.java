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
package org.exist.xquery.functions.fn;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for {@link FunXmlToJson} when the input is an element node
 * nested inside a larger host document.
 *
 * Before the fix, fn:xml-to-json constructed its XMLStreamReader from the owner
 * document root, so traversal visited ancestor elements (e.g. xsl:stylesheet)
 * and raised FOJS0006 against those rather than the JSON wrapper element.
 */
public class FunXmlToJsonTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer SERVER = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void elementSelectedFromHostDocument() throws Exception {
        final String query = """
                let $host :=
                    <stylesheet xmlns="http://www.w3.org/1999/XSL/Transform">
                        <template name="t301">
                            <variable name="in"><null xmlns="http://www.w3.org/2005/xpath-functions"/></variable>
                        </template>
                    </stylesheet>
                return fn:xml-to-json($host//*:template[@name="t301"]/*:variable/*)
                """;
        assertEquals("null", runScalar(query));
    }

    @Test
    public void mapSelectedFromHostDocument() throws Exception {
        final String query = """
                let $host :=
                    <wrapper>
                        <slot>
                            <map xmlns="http://www.w3.org/2005/xpath-functions">
                                <string key="a">1</string>
                            </map>
                        </slot>
                    </wrapper>
                return fn:xml-to-json($host//*:map)
                """;
        assertEquals("{\"a\":\"1\"}", runScalar(query));
    }

    @Test
    public void stringSelectedFromHostDocument() throws Exception {
        final String query = """
                let $host :=
                    <wrapper>
                        <slot>
                            <string xmlns="http://www.w3.org/2005/xpath-functions">hi</string>
                        </slot>
                    </wrapper>
                return fn:xml-to-json($host//*:string)
                """;
        assertEquals("\"hi\"", runScalar(query));
    }

    private static String runScalar(final String query) throws Exception {
        final XQueryService xq = SERVER.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xq.query(query);
        assertEquals(1, rs.getSize());
        return rs.getResource(0).getContent().toString();
    }
}
