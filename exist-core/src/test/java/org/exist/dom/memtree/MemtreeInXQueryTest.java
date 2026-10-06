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
package org.exist.dom.memtree;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@Execution(ExecutionMode.CONCURRENT)
public class MemtreeInXQueryTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    @org.junit.jupiter.api.Test
    void pi_attributes() throws XMLDBException {
        final String xquery = """
                let $doc := document{
                    processing-instruction{"ok"}{"ok"},
                    <root/>
                }
                return count($doc//processing-instruction()/@*)""";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @org.junit.jupiter.api.Test
    void pi_children() throws XMLDBException {
        final String xquery = """
                let $doc := document{
                    processing-instruction{"ok"}{"ok"},
                    <root/>
                }
                return count($doc//processing-instruction()/node())""";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @org.junit.jupiter.api.Test
    void pi_descendantAttributes() throws XMLDBException {
        final String xquery = """
                let $doc := document{
                    processing-instruction{"ok"}{"ok"},
                    <root/>
                }
                return count($doc//processing-instruction()//@*)""";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @org.junit.jupiter.api.Test
    void attr_attributes() throws XMLDBException {
        final String xquery = """
                let $doc := document {
                    element a {
                        attribute x { "y" }
                    }
                } return
                    count($doc/a/@x/@y)""";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @org.junit.jupiter.api.Test
    void attr_children() throws XMLDBException {
        final String xquery = """
                let $doc := document {
                    element a {
                        attribute x { "y" }
                    }
                } return
                    count($doc/a/@x/node())""";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }
}
