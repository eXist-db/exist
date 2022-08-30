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

import org.exist.dom.QName;

import javax.xml.transform.TransformerException;
import java.io.Writer;
import java.util.Properties;

public interface SerializerWriter {

    void setOutputProperties(final Properties properties);

    void setWriter(final Writer writer);

    Writer getWriter();

    String getDefaultNamespace();

    void setDefaultNamespace(final String namespace);

    void startDocument() throws TransformerException;

    void endDocument() throws TransformerException;

    void startElement(final String namespaceUri, final String localName, final String qname) throws TransformerException;

    void startElement(final QName qname) throws TransformerException;

    void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException;

    void endElement(final QName qname) throws TransformerException;

    void namespace(final String prefix, final String nsURI) throws TransformerException;

    void attribute(String qname, CharSequence value) throws TransformerException;

    void attribute(final QName qname, final CharSequence value) throws TransformerException;

    void characters(final CharSequence chars) throws TransformerException;

    void characters(final char[] ch, final int start, final int len) throws TransformerException;

    void processingInstruction(final String target, final String data) throws TransformerException;

    void comment(final CharSequence data) throws TransformerException;

    void startCdataSection() throws TransformerException;

    void endCdataSection() throws TransformerException;

    void cdataSection(final char[] ch, final int start, final int len) throws TransformerException;

    void startDocumentType(final String name, final String publicId, final String systemId) throws TransformerException;

    void endDocumentType() throws TransformerException;

    void documentType(final String name, final String publicId, final String systemId) throws TransformerException;

    void reset();
}
