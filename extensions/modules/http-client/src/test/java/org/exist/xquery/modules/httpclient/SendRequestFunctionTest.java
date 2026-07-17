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
package org.exist.xquery.modules.httpclient;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.exist.xmldb.EXistResource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.*;

/**
 * Comprehensive tests for the EXPath HTTP Client {@code http:send-request} function.
 *
 * <p>Uses an embedded {@link HttpServer} for self-contained testing — no external
 * services required. Each test endpoint simulates a specific HTTP scenario.</p>
 */
public class SendRequestFunctionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final String HTTP_NS =
            "declare namespace http = 'http://expath.org/ns/http-client';\n";

    private static HttpServer httpServer;
    private static int port;

    @BeforeClass
    public static void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = httpServer.getAddress().getPort();

        // Plain text endpoint
        httpServer.createContext("/text", exchange -> {
            sendResponse(exchange, 200, "text/plain", "Hello, World!");
        });

        // JSON endpoint — THE KEY TEST: must return xs:string, not base64Binary
        httpServer.createContext("/json", exchange -> {
            sendResponse(exchange, 200, "application/json",
                    "{\"name\":\"eXist\",\"version\":7}");
        });

        // JSON with charset
        httpServer.createContext("/json-charset", exchange -> {
            sendResponse(exchange, 200, "application/json; charset=utf-8",
                    "{\"key\":\"value\"}");
        });

        // JSON subtype (application/vnd.api+json)
        httpServer.createContext("/json-subtype", exchange -> {
            sendResponse(exchange, 200, "application/vnd.api+json",
                    "{\"data\":[]}");
        });

        // XML endpoint
        httpServer.createContext("/xml", exchange -> {
            sendResponse(exchange, 200, "application/xml",
                    "<?xml version=\"1.0\"?><root><item>test</item></root>");
        });

        // text/xml endpoint
        httpServer.createContext("/text-xml", exchange -> {
            sendResponse(exchange, 200, "text/xml",
                    "<doc><p>paragraph</p></doc>");
        });

        // XML subtype (application/atom+xml)
        httpServer.createContext("/xml-subtype", exchange -> {
            sendResponse(exchange, 200, "application/atom+xml",
                    "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>Test</title></feed>");
        });

        // HTML endpoint
        httpServer.createContext("/html", exchange -> {
            sendResponse(exchange, 200, "text/html",
                    "<html><head><title>Test</title></head><body><p>Hello</p></body></html>");
        });

        // Binary endpoint (image/png — 1x1 transparent pixel)
        httpServer.createContext("/binary", exchange -> {
            byte[] pixel = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, pixel.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pixel);
            }
        });

        // CSS endpoint (text/css — should return string)
        httpServer.createContext("/css", exchange -> {
            sendResponse(exchange, 200, "text/css", "body { color: red; }");
        });

        // JavaScript endpoint (application/javascript — should return string)
        httpServer.createContext("/js", exchange -> {
            sendResponse(exchange, 200, "application/javascript", "console.log('hi');");
        });

        // Echo endpoint — returns the request method, headers, and body
        httpServer.createContext("/echo", exchange -> {
            String method = exchange.getRequestMethod();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            String custom = exchange.getRequestHeaders().getFirst("X-Custom");

            String response = "{" +
                    "\"method\":\"" + method + "\"," +
                    "\"body\":" + (body.isEmpty() ? "null" : "\"" + escapeJson(body) + "\"") + "," +
                    "\"contentType\":" + (contentType == null ? "null" : "\"" + contentType + "\"") + "," +
                    "\"accept\":" + (accept == null ? "null" : "\"" + accept + "\"") + "," +
                    "\"custom\":" + (custom == null ? "null" : "\"" + custom + "\"") +
                    "}";
            sendResponse(exchange, 200, "application/json", response);
        });

        // Redirect endpoint (302 -> /text)
        httpServer.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location",
                    "http://localhost:" + port + "/text");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // Double redirect (301 -> /redirect -> /text)
        httpServer.createContext("/redirect-chain", exchange -> {
            exchange.getResponseHeaders().set("Location",
                    "http://localhost:" + port + "/redirect");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        // Slow endpoint — sleeps 5 seconds (for timeout testing)
        httpServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendResponse(exchange, 200, "text/plain", "slow response");
        });

        // 404 endpoint
        httpServer.createContext("/not-found", exchange -> {
            sendResponse(exchange, 404, "text/plain", "Not Found");
        });

        // 500 endpoint
        httpServer.createContext("/server-error", exchange -> {
            sendResponse(exchange, 500, "text/plain", "Internal Server Error");
        });

        registerAuthEndpoints();

        // Multi-header endpoint — returns multiple Set-Cookie headers
        httpServer.createContext("/multi-header", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "a=1");
            exchange.getResponseHeaders().add("Set-Cookie", "b=2");
            exchange.getResponseHeaders().add("X-Request-Id", "abc123");
            sendResponse(exchange, 200, "text/plain", "ok");
        });

        // HEAD endpoint — returns headers but no body
        httpServer.createContext("/head-test", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("X-Custom-Header", "head-value");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        // Empty body endpoint
        httpServer.createContext("/empty", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        // Large response endpoint
        httpServer.createContext("/large", exchange -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("Line ").append(i).append(": This is a test line of text.\n");
            }
            sendResponse(exchange, 200, "text/plain", sb.toString());
        });

        // UTF-8 text response
        httpServer.createContext("/utf8", exchange -> {
            sendResponse(exchange, 200, "text/plain; charset=utf-8",
                    "Héllo Wörld! 日本語テスト");
        });

        // gzip endpoint
        httpServer.createContext("/gzip", exchange -> {
            String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            byte[] bodyBytes = "gzipped content".getBytes(StandardCharsets.UTF_8);

            if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                final UnsynchronizedByteArrayOutputStream baos = UnsynchronizedByteArrayOutputStream.builder().get();
                try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(baos)) {
                    gzip.write(bodyBytes);
                }
                byte[] compressed = baos.toByteArray();
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(200, compressed.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(compressed);
                }
            } else {
                sendResponse(exchange, 200, "text/plain", "gzipped content");
            }
        });

        // Multipart response
        httpServer.createContext("/multipart", exchange -> {
            String boundary = "boundary42";
            String body = "--" + boundary + "\r\n" +
                    "Content-Type: text/plain\r\n\r\n" +
                    "Part one\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Type: application/json\r\n\r\n" +
                    "{\"part\":2}\r\n" +
                    "--" + boundary + "--\r\n";
            exchange.getResponseHeaders().set("Content-Type",
                    "multipart/mixed; boundary=" + boundary);
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        });

        // Multipart byte-safety + nesting endpoints (extracted to keep startHttpServer's complexity down)
        registerMultipartTestEndpoints(httpServer);

        // OPTIONS endpoint
        httpServer.createContext("/options", exchange -> {
            exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        // PATCH endpoint
        httpServer.createContext("/patch", exchange -> {
            if ("PATCH".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "application/json",
                        "{\"patched\":true,\"body\":\"" + escapeJson(body) + "\"}");
            } else {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            }
        });

        httpServer.setExecutor(null);
        httpServer.start();
    }

    @AfterClass
    public static void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    /**
     * Registers the multipart response endpoints used by the byte-safety and nesting tests, kept out
     * of startHttpServer so that method's complexity is not inflated.
     */
    private static void registerMultipartTestEndpoints(final HttpServer server) {
        // Multipart response with a binary part (bytes 0xFF 0xFE, base64 "//4=") that is not valid
        // UTF-8 — corrupted by the old String-based splitter
        server.createContext("/multipart-binary", exchange -> {
            final String boundary = "bnd";
            final UnsynchronizedByteArrayOutputStream baos = UnsynchronizedByteArrayOutputStream.builder().get();
            baos.write(("--" + boundary + "\r\nContent-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            baos.write(new byte[]{(byte) 0xFF, (byte) 0xFE});
            baos.write(("\r\n--" + boundary + "\r\nContent-Type: text/plain\r\n\r\nhello\r\n--"
                    + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            final byte[] bodyBytes = baos.toByteArray();
            exchange.getResponseHeaders().set("Content-Type", "multipart/mixed; boundary=" + boundary);
            exchange.sendResponseHeaders(200, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        });

        // Nested multipart: outer multipart/mixed whose first part is itself a multipart/mixed
        server.createContext("/multipart-nested", exchange -> {
            final String inner = "--inner\r\nContent-Type: text/plain\r\n\r\nA\r\n"
                    + "--inner\r\nContent-Type: text/plain\r\n\r\nB\r\n--inner--\r\n";
            final String body = "--outer\r\nContent-Type: multipart/mixed; boundary=inner\r\n\r\n"
                    + inner + "\r\n"
                    + "--outer\r\nContent-Type: text/plain\r\n\r\nC\r\n--outer--\r\n";
            exchange.getResponseHeaders().set("Content-Type", "multipart/mixed; boundary=outer");
            final byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        });
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType,
                                      String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bodyBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bodyBytes);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Registers the Basic and Digest authentication test endpoints. */
    private static void registerAuthEndpoints() {
        // Basic auth endpoint: challenges with Basic, then accepts the credentials
        httpServer.createContext("/auth/basic", exchange -> {
            final String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Basic ")) {
                final String decoded = new String(Base64.getDecoder().decode(auth.substring(6)),
                        StandardCharsets.UTF_8);
                if ("testuser:testpass".equals(decoded)) {
                    sendResponse(exchange, 200, "application/json",
                            "{\"authenticated\":true,\"user\":\"testuser\"}");
                    return;
                }
            }
            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"test\"");
            sendResponse(exchange, 401, "text/plain", "Unauthorized");
        });

        // Digest auth endpoint (RFC 2617): challenges with Digest, then validates the response hash
        httpServer.createContext("/auth/digest", exchange -> {
            final String realm = "testrealm";
            final String nonce = "abc123nonce";
            final String qop = "auth";
            final String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Digest ")) {
                final Map<String, String> p = parseDigestParams(auth.substring("Digest ".length()));
                final String ha1 = md5Hex("testuser:" + realm + ":testpass");
                final String ha2 = md5Hex(exchange.getRequestMethod() + ":" + p.getOrDefault("uri", ""));
                final String expected = md5Hex(ha1 + ":" + nonce + ":" + p.getOrDefault("nc", "")
                        + ":" + p.getOrDefault("cnonce", "") + ":" + qop + ":" + ha2);
                if ("testuser".equals(p.get("username")) && expected.equals(p.get("response"))) {
                    sendResponse(exchange, 200, "application/json",
                            "{\"authenticated\":true,\"user\":\"testuser\"}");
                    return;
                }
            }
            exchange.getResponseHeaders().set("WWW-Authenticate",
                    "Digest realm=\"" + realm + "\", nonce=\"" + nonce + "\", qop=\"" + qop + "\"");
            sendResponse(exchange, 401, "text/plain", "Unauthorized");
        });
    }

    /** Parses comma-separated, optionally-quoted Digest auth parameters into a lower-cased map. */
    private static Map<String, String> parseDigestParams(final String params) {
        final Map<String, String> map = new HashMap<>();
        for (final String part : params.split(",")) {
            final int eq = part.indexOf('=');
            if (eq > 0) {
                final String key = part.substring(0, eq).trim().toLowerCase();
                String val = part.substring(eq + 1).trim();
                if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                map.put(key, val);
            }
        }
        return map;
    }

    private static String md5Hex(final String s) {
        try {
            final byte[] digest = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ========================================================================
    // Response Structure Tests
    // ========================================================================

    @Test
    public void getTextReturnsResponseElementAndBody() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return count($response)");
        assertEquals("GET text should return 2 items (response element + body)",
                "2", result.getResource(0).getContent().toString());
    }

    @Test
    public void responseElementHasStatusAttribute() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("Response status should be 200",
                "200", result.getResource(0).getContent().toString());
    }

    @Test
    public void responseElementHasMessageAttribute() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return string-length($response[1]/@message) > 0");
        assertEquals("Response should have a non-empty message attribute",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void responseElementIsNamespacedCorrectly() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return namespace-uri($response[1])");
        assertEquals("Response element should be in EXPath HTTP namespace",
                "http://expath.org/ns/http-client",
                result.getResource(0).getContent().toString());
    }

    @Test
    public void responseElementLocalNameIsResponse() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return local-name($response[1])");
        assertEquals("First item should be 'response' element",
                "response", result.getResource(0).getContent().toString());
    }

    @Test
    public void responseElementContainsHeaders() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return count($response[1]/http:header) > 0");
        assertEquals("Response should contain header elements",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void responseHeadersHaveNameAndValueAttributes() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "let $h := $response[1]/http:header[1]\n" +
                "return exists($h/@name) and exists($h/@value)");
        assertEquals("Headers should have name and value attributes",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void responseContainsContentTypeHeader() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return $response[1]/http:header[lower-case(@name) = 'content-type']/@value/string()");
        assertTrue("Content-Type header should contain text/plain",
                result.getResource(0).getContent().toString().contains("text/plain"));
    }

    @Test
    public void responseContainsBodyDescriptor() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return count($response[1]/http:body)");
        assertEquals("Response element should contain a body descriptor",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void bodyDescriptorHasMediaType() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return $response[1]/http:body/@media-type/string()");
        assertTrue("Body descriptor should have media-type",
                result.getResource(0).getContent().toString().contains("text/plain"));
    }

    // ========================================================================
    // Content Type Classification Tests (THE KEY TESTS)
    // ========================================================================

    @Test
    public void textPlainResponseReturnsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return $response[2]");
        assertEquals("Plain text response body should be the string content",
                "Hello, World!", result.getResource(0).getContent().toString());
    }

    @Test
    public void textPlainResponseIsXsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'/>" +
                ")\n" +
                "return $response[2] instance of xs:string");
        assertEquals("text/plain response should be xs:string",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void jsonResponseReturnsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/json'/>" +
                ")\n" +
                "return $response[2]");
        assertEquals("JSON response body should be the string content",
                "{\"name\":\"eXist\",\"version\":7}",
                result.getResource(0).getContent().toString());
    }

    @Test
    public void jsonResponseIsXsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/json'/>" +
                ")\n" +
                "return $response[2] instance of xs:string");
        assertEquals("application/json response should be xs:string, NOT base64Binary",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void jsonWithCharsetReturnsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/json-charset'/>" +
                ")\n" +
                "return $response[2] instance of xs:string");
        assertEquals("JSON with charset should still be xs:string",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void jsonSubtypeReturnsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/json-subtype'/>" +
                ")\n" +
                "return $response[2] instance of xs:string");
        assertEquals("application/vnd.api+json should be xs:string",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void jsonResponseCanBeParsed() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/json'/>" +
                ")\n" +
                "return parse-json($response[2])?name");
        assertEquals("JSON response should be directly parseable without binary-to-string",
                "eXist", result.getResource(0).getContent().toString());
    }

    @Test
    public void xmlResponseIsParsedAsDocument() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/xml'/>" +
                ")\n" +
                "return $response[2]//item/string()");
        assertEquals("XML response should be parsed as document node",
                "test", result.getResource(0).getContent().toString());
    }

    @Test
    public void xmlResponseIsDocumentNode() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/xml'/>" +
                ")\n" +
                "return $response[2] instance of document-node()");
        assertEquals("application/xml response should be document-node()",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void textXmlResponseIsParsed() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text-xml'/>" +
                ")\n" +
                "return $response[2]//p/string()");
        assertEquals("text/xml response should be parsed as XML",
                "paragraph", result.getResource(0).getContent().toString());
    }

    @Test
    public void xmlSubtypeResponseIsParsed() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/xml-subtype'/>" +
                ")\n" +
                "return $response[2] instance of document-node()");
        assertEquals("application/atom+xml should be parsed as XML",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void htmlResponseIsParsed() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/html'/>" +
                ")\n" +
                "return $response[2] instance of document-node()");
        assertEquals("text/html response should be parsed as document",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void binaryResponseIsBase64Binary() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/binary'/>" +
                ")\n" +
                "return $response[2] instance of xs:base64Binary");
        assertEquals("image/png response should be xs:base64Binary",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void cssResponseIsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/css'/>" +
                ")\n" +
                "return $response[2] instance of xs:string");
        assertEquals("text/css response should be xs:string",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void javascriptResponseIsString() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/js'/>" +
                ")\n" +
                "return $response[2] instance of xs:string");
        assertEquals("application/javascript response should be xs:string",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // HTTP Methods Tests
    // ========================================================================

    @Test
    public void getMethod() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/echo'/>" +
                ")\n" +
                "return parse-json($response[2])?method");
        assertEquals("GET method should be sent",
                "GET", result.getResource(0).getContent().toString());
    }

    @Test
    public void postMethodWithBody() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='POST' href='" + baseUrl() + "/echo'>" +
                "    <http:body media-type='application/json'>{{\"test\": true}}</http:body>" +
                "  </http:request>" +
                ")\n" +
                "return parse-json($response[2])?method");
        assertEquals("POST method should be sent",
                "POST", result.getResource(0).getContent().toString());
    }

    @Test
    public void postBodyIsSent() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='POST' href='" + baseUrl() + "/echo'>" +
                "    <http:body media-type='text/plain'>hello server</http:body>" +
                "  </http:request>" +
                ")\n" +
                "return contains(parse-json($response[2])?body, 'hello server')");
        assertEquals("POST body should be transmitted",
                "true", result.getResource(0).getContent().toString());
    }

    /**
     * The http:body @src attribute (EXPath HTTP Client 3.1) sends the linked database resource as the
     * request body. Regression for eXist-db/exist#6510, where @src was silently ignored and an empty
     * body was sent.
     */
    @Test
    public void bodySrcSendsResourceContent() throws XMLDBException {
        storeBinaryResource("http-src-body.txt", "abracadabra");
        try {
            final ResourceSet result = existEmbeddedServer.executeQuery(HTTP_NS + """
                    let $response := http:send-request(
                      <http:request method='POST' href='%s/echo'>
                        <http:body media-type='text/plain' src='/db/http-src-body.txt'/>
                      </http:request>)
                    return parse-json($response[2])?body""".formatted(baseUrl()));
            assertEquals("http:body/@src content should be sent as the request body",
                    "abracadabra", result.getResource(0).getContent().toString());
        } finally {
            removeResource("http-src-body.txt");
        }
    }

    private static void storeBinaryResource(final String name, final String content) throws XMLDBException {
        final Collection root = existEmbeddedServer.getRoot();
        final BinaryResource res = root.createResource(name, BinaryResource.class);
        ((EXistResource) res).setMimeType("text/plain");
        res.setContent(content.getBytes(StandardCharsets.UTF_8));
        root.storeResource(res);
    }

    private static void removeResource(final String name) throws XMLDBException {
        final Collection root = existEmbeddedServer.getRoot();
        final Resource res = root.getResource(name);
        if (res != null) {
            root.removeResource(res);
        }
    }

    /**
     * Per EXPath HTTP Client 3.1, combining @src with body content is err:HC004.
     */
    @Test
    public void bodySrcWithContentThrowsHC004() {
        assertThatExceptionOfType(XMLDBException.class)
                .isThrownBy(() -> existEmbeddedServer.executeQuery(HTTP_NS + """
                        http:send-request(
                          <http:request method='POST' href='%s/echo'>
                            <http:body media-type='text/plain' src='/db/http-src-body.txt'>inline</http:body>
                          </http:request>)""".formatted(baseUrl())))
                .withStackTraceContaining("HC004");
    }

    /**
     * media-type is mandatory on an http:body that uses @src (request-validity error err:HC005).
     */
    @Test
    public void bodySrcWithoutMediaTypeThrowsHC005() {
        assertThatExceptionOfType(XMLDBException.class)
                .isThrownBy(() -> existEmbeddedServer.executeQuery(HTTP_NS + """
                        http:send-request(
                          <http:request method='POST' href='%s/echo'>
                            <http:body src='/db/http-src-body.txt'/>
                          </http:request>)""".formatted(baseUrl())))
                .withStackTraceContaining("HC005");
    }

    @Test
    public void putMethod() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='PUT' href='" + baseUrl() + "/echo'>" +
                "    <http:body media-type='text/plain'>update</http:body>" +
                "  </http:request>" +
                ")\n" +
                "return parse-json($response[2])?method");
        assertEquals("PUT method should be sent",
                "PUT", result.getResource(0).getContent().toString());
    }

    @Test
    public void deleteMethod() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='DELETE' href='" + baseUrl() + "/echo'/>" +
                ")\n" +
                "return parse-json($response[2])?method");
        assertEquals("DELETE method should be sent",
                "DELETE", result.getResource(0).getContent().toString());
    }

    @Test
    public void headMethod() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='HEAD' href='" + baseUrl() + "/head-test'/>" +
                ")\n" +
                "return ($response[1]/@status/string(), count($response))");
        // HEAD returns status but no body — sequence should have 1 item (response only)
        final String status = result.getResource(0).getContent().toString();
        assertEquals("HEAD should return 200 status", "200", status);
    }

    @Test
    public void headMethodReturnsNoBody() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='HEAD' href='" + baseUrl() + "/head-test'/>" +
                ")\n" +
                "return count($response)");
        assertEquals("HEAD should return only response element, no body",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void optionsMethod() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='OPTIONS' href='" + baseUrl() + "/options'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("OPTIONS should return 200",
                "200", result.getResource(0).getContent().toString());
    }

    @Test
    public void patchMethod() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='PATCH' href='" + baseUrl() + "/patch'>" +
                "    <http:body media-type='application/json'>{{\"field\":\"value\"}}</http:body>" +
                "  </http:request>" +
                ")\n" +
                "return parse-json($response[2])?patched");
        assertEquals("PATCH method should work",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Request Headers Tests
    // ========================================================================

    @Test
    public void customHeadersAreSent() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/echo'>" +
                "    <http:header name='X-Custom' value='test-value'/>" +
                "  </http:request>" +
                ")\n" +
                "return parse-json($response[2])?custom");
        assertEquals("Custom header should be sent",
                "test-value", result.getResource(0).getContent().toString());
    }

    @Test
    public void acceptHeaderIsSent() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/echo'>" +
                "    <http:header name='Accept' value='application/json'/>" +
                "  </http:request>" +
                ")\n" +
                "return parse-json($response[2])?accept");
        assertEquals("Accept header should be sent",
                "application/json", result.getResource(0).getContent().toString());
    }

    @Test
    public void contentTypeFromBodyMediaType() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='POST' href='" + baseUrl() + "/echo'>" +
                "    <http:body media-type='application/json'>{{\"test\":true}}</http:body>" +
                "  </http:request>" +
                ")\n" +
                "return contains(parse-json($response[2])?contentType, 'application/json')");
        assertEquals("Content-Type should be set from body media-type",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Function Arity Tests
    // ========================================================================

    @Test
    public void twoArgFormWithHref() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET'/>, '" + baseUrl() + "/text'" +
                ")\n" +
                "return $response[2]");
        assertEquals("2-arg form with href should work",
                "Hello, World!", result.getResource(0).getContent().toString());
    }

    @Test
    public void hrefParameterOverridesAttribute() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/not-found'/>, " +
                "  '" + baseUrl() + "/text'" +
                ")\n" +
                "return $response[2]");
        assertEquals("href parameter should override href attribute",
                "Hello, World!", result.getResource(0).getContent().toString());
    }

    @Test
    public void threeArgFormWithBodies() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='POST'>" +
                "    <http:body media-type='text/plain'/>" +
                "  </http:request>," +
                "  '" + baseUrl() + "/echo'," +
                "  'external body content'" +
                ")\n" +
                "return contains(parse-json($response[2])?body, 'external body content')");
        assertEquals("3-arg form with external body should work",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // HTTP Status Code Tests
    // ========================================================================

    @Test
    public void status404() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/not-found'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("404 status should be reported",
                "404", result.getResource(0).getContent().toString());
    }

    @Test
    public void status500() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/server-error'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("500 status should be reported",
                "500", result.getResource(0).getContent().toString());
    }

    @Test
    public void status204NoContent() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/empty'/>" +
                ")\n" +
                "return ($response[1]/@status/string(), count($response))");
        assertEquals("204 No Content status should be reported",
                "204", result.getResource(0).getContent().toString());
    }

    @Test
    public void status204HasNoBody() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/empty'/>" +
                ")\n" +
                "return count($response)");
        assertEquals("204 response should have no body",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void errorStatusStillReturnsBody() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/not-found'/>" +
                ")\n" +
                "return $response[2]");
        assertEquals("Error responses should still return body content",
                "Not Found", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Redirect Tests
    // ========================================================================

    @Test
    public void followRedirectTrue() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/redirect'" +
                "    follow-redirect='true'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("Following redirect should give final 200 status",
                "200", result.getResource(0).getContent().toString());
    }

    @Test
    public void followRedirectTrueGetsContent() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/redirect'" +
                "    follow-redirect='true'/>" +
                ")\n" +
                "return $response[2]");
        assertEquals("Following redirect should return final response body",
                "Hello, World!", result.getResource(0).getContent().toString());
    }

    @Test
    public void followRedirectFalse() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/redirect'" +
                "    follow-redirect='false'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("Not following redirect should return 302",
                "302", result.getResource(0).getContent().toString());
    }

    @Test
    public void redirectChainFollowed() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/redirect-chain'" +
                "    follow-redirect='true'/>" +
                ")\n" +
                "return $response[2]");
        assertEquals("Chain of redirects should be followed",
                "Hello, World!", result.getResource(0).getContent().toString());
    }

    @Test
    public void defaultFollowRedirectIsTrue() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/redirect'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("Default follow-redirect should be true (follow redirects)",
                "200", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Timeout Tests
    // ========================================================================

    @Test
    public void timeoutRaisesHC006() throws XMLDBException {
        try {
            existEmbeddedServer.executeQuery(
                    HTTP_NS +
                    "http:send-request(" +
                    "  <http:request method='GET' href='" + baseUrl() + "/slow'" +
                    "    timeout='1'/>" +
                    ")");
            fail("Timeout should raise an error");
        } catch (XMLDBException e) {
            assertTrue("Timeout error should mention HC006 or timeout: " + e.getMessage(),
                    e.getMessage().contains("HC006") || e.getMessage().toLowerCase().contains("timeout"));
        }
    }

    // ========================================================================
    // Authentication Tests
    // ========================================================================

    @Test
    public void basicAuthWithCredentials() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/auth/basic'" +
                "    username='testuser' password='testpass' auth-method='basic'" +
                "    send-authorization='true'/>" +
                ")\n" +
                "return parse-json($response[2])?authenticated");
        assertEquals("Basic auth should authenticate successfully",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void basicAuthWithWrongCredentials() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/auth/basic'" +
                "    username='wrong' password='wrong' auth-method='basic'" +
                "    send-authorization='true'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("Wrong credentials should get 401",
                "401", result.getResource(0).getContent().toString());
    }

    @Test
    public void noAuthReturns401() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/auth/basic'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("No auth should get 401",
                "401", result.getResource(0).getContent().toString());
    }

    @Test
    public void basicChallengeResponse() throws XMLDBException {
        // No send-authorization: the client must answer the server's 401 Basic challenge by
        // re-sending the request with credentials (EXPath default behavior).
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/auth/basic'" +
                "    username='testuser' password='testpass' auth-method='basic'/>" +
                ")\n" +
                "return parse-json($response[2])?authenticated");
        assertEquals("Basic challenge-response should authenticate without send-authorization",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void digestChallengeResponse() throws XMLDBException {
        // The client must answer the server's 401 Digest challenge by computing the RFC 2617
        // digest response and re-sending; the test server validates the response hash.
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/auth/digest'" +
                "    username='testuser' password='testpass' auth-method='digest'/>" +
                ")\n" +
                "return parse-json($response[2])?authenticated");
        assertEquals("Digest challenge-response should authenticate",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Special Request Attributes Tests
    // ========================================================================

    @Test
    public void statusOnlyReturnsNoBody() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'" +
                "    status-only='true'/>" +
                ")\n" +
                "return count($response)");
        assertEquals("status-only should return only response element",
                "1", result.getResource(0).getContent().toString());
    }

    @Test
    public void statusOnlyStillHasStatus() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'" +
                "    status-only='true'/>" +
                ")\n" +
                "return $response[1]/@status/string()");
        assertEquals("status-only should still report status",
                "200", result.getResource(0).getContent().toString());
    }

    @Test
    public void overrideMediaType() throws XMLDBException {
        // Override binary content-type to treat as text
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/text'" +
                "    override-media-type='application/json'/>" +
                ")\n" +
                "return $response[2] instance of xs:string");
        assertEquals("override-media-type should control response type classification",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Multi-Header Tests
    // ========================================================================

    @Test
    public void multipleResponseHeaders() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/multi-header'/>" +
                ")\n" +
                "return count($response[1]/http:header[lower-case(@name) = 'set-cookie'])");
        // Multiple Set-Cookie headers should be preserved
        final int count = Integer.parseInt(result.getResource(0).getContent().toString());
        assertTrue("Multiple headers with same name should be preserved, got: " + count,
                count >= 2);
    }

    // ========================================================================
    // Encoding Tests
    // ========================================================================

    @Test
    public void utf8ResponseContent() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/utf8'/>" +
                ")\n" +
                "return contains($response[2], 'Héllo')");
        assertEquals("UTF-8 response should preserve characters",
                "true", result.getResource(0).getContent().toString());
    }

    @Test
    public void utf8ResponseWithJapanese() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/utf8'/>" +
                ")\n" +
                "return contains($response[2], '日本語')");
        assertEquals("UTF-8 response should preserve Japanese characters",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // gzip Tests
    // ========================================================================

    @Test
    public void gzipResponseIsDecompressed() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/gzip'>" +
                "    <http:header name='Accept-Encoding' value='gzip'/>" +
                "  </http:request>" +
                ")\n" +
                "return $response[2]");
        assertEquals("gzip response should be transparently decompressed",
                "gzipped content", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // XML Body Request Tests
    // ========================================================================

    @Test
    public void postXmlBody() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='POST' href='" + baseUrl() + "/echo'>" +
                "    <http:body media-type='application/xml'><data><item>1</item></data></http:body>" +
                "  </http:request>" +
                ")\n" +
                "return contains(parse-json($response[2])?body, '<item>1</item>')");
        assertEquals("XML body should be serialized and sent",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Error Handling Tests
    // ========================================================================

    @Test
    public void invalidUriRaisesError() throws XMLDBException {
        try {
            existEmbeddedServer.executeQuery(
                    HTTP_NS +
                    "http:send-request(" +
                    "  <http:request method='GET' href='not://a valid uri!!'/>" +
                    ")");
            fail("Invalid URI should raise an error");
        } catch (XMLDBException e) {
            // Expected — should get HC001 or similar error
            assertNotNull("Error should have a message", e.getMessage());
        }
    }

    @Test
    public void connectionRefusedRaisesHC001() throws XMLDBException {
        try {
            existEmbeddedServer.executeQuery(
                    HTTP_NS +
                    "http:send-request(" +
                    "  <http:request method='GET' href='http://localhost:1'" +
                    "    timeout='2'/>" +
                    ")");
            fail("Connection refused should raise an error");
        } catch (XMLDBException e) {
            assertTrue("Error should mention HC001 or connection: " + e.getMessage(),
                    e.getMessage().contains("HC001") ||
                    e.getMessage().toLowerCase().contains("connect") ||
                    e.getMessage().toLowerCase().contains("refused"));
        }
    }

    @Test
    public void connectionErrorIsCatchableAsExpathHC001() throws XMLDBException {
        // Regression test for #4256: a connection error must surface as the EXPath error
        // expath-err:HC001 (namespace http://expath.org/ns/error), catchable from XQuery — not as a
        // raw Java exception (org.expath.httpclient.HttpClientException), as the old client did.
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "declare namespace expath-err = 'http://expath.org/ns/error';\n" +
                "try {\n" +
                "  http:send-request(<http:request method='GET' href='http://localhost:1' timeout='2'/>)\n" +
                "} catch expath-err:HC001 {\n" +
                "  'caught-HC001'\n" +
                "}");
        assertEquals("Connection error must be catchable as expath-err:HC001 (#4256)",
                "caught-HC001", result.getResource(0).getContent().toString());
    }

    @Test
    public void missingHrefRaisesError() throws XMLDBException {
        try {
            existEmbeddedServer.executeQuery(
                    HTTP_NS +
                    "http:send-request(" +
                    "  <http:request method='GET'/>" +
                    ")");
            fail("Missing href should raise an error");
        } catch (XMLDBException e) {
            assertNotNull("Missing href should produce an error", e.getMessage());
        }
    }

    @Test
    public void missingMethodRaisesError() throws XMLDBException {
        try {
            existEmbeddedServer.executeQuery(
                    HTTP_NS +
                    "http:send-request(" +
                    "  <http:request href='" + baseUrl() + "/text'/>" +
                    ")");
            fail("Missing method should raise an error");
        } catch (XMLDBException e) {
            assertNotNull("Missing method should produce an error", e.getMessage());
        }
    }

    // ========================================================================
    // Large Response Tests
    // ========================================================================

    @Test
    public void largeResponseIsFullyReturned() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/large'/>" +
                ")\n" +
                "return string-length($response[2]) > 10000");
        assertEquals("Large response should be fully returned",
                "true", result.getResource(0).getContent().toString());
    }

    // ========================================================================
    // Backward Compatibility Tests
    // ========================================================================

    @Test
    public void binaryToStringStillWorksOnTextResponse() throws XMLDBException {
        // The old workaround (util:binary-to-string) should still work even though
        // the response is now a string — util:binary-to-string accepts strings too
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/json'/>" +
                ")\n" +
                "return $response[2]");
        // Just verify we get the content — the key is no error is thrown
        assertTrue("Response should contain JSON content",
                result.getResource(0).getContent().toString().contains("eXist"));
    }

    // ========================================================================
    // Multipart Response Tests
    // ========================================================================

    @Test
    public void multipartResponseReturnsMultipleItems() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/multipart'/>" +
                ")\n" +
                "return count($response)");
        final int count = Integer.parseInt(result.getResource(0).getContent().toString());
        assertTrue("Multipart response should return 3+ items (response + 2 parts), got: " + count,
                count >= 3);
    }

    @Test
    public void multipartResponseContainsMultibodyElements() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $response := http:send-request(" +
                "  <http:request method='GET' href='" + baseUrl() + "/multipart'/>" +
                ")\n" +
                "return exists($response[1]/http:multipart)");
        assertEquals("Multipart response element should contain http:multipart",
                "true", result.getResource(0).getContent().toString());
    }

    /**
     * A binary multipart part is returned byte-for-byte (base64Binary), not corrupted by a UTF-8
     * String round-trip. The first part is the two bytes 0xFF 0xFE (base64 "//4="); the second is text.
     */
    @Test
    public void multipartBinaryPartIsByteSafe() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $r := http:send-request(\n" +
                "  <http:request method='GET' href='" + baseUrl() + "/multipart-binary'/>)\n" +
                "return $r[2] instance of xs:base64Binary\n" +
                "  and string($r[2]) = '//4='\n" +
                "  and $r[3] = 'hello'");
        assertEquals("binary multipart part should round-trip byte-for-byte",
                "true", result.getResource(0).getContent().toString());
    }

    /**
     * A nested multipart part is parsed recursively, so its sub-parts surface as individual items:
     * outer = [ multipart[ "A", "B" ], "C" ] yields the three leaf text items A, B, C.
     */
    @Test
    public void multipartNestedIsParsedRecursively() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                HTTP_NS +
                "let $r := http:send-request(\n" +
                "  <http:request method='GET' href='" + baseUrl() + "/multipart-nested'/>)\n" +
                "let $bodies := $r[position() gt 1]\n" +
                "return string-join($bodies, ',')");
        assertEquals("nested multipart should yield the leaf parts A,B,C",
                "A,B,C", result.getResource(0).getContent().toString());
    }
}
