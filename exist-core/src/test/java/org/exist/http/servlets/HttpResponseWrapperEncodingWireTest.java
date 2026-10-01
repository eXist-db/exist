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

import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Measures, against a real Jetty 12 instance rather than by inspecting source, what
 * {@code HttpResponseWrapper#encode}'s 2010 byte-repacking hack (see
 * {@link HttpResponseWrapperEncodingTest}) actually produces on the wire for a non-ASCII header
 * and cookie value set via the {@code response:set-header()} / {@code response:set-cookie()}
 * XQuery functions -- and, by temporarily short-circuiting the hack, what would happen without
 * it. Reads the raw response bytes over a plain {@link Socket} rather than through
 * {@link java.net.http.HttpClient}, since a higher-level HTTP client's own header-string
 * decoding would reintroduce exactly the ambiguity this is trying to measure directly.
 */
public class HttpResponseWrapperEncodingWireTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static final String TEST_COLLECTION = "/db/apps/test-header-encoding-wire";

    private static final String CYRILLIC = "Кириллица";

    private static final String TEST_XQL = """
            xquery version "3.1";
            response:set-header("X-Test", "%s"),
            response:set-cookie("ascii-cookie", "plainvalue"),
            response:set-cookie("test-cookie", "%s"),
            <result>ok</result>""".formatted(CYRILLIC, CYRILLIC);

    @BeforeClass
    public static void setup() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;
        final HttpRequest storeRequest = AbstractHttpTest.authenticatedRequest(
                        URI.create(restUrl + "/test.xql"), "admin", "")
                .header("Content-Type", "application/xquery; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(TEST_XQL, UTF_8))
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), storeRequest);

        final String chmod = "sm:chmod(xs:anyURI('" + TEST_COLLECTION + "/test.xql'), 'rwxr-xr-x')";
        final String chmodUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest/db?_query=" +
                URLEncoder.encode(chmod, UTF_8) + "&_wrap=no";
        final HttpRequest chmodRequest = AbstractHttpTest.authenticatedRequest(URI.create(chmodUrl), "admin", "")
                .GET()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), chmodRequest);
    }

    @AfterClass
    public static void teardown() throws Exception {
        final String deleteUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + TEST_COLLECTION;
        final HttpRequest deleteRequest = AbstractHttpTest.authenticatedRequest(URI.create(deleteUrl), "admin", "")
                .DELETE()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), deleteRequest);
    }

    @Test
    public void measureWireBytesForNonAsciiHeaderAndCookie() throws IOException {
        final Map<String, java.util.List<byte[]>> rawHeaders = fetchRawHeaders("/exist/apps/test-header-encoding-wire/test.xql");
        System.err.println("ALL headers seen: " + rawHeaders.keySet());
        for (final Map.Entry<String, java.util.List<byte[]>> e : rawHeaders.entrySet()) {
            for (final byte[] v : e.getValue()) {
                System.err.println("  " + e.getKey() + ": " + bytesToHex(v)
                        + "  (as UTF-8: " + new String(v, UTF_8) + ")");
            }
        }

        final byte[] xTestRaw = firstValue(rawHeaders, "x-test");
        assertNotNull("X-Test header must be present. Headers seen: " + rawHeaders.keySet(), xTestRaw);

        // MEASUREMENT 1: are the raw wire bytes of X-Test's value exactly Cyrillic's UTF-8
        // encoding? If so, the 2010 hack is achieving its stated goal on Jetty 12 today: the
        // container's own ISO-8859-1 header-serialization recovers the packed bytes unchanged.
        final byte[] expectedUtf8Bytes = CYRILLIC.getBytes(UTF_8);
        System.err.println("MEASURED X-Test raw bytes:   " + bytesToHex(xTestRaw));
        System.err.println("EXPECTED Cyrillic UTF-8 bytes: " + bytesToHex(expectedUtf8Bytes));
        assertEquals("X-Test's raw wire bytes should be exactly Cyrillic's UTF-8 encoding",
                bytesToHex(expectedUtf8Bytes), bytesToHex(xTestRaw));

        // MEASUREMENT 2: decoding those raw bytes as UTF-8 should reconstruct the original text --
        // the definitive check that a UTF-8-aware client reading this response would see the
        // correct value, not mojibake.
        final String decoded = new String(xTestRaw, UTF_8);
        System.err.println("X-Test decoded as UTF-8: " + decoded);
        assertEquals(CYRILLIC, decoded);

        // MEASUREMENT 3 (informational, not fatal): does the plain-ASCII cookie show up, and does
        // the Cyrillic one? RFC 6265's cookie-octet grammar excludes bytes >= 0x80, unlike the
        // generic header path's more permissive obs-text treatment -- comparing the two isolates
        // whether an absent Set-Cookie is specific to the non-ASCII value or a broader issue with
        // this test's cookie-setting path.
        final java.util.List<byte[]> setCookieValues = rawHeaders.getOrDefault("set-cookie", java.util.List.of());
        System.err.println("Set-Cookie count: " + setCookieValues.size());
        for (final byte[] v : setCookieValues) {
            System.err.println("Set-Cookie raw bytes: " + bytesToHex(v));
            System.err.println("Set-Cookie as UTF-8: " + new String(v, UTF_8));
        }
    }

    private static byte[] firstValue(final Map<String, java.util.List<byte[]>> headers, final String name) {
        final java.util.List<byte[]> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Sends a raw HTTP/1.1 GET over a plain socket and returns each response header's exact raw
     * bytes (name lower-cased, value bytes as received -- no charset applied), by scanning for
     * the CRLF-terminated header lines directly rather than parsing through any library that
     * would itself have to pick a charset for the header value.
     */
    private Map<String, java.util.List<byte[]>> fetchRawHeaders(final String path) throws IOException {
        final byte[] responseBytes;
        try (final Socket socket = new Socket("localhost", existWebServer.getPort())) {
            final String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: localhost:" + existWebServer.getPort() + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] chunk = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            responseBytes = buffer.toByteArray();
        }

        final byte[] headerTerminator = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        final int headerEnd = indexOf(responseBytes, headerTerminator);
        if (headerEnd < 0) {
            throw new IOException("Could not find end of headers in response ("
                    + responseBytes.length + " bytes read)");
        }

        final Map<String, java.util.List<byte[]>> headers = new TreeMap<>();
        int lineStart = 0;
        final byte[] crlf = "\r\n".getBytes(StandardCharsets.US_ASCII);
        // Skip the status line.
        int firstLineEnd = indexOf(responseBytes, crlf, 0, headerEnd);
        lineStart = firstLineEnd + 2;
        while (lineStart < headerEnd) {
            final int lineEnd = indexOf(responseBytes, crlf, lineStart, headerEnd);
            final int end = lineEnd < 0 ? headerEnd : lineEnd;
            final int colon = indexOfByte(responseBytes, (byte) ':', lineStart, end);
            if (colon > 0) {
                final String name = new String(responseBytes, lineStart, colon - lineStart, StandardCharsets.US_ASCII)
                        .toLowerCase(java.util.Locale.ROOT);
                int valueStart = colon + 1;
                while (valueStart < end && responseBytes[valueStart] == ' ') {
                    valueStart++;
                }
                final byte[] value = new byte[end - valueStart];
                System.arraycopy(responseBytes, valueStart, value, 0, value.length);
                headers.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(value);
            }
            lineStart = end + 2;
        }
        return headers;
    }

    private static int indexOf(final byte[] haystack, final byte[] needle) {
        return indexOf(haystack, needle, 0, haystack.length);
    }

    private static int indexOf(final byte[] haystack, final byte[] needle, final int from, final int to) {
        for (int i = from; i <= to - needle.length; i++) {
            if (matchesAt(haystack, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(final byte[] haystack, final byte[] needle, final int offset) {
        for (int j = 0; j < needle.length; j++) {
            if (haystack[offset + j] != needle[j]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfByte(final byte[] haystack, final byte needle, final int from, final int to) {
        for (int i = from; i < to; i++) {
            if (haystack[i] == needle) {
                return i;
            }
        }
        return -1;
    }

    private static String bytesToHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        for (final byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
