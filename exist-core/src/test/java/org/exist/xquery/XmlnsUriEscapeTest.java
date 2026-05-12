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

import com.evolvedbinary.j8fu.Either;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.XQueryCompilationTest;
import org.exist.xquery.value.Sequence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests XQuery 3.1 attribute-value normalization on namespace declaration
 * attribute values (xmlns and xmlns:prefix).
 *
 * Per XQuery 3.1 §3.9.1.2 (Namespace Declaration Attributes), namespace
 * declaration attribute values are processed by rule 1 of §3.9.1.1:
 *   - {{ -> {
 *   - }} -> }
 *   - EscapeQuot ("") -> "
 *   - EscapeApos ('') -> '
 *   - predefined entity refs and char refs are expanded
 *
 * Covers XQTS prod-DirElemContent.namespace cluster C:
 * DirectConElemNamespace-3, -4, -5, -6,
 * K2-DirectConElemNamespace-59, -65, -75.
 */
public class XmlnsUriEscapeTest extends XQueryCompilationTest {

    private static String stringResult(final Either<XPathException, Sequence> r) throws XPathException {
        assertTrue("query returned an error: " + (r.isLeft() ? r.left().get().getMessage() : ""), r.isRight());
        return r.right().get().getStringValue();
    }

    @Test
    public void escapeQuotInPrefixedXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // K2 DirectConElemNamespace-4 shape: "" -> "
        final String query = "namespace-uri(<p:e xmlns:p=\"http://ns.example.com/ns?val=\"\"asd\"/>)";
        assertEquals("http://ns.example.com/ns?val=\"asd", stringResult(executeQuery(query)));
    }

    @Test
    public void multipleEscapeQuotInPrefixedXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // DirectConElemNamespace-3 shape: """""" -> """
        final String query = "namespace-uri(<p:e xmlns:p=\"http://ns.example.com/ns?val=\"\"\"\"\"\"asd\"/>)";
        assertEquals("http://ns.example.com/ns?val=\"\"\"asd", stringResult(executeQuery(query)));
    }

    @Test
    public void escapeAposInPrefixedXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // DirectConElemNamespace-5 shape: '''''' -> '''
        final String query = "namespace-uri(<p:e xmlns:p='http://ns.example.com/ns?val=''''''asd'/>)";
        assertEquals("http://ns.example.com/ns?val='''asd", stringResult(executeQuery(query)));
    }

    @Test
    public void escapeAposInDefaultXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // DirectConElemNamespace-6 shape: '' -> ' (default xmlns)
        final String query = "namespace-uri(<e xmlns='http://ns.example.com/ns?val=''asd'/>)";
        assertEquals("http://ns.example.com/ns?val='asd", stringResult(executeQuery(query)));
    }

    @Test
    public void doubleBracesInPrefixedXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // K2-DirectConElemNamespace-59 shape: {{{{{{}}}}}} -> {{{}}}
        final String query = "namespace-uri(<p:e xmlns:p=\"http://example.com/{{{{{{}}}}}}asd\"/>)";
        assertEquals("http://example.com/{{{}}}asd", stringResult(executeQuery(query)));
    }

    @Test
    public void mixedDoubleBracesInPrefixedXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // K2-DirectConElemNamespace-65 shape: {{}}{{{{}}}} -> {}{{}}
        final String query = "namespace-uri-for-prefix(\"p\", <e xmlns:p=\"http://example.com/{{}}{{{{}}}}\"/>)";
        assertEquals("http://example.com/{}{{}}", stringResult(executeQuery(query)));
    }

    @Test
    public void doubleBracesInDefaultXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // K2-DirectConElemNamespace-75 shape: {{1}} -> {1}
        final String query = "namespace-uri(<e xmlns=\"http://example.com/{{1}}\"/>)";
        assertEquals("http://example.com/{1}", stringResult(executeQuery(query)));
    }

    @Test
    public void predefinedEntityRefInXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // &quot; entity ref expansion in xmlns URI
        final String query = "namespace-uri(<p:e xmlns:p=\"http://example.com/?val=&quot;asd\"/>)";
        assertEquals("http://example.com/?val=\"asd", stringResult(executeQuery(query)));
    }

    @Test
    public void ampersandEntityRefInXmlnsUri() throws EXistException, PermissionDeniedException, XPathException {
        // &amp; entity ref expansion in xmlns URI
        final String query = "namespace-uri(<p:e xmlns:p=\"http://example.com/?a=1&amp;b=2\"/>)";
        assertEquals("http://example.com/?a=1&b=2", stringResult(executeQuery(query)));
    }
}
