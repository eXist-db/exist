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
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that XQuery keywords can be used as variable names (NCNames).
 *
 * The reservedKeywords grammar rule lists keywords that are also valid NCNames.
 * Keywords used in grammar rules but missing from this list cause XPST0003
 * parse errors when used as variable names.
 */
public class ReservedKeywordsAsNCNamesTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private ResourceSet execute(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        return xqs.query(xquery);
    }

    @Test
    public void ascendingDescendingAsVariableNames() throws XMLDBException {
        final ResourceSet result = execute(
                "let $ascending := 1, $descending := 2 return $ascending + $descending");
        assertEquals("3", result.getResource(0).getContent().toString());
    }

    @Test
    public void greatestLeastAsVariableNames() throws XMLDBException {
        final ResourceSet result = execute(
                "let $greatest := 10, $least := 1 return $greatest - $least");
        assertEquals("9", result.getResource(0).getContent().toString());
    }

    @Test
    public void satisfiesAsVariableName() throws XMLDBException {
        final ResourceSet result = execute(
                "let $satisfies := 'ok' return $satisfies");
        assertEquals("ok", result.getResource(0).getContent().toString());
    }

    @Test
    public void castableAsVariableName() throws XMLDBException {
        final ResourceSet result = execute(
                "let $castable := true() return $castable");
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @Test
    public void idivAsVariableName() throws XMLDBException {
        final ResourceSet result = execute(
                "let $idiv := 42 return $idiv");
        assertEquals("42", result.getResource(0).getContent().toString());
    }

    @Test
    public void processingInstructionAsVariableName() throws XMLDBException {
        final ResourceSet result = execute(
                "let $processing-instruction := 'test' return $processing-instruction");
        assertEquals("test", result.getResource(0).getContent().toString());
    }

    @Test
    public void schemaAttributeAsVariableName() throws XMLDBException {
        final ResourceSet result = execute(
                "let $schema-attribute := 'sa' return $schema-attribute");
        assertEquals("sa", result.getResource(0).getContent().toString());
    }

    @Test
    public void allowingAsVariableName() throws XMLDBException {
        final ResourceSet result = execute(
                "let $allowing := 'yes' return $allowing");
        assertEquals("yes", result.getResource(0).getContent().toString());
    }

    @Test
    public void allKeywordsTogether() throws XMLDBException {
        final ResourceSet result = execute(
                "let $ascending := 1, $descending := 2, $greatest := 3, $least := 4,\n" +
                "    $satisfies := 5, $castable := 6, $idiv := 7\n" +
                "return $ascending + $descending + $greatest + $least + $satisfies + $castable + $idiv");
        assertEquals("28", result.getResource(0).getContent().toString());
    }
}
