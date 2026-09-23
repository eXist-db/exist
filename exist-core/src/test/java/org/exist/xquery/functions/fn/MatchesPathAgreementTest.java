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
import org.exist.xmldb.IndexQueryService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * {@code fn:matches} has three evaluation paths -- a value, a predicate over stored nodes with no
 * index, and a predicate over a range-indexed element -- and they must give one answer.
 *
 * <p>They did not. The value path compiled with Saxon's native engine; the two node paths ran the
 * pattern through Saxon's Java translator into {@code java.util.regex}, whose reading of
 * {@code \d}, {@code \w}, {@code $}, character-class subtraction, {@code \i}/{@code \c} and block
 * escapes differs from XPath's. The cases here are constructs where the two engines disagree and
 * whose inputs are representable in XML 1.0 (form feed and carriage return are not, so the
 * {@code \s} and {@code .} divergences cannot be reached from stored content).</p>
 *
 * <p>Each case asserts all three paths against the XPath answer, not merely against each other, so
 * that "agreeing on the wrong result" cannot pass.</p>
 */
public class MatchesPathAgreementTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String COLLECTION_NAME = "matches-path-agreement";
    private static final String DOC = "/db/" + COLLECTION_NAME + "/data.xml";

    /** {@code indexed} has a string range index; {@code plain} does not. */
    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index><create qname="indexed" type="xs:string"/></index>
            </collection>
            """;

    /** Values chosen so each construct below has some matching and some non-matching entries. */
    private static final String[] VALUES = {
        "e", "b", "_", "ж", "٣", "7", "abc", "abc\n", "a", "-", "1x", "HAM 42 ophelia"
    };

    @BeforeClass
    public static void loadFixture() throws XMLDBException {
        final Collection root = server.getRoot();
        final Collection col = root.getService(CollectionManagementService.class).createCollection(COLLECTION_NAME);
        col.getService(IndexQueryService.class).configureCollection(COLLECTION_CONFIG);
        final StringBuilder sb = new StringBuilder("<data>");
        for (final String v : VALUES) {
            sb.append("<e><indexed>").append(v).append("</indexed><plain>").append(v).append("</plain></e>");
        }
        sb.append("</data>");
        final XMLResource res = col.createResource("data.xml", XMLResource.class);
        res.setContent(sb.toString());
        col.storeResource(res);
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        server.getRoot().getService(CollectionManagementService.class).removeCollection(COLLECTION_NAME);
    }

    private long count(final String query) throws XMLDBException {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query("declare option exist:optimize 'enable=yes'; " + query);
        return Long.parseLong(rs.getResource(0).getContent().toString());
    }

    /** Asserts the XPath answer on all three paths for one pattern. */
    private void assertAllPaths(final String pattern, final long expected) throws XMLDBException {
        final String p = "'" + pattern + "'";
        assertEquals("value path: " + pattern, expected,
                count("count(doc('" + DOC + "')//e/plain/string()[matches(., " + p + ")])"));
        assertEquals("node scan: " + pattern, expected,
                count("count(doc('" + DOC + "')//e[matches(plain, " + p + ")])"));
        assertEquals("index scan: " + pattern, expected,
                count("count(doc('" + DOC + "')//e[matches(indexed, " + p + ")])"));
    }

    /** Java reads [a-z-[aeiou]] as a union and would also match "e" and "a". */
    @Test
    public void characterClassSubtraction() throws XMLDBException {
        assertAllPaths("^[a-z-[aeiou]]$", 1);   // "b"
    }

    /** XPath's \\d is any Unicode decimal digit; Java's is ASCII. */
    @Test
    public void unicodeDigits() throws XMLDBException {
        assertAllPaths("^\\d$", 2);             // "٣", "7"
    }

    /** XPath's \\w excludes '_' and includes non-ASCII letters; Java's is the reverse. */
    @Test
    public void wordCharacters() throws XMLDBException {
        assertAllPaths("^\\w$", 6);             // e, b, ж, ٣, 7, a -- not _ or -, which are punctuation
    }

    /** Java lets $ match before a trailing newline; XPath does not. */
    @Test
    public void dollarBeforeTrailingNewline() throws XMLDBException {
        assertAllPaths("^abc$", 1);             // "abc" but not "abc\n"
    }

    /** \\i and \\c are XPath-only; the Java translator raised FORX0002 on the node paths. */
    @Test
    public void xmlNameEscapes() throws XMLDBException {
        // e, b, _, ж, a, and ٣: XML 1.0 5th edition NameStartChar includes [#x37F-#x1FFF], so an
        // Arabic-Indic digit is a legal name start. Not 7 or -.
        assertAllPaths("^\\i$", 6);
    }

    /** Block escapes are XPath-only; the Java translator raised FORX0002 on the node paths. */
    @Test
    public void blockEscapes() throws XMLDBException {
        assertAllPaths("^\\p{IsBasicLatin}+$", 10);  // everything but ж and ٣; LF and space are Basic Latin
    }
}
