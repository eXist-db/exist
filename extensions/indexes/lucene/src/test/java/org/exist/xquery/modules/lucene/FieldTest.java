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
package org.exist.xquery.modules.lucene;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.BooleanValue;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JUnit tests for {@link FieldValueParser#parseBoolean}, which backs
 * {@link Field#bytesToAtomic} and {@link Field#stringToAtomic} for xs:boolean.
 * Covers XQuery lexical forms: "true", "false", "1", "0".
 */
public class FieldTest {

    @Test
    public void parseBooleanTrue() throws XPathException {
        assertTrue(FieldValueParser.parseBoolean("true").getValue());
    }

    @Test
    public void parseBooleanFalse() throws XPathException {
        assertFalse(FieldValueParser.parseBoolean("false").getValue());
    }

    @Test
    public void parseBooleanCaseInsensitive() throws XPathException {
        assertTrue(FieldValueParser.parseBoolean("True").getValue());
        assertTrue(FieldValueParser.parseBoolean("TRUE").getValue());
        assertFalse(FieldValueParser.parseBoolean("False").getValue());
        assertFalse(FieldValueParser.parseBoolean("FALSE").getValue());
    }

    @Test
    public void parseBooleanOneAndZero() throws XPathException {
        assertTrue(FieldValueParser.parseBoolean("1").getValue());
        assertFalse(FieldValueParser.parseBoolean("0").getValue());
    }

    @Test
    public void parseBooleanTrimmed() throws XPathException {
        assertTrue(FieldValueParser.parseBoolean("  true  ").getValue());
        assertFalse(FieldValueParser.parseBoolean("  false  ").getValue());
    }

    @Test(expected = XPathException.class)
    public void parseBooleanInvalidThrows() throws XPathException {
        FieldValueParser.parseBoolean("yes");
    }

    @Test(expected = XPathException.class)
    public void parseBooleanEmptyThrows() throws XPathException {
        FieldValueParser.parseBoolean("");
    }
}
