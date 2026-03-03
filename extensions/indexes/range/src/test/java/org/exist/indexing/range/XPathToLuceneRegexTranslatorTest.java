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
package org.exist.indexing.range;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for XPath fn:matches pattern translation to Lucene RegExp format.
 *
 * @see org.exist.indexing.range.XPathToLuceneRegexTranslator
 */
public class XPathToLuceneRegexTranslatorTest {

    @Test
    public void prefixPattern() {
        assertEquals("b.*", XPathToLuceneRegexTranslator.translate("^b"));
    }

    @Test
    public void prefixPatternLonger() {
        assertEquals("foo.*", XPathToLuceneRegexTranslator.translate("^foo"));
    }

    @Test
    public void prefixPatternWithDigits() {
        assertEquals("some_123_thing.*", XPathToLuceneRegexTranslator.translate("^some_123_thing"));
    }

    @Test
    public void suffixPattern() {
        assertEquals(".*b", XPathToLuceneRegexTranslator.translate("b$"));
    }

    @Test
    public void suffixPatternLonger() {
        assertEquals(".*z", XPathToLuceneRegexTranslator.translate("z$"));
    }

    @Test
    public void exactPattern() {
        assertEquals("b", XPathToLuceneRegexTranslator.translate("^b$"));
    }

    @Test
    public void exactPatternLonger() {
        assertEquals("baz", XPathToLuceneRegexTranslator.translate("^baz$"));
    }

    @Test
    public void anchorOnlyStart() {
        assertEquals("^", XPathToLuceneRegexTranslator.translate("^"));
    }

    @Test
    public void anchorOnlyEnd() {
        assertEquals("$", XPathToLuceneRegexTranslator.translate("$"));
    }

    @Test
    public void emptyPattern() {
        assertEquals("", XPathToLuceneRegexTranslator.translate(""));
    }

    @Test
    public void isTranslatablePrefix() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("^b"));
    }

    @Test
    public void isTranslatableSuffix() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("b$"));
    }

    @Test
    public void isTranslatableExact() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("^b$"));
    }

    @Test
    public void isTranslatableUnanchored() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("b"));
    }

    @Test
    public void isTranslatableBackref() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^(.)\\1$"));
    }

    @Test
    public void isTranslatableXmlSchemaEscapeI() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^\\i"));
    }

    @Test
    public void isTranslatableXmlSchemaEscapeC() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^\\c$"));
    }

    @Test
    public void isTranslatableDigitEscape() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("^\\d+$"));
    }
}
