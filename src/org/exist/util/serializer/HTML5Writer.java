/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist-db Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.util.serializer;

import org.exist.dom.QName;
import org.exist.util.hashtable.ObjectHashSet;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Writer;

/**
 * HTML5 writer which does not produce well-formed XHTML.
 *
 * @author Wolfgang
 */
public class HTML5Writer extends XHTML5Writer {

    private final static ObjectHashSet<String> EMPTY_TAGS = new ObjectHashSet<String>(31);
    static {
        EMPTY_TAGS.add("area");
        EMPTY_TAGS.add("base");
        EMPTY_TAGS.add("br");
        EMPTY_TAGS.add("col");
        EMPTY_TAGS.add("embed");
        EMPTY_TAGS.add("hr");
        EMPTY_TAGS.add("img");
        EMPTY_TAGS.add("input");
        EMPTY_TAGS.add("keygen");
        EMPTY_TAGS.add("link");
        EMPTY_TAGS.add("meta");
        EMPTY_TAGS.add("param");
        EMPTY_TAGS.add("source");
        EMPTY_TAGS.add("track");
        EMPTY_TAGS.add("wbr");
    }

    private final static ObjectHashSet<String> RAW_TEXT_ELEMENTS = new ObjectHashSet<String>(31);
    static {
        RAW_TEXT_ELEMENTS.add("script");
        RAW_TEXT_ELEMENTS.add("style");
        RAW_TEXT_ELEMENTS.add("textarea");
        RAW_TEXT_ELEMENTS.add("title");
    }

    public HTML5Writer() {
        super(EMPTY_TAGS, INLINE_TAGS);
    }

    public HTML5Writer(Writer writer) {
        super(writer, EMPTY_TAGS, INLINE_TAGS);
    }

    @Override
    public void endElement(QName qname) throws TransformerException {
        if (!isEmptyTag(qname.getLocalPart())) {
            super.endElement(qname);
        } else {
            closeStartTag(true);
            endIndent(qname.getNamespaceURI(), qname.getLocalPart());
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qname) throws TransformerException {
        if (!isEmptyTag(localName)) {
            super.endElement(namespaceURI, localName, qname);
        } else {
            closeStartTag(true);
            endIndent(namespaceURI, localName);
        }
    }

    @Override
    public void attribute(String qname, String value) throws TransformerException {
        try {
            if(!tagIsOpen) {
                characters(value);
                return;
            }
            final Writer writer = getWriter();
            writer.write(' ');
            writer.write(qname);
            if (!qname.equals(value)) {
                writer.write("=\"");
                writeChars(value, true);
                writer.write('"');
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    @Override
    public void attribute(QName qname, String value) throws TransformerException {
        try {
            if(!tagIsOpen) {
                characters(value);
                return;
                // throw new TransformerException("Found an attribute outside an
                // element");
            }
            final Writer writer = getWriter();
            writer.write(' ');
            if(qname.getPrefix() != null && qname.getPrefix().length() > 0) {
                writer.write(qname.getPrefix());
                writer.write(':');
            }
            if (!qname.getLocalPart().equals(value)) {
                writer.write(qname.getLocalPart());
                writer.write("=\"");
                writeChars(value, true);
                writer.write('"');
            }
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    @Override
    public void namespace(String prefix, String nsURI) throws TransformerException {
        // no namespaces allowed in HTML5
    }

    @Override
    protected void closeStartTag(boolean isEmpty) throws TransformerException {
        try {
            if (tagIsOpen) {
                if (isEmpty) {
                    if (isEmptyTag(currentTag)) {
                        getWriter().write(">");
                    } else {
                        getWriter().write('>');
                        getWriter().write("</");
                        getWriter().write(currentTag);
                        getWriter().write('>');
                    }
                } else {
                    getWriter().write('>');
                }
                tagIsOpen = false;
            }
        } catch (final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }

    @Override
    protected boolean needsEscape(char ch) {
        if (RAW_TEXT_ELEMENTS.contains(currentTag)) {
            return false;
        }
        return super.needsEscape(ch);
    }
}
