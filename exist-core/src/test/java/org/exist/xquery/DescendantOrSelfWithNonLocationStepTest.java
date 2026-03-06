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
 * Tests for // (descendant-or-self) with non-LocationStep expressions.
 *
 * The XPath abbreviation // expands to /descendant-or-self::node()/. When
 * the right-hand side is a LocationStep, the tree walker can merge the axis.
 * When it is a non-LocationStep expression (parenthesized expression, variable
 * reference, filtered expression), we must insert an explicit
 * descendant-or-self::node() step instead.
 */
public class DescendantOrSelfWithNonLocationStepTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private ResourceSet execute(final String xquery) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        return xqs.query(xquery);
    }

    @Test
    public void parenthesizedAttribute() throws XMLDBException {
        // //(@x) should find all @x attributes at any depth
        final ResourceSet result = execute(
                "let $doc := <root x='1'><a x='2'><b x='3'/></a></root>\n" +
                "return count($doc//(@x))");
        assertEquals("3", result.getResource(0).getContent().toString());
    }

    @Test
    public void parenthesizedAttributeUnion() throws XMLDBException {
        // //(@x | @y) should find all @x and @y attributes at any depth
        final ResourceSet result = execute(
                "let $doc := <root x='1' y='a'><a x='2'><b y='b'/></a></root>\n" +
                "return count($doc//(@x | @y))");
        assertEquals("4", result.getResource(0).getContent().toString());
    }

    @Test
    public void parenthesizedElementUnion() throws XMLDBException {
        // //(b | c) should find elements at any depth, including direct children
        final ResourceSet result = execute(
                "let $doc := <root><b/><a><c/><b/></a></root>\n" +
                "return count($doc//(b | c))");
        assertEquals("3", result.getResource(0).getContent().toString());
    }

    @Test
    public void parenthesizedUnionWithFollowingAxis() throws XMLDBException {
        // //(north | near-south)/preceding-sibling::comment() should work
        final ResourceSet result = execute(
                "let $doc :=\n" +
                "  <far-north>\n" +
                "    <!-- 1 -->\n" +
                "    <north mark='1'>\n" +
                "      <!-- 2 -->\n" +
                "      <near-north>\n" +
                "        <!-- 3 -->\n" +
                "        <center mark='0'/>\n" +
                "        <!-- 4 -->\n" +
                "        <near-south/>\n" +
                "      </near-north>\n" +
                "    </north>\n" +
                "  </far-north>\n" +
                "return count($doc//(north | near-south)/preceding-sibling::comment())");
        assertEquals("3", result.getResource(0).getContent().toString());
    }
}
