/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.dom;

import org.junit.Test;

import javax.xml.XMLConstants;

import static org.exist.dom.QName.Validity.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class QNameTest {

    @Test
    public void validLocalPart_1() {
        final QName qName = new QName("valid-name", XMLConstants.NULL_NS_URI);
        assertEquals("valid-name", qName.getLocalPart());
        assertEquals(XMLConstants.NULL_NS_URI, qName.getNamespaceURI());
        assertEquals(null, qName.getPrefix());  //TODO(AR) should this be XMLConstants.DEFAULT_NS_PREFIX
        assertEquals(VALID.val, qName.isValid(false));
    }

    @Test
    public void invalidLocalPart_1() {
        final QName qname = new QName("invalid^Name", XMLConstants.NULL_NS_URI);
        assertEquals(INVALID_LOCAL_PART.val, qname.isValid(false));
    }

    @Test
    public void invalidLocalPart_validNamespace_1() {
        final QName qname = new QName("invalid^Name", "http://some/ns");
        assertEquals(INVALID_LOCAL_PART.val, qname.isValid(false));
    }

    @Test
    public void validWildcard_1() {
        final QName qName = new QName.WildcardLocalPartQName("abc");
        assertEquals(VALID.val, qName.isValid(true));
    }

    @Test
    public void invalidWildcard_1() {
        final QName qName = new QName.WildcardLocalPartQName("abc");
        assertEquals(INVALID_LOCAL_PART.val, qName.isValid(false));
    }

    @Test
    public void validWildcard_2() {
        final QName qName = new QName.WildcardNamespaceURIQName("xyz");
        assertEquals(VALID.val, qName.isValid(true));
    }

    @Test
    public void validWildcard_3() {
        final QName qName = QName.WildcardQName.getInstance();
        assertEquals(VALID.val, qName.isValid(true));
    }

    @Test
    public void invalidWildcard_3() {
        final QName qName = QName.WildcardQName.getInstance();
        assertEquals(INVALID_LOCAL_PART.val ^ INVALID_PREFIX.val, qName.isValid(false));
    }

    @Test
    public void isQName_illegalFormat1() {
        assertEquals(ILLEGAL_FORMAT.val, QName.isQName("emp:"));
    }

    @Test
    public void isQName_illegalFormat2() {
        assertEquals(ILLEGAL_FORMAT.val, QName.isQName(":emp"));
    }
}

