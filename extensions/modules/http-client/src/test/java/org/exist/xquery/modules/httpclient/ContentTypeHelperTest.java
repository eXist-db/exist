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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ContentTypeHelper}.
 *
 * <p>These tests verify the content-type classification logic that determines
 * whether a response body should be returned as XML (parsed), string, or binary.
 * This is the critical behavior difference from the old implementation.</p>
 */
public class ContentTypeHelperTest {

    // ========================================================================
    // XML detection
    // ========================================================================

    @Test
    public void applicationXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/xml"));
    }

    @Test
    public void textXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("text/xml"));
    }

    @Test
    public void applicationAtomXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/atom+xml"));
    }

    @Test
    public void applicationSoapXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/soap+xml"));
    }

    @Test
    public void applicationXsltXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/xslt+xml"));
    }

    @Test
    public void applicationSvgXmlIsXml() {
        assertTrue(ContentTypeHelper.isXml("image/svg+xml"));
    }

    @Test
    public void xmlWithCharsetIsXml() {
        assertTrue(ContentTypeHelper.isXml("application/xml; charset=utf-8"));
    }

    @Test
    public void xmlCaseInsensitive() {
        assertTrue(ContentTypeHelper.isXml("APPLICATION/XML"));
    }

    // ========================================================================
    // HTML detection
    // ========================================================================

    @Test
    public void textHtmlIsHtml() {
        assertTrue(ContentTypeHelper.isHtml("text/html"));
    }

    @Test
    public void textHtmlWithCharsetIsHtml() {
        assertTrue(ContentTypeHelper.isHtml("text/html; charset=utf-8"));
    }

    @Test
    public void applicationXhtmlIsHtml() {
        assertTrue(ContentTypeHelper.isHtml("application/xhtml+xml"));
    }

    @Test
    public void htmlCaseInsensitive() {
        assertTrue(ContentTypeHelper.isHtml("TEXT/HTML"));
    }

    // ========================================================================
    // Text detection (should return xs:string)
    // ========================================================================

    @Test
    public void textPlainIsText() {
        assertTrue(ContentTypeHelper.isText("text/plain"));
    }

    @Test
    public void textCssIsText() {
        assertTrue(ContentTypeHelper.isText("text/css"));
    }

    @Test
    public void textCsvIsText() {
        assertTrue(ContentTypeHelper.isText("text/csv"));
    }

    @Test
    public void applicationJsonIsText() {
        assertTrue(ContentTypeHelper.isText("application/json"));
    }

    @Test
    public void applicationJsonWithCharsetIsText() {
        assertTrue(ContentTypeHelper.isText("application/json; charset=utf-8"));
    }

    @Test
    public void applicationVndApiJsonIsText() {
        assertTrue(ContentTypeHelper.isText("application/vnd.api+json"));
    }

    @Test
    public void applicationLdJsonIsText() {
        assertTrue(ContentTypeHelper.isText("application/ld+json"));
    }

    @Test
    public void applicationJavascriptIsText() {
        assertTrue(ContentTypeHelper.isText("application/javascript"));
    }

    @Test
    public void applicationEcmascriptIsText() {
        assertTrue(ContentTypeHelper.isText("application/ecmascript"));
    }

    @Test
    public void textJavascriptIsText() {
        assertTrue(ContentTypeHelper.isText("text/javascript"));
    }

    @Test
    public void applicationFormUrlencodedIsText() {
        assertTrue(ContentTypeHelper.isText("application/x-www-form-urlencoded"));
    }

    @Test
    public void textCaseInsensitive() {
        assertTrue(ContentTypeHelper.isText("APPLICATION/JSON"));
    }

    // ========================================================================
    // Binary detection (everything else)
    // ========================================================================

    @Test
    public void imagePngIsBinary() {
        assertFalse("image/png should not be text", ContentTypeHelper.isText("image/png"));
        assertFalse("image/png should not be xml", ContentTypeHelper.isXml("image/png"));
        assertFalse("image/png should not be html", ContentTypeHelper.isHtml("image/png"));
    }

    @Test
    public void applicationOctetStreamIsBinary() {
        assertFalse(ContentTypeHelper.isText("application/octet-stream"));
    }

    @Test
    public void applicationPdfIsBinary() {
        assertFalse(ContentTypeHelper.isText("application/pdf"));
    }

    @Test
    public void applicationZipIsBinary() {
        assertFalse(ContentTypeHelper.isText("application/zip"));
    }

    @Test
    public void audioMpegIsBinary() {
        assertFalse(ContentTypeHelper.isText("audio/mpeg"));
    }

    @Test
    public void videoMp4IsBinary() {
        assertFalse(ContentTypeHelper.isText("video/mp4"));
    }

    // ========================================================================
    // Negative text checks: XML/HTML types are NOT text
    // (they should be parsed as documents, not returned as strings)
    // ========================================================================

    @Test
    public void applicationXmlIsNotText() {
        assertFalse("XML should be parsed, not returned as text",
                ContentTypeHelper.isText("application/xml"));
    }

    @Test
    public void textHtmlIsNotText() {
        assertFalse("HTML should be parsed, not returned as text",
                ContentTypeHelper.isText("text/html"));
    }

    // ========================================================================
    // Media type extraction (strip charset and params)
    // ========================================================================

    @Test
    public void extractMediaTypeStripsCharset() {
        assertEquals("application/json",
                ContentTypeHelper.extractMediaType("application/json; charset=utf-8"));
    }

    @Test
    public void extractMediaTypeTrimsWhitespace() {
        assertEquals("text/plain",
                ContentTypeHelper.extractMediaType("  text/plain  "));
    }

    @Test
    public void extractMediaTypeLowercases() {
        assertEquals("application/json",
                ContentTypeHelper.extractMediaType("Application/JSON"));
    }

    @Test
    public void extractMediaTypeHandlesNull() {
        assertEquals("application/octet-stream",
                ContentTypeHelper.extractMediaType(null));
    }

    @Test
    public void extractMediaTypeHandlesEmpty() {
        assertEquals("application/octet-stream",
                ContentTypeHelper.extractMediaType(""));
    }

    // ========================================================================
    // Charset extraction
    // ========================================================================

    @Test
    public void extractCharsetFromContentType() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain; charset=utf-8"));
    }

    @Test
    public void extractCharsetCaseInsensitive() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain; Charset=UTF-8"));
    }

    @Test
    public void extractCharsetDefaultsToUtf8() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain"));
    }

    @Test
    public void extractCharsetHandlesNull() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset(null));
    }

    @Test
    public void extractCharsetWithQuotes() {
        assertEquals("utf-8",
                ContentTypeHelper.extractCharset("text/plain; charset=\"utf-8\""));
    }

    @Test
    public void extractCharsetIso8859() {
        assertEquals("iso-8859-1",
                ContentTypeHelper.extractCharset("text/plain; charset=iso-8859-1"));
    }
}
