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

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.exist.dom.QName;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Writer;

/**
 * HTML5 writer which does not produce well-formed XHTML.
 *
 * @author Wolfgang
 */
public class HTML5Writer extends XHTML5Writer {

    /**
     * Holds the names of the attributes that are considered boolean
     * according to http://www.w3.org/TR/html51/single-page.html.
     * 
     * The value of these attributes are written if they equal the
     * name of the attribute. For example: checked="checked" will be
     * written as checked.
     * 
     * See https://github.com/eXist-db/exist/issues/777 for details. 
     */
    private static final ObjectSet<String> BOOLEAN_ATTRIBUTE_NAMES = new ObjectOpenHashSet<>(68);
    static {
        BOOLEAN_ATTRIBUTE_NAMES.add("allowFullscreen");
        BOOLEAN_ATTRIBUTE_NAMES.add("async");
        BOOLEAN_ATTRIBUTE_NAMES.add("autofocus");
        BOOLEAN_ATTRIBUTE_NAMES.add("autoplay");
        BOOLEAN_ATTRIBUTE_NAMES.add("badInput");
        BOOLEAN_ATTRIBUTE_NAMES.add("checked");
        BOOLEAN_ATTRIBUTE_NAMES.add("closed");
        BOOLEAN_ATTRIBUTE_NAMES.add("commandChecked");
        BOOLEAN_ATTRIBUTE_NAMES.add("commandDisabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("commandHidden");
        BOOLEAN_ATTRIBUTE_NAMES.add("compact");
        BOOLEAN_ATTRIBUTE_NAMES.add("complete");
        BOOLEAN_ATTRIBUTE_NAMES.add("controls");
        BOOLEAN_ATTRIBUTE_NAMES.add("cookieEnabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("customError");
        BOOLEAN_ATTRIBUTE_NAMES.add("declare");
        BOOLEAN_ATTRIBUTE_NAMES.add("default");
        BOOLEAN_ATTRIBUTE_NAMES.add("defaultChecked");
        BOOLEAN_ATTRIBUTE_NAMES.add("defaultMuted");
        BOOLEAN_ATTRIBUTE_NAMES.add("defaultSelected");
        BOOLEAN_ATTRIBUTE_NAMES.add("defer");
        BOOLEAN_ATTRIBUTE_NAMES.add("disabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("draggable");
        BOOLEAN_ATTRIBUTE_NAMES.add("enabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("ended");
        BOOLEAN_ATTRIBUTE_NAMES.add("formNoValidate");
        BOOLEAN_ATTRIBUTE_NAMES.add("hidden");
        BOOLEAN_ATTRIBUTE_NAMES.add("indeterminate");
        BOOLEAN_ATTRIBUTE_NAMES.add("isContentEditable");
        BOOLEAN_ATTRIBUTE_NAMES.add("isMap");
        BOOLEAN_ATTRIBUTE_NAMES.add("itemScope");
        BOOLEAN_ATTRIBUTE_NAMES.add("javaEnabled");
        BOOLEAN_ATTRIBUTE_NAMES.add("loop");
        BOOLEAN_ATTRIBUTE_NAMES.add("multiple");
        BOOLEAN_ATTRIBUTE_NAMES.add("muted");
        BOOLEAN_ATTRIBUTE_NAMES.add("noHref");
        BOOLEAN_ATTRIBUTE_NAMES.add("noResize");
        BOOLEAN_ATTRIBUTE_NAMES.add("noShade");
        BOOLEAN_ATTRIBUTE_NAMES.add("noValidate");
        BOOLEAN_ATTRIBUTE_NAMES.add("noWrap");
        BOOLEAN_ATTRIBUTE_NAMES.add("onLine");
        BOOLEAN_ATTRIBUTE_NAMES.add("open");
        BOOLEAN_ATTRIBUTE_NAMES.add("patternMismatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("pauseOnExit");
        BOOLEAN_ATTRIBUTE_NAMES.add("paused");
        BOOLEAN_ATTRIBUTE_NAMES.add("persisted");
        BOOLEAN_ATTRIBUTE_NAMES.add("rangeOverflow");
        BOOLEAN_ATTRIBUTE_NAMES.add("rangeUnderflow");
        BOOLEAN_ATTRIBUTE_NAMES.add("readOnly");
        BOOLEAN_ATTRIBUTE_NAMES.add("required");
        BOOLEAN_ATTRIBUTE_NAMES.add("reversed");
        BOOLEAN_ATTRIBUTE_NAMES.add("scoped");
        BOOLEAN_ATTRIBUTE_NAMES.add("seamless");
        BOOLEAN_ATTRIBUTE_NAMES.add("seeking");
        BOOLEAN_ATTRIBUTE_NAMES.add("selected");
        BOOLEAN_ATTRIBUTE_NAMES.add("sortable");
        BOOLEAN_ATTRIBUTE_NAMES.add("spellcheck");
        BOOLEAN_ATTRIBUTE_NAMES.add("stepMismatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("tooLong");
        BOOLEAN_ATTRIBUTE_NAMES.add("tooShort");
        BOOLEAN_ATTRIBUTE_NAMES.add("translate");
        BOOLEAN_ATTRIBUTE_NAMES.add("trueSpeed");
        BOOLEAN_ATTRIBUTE_NAMES.add("typeMismatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("typeMustMatch");
        BOOLEAN_ATTRIBUTE_NAMES.add("valid");
        BOOLEAN_ATTRIBUTE_NAMES.add("valueMissing");
        BOOLEAN_ATTRIBUTE_NAMES.add("visible");
        BOOLEAN_ATTRIBUTE_NAMES.add("willValidate");
    }

    private static final ObjectSet<String> EMPTY_TAGS = new ObjectOpenHashSet<>(31);
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

    private static final ObjectSet<String> RAW_TEXT_ELEMENTS = new ObjectOpenHashSet<>(31);
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
            if (!(BOOLEAN_ATTRIBUTE_NAMES.contains(qname) && qname.equals(value))) {
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
            final String localPart = qname.getLocalPart();
            writer.write(localPart);
            if (!(BOOLEAN_ATTRIBUTE_NAMES.contains(localPart) && localPart.equals(value))) {
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
