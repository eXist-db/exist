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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ContentTypeHelper}.
 *
 * <p>These tests verify the content-type classification logic that determines
 * whether a response body should be returned as XML (parsed), string, or binary.
 * This is the critical behavior difference from the old implementation.</p>
 */
class ContentTypeHelperTest {

    // ========================================================================
    // XML detection
    // ========================================================================

    @Test
    void applicationXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/xml"));
    }

    @Test
    void textXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("text/xml"));
    }

    @Test
    void applicationAtomXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/atom+xml"));
    }

    @Test
    void applicationSoapXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/soap+xml"));
    }

    @Test
    void applicationXsltXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/xslt+xml"));
    }

    @Test
    void applicationSvgXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("image/svg+xml"));
    }

    @Test
    void xmlWithCharsetIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/xml; charset=utf-8"));
    }

    @Test
    void xmlCaseInsensitive() {
        assertTrue(ContentTypeHelper.isXml("APPLICATION/XML"));
    }

    // ========================================================================
    // HTML detection
    // ========================================================================

    @Test
    void textHtmlIsHtml() {
        assertTrue(ContentTypeHelper.isHtml("text/html"));
    }

    @Test
    void textHtmlWithCharsetIsHtml() {
        assertTrue(ContentTypeHelper.isHtml("text/html; charset=utf-8"));
    }

    @Test
    void applicationXhtmlIsHtml() {
        assertTrue(ContentTypeHelper.isHtml("application/xhtml+xml"));
    }

    @Test
    void htmlCaseInsensitive() {
        assertTrue(ContentTypeHelper.isHtml("TEXT/HTML"));
    }

    // ========================================================================
    // Text detection (should return xs:string)
    // ========================================================================

    @Test
    void textPlainIsText() {
        assertTrue(ContentTypeHelper.isText("text/plain"));
    }

    @Test
    void textCssIsText() {
        assertTrue(ContentTypeHelper.isText("text/css"));
    }

    @Test
    void textCsvIsText() {
        assertTrue(ContentTypeHelper.isText("text/csv"));
    }

    @Test
    void applicationJsonIsText() {
        assertTrue(ContentTypeHelper.isText("application/json"));
    }

    @Test
    void applicationJsonWithCharsetIsText() {
        assertTrue(ContentTypeHelper.isText("application/json; charset=utf-8"));
    }

    @Test
    void applicationVndApiJsonIsText() {
        assertTrue(ContentTypeHelper.isText("application/vnd.api+json"));
    }

    @Test
    void applicationLdJsonIsText() {
        assertTrue(ContentTypeHelper.isText("application/ld+json"));
    }

    @Test
    void applicationJavascriptIsText() {
        assertTrue(ContentTypeHelper.isText("application/javascript"));
    }

    @Test
    void applicationEcmascriptIsText() {
        assertTrue(ContentTypeHelper.isText("application/ecmascript"));
    }

    @Test
    void textJavascriptIsText() {
        assertTrue(ContentTypeHelper.isText("text/javascript"));
    }

    @Test
    void applicationFormUrlencodedIsText() {
        assertTrue(ContentTypeHelper.isText("application/x-www-form-urlencoded"));
    }

    @Test
    void textCaseInsensitive() {
        assertTrue(ContentTypeHelper.isText("APPLICATION/JSON"));
    }

    // ========================================================================
    // Binary detection (everything else)
    // ========================================================================

    @Test
    void imagePngIsBinary() {
        assertFalse(ContentTypeHelper.isText("image/png"), "image/png should not be text");
        assertFalse(ContentTypeHelper.isXml("image/png"), "image/png should not be xml");
        assertFalse(ContentTypeHelper.isHtml("image/png"), "image/png should not be html");
    }

    @Test
    void applicationOctetStreamIsBinary() {
        assertFalse(ContentTypeHelper.isText("application/octet-stream"));
    }

    @Test
    void applicationPdfIsBinary() {
        assertFalse(ContentTypeHelper.isText("application/pdf"));
    }

    @Test
    void applicationZipIsBinary() {
        assertFalse(ContentTypeHelper.isText("application/zip"));
    }

    @Test
    void audioMpegIsBinary() {
        assertFalse(ContentTypeHelper.isText("audio/mpeg"));
    }

    @Test
    void videoMp4IsBinary() {
        assertFalse(ContentTypeHelper.isText("video/mp4"));
    }

    // ========================================================================
    // Negative text checks: XML/HTML types are NOT text
    // (they should be parsed as documents, not returned as strings)
    // ========================================================================

    @Test
    void applicationXmlIsNotText() {
        assertFalse(ContentTypeHelper.isText("application/xml"),
                "XML should be parsed, not returned as text");
    }

    @Test
    void textHtmlIsNotText() {
        assertFalse(ContentTypeHelper.isText("text/html"),
                "HTML should be parsed, not returned as text");
    }

    // ========================================================================
    // Media type extraction (strip charset and params)
    // ========================================================================

    @Test
    void extractMediaTypeStripsCharset() {
        assertEquals("application/json",
                ContentTypeHelper.extractMediaType("application/json; charset=utf-8"));
    }

    @Test
    void extractMediaTypeTrimsWhitespace() {
        assertEquals("text/plain",
                ContentTypeHelper.extractMediaType("  text/plain  "));
    }

    @Test
    void extractMediaTypeLowercases() {
        assertEquals("application/json",
                ContentTypeHelper.extractMediaType("Application/JSON"));
    }

    @Test
    void extractMediaTypeHandlesNull() {
        assertEquals("application/octet-stream",
                ContentTypeHelper.extractMediaType(null));
    }

    @Test
    void extractMediaTypeHandlesEmpty() {
        assertEquals("application/octet-stream",
                ContentTypeHelper.extractMediaType(""));
    }

    // ========================================================================
    // Charset extraction
    // ========================================================================

    @Test
    void extractCharsetFromContentType() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain; charset=utf-8"));
    }

    @Test
    void extractCharsetCaseInsensitive() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain; Charset=UTF-8"));
    }

    @Test
    void extractCharsetDefaultsToUtf8() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain"));
    }

    @Test
    void extractCharsetHandlesNull() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset(null));
    }

    @Test
    void extractCharsetWithQuotes() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain; charset=\"utf-8\""));
    }

    @Test
    void extractCharsetIso8859() {
        assertEquals("iso-8859-1",
                ContentTypeHelper.extractCharset("text/plain; charset=iso-8859-1"));
    }
}
