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
package org.exist.xquery.value;

import java.net.URI;

import org.exist.test.TestConstants;
import org.exist.xquery.XPathException;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author cgeorg
 */
public class AnyURITest {

	@Test
    public void fullyEscapedStringToXmldbURI() throws XPathException {
        String escaped = TestConstants.SPECIAL_NAME;
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toXmldbURI(),TestConstants.SPECIAL_URI);
    }

    @Test
    public void fullyEscapedStringToURI() throws XPathException {
        URI uri = TestConstants.SPECIAL_URI.getXmldbURI();
        String escaped = TestConstants.SPECIAL_NAME;
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toURI(),uri);
    }

    /**
     * TODO: change AnyURIValue to directly store the escaped value?
     */
    @Ignore
    @Test
    public void partiallyEscapedStringToXmldbURI() throws XPathException {
        String escaped = TestConstants.SPECIAL_NAME.replaceAll("%20"," ").replaceAll("%C3%A0","\u00E0");
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toXmldbURI(), TestConstants.SPECIAL_URI);
    }

    @Test
    public void partiallyEscapedStringToURI() throws XPathException {
        URI uri = TestConstants.SPECIAL_URI.getXmldbURI();
        String escaped = TestConstants.SPECIAL_NAME.replaceAll("%20"," ").replaceAll("%C3%A0","\u00E0");
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toURI(),uri);
    }
}
