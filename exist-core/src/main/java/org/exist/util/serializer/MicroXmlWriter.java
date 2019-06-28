/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.util.serializer;

import org.exist.dom.QName;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import java.nio.CharBuffer;
import java.util.Properties;

/**
 * @author <a href="adam.retter@googlemail.com">Adam Retter</a>
 */
public class MicroXmlWriter extends IndentingXMLWriter {

    private String removePrefix(final String qname) {
        final int prefixDelimIdx = qname.indexOf(':');
        final String result;
        if(prefixDelimIdx > -1) {
            result = qname.substring(prefixDelimIdx + 1);
        } else {
            result = qname;
        }
        return result;
    }

    private QName removePrefix(final QName qname) {
        return new QName(qname.getLocalPart(),  XMLConstants.NULL_NS_URI);
    }

    private CharSequence removeRestrictedChars(final CharSequence charSeq) {

        final CharBuffer buf = CharBuffer.allocate(charSeq.length());

        for(int i = 0; i < charSeq.length(); i++) {
            switch(charSeq.charAt(i)) {

                case '>':
                    //ignore char
                    break;

                default:
                    buf.append(charSeq.charAt(i));
            }
        }
        buf.compact();
        return buf;

        //TODO prohibit
        /*
        Unicode noncharacters (XML only prohibits #xFFFE and #xFFFF);
        Unicode C1 control characters;
        numeric character references to #xD;
        decimal character references;
        */
    }

    @Override
    public void startElement(final String namespaceUri, final String localName, final String qname) throws TransformerException {
        super.startElement("", localName, removePrefix(qname));
    }

    @Override
    public void startElement(final QName qname) throws TransformerException {
        super.startElement(qname);
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        super.endElement("", localName, removePrefix(qname));
    }

    @Override
    public void endElement(final QName qname) throws TransformerException {
        super.endElement(removePrefix(qname));
    }

    @Override
    public void namespace(final String prefix, final String nsURI) throws TransformerException {
        //no-op
    }

    @Override
    public void attribute(final String qname, final String value) throws TransformerException {
        if(qname != null && !qname.startsWith("xmlns")) {
            super.attribute(removePrefix(qname), removeRestrictedChars(value).toString());
        }
    }

    @Override
    public void attribute(final QName qname, final String value) throws TransformerException {
        if(qname != null && (!qname.getLocalPart().startsWith("xmlns") || (qname.getPrefix() != null && !qname.getPrefix().startsWith("xmlns")))) {
            super.attribute(removePrefix(qname), removeRestrictedChars(value).toString());
        }
    }

    @Override
    public void setDefaultNamespace(final String namespace) {
        //no-op
    }

    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        //no-op
    }

    @Override
    public void startCdataSection() {
        // empty
    }

    @Override
    public void endCdataSection() {
        // empty
    }

    @Override
    public void cdataSection(final char[] ch, final int start, final int len) throws TransformerException {
        //no-op
    }


    @Override
    public void startDocumentType(final String name, final String publicId, final String systemId) {
        // empty
    }

    @Override
    public void endDocumentType() {
        // empty
    }

    @Override
    public void documentType(String name, String publicId, String systemId) throws TransformerException {
        //no-op
    }

    @Override
    public void characters(final CharSequence chars) throws TransformerException {
        super.characters(removeRestrictedChars(chars));
    }

    @Override
    public void characters(final char[] ch, final int start, final int len) throws TransformerException {
        super.characters(removeRestrictedChars(String.valueOf(ch, start, len)));
    }

    @Override
    public void setOutputProperties(final Properties properties) {
        properties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        super.setOutputProperties(properties);    //To change body of overridden methods use File | Settings | File Templates.
    }
}
