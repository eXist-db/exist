/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
class URIUtilsTest {

    /**
     * Unreserved Characters from <a href="https://www.ietf.org/rfc/rfc3986.html#section-2.3">RFC 3986 Section 2.3</a>.
     */
    @Test
    void encodeForURIPathComponentUnreserved() {
        // alpha
        String encoded = URIUtils.encodeForURI("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz", encoded);

        // digit
        encoded = URIUtils.encodeForURI("0123456789");
        assertEquals("0123456789", encoded);

        // hyphen
        encoded = URIUtils.encodeForURI("dash-case");
        assertEquals("dash-case", encoded);

        // full-stop
        encoded = URIUtils.encodeForURI("file.ext");
        assertEquals("file.ext", encoded);

        // underscore
        encoded = URIUtils.encodeForURI("snake_case");
        assertEquals("snake_case", encoded);

        // tilde
        encoded = URIUtils.encodeForURI("~home");
        assertEquals("~home", encoded);
    }

    /**
     * General Delimiters from <a href="https://www.ietf.org/rfc/rfc3986.html#section-2.2">RFC 3986 Section 2.2</a>.
     */
    @Test
    void encodeForURIPathComponentGeneralDelimiter() {
        // colon
        String encoded = URIUtils.encodeForURI("a:b");
        assertEquals("a%3Ab", encoded);

        // forward slash
        encoded = URIUtils.encodeForURI("x/y");
        assertEquals("x%2Fy", encoded);

        // question mark
        encoded = URIUtils.encodeForURI("Goodbye?");
        assertEquals("Goodbye%3F", encoded);

        // hash
        encoded = URIUtils.encodeForURI("#comment");
        assertEquals("%23comment", encoded);

        // opening square bracket
        encoded = URIUtils.encodeForURI("[predicate");
        assertEquals("%5Bpredicate", encoded);

        // closing square bracket
        encoded = URIUtils.encodeForURI("predicate]");
        assertEquals("predicate%5D", encoded);

        // at symbol
        encoded = URIUtils.encodeForURI("adam@work");
        assertEquals("adam%40work", encoded);
    }

    /**
     * Sub Delimiters from <a href="https://www.ietf.org/rfc/rfc3986.html#section-2.2">RFC 3986 Section 2.2</a>.
     */
    @Test
    void encodeForURIPathComponentSubDelimiter() {
        // exclamation mark
        String encoded = URIUtils.encodeForURI("Hello!");
        assertEquals("Hello%21", encoded);

        // dollar sign
        encoded = URIUtils.encodeForURI("$100");
        assertEquals("%24100", encoded);

        // ampersand
        encoded = URIUtils.encodeForURI("Jack&Jill");
        assertEquals("Jack%26Jill", encoded);

        // single quote
        encoded = URIUtils.encodeForURI("it's");
        assertEquals("it%27s", encoded);

        // opening bracket
        encoded = URIUtils.encodeForURI("(comment");
        assertEquals("%28comment", encoded);

        // closing bracket
        encoded = URIUtils.encodeForURI("comment)");
        assertEquals("comment%29", encoded);

        // asterisk
        encoded = URIUtils.encodeForURI("1*2");
        assertEquals("1%2A2", encoded);

        // plus sign
        encoded = URIUtils.encodeForURI("1+2");
        assertEquals("1%2B2", encoded);

        // comma
        encoded = URIUtils.encodeForURI("x,y");
        assertEquals("x%2Cy", encoded);

        // semi-colon
        encoded = URIUtils.encodeForURI("a;b");
        assertEquals("a%3Bb", encoded);

        // equals sign
        encoded = URIUtils.encodeForURI("n=1");
        assertEquals("n%3D1", encoded);
    }

    @Test
    void encodeForURIPathComponent() {
        // path
        String encoded = URIUtils.encodeForURI("/db/a/b/c");
        assertEquals("%2Fdb%2Fa%2Fb%2Fc", encoded);

        // space
        encoded = URIUtils.encodeForURI("hello world");
        assertEquals("hello%20world", encoded);

        // percent sign
        encoded = URIUtils.encodeForURI("99%");
        assertEquals("99%25", encoded);

        // percent sign
        encoded = URIUtils.encodeForURI("%2F");
        assertEquals("%252F", encoded);

        // double percent sign
        encoded = URIUtils.encodeForURI("99%%100");
        assertEquals("99%25%25100", encoded);
    }

