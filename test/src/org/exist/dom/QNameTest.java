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

import org.exist.xquery.XPathException;
import org.junit.Test;

import javax.xml.XMLConstants;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class QNameTest {

    @Test
    public void validLocalPart_1() throws XPathException {
        final QName qName = new QName("valid-name");
        assertEquals("valid-name", qName.getLocalPart());
        assertEquals(XMLConstants.NULL_NS_URI, qName.getNamespaceURI());
        assertEquals(null, qName.getPrefix());  //TODO(AR) should this be XMLConstants.DEFAULT_NS_PREFIX
        qName.isValid(false);
    }

    @Test(expected = XPathException.class)
    public void invalidLocalPart_1() throws XPathException {
       new QName("invalid^Name")
               .isValid(false);
    }

    @Test(expected = XPathException.class)
    public void invalidLocalPart_validNamespace_1() throws XPathException {
        new QName("invalid^Name", "http://some/ns")
                .isValid(false);
    }

    @Test
    public void validWildcard_1() throws XPathException {
        final QName qName = new QName.WildcardLocalPartQName("abc");
        qName.isValid(true);
    }

    @Test(expected = XPathException.class)
    public void invalidWildcard_1() throws XPathException {
        final QName qName = new QName.WildcardLocalPartQName("abc");
        qName.isValid(false);
    }

    @Test
    public void validWildcard_2() throws XPathException {
        final QName qName = new QName.WildcardNamespaceURIQName("xyz");
        qName.isValid(true);
    }

    @Test
    public void validWildcard_3() throws XPathException {
        final QName qName = QName.WildcardQName.getInstance();
        qName.isValid(true);
    }

    @Test(expected = XPathException.class)
    public void invalidWildcard_3() throws XPathException {
        final QName qName = QName.WildcardQName.getInstance();
        qName.isValid(false);
    }
}

