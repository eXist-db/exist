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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.exist.dom.QName;
import org.exist.xquery.util.SerializerUtils;

import javax.annotation.Nullable;
import javax.xml.transform.TransformerException;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.Properties;

/**
 * On serialization, occurrences of a character specified in the use-character-maps in text nodes,
 * attribute values and strings are replaced by the corresponding string from the use-character-maps parameter.
 */
public class CharacterMappingWriter implements SerializerWriter {

    private SerializerWriter wrappedSerializerWriter;
    private @Nullable Int2ObjectMap<String> characterMap = null;

    @Override
    public void setOutputProperties(final Properties properties) {
        this.characterMap = SerializerUtils.getCharacterMap(properties);

        if (wrappedSerializerWriter != null) {
            wrappedSerializerWriter.setOutputProperties(properties);
        }
    }

    protected void setWrappedSerializerWriter(final SerializerWriter serializerWriter) {
        wrappedSerializerWriter = serializerWriter;
    }

    @Override
    public void setWriter(final Writer writer) {
        if (wrappedSerializerWriter != null) {
            wrappedSerializerWriter.setWriter(writer);
        }
    }

    @Override
    public Writer getWriter() {
        return wrappedSerializerWriter.getWriter();
    }

    @Override
    public String getDefaultNamespace() {
        return wrappedSerializerWriter.getDefaultNamespace();
    }

    @Override
    public void setDefaultNamespace(final String namespace) {
        wrappedSerializerWriter.setDefaultNamespace(namespace);
    }

    @Override
    public void startDocument() throws TransformerException {
        wrappedSerializerWriter.startDocument();
    }

    @Override
    public void endDocument() throws TransformerException {
        wrappedSerializerWriter.endDocument();
    }

    @Override
    public void declaration(@Nullable final String version, @Nullable final String encoding, @Nullable final String standalone) throws TransformerException {
        wrappedSerializerWriter.declaration(version, encoding, standalone);
    }

    @Override
    public void startElement(final String namespaceUri, final String localName, final String qname) throws TransformerException {
        wrappedSerializerWriter.startElement(namespaceUri, localName, qname);
    }

    @Override
    public void startElement(final QName qname) throws TransformerException {
        wrappedSerializerWriter.startElement(qname);
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        wrappedSerializerWriter.endElement(namespaceURI, localName, qname);
    }

    @Override
    public void endElement(final QName qname) throws TransformerException {
        wrappedSerializerWriter.endElement(qname);
    }

    @Override
    public void namespace(final String prefix, final String nsURI) throws TransformerException {
        wrappedSerializerWriter.namespace(prefix, nsURI);
    }

    @Override
    public void attribute(final String qname, final CharSequence value) throws TransformerException {
        final StringBuilder sb = mapCodePoints(value, 0, value.length());
        wrappedSerializerWriter.attribute(qname, sb);
    }

    @Override
    public void attribute(final QName qname, final CharSequence value) throws TransformerException {
        final StringBuilder sb = mapCodePoints(value, 0, value.length());
        wrappedSerializerWriter.attribute(qname, sb);
    }

    @Override
    public void characters(final CharSequence chars) throws TransformerException {
        final StringBuilder sb = mapCodePoints(chars, 0, chars.length());
        wrappedSerializerWriter.characters(sb);
    }

    @Override
    public void characters(final char[] ch, final int start, final int len) throws TransformerException {
        final StringBuilder sb = mapCodePoints(CharBuffer.wrap(ch), start, len);
        wrappedSerializerWriter.characters(sb);
    }

    private StringBuilder mapCodePoints(final CharSequence charSequence, final int start, final int len) {
        @Nullable char[] useBuffer = null;
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < len;) {
            final int codePoint = Character.codePointAt(charSequence, i);
            i += Character.charCount(codePoint);
            @Nullable final String content = characterMap == null ? null : characterMap.get(codePoint);
            if (content == null || content.isEmpty()) {
                if (useBuffer == null) {
                    useBuffer = new char[2];
                }
                final int sz = Character.toChars(codePoint, useBuffer, 0);
                sb.append(useBuffer, 0, sz);
            } else {
                sb.append(content);
            }
        }
        return sb;
    }

    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        wrappedSerializerWriter.processingInstruction(target, data);
    }

    @Override
    public void comment(final CharSequence data) throws TransformerException {
        wrappedSerializerWriter.comment(data);
    }

    @Override
    public void startCdataSection() throws TransformerException {
        wrappedSerializerWriter.startCdataSection();
    }

    @Override
    public void endCdataSection() throws TransformerException {
        wrappedSerializerWriter.endCdataSection();
    }

    @Override
    public void cdataSection(char[] ch, int start, int len) throws TransformerException {
        wrappedSerializerWriter.cdataSection(ch, start, len);
    }

    @Override
    public void startDocumentType(final String name, final String publicId, final String systemId) throws TransformerException {
        wrappedSerializerWriter.startDocumentType(name, publicId, systemId);
    }

    @Override
    public void endDocumentType() throws TransformerException {
        wrappedSerializerWriter.endDocumentType();
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId) throws TransformerException {
        wrappedSerializerWriter.documentType(name, publicId, systemId);
    }

    @Override
    public void reset() {
        this.characterMap = null;
        if (wrappedSerializerWriter != null) {
            wrappedSerializerWriter.reset();
        }
    }
}
