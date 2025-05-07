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
package org.exist.util.serializer;

import java.io.StringWriter;

import org.exist.dom.QName;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HTML5WriterTest {

    private HTML5Writer writer;
    private StringWriter targetWriter;
    
    @Before
    public void setUp() throws Exception {
        targetWriter = new StringWriter();
        writer = new HTML5Writer(targetWriter);
    }

    @Test
    public void testAttributeWithBooleanValue() throws Exception {
        final String expected = "<!DOCTYPE html><input checked>";
        final QName elQName = new QName("input");
        writer.startElement(elQName);
        writer.attribute("checked", "checked");
        writer.closeStartTag(true);

        final String actual = targetWriter.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testAttributeWithNonBooleanValue() throws Exception {
        final String expected = "<!DOCTYPE html><input name=\"name\">";
        final QName elQName = new QName("input");
        writer.startElement(elQName);
        writer.attribute("name", "name");
        writer.closeStartTag(true);

        final String actual = targetWriter.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testAttributeQNameWithBooleanValue() throws Exception {
        final String expected = "<!DOCTYPE html><input checked>";
        final QName elQName = new QName("input");
        final QName attrQName = new QName("checked");
        writer.startElement(elQName);
        writer.attribute(attrQName, attrQName.getLocalPart());
        writer.closeStartTag(true);

        final String actual = targetWriter.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testAttributeQNameWithNonBooleanValue() throws Exception {
        final String expected = "<!DOCTYPE html><input name=\"name\">";
        final QName elQName = new QName("input");
        final QName attrQName = new QName("name");
        writer.startElement(elQName);
        writer.attribute(attrQName, attrQName.getLocalPart());
        writer.closeStartTag(true);

        final String actual = targetWriter.toString();
        assertEquals(expected, actual);
    }
}
