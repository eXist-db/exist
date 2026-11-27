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

import org.exist.util.serializer.json.JSONWriter;
import org.junit.jupiter.api.Test;

import javax.xml.transform.TransformerException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The declarations of an internal subset are written behind the DOCTYPE by the XML writers and by no other writer.
 */
public class InternalSubsetWriterTest {

    private static final String SUBSET = "<!ATTLIST c id ID #IMPLIED>";

    private static String write(final SerializerWriter writer, final StringWriter out, final String systemId) throws TransformerException {
        writer.documentType("r", null, systemId, SUBSET);
        return out.toString();
    }

    @Test
    void xmlWriterWritesTheSubset() throws TransformerException {
        final StringWriter out = new StringWriter();
        final String written = write(new XMLWriter(out), out, null);
        assertEquals("<!DOCTYPE r [" + SUBSET + "]>", written.substring(written.indexOf("<!DOCTYPE")));
    }

    @Test
    void xmlWriterWritesTheSubsetBehindAnExternalId() throws TransformerException {
        final StringWriter out = new StringWriter();
        final String written = write(new XMLWriter(out), out, "r.dtd");
        assertEquals("<!DOCTYPE r SYSTEM \"r.dtd\" [" + SUBSET + "]>", written.substring(written.indexOf("<!DOCTYPE")));
    }

    @Test
    void xmlWriterWithoutASubsetWritesTheDoctypeAsBefore() throws TransformerException {
        final StringWriter out = new StringWriter();
        new XMLWriter(out).documentType("r", null, null, null);
        assertEquals("<!DOCTYPE r>", out.toString().substring(out.toString().indexOf("<!DOCTYPE")));
    }

    @Test
    void xhtmlWriterDoesNotWriteTheSubset() throws TransformerException {
        final StringWriter out = new StringWriter();
        assertFalse(write(new XHTMLWriter(out), out, null).contains("<!ATTLIST"));
    }

    @Test
    void jsonWriterDoesNotWriteTheSubset() throws TransformerException {
        final StringWriter out = new StringWriter();
        assertEquals("", write(new JSONWriter(out), out, null));
    }

    @Test
    void xhtml5WriterDoesNotWriteTheSubset() throws TransformerException {
        final StringWriter out = new StringWriter();
        assertFalse(write(new XHTML5Writer(out), out, null).contains("<!ATTLIST"));
    }

    @Test
    void microXmlWriterDoesNotWriteTheSubset() throws TransformerException {
        final StringWriter out = new StringWriter();
        final MicroXmlWriter writer = new MicroXmlWriter();
        writer.setWriter(out);
        assertFalse(write(writer, out, null).contains("<!ATTLIST"));
    }

    @Test
    void textWriterWritesNothing() throws TransformerException {
        final StringWriter out = new StringWriter();
        assertEquals("", write(new TEXTWriter(out), out, null));
    }
}
