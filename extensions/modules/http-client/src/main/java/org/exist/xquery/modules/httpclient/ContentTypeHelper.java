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

/**
 * Utility for parsing Content-Type headers and classifying response body types.
 *
 * <p>The classification determines how HTTP response bodies are returned to XQuery:</p>
 * <ul>
 *   <li><strong>XML</strong> ({@code text/xml}, {@code application/xml}, {@code *+xml}):
 *       parsed as {@code document-node()}</li>
 *   <li><strong>HTML</strong> ({@code text/html}, {@code application/xhtml+xml}):
 *       parsed as {@code document-node()}</li>
 *   <li><strong>Text</strong> ({@code text/*}, {@code application/json}, {@code *+json},
 *       {@code application/javascript}): returned as {@code xs:string}</li>
 *   <li><strong>Binary</strong> (everything else): returned as {@code xs:base64Binary}</li>
 * </ul>
 *
 * <p><strong>Critical fix:</strong> The previous implementation returned JSON and other
 * text types as {@code xs:base64Binary}. This class ensures they are correctly returned
 * as {@code xs:string}, matching BaseX behavior and the EXPath spec.</p>
 */
public final class ContentTypeHelper {

    private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";
    private static final String DEFAULT_CHARSET = "utf-8";

    private ContentTypeHelper() {
        // utility class
    }

    /**
     * Tests whether the content type represents XML that should be parsed.
     *
     * @param contentType the content type to test
     * @return {@code true} if the content type is XML
     */
    public static boolean isXml(final String contentType) {
        final String mediaType = extractMediaType(contentType);
        return "application/xml".equals(mediaType)
                || "text/xml".equals(mediaType)
                || mediaType.endsWith("+xml");
    }

    /**
     * Tests whether the content type represents HTML that should be parsed.
     *
     * @param contentType the content type to test
     * @return {@code true} if the content type is HTML
     */
    public static boolean isHtml(final String contentType) {
        final String mediaType = extractMediaType(contentType);
        return "text/html".equals(mediaType)
                || "application/xhtml+xml".equals(mediaType);
    }

    /**
     * Tests whether the content type represents text that should be returned as xs:string.
     *
     * <p>Excludes XML and HTML types (which are parsed as documents instead).</p>
     *
     * @param contentType the content type to test
     * @return {@code true} if the content type is text
     */
    public static boolean isText(final String contentType) {
        final String mediaType = extractMediaType(contentType);

        // XML and HTML are parsed, not returned as text
        if (isXml(contentType) || isHtml(contentType)) {
            return false;
        }

        // All text/* types
        if (mediaType.startsWith("text/")) {
            return true;
        }

        // JSON types
        if ("application/json".equals(mediaType) || mediaType.endsWith("+json")) {
            return true;
        }

        // JavaScript
        if ("application/javascript".equals(mediaType)
                || "application/ecmascript".equals(mediaType)
                || "text/javascript".equals(mediaType)) {
            return true;
        }

        // Form data
        return "application/x-www-form-urlencoded".equals(mediaType);
    }

    /**
     * Extracts the media type from a Content-Type header, stripping parameters
     * (charset, boundary, etc.) and normalizing to lowercase.
     *
     * @param contentType the raw Content-Type header value (may be null)
     * @return the normalized media type, or "application/octet-stream" if null/empty
     */
    public static String extractMediaType(final String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_MEDIA_TYPE;
        }
        final int semicolon = contentType.indexOf(';');
        final String mediaType = semicolon >= 0
                ? contentType.substring(0, semicolon)
                : contentType;
        return mediaType.trim().toLowerCase();
    }

    /**
     * Extracts the charset from a Content-Type header.
     *
     * @param contentType the raw Content-Type header value (may be null)
     * @return the charset name (lowercase), defaulting to "utf-8"
     */
    public static String extractCharset(final String contentType) {
        if (contentType == null) {
            return DEFAULT_CHARSET;
        }
        final String lower = contentType.toLowerCase();
        final int charsetIdx = lower.indexOf("charset=");
        if (charsetIdx < 0) {
            return DEFAULT_CHARSET;
        }
        String charset = contentType.substring(charsetIdx + 8).trim();
        // Strip quotes
        if (charset.startsWith("\"") && charset.endsWith("\"")) {
            charset = charset.substring(1, charset.length() - 1);
        }
        // Strip trailing params
        final int semicolon = charset.indexOf(';');
        if (semicolon >= 0) {
            charset = charset.substring(0, semicolon);
        }
        return charset.trim().toLowerCase();
    }
}
