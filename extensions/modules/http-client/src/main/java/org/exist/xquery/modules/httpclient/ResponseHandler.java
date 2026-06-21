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

import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Converts an {@link HttpResponse} into the EXPath HTTP Client XDM return sequence.
 *
 * <p>The return sequence is:</p>
 * <ol>
 *   <li>An {@code <http:response>} element with status, message, headers, and body descriptors</li>
 *   <li>Zero or more body items (one per body/multipart part)</li>
 * </ol>
 *
 * <p>Body items are typed according to Content-Type:</p>
 * <ul>
 *   <li>XML types → parsed {@code document-node()}</li>
 *   <li>HTML types → parsed {@code document-node()}</li>
 *   <li>Text/JSON types → {@code xs:string}</li>
 *   <li>Binary types → {@code xs:base64Binary}</li>
 * </ul>
 */
public class ResponseHandler {

    private static final String HTTP_NS = HttpClientModule.NAMESPACE_URI;
    private static final String HTTP_PREFIX = HttpClientModule.PREFIX;

    private static final QName RESPONSE_QNAME = new QName("response", HTTP_NS, HTTP_PREFIX);
    private static final QName HEADER_QNAME = new QName("header", HTTP_NS, HTTP_PREFIX);
    private static final QName BODY_QNAME = new QName("body", HTTP_NS, HTTP_PREFIX);
    private static final QName MULTIPART_QNAME = new QName("multipart", HTTP_NS, HTTP_PREFIX);

    /**
     * Builds the XDM return sequence from an HTTP response.
     *
     * @param response      the HTTP response
     * @param context       the XQuery context
     * @param callerFunc    the calling function (for value construction)
     * @param statusOnly    if true, return only the response element (no body)
     * @param overrideMedia if non-null, use this media type instead of the response Content-Type
     * @return the result sequence
     * @throws XPathException if an error occurs during result building
     */
    public static Sequence buildResult(final HttpResponse<byte[]> response,
                                        final XQueryContext context,
                                        final BasicFunction callerFunc,
                                        final boolean statusOnly,
                                        final String overrideMedia) throws XPathException {
        final ValueSequence result = new ValueSequence();

        // Determine content type
        final String rawContentType = overrideMedia != null ? overrideMedia :
                response.headers().firstValue("Content-Type").orElse(null);
        final String mediaType = ContentTypeHelper.extractMediaType(rawContentType);

        // Build response element — add the root element, not the document node,
        // so that $response[1]/@status works directly in XQuery
        final DocumentImpl responseDoc = buildResponseElement(response, context, mediaType);
        result.add((Item) responseDoc.getDocumentElement());

        // Add body content (unless status-only or no body). The Methanol client advertises
        // Accept-Encoding and transparently decodes gzip/deflate, so response.body() is already
        // decompressed here (and the Content-Encoding header has been removed).
        if (!statusOnly && response.body() != null && response.body().length > 0) {
            final byte[] bodyBytes = response.body();

            // Check for multipart
            if (mediaType.startsWith("multipart/")) {
                addMultipartBodies(result, bodyBytes, rawContentType, callerFunc);
            } else {
                addBody(result, bodyBytes, rawContentType, callerFunc);
            }
        }

        return result;
    }

