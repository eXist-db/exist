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
public class URIUtilsTest {

    /**
     * Unreserved Characters from <a href="https://www.ietf.org/rfc/rfc3986.html#section-2.3">RFC 3986 Section 2.3</a>.
     */
    @Test
    public void encodeForURIPathComponentUnreserved() {
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
    public void encodeForURIPathComponentGeneralDelimiter() {
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
    public void encodeForURIPathComponentSubDelimiter() {
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
    public void encodeForURIPathComponent() {
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
    public void encodeForURIPathComponentUtf8() {
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
}