    @Test
    void encodeForURIPathComponentUtf8() {
        // 2 byte character - yen sign
        String encoded = URIUtils.encodeForURI("¥");
        assertEquals("%C2%A5", encoded);

        // 3 byte character - samaritan letter tsasdiy
        encoded = URIUtils.encodeForURI("ࠑ");
        assertEquals("%E0%A0%91", encoded);

        // 4 byte character - phoenician letter het
        encoded = URIUtils.encodeForURI("\uD802\uDD07");
        assertEquals("%F0%90%A4%87", encoded);
    }

    /**
     * decodeForURI must treat '+' as a literal plus sign, not a space \u2014 the regression in
     * eXist-db/exist#1824 and #44, where URLDecoder's form-encoding rules turned '+' into ' '.
     */
    @Test
    void decodeForURIPlusIsLiteral() {
        // a percent-encoded plus decodes back to a plus
        assertEquals("1+2", URIUtils.decodeForURI("1%2B2"));

        // a bare '+' (nothing percent-encoded) is returned literally
        assertEquals("a+b", URIUtils.decodeForURI("a+b"));
    }

    @Test
    void decodeForURISpaceAndPercent() {
        // space
        assertEquals("hello world", URIUtils.decodeForURI("hello%20world"));

        // percent sign
        assertEquals("99%", URIUtils.decodeForURI("99%25"));

        // a literal "%2F" in a name encodes to "%252F" and must decode back to "%2F"
        assertEquals("%2F", URIUtils.decodeForURI("%252F"));

        // double percent sign
        assertEquals("99%%100", URIUtils.decodeForURI("99%25%25100"));
    }

    @Test
    void decodeForURIUnreservedUnchanged() {
        assertEquals("ABCabc019-._~", URIUtils.decodeForURI("ABCabc019-._~"));
    }

    @Test
    void decodeForURIUtf8() {
        // 2 byte character - yen sign
        assertEquals("\u00A5", URIUtils.decodeForURI("%C2%A5"));

        // 3 byte character - samaritan letter tsasdiy
        assertEquals("\u0811", URIUtils.decodeForURI("%E0%A0%91"));

        // 4 byte character - phoenician letter het
        assertEquals("\uD802\uDD07", URIUtils.decodeForURI("%F0%90%A4%87"));
    }

    /**
     * decodeForURI is the exact inverse of encodeForURI for any input \u2014 the bijective property
     * the xmldb URI functions rely on.
     */
    @Test
    void decodeForURIRoundTripsEncodeForURI() {
        final String[] names = {
                "plain", "dash-case", "file.ext", "snake_case", "~home",
                "hello world", "1+2", "99%", "%2F", "99%%100",
                "a:b", "x/y", "Goodbye?", "#comment", "[predicate", "predicate]", "adam@work",
                "Hello!", "$100", "Jack&Jill", "it's", "(comment", "comment)", "1*2", "x,y", "a;b", "n=1",
                "caf\u00E9", "\u041F\u0440\u0438\u0432\u0435\u0442", "\u6587\u66F8", "\u00A5", "\u0811", "\uD802\uDD07"
        };
        for (final String name : names) {
            assertEquals(name, URIUtils.decodeForURI(URIUtils.encodeForURI(name)),
                    "encode/decode round-trip failed for: " + name);
        }
    }

    /**
     * decodeForURI must never throw and never truncate, even on input that is not the output of
     * encodeForURI — xmldb:decode/xmldb:decode-uri accept arbitrary user strings. Each case here
     * is one that {@code new java.net.URI(s).getPath()} mishandles (throws URISyntaxException, or
     * silently drops everything from a '?' or '#' onward), which is why this is a standalone decoder.
     */
    @Test
    void decodeForURIRobustOnMalformedAndReservedInput() {
        // a lone '%' not followed by two hex digits is preserved verbatim (URI: throws)
        assertEquals("100%", URIUtils.decodeForURI("100%"));

        // a truncated escape is preserved verbatim (URI: throws)
        assertEquals("a%2", URIUtils.decodeForURI("a%2"));

        // a '%' followed by non-hex is preserved verbatim (URI: throws)
        assertEquals("a%ZZb", URIUtils.decodeForURI("a%ZZb"));

        // a literal space is left as-is (URI: throws on an unencoded space)
        assertEquals("a b", URIUtils.decodeForURI("a b"));

        // '?' and '#' are ordinary characters here, not query/fragment delimiters (URI: truncates to "a")
        assertEquals("a?b", URIUtils.decodeForURI("a?b"));
        assertEquals("a#b", URIUtils.decodeForURI("a#b"));

        // braces are ordinary characters (URI: throws)
        assertEquals("a{b}c", URIUtils.decodeForURI("a{b}c"));

        // valid escapes still decode even when mixed with characters URI would reject
        assertEquals("a b?c", URIUtils.decodeForURI("a%20b?c"));
    }
}