    private static DocumentImpl buildResponseElement(final HttpResponse<byte[]> response,
                                                      final XQueryContext context,
                                                      final String mediaType) {
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();

            builder.startElement(RESPONSE_QNAME, null);
            builder.addAttribute(new QName("status", ""), String.valueOf(response.statusCode()));
            builder.addAttribute(new QName("message", ""), getReasonPhrase(response.statusCode()));

            // Add headers
            final Map<String, List<String>> headerMap = response.headers().map();
            for (final Map.Entry<String, List<String>> entry : headerMap.entrySet()) {
                final String name = entry.getKey();
                if (name == null || name.startsWith(":")) {
                    continue; // Skip HTTP/2 pseudo-headers
                }
                for (final String value : entry.getValue()) {
                    builder.startElement(HEADER_QNAME, null);
                    builder.addAttribute(new QName("name", ""), name);
                    builder.addAttribute(new QName("value", ""), value);
                    builder.endElement();
                }
            }

            // Add body descriptor (empty element describing the body type)
            if (response.body() != null && response.body().length > 0) {
                if (mediaType.startsWith("multipart/")) {
                    builder.startElement(MULTIPART_QNAME, null);
                    builder.addAttribute(new QName("media-type", ""), mediaType);
                    // Add body elements for each part (without content)
                    final String boundary = extractBoundary(
                            response.headers().firstValue("Content-Type").orElse(""));
                    if (boundary != null) {
                        final byte[][] parts = splitMultipart(response.body(), boundary);
                        for (final byte[] part : parts) {
                            final String partContentType = extractPartContentType(part);
                            builder.startElement(BODY_QNAME, null);
                            builder.addAttribute(new QName("media-type", ""),
                                    partContentType != null ? partContentType : "application/octet-stream");
                            builder.endElement();
                        }
                    }
                    builder.endElement(); // multipart
                } else {
                    builder.startElement(BODY_QNAME, null);
                    builder.addAttribute(new QName("media-type", ""), mediaType);
                    builder.endElement();
                }
            }

            builder.endElement(); // response
            builder.endDocument();

            return builder.getDocument();
        } finally {
            context.popDocumentContext();
        }
    }

    private static void addBody(final ValueSequence result, final byte[] bodyBytes,
                                 final String contentType, final BasicFunction callerFunc) throws XPathException {
        if (ContentTypeHelper.isXml(contentType)) {
            result.add(parseXml(bodyBytes, callerFunc));
        } else if (ContentTypeHelper.isHtml(contentType)) {
            result.add(parseHtml(bodyBytes, contentType, callerFunc));
        } else if (ContentTypeHelper.isText(contentType)) {
            final String charset = ContentTypeHelper.extractCharset(contentType);
            final String text = new String(bodyBytes, Charset.forName(charset));
            result.add(new StringValue(callerFunc, text));
        } else {
            final String base64 = java.util.Base64.getEncoder().encodeToString(bodyBytes);
            result.add(new org.exist.xquery.value.BinaryValueFromBinaryString(
                    new Base64BinaryValueType(), base64));
        }
    }

    private static Item parseXml(final byte[] bodyBytes,
                                   final BasicFunction callerFunc) throws XPathException {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            final SAXParser saxParser = factory.newSAXParser();
            final XMLReader reader = saxParser.getXMLReader();

            final SAXAdapter adapter = new SAXAdapter(callerFunc);
            reader.setContentHandler(adapter);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
            reader.parse(new InputSource(new ByteArrayInputStream(bodyBytes)));
            return adapter.getDocument();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new XPathException(callerFunc,
                    HttpClientModule.HC002, "Error parsing XML response: " + e.getMessage());
        }
    }

    private static Item parseHtml(final byte[] bodyBytes, final String contentType,
                                   final BasicFunction callerFunc) throws XPathException {
        // Try parsing as XML first (XHTML), fall back to string if it fails
        try {
            return parseXml(bodyBytes, callerFunc);
        } catch (XPathException e) {
            // HTML that isn't well-formed XML — return as string
            final String charset = ContentTypeHelper.extractCharset(contentType);
            return new StringValue(callerFunc,
                    new String(bodyBytes, Charset.forName(charset)));
        }
    }

    private static void addMultipartBodies(final ValueSequence result, final byte[] bodyBytes,
                                            final String contentType,
                                            final BasicFunction callerFunc) throws XPathException {
        final String boundary = extractBoundary(contentType);
        if (boundary == null) {
            throw new XPathException(callerFunc,
                    HttpClientModule.HC003, "No boundary found in multipart Content-Type");
        }

        final byte[][] parts = splitMultipart(bodyBytes, boundary);
        for (final byte[] part : parts) {
            final String partContentType = extractPartContentType(part);
            final byte[] partBody = extractPartBody(part);
            if (partBody.length > 0) {
                addBody(result, partBody, partContentType, callerFunc);
            }
        }
    }

    private static String extractBoundary(final String contentType) {
        if (contentType == null) {
            return null;
        }
        final String lower = contentType.toLowerCase();
        final int idx = lower.indexOf("boundary=");
        if (idx < 0) {
            return null;
        }
        String boundary = contentType.substring(idx + 9).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        final int semicolon = boundary.indexOf(';');
        if (semicolon >= 0) {
            boundary = boundary.substring(0, semicolon);
        }
        return boundary.trim();
    }

    private static byte[][] splitMultipart(final byte[] body, final String boundary) {
        final String bodyStr = new String(body, StandardCharsets.UTF_8);
        final String delimiter = "--" + boundary;
        final String[] rawParts = bodyStr.split(delimiter);
        final List<byte[]> parts = new java.util.ArrayList<>();
        for (final String part : rawParts) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.equals("--")) {
                continue; // Skip preamble and closing delimiter
            }
            parts.add(part.getBytes(StandardCharsets.UTF_8));
        }
        return parts.toArray(new byte[0][]);
    }

    private static String extractPartContentType(final byte[] part) {
        final String partStr = new String(part, StandardCharsets.UTF_8);
        final String[] lines = partStr.split("\r?\n");
        for (final String line : lines) {
            if (line.toLowerCase().startsWith("content-type:")) {
                return line.substring(13).trim();
            }
        }
        return "text/plain";
    }

    private static byte[] extractPartBody(final byte[] part) {
        final String partStr = new String(part, StandardCharsets.UTF_8);
        // Body starts after the first blank line (double CRLF or double LF)
        int bodyStart = partStr.indexOf("\r\n\r\n");
        if (bodyStart >= 0) {
            bodyStart += 4;
        } else {
            bodyStart = partStr.indexOf("\n\n");
            if (bodyStart >= 0) {
                bodyStart += 2;
            } else {
                return new byte[0];
            }
        }
        final String bodyStr = partStr.substring(bodyStart);
        return bodyStr.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns a reason phrase for common HTTP status codes.
     */
    private static String getReasonPhrase(final int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 415 -> "Unsupported Media Type";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }
}
