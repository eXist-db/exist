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
package org.exist.http.servlets;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.easymock.Capture;
import org.junit.Test;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the exact transformation {@code HttpResponseWrapper} applies to header and cookie values,
 * so that behavior is verified rather than only understood from reading the source. Deliberately
 * does not assert on Jetty's actual wire behavior -- that requires a real HTTP round trip and is
 * covered separately (see {@code HttpResponseWrapperEncodingWireTest},
 * {@code NonAsciiCookieRoundTripTest}); this only confirms what the Java-level transformation
 * itself produces.
 * <p>
 * Headers and cookies use different schemes, and deliberately so: {@code encode()} (added
 * 2010-10-14, commit 67612ad4be, originally marked
 * {@code // TODO: remove this hack after fixing HTTP 1.1}) byte-repacks a header value's UTF-8
 * bytes into ISO-8859-1 chars, confirmed still necessary and correct for headers on Jetty 12. The
 * same scheme applied to a cookie value produces bytes RFC 6265's stricter cookie-octet grammar
 * forbids, which Jetty 12 enforces by dropping the cookie or failing the whole request -- so
 * {@code encodeCookieValue()} percent-encodes instead.
 */
public class HttpResponseWrapperEncodingTest {

    private static final String CYRILLIC = "Кириллица";

    @Test
    public void setHeaderPacksEachUtf8ByteIntoOneChar() {
        final Capture<String> capturedValue = newCapture();
        final HttpServletResponse mockResponse = createMock(HttpServletResponse.class);
        mockResponse.setHeader(eq("X-Test"), capture(capturedValue));
        expectLastCall();
        replay(mockResponse);

        new HttpResponseWrapper(mockResponse).setHeader("X-Test", CYRILLIC);

        verify(mockResponse);
        assertPackedUtf8Bytes(CYRILLIC, capturedValue.getValue());
    }

    @Test
    public void addHeaderPacksEachUtf8ByteIntoOneChar() {
        final Capture<String> capturedValue = newCapture();
        final HttpServletResponse mockResponse = createMock(HttpServletResponse.class);
        mockResponse.addHeader(eq("X-Test"), capture(capturedValue));
        expectLastCall();
        replay(mockResponse);

        new HttpResponseWrapper(mockResponse).addHeader("X-Test", CYRILLIC);

        verify(mockResponse);
        assertPackedUtf8Bytes(CYRILLIC, capturedValue.getValue());
    }

    @Test
    public void addCookiePercentEncodesNonAsciiValue() {
        final Capture<Cookie> capturedCookie = newCapture();
        final HttpServletResponse mockResponse = createMock(HttpServletResponse.class);
        mockResponse.addCookie(capture(capturedCookie));
        expectLastCall();
        replay(mockResponse);

        new HttpResponseWrapper(mockResponse).addCookie("test-cookie", CYRILLIC);

        verify(mockResponse);
        final String encoded = capturedCookie.getValue().getValue();
        // Every char of a percent-encoded value is plain US-ASCII (letters, digits, '%', '+') --
        // exactly what RFC 6265's cookie-octet grammar requires and the packed-byte header scheme
        // cannot guarantee.
        for (int i = 0; i < encoded.length(); i++) {
            assertTrue("Char at index " + i + " (" + encoded.charAt(i) + ") should be plain US-ASCII",
                    encoded.charAt(i) < 128);
        }
        assertEquals(CYRILLIC, URLDecoder.decode(encoded, UTF_8));
    }

    @Test
    public void pureAsciiValuesAreUnaffected() {
        // ASCII bytes (0-127) are identical in UTF-8 and ISO-8859-1, so the transformation is a
        // no-op for the common case -- this is why the hack has been invisible for values like
        // "no-cache" and only bites on genuinely non-ASCII content.
        final Capture<String> capturedValue = newCapture();
        final HttpServletResponse mockResponse = createMock(HttpServletResponse.class);
        mockResponse.setHeader(eq("Cache-Control"), capture(capturedValue));
        expectLastCall();
        replay(mockResponse);

        new HttpResponseWrapper(mockResponse).setHeader("Cache-Control", "no-cache");

        verify(mockResponse);
        assertEquals("no-cache", capturedValue.getValue());
    }

    /**
     * Asserts {@code actual} is exactly {@code original}'s UTF-8 byte sequence, one byte packed
     * per {@code char} (i.e. {@code actual.charAt(i) == (original.getBytes(UTF_8)[i] & 0xFF)}),
     * and that its length equals the UTF-8 byte count rather than the original character count.
     */
    private static void assertPackedUtf8Bytes(final String original, final String actual) {
        final byte[] utf8Bytes = original.getBytes(UTF_8);
        assertEquals("Encoded length should equal the UTF-8 byte count, not the original character count",
                utf8Bytes.length, actual.length());
        for (int i = 0; i < utf8Bytes.length; i++) {
            final int expectedByteAsChar = utf8Bytes[i] & 0xFF;
            assertEquals("Char at index " + i + " should be UTF-8 byte " + i + " reinterpreted as ISO-8859-1",
                    expectedByteAsChar, actual.charAt(i));
        }
    }
}
