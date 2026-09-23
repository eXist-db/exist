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
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * The four regular-expression functions share one engine, Saxon's native one, and therefore one
 * set of XPath 3.1 semantics.
 *
 * <p>Before this, {@code fn:tokenize} ran on {@code java.util.regex} and the other three fell back
 * to it whenever Saxon rejected a pattern. The two engines disagree on ordinary constructs, and
 * the fallback turned FORX0002 into {@code false()}. The cases here are the ones that used to
 * come out differently, plus the flag syntax: XPath's {@code smixq}, and Saxon's {@code ;j} to
 * opt into Java's engine explicitly, with nothing else admitted after the semicolon.</p>
 */
public class RegexEngineConsistencyTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private String eval(final String xquery) throws XMLDBException {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query(
                "try { let $r := (" + xquery + ") return string-join(for $i in $r return string($i), '|') } catch * { string($err:code) }");
        return rs.getSize() == 0 ? "" : rs.getResource(0).getContent().toString();
    }

    // ---- XPath 3.1 semantics where java.util.regex differs ----

    /**
     * XPath's \s is #x20, \t, \n and \r. Java's also includes form feed and vertical tab -- but
     * neither of those is an XML 1.0 character, so that divergence cannot be reached from a query
     * in eXist. What can be checked is that the four XML whitespace characters all match.
     */
    @Test
    public void whitespaceClassMatchesXmlWhitespace() throws XMLDBException {
        for (final int cp : new int[] {32, 9, 10, 13}) {
            assertEquals("codepoint " + cp, "true",
                    eval("matches(concat('a', codepoints-to-string(" + cp + "), 'b'), '^a\\sb$')"));
        }
    }

    @Test
    public void digitClassIsUnicode() throws XMLDBException {
        assertEquals("true", eval("matches(codepoints-to-string(1635), '^\\d$')"));
    }

    @Test
    public void wordClassIsUnicodeAndExcludesUnderscore() throws XMLDBException {
        assertEquals("false", eval("matches('_', '^\\w$')"));
        assertEquals("true", eval("matches(codepoints-to-string(1078), '^\\w$')"));
    }

    @Test
    public void dollarDoesNotMatchBeforeATrailingNewline() throws XMLDBException {
        assertEquals("false", eval("matches(concat('abc', codepoints-to-string(10)), 'abc$')"));
    }

    @Test
    public void dotDoesNotMatchCarriageReturn() throws XMLDBException {
        assertEquals("false", eval("matches(concat('a', codepoints-to-string(13), 'b'), 'a.b')"));
    }

    @Test
    public void characterClassSubtraction() throws XMLDBException {
        // Java has no subtraction syntax and reads this as a union, matching everything
        assertEquals("false", eval("matches('e', '^[a-z-[aeiou]]$')"));
        assertEquals("true", eval("matches('b', '^[a-z-[aeiou]]$')"));
    }

    @Test
    public void xmlNameEscapesAndBlockEscapes() throws XMLDBException {
        assertEquals("false", eval("matches('1', '^\\i$')"));
        assertEquals("true", eval("matches('-', '^\\c$')"));
        assertEquals("true", eval("matches('a', '^\\p{IsBasicLatin}$')"));
    }

    @Test
    public void caseInsensitiveFinalSigma() throws XMLDBException {
        assertEquals("true", eval("matches(codepoints-to-string(931), codepoints-to-string(962), 'i')"));
    }

    // ---- invalid patterns raise FORX0002 instead of answering false ----

    @Test
    public void invalidPatternsAreErrorsNotFalse() throws XMLDBException {
        // XQTS re00019, re00804, fn-matchesErr-4, fn-matchesErr-5
        assertEquals("err:FORX0002", eval("matches('qwerty', '{1}a')"));
        assertEquals("err:FORX0002", eval("matches('qwerty', 'a]')"));
        assertEquals("err:FORX0002", eval("matches('#abc#1', '^((#)abc\\1)$')"));
        assertEquals("err:FORX0002", eval("matches('abcdefghijklmnopq', '(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)(l)((m)(n)(o)(p)(q)\\13)$')"));
    }

    @Test
    public void javaOnlySyntaxIsAnErrorWithoutOptingIn() throws XMLDBException {
        assertEquals("err:FORX0002", eval("matches('a foo b', '\\bfoo\\b')"));
        assertEquals("err:FORX0002", eval("replace('a foo b', '\\bfoo\\b', 'X')"));
        assertEquals("err:FORX0002", eval("tokenize('a foo b', '\\bfoo\\b')"));
        assertEquals("err:FORX0002", eval("analyze-string('a foo b', '\\bfoo\\b')"));
    }

    // ---- flags: XPath's set, plus exactly ;j ----

    @Test
    public void semicolonJSelectsJavaSyntaxEverywhere() throws XMLDBException {
        assertEquals("true", eval("matches('a foo b', '\\bfoo\\b', ';j')"));
        assertEquals("a X b", eval("replace('a foo b', '\\bfoo\\b', 'X', ';j')"));
        assertEquals("a | b", eval("tokenize('a foo b', '\\bfoo\\b', ';j')"));
        assertEquals("true", eval("matches('a FOO b', '\\bfoo\\b', 'i;j')"));
        assertEquals("1", eval("count(analyze-string('a foo b', '\\bfoo\\b', ';j')//*:match)"));
    }

    @Test
    public void aBareJIsNotAFlag() throws XMLDBException {
        assertEquals("err:FORX0001", eval("matches('a', 'a', 'j')"));
    }

    @Test
    public void onlyJIsAdmittedAfterTheSemicolon() throws XMLDBException {
        assertEquals("err:FORX0001", eval("matches('a', 'a', ';x')"));
        assertEquals("err:FORX0001", eval("matches('a', 'a', ';jz')"));
        assertEquals("err:FORX0001", eval("matches('a', 'a', ';J')"));
        assertEquals("err:FORX0001", eval("matches('a', 'a', ';')"));
        assertEquals("err:FORX0001", eval("tokenize('a b', ' ', ';n')"));
    }

    @Test
    public void unknownXPathFlagsAreStillErrors() throws XMLDBException {
        assertEquals("err:FORX0001", eval("matches('a', 'a', 'p')"));
        assertEquals("err:FORX0001", eval("matches('a', 'a', 'X')"));
    }

    // ---- fn:tokenize on the shared engine ----

    @Test
    public void tokenizePatternMatchingEmptyIsAnError() throws XMLDBException {
        // XQTS fn-tokenize-36 and -38
        assertEquals("err:FORX0003", eval("tokenize(concat('Mary', codepoints-to-string(10), 'Jones'), '^', 'm')"));
        assertEquals("err:FORX0003", eval("tokenize(concat('Mary', codepoints-to-string(10), 'Jones'), '^[\\s]*$', 'm')"));
    }

    @Test
    public void tokenizeOfWhitespaceOnlyIsEmpty() throws XMLDBException {
        // XQTS fn-tokenize-41 and -42
        assertEquals("0", eval("count(tokenize('   '))"));
        assertEquals("0", eval("count(tokenize(codepoints-to-string((9, 10, 13, 32, 13, 10, 9))))"));
    }

    @Test
    public void tokenizeKeepsEmptyTokensBetweenAndAfterSeparators() throws XMLDBException {
        assertEquals("a||b", eval("tokenize('a,,b', ',')"));
        assertEquals("a|b|", eval("tokenize('a,b,', ',')"));
        assertEquals("0", eval("count(tokenize('', ','))"));
        assertEquals("a|b", eval("tokenize(' a  b ')"));
    }

    // ---- one engine, one answer ----

    /** Class subtraction: Java reads the class as a union and would match the vowel. */
    @Test
    public void allFourFunctionsAgreeOnADivergentConstruct() throws XMLDBException {
        assertEquals("false", eval("matches('e', '^[a-z-[aeiou]]$')"));
        assertEquals("e", eval("replace('e', '^[a-z-[aeiou]]$', 'X')"));
        assertEquals("1", eval("count(tokenize('e', '[a-z-[aeiou]]'))"));
        assertEquals("0", eval("count(analyze-string('e', '[a-z-[aeiou]]')//*:match)"));
    }
}
