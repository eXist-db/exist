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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;

public class IndentingXMLWriter extends XMLWriter {

    private final static Logger LOG = LogManager.getLogger(IndentingXMLWriter.class);

    private boolean indent = false;
    private int indentAmount = 4;
    private String indentChars = "                                                                                           ";
    private int level = 0;
    private boolean afterTag = false;
    private boolean sameline = false;
    private boolean whitespacePreserve = false;
    private final Deque<Integer> whitespacePreserveStack = new ArrayDeque<>();

    public IndentingXMLWriter() {
        super();
    }

    /**
     * @param writer A writer to send the serialized XML output to
     */
    public IndentingXMLWriter(final Writer writer) {
        super(writer);
    }

    @Override
    public void setWriter(final Writer writer) {
        super.setWriter(writer);
        level = 0;
        afterTag = false;
        sameline = false;
        whitespacePreserveStack.clear();
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        if (afterTag && !isInlineTag(namespaceURI, localName)) {
            indent();
        }
        super.startElement(namespaceURI, localName, qname);
        addIndent();
        afterTag = true;
        sameline = true;
    }

    @Override
    public void startElement(final QName qname) throws TransformerException {
        if (afterTag && !isInlineTag(qname.getNamespaceURI(), qname.getLocalPart())) {
            indent();
        }
        super.startElement(qname);
        addIndent();
        afterTag = true;
        sameline = true;
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        endIndent(namespaceURI, localName);
        super.endElement(namespaceURI, localName, qname);
        popWhitespacePreserve(); // apply ancestor's xml:space value _after_ end element
        sameline = isInlineTag(namespaceURI, localName);
        afterTag = true;
    }

    @Override
    public void endElement(final QName qname) throws TransformerException {
        endIndent(qname.getNamespaceURI(), qname.getLocalPart());
        super.endElement(qname);
        popWhitespacePreserve(); // apply ancestor's xml:space value _after_ end element
        sameline = isInlineTag(qname.getNamespaceURI(), qname.getLocalPart());
        afterTag = true;
    }

    @Override
    public void characters(CharSequence chars) throws TransformerException {
        final int start = 0;
        final int length = chars.length();
        if (length == 0) {
            return;    // whitespace only: skip
        }
        if (length < chars.length()) {
            chars = chars.subSequence(start, length);    // drop whitespace
        }
        for (int i = 0; i < chars.length(); i++) {
            if (chars.charAt(i) == '\n') {
                sameline = false;
            }
        }
        afterTag = false;
        super.characters(chars);
    }

    @Override
    public void comment(final CharSequence data) throws TransformerException {
        super.comment(data);
        afterTag = true;
    }

    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        super.processingInstruction(target, data);
        afterTag = true;
    }

    @Override
    public void endDocument() throws TransformerException {
        super.endDocument();
        if ("yes".equals(outputProperties.getProperty(EXistOutputKeys.INSERT_FINAL_NEWLINE, "no"))) {
            super.characters("\n");
        }
    }

    @Override
    public void endDocumentType() throws TransformerException {
        super.endDocumentType();
        super.characters("\n");
        sameline = false;
    }

    @Override
    public void setOutputProperties(final Properties properties) {
        super.setOutputProperties(properties);
        final String option = outputProperties.getProperty(EXistOutputKeys.INDENT_SPACES, "4");
        try {
            indentAmount = Integer.parseInt(option);
        } catch (final NumberFormatException e) {
            LOG.warn("Invalid indentation value: '{}'", option);
        }
        indent = "yes".equals(outputProperties.getProperty(OutputKeys.INDENT, "no"));
    }

    @Override
    public void attribute(final String qname, final CharSequence value) throws TransformerException {
        if ("xml:space".equals(qname)) {
            pushWhitespacePreserve(value);
        }
        super.attribute(qname, value);
    }

    @Override
    public void attribute(final QName qname, final CharSequence value)
            throws TransformerException {
        if ("xml".equals(qname.getPrefix()) && "space".equals(qname.getLocalPart())) {
            pushWhitespacePreserve(value);
        }
        super.attribute(qname, value);
    }

    protected void pushWhitespacePreserve(final CharSequence value) {
        if (value.equals("preserve")) {
            whitespacePreserve = true;
            whitespacePreserveStack.push(-level);
        } else if (value.equals("default")) {
            whitespacePreserve = false;
            whitespacePreserveStack.push(level);
        }
    }

    protected void popWhitespacePreserve() {
        if (!whitespacePreserveStack.isEmpty() && Math.abs(whitespacePreserveStack.peek()) > level) {
            whitespacePreserveStack.pop();
            if (whitespacePreserveStack.isEmpty() || whitespacePreserveStack.peek() >= 0) {
                whitespacePreserve = false;
            } else {
                whitespacePreserve = true;
            }
        }
    }

    protected boolean isInlineTag(final String namespaceURI, final String localName) {
        return isMatchTag(namespaceURI, localName);
    }

    private boolean isMatchTag(final String namespaceURI, final String localName) {
        return namespaceURI != null && namespaceURI.equals(Namespaces.EXIST_NS) && "match".equals(localName);
    }

    protected void addSpaceIfIndent() throws IOException {
        if (!indent || whitespacePreserve) {
            return;
        }
        writer.write(' ');
    }

    protected void indent() throws TransformerException {
        if (!indent || whitespacePreserve) {
            return;
        }
        final int spaces = indentAmount * level;
        while (spaces >= indentChars.length()) {
            indentChars += indentChars;
        }
        super.characters("\n");
        super.characters(indentChars.subSequence(0, spaces));
        sameline = false;
    }

    protected void addIndent() {
        level++;
    }

    protected void endIndent(final String namespaceURI, final String localName) throws TransformerException {
        level--;
        if (afterTag && !sameline && !isInlineTag(namespaceURI, localName)) {
            indent();
        }
    }
}
