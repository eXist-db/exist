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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.exist.Namespaces;
import org.exist.dom.QName;

/**
 * @author wolf
 *
 */
public class XHTMLWriter extends IndentingXMLWriter {

    /**
     * HTML boolean attributes per HTML 4.01 and HTML5 spec.
     * When method="html" and the attribute value equals the attribute name
     * (case-insensitive), the attribute is minimized to just the name.
     */
    protected static final ObjectSet<String> BOOLEAN_ATTRIBUTES = new ObjectOpenHashSet<>(31);
    static {
        BOOLEAN_ATTRIBUTES.add("checked");
        BOOLEAN_ATTRIBUTES.add("compact");
        BOOLEAN_ATTRIBUTES.add("declare");
        BOOLEAN_ATTRIBUTES.add("defer");
        BOOLEAN_ATTRIBUTES.add("disabled");
        BOOLEAN_ATTRIBUTES.add("ismap");
        BOOLEAN_ATTRIBUTES.add("multiple");
        BOOLEAN_ATTRIBUTES.add("nohref");
        BOOLEAN_ATTRIBUTES.add("noresize");
        BOOLEAN_ATTRIBUTES.add("noshade");
        BOOLEAN_ATTRIBUTES.add("nowrap");
        BOOLEAN_ATTRIBUTES.add("readonly");
        BOOLEAN_ATTRIBUTES.add("selected");
    }

    protected static final ObjectSet<String> EMPTY_TAGS = new ObjectOpenHashSet<>(31);
    static {
        EMPTY_TAGS.add("area");
        EMPTY_TAGS.add("base");
        EMPTY_TAGS.add("br");
        EMPTY_TAGS.add("col");
        EMPTY_TAGS.add("embed");
        EMPTY_TAGS.add("hr");
        EMPTY_TAGS.add("img");
        EMPTY_TAGS.add("input");
        EMPTY_TAGS.add("link");
        EMPTY_TAGS.add("meta");
        EMPTY_TAGS.add("basefont");
        EMPTY_TAGS.add("frame");
        EMPTY_TAGS.add("isindex");
        EMPTY_TAGS.add("param");
    }
    
    protected static final ObjectSet<String> INLINE_TAGS = new ObjectOpenHashSet<>(31);
    
    static {
    	INLINE_TAGS.add("a");
    	INLINE_TAGS.add("abbr");
    	INLINE_TAGS.add("acronym");
    	INLINE_TAGS.add("b");
    	INLINE_TAGS.add("bdo");
    	INLINE_TAGS.add("big");
    	INLINE_TAGS.add("br");
    	INLINE_TAGS.add("button");
    	INLINE_TAGS.add("cite");
    	INLINE_TAGS.add("code");
    	INLINE_TAGS.add("del");
    	INLINE_TAGS.add("dfn");
    	INLINE_TAGS.add("em");
    	INLINE_TAGS.add("i");
    	INLINE_TAGS.add("img");
    	INLINE_TAGS.add("input");
    	INLINE_TAGS.add("kbd");
    	INLINE_TAGS.add("label");
    	INLINE_TAGS.add("q");
    	INLINE_TAGS.add("samp");
    	INLINE_TAGS.add("select");
    	INLINE_TAGS.add("small");
    	INLINE_TAGS.add("span");
    	INLINE_TAGS.add("strong");
    	INLINE_TAGS.add("sub");
    	INLINE_TAGS.add("sup");
    	INLINE_TAGS.add("textarea");
    	INLINE_TAGS.add("tt");
    	INLINE_TAGS.add("var");
    }
    
    protected String currentTag;
    protected boolean inHead = false;
    protected boolean contentTypeMetaWritten = false;

    protected final ObjectSet<String> emptyTags;
    protected final ObjectSet<String> inlineTags;

    /**
     * 
     */
    public XHTMLWriter() {
        this(EMPTY_TAGS, INLINE_TAGS);
    }

    public XHTMLWriter(ObjectSet<String> emptyTags, ObjectSet<String> inlineTags) {
        super();
        this.emptyTags = emptyTags;
        this.inlineTags = inlineTags;
    }

    public XHTMLWriter(final Writer writer) {
        this(writer, EMPTY_TAGS, INLINE_TAGS);
    }

    /**
     * @param writer the writer
     * @param emptyTags tags that are allowed to be empty
     * @param inlineTags tags that should be written inline
     */
    public XHTMLWriter(final Writer writer, ObjectSet<String> emptyTags, ObjectSet<String> inlineTags) {
        super(writer);
        this.emptyTags = emptyTags;
        this.inlineTags = inlineTags;
    }

    @Override
    protected void resetObjectState() {
        super.resetObjectState();
        inHead = false;
        contentTypeMetaWritten = false;
    }

    protected boolean isEmptyTag(final String tag) {
        return emptyTags.contains(tag);
    }

    boolean haveCollapsedXhtmlPrefix = false;

    @Override
    public void startElement(final QName qname) throws TransformerException {

        final QName xhtmlQName = removeXhtmlPrefix(qname);

        super.startElement(xhtmlQName);
        currentTag = xhtmlQName.getStringValue();
        if ("head".equalsIgnoreCase(xhtmlQName.getLocalPart())) {
            inHead = true;
            writeContentTypeMeta();
        }
    }
    
    @Override
    public void endElement(final QName qname) throws TransformerException {
        final QName xhtmlQName = removeXhtmlPrefix(qname);
        if (inHead && "head".equalsIgnoreCase(xhtmlQName.getLocalPart())) {
            inHead = false;
        }

        super.endElement(xhtmlQName);

        haveCollapsedXhtmlPrefix = false;
    }
    
    protected QName removeXhtmlPrefix(final QName qname) {
        final String prefix = qname.getPrefix();
        final String namespaceURI = qname.getNamespaceURI();
        if(prefix != null && !prefix.isEmpty() && namespaceURI != null && namespaceURI.equals(Namespaces.XHTML_NS)) {
            haveCollapsedXhtmlPrefix = true;
            return new QName(qname.getLocalPart(), namespaceURI);
        }
        
        return qname;
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {

        final String xhtmlQName = removeXhtmlPrefix(namespaceURI, qname);

        super.startElement(namespaceURI, localName, xhtmlQName);
        currentTag = xhtmlQName;
        if ("head".equalsIgnoreCase(localName)) {
            inHead = true;
            writeContentTypeMeta();
        }
    }
    
    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        if (inHead && "head".equalsIgnoreCase(localName)) {
            inHead = false;
        }

        final String xhtmlQName = removeXhtmlPrefix(namespaceURI, qname);

        super.endElement(namespaceURI, localName, xhtmlQName);

        haveCollapsedXhtmlPrefix = false;
    }
    
    protected String removeXhtmlPrefix(final String namespaceURI, final String qname) {
        
        final int pos = qname.indexOf(':');
        if(pos > 0 && namespaceURI != null && namespaceURI.equals(Namespaces.XHTML_NS)) {
            haveCollapsedXhtmlPrefix = true;
            return qname.substring(pos+1);
            
        }
        
        return qname;
    }

    @Override
    public void namespace(final String prefix, final String nsURI) throws TransformerException {
        if(haveCollapsedXhtmlPrefix && prefix != null && !prefix.isEmpty() && nsURI.equals(Namespaces.XHTML_NS)) {
            return; //dont output the xmlns:prefix for the collapsed nodes prefix
        }
        
        super.namespace(prefix, nsURI);
    }
    
    
    @Override
    protected void closeStartTag(final boolean isEmpty) throws TransformerException {
        try {
            if (tagIsOpen) {
                if (isEmpty) {
                    if (isEmptyTag(currentTag)) {
                        // For method="html", use HTML-style void tags (<br>)
                        // For method="xhtml", use XHTML-style (<br />)
                        if (isHtmlMethod()) {
                            getWriter().write(">");
                        } else {
                            getWriter().write(" />");
                        }
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

    /**
     * Returns true if the output method is "html" (not "xhtml").
     * HTML uses void element syntax (<br>) while XHTML uses self-closing (<br />).
     */
    private boolean isHtmlMethod() {
        if (outputProperties != null) {
            final String method = outputProperties.getProperty(javax.xml.transform.OutputKeys.METHOD);
            return "html".equalsIgnoreCase(method);
        }
        return false;
    }
    
    @Override
    public void attribute(final QName qname, final CharSequence value) throws TransformerException {
        // For method="html", minimize boolean attributes when value matches name
        if (isHtmlMethod() && isBooleanAttribute(qname.getLocalPart(), value)) {
            try {
                if (!tagIsOpen) {
                    characters(value);
                    return;
                }
                final Writer w = getWriter();
                w.write(' ');
                w.write(qname.getLocalPart());
                // Don't write ="value" — minimized form
            } catch (final IOException ioe) {
                throw new TransformerException(ioe.getMessage(), ioe);
            }
            return;
        }
        super.attribute(qname, value);
    }

    @Override
    public void attribute(final String qname, final CharSequence value) throws TransformerException {
        if (isHtmlMethod() && isBooleanAttribute(qname, value)) {
            try {
                if (!tagIsOpen) {
                    characters(value);
                    return;
                }
                final Writer w = getWriter();
                w.write(' ');
                w.write(qname);
            } catch (final IOException ioe) {
                throw new TransformerException(ioe.getMessage(), ioe);
            }
            return;
        }
        super.attribute(qname, value);
    }

    private boolean isBooleanAttribute(final String attrName, final CharSequence value) {
        return BOOLEAN_ATTRIBUTES.contains(attrName.toLowerCase(java.util.Locale.ROOT))
                && attrName.equalsIgnoreCase(value.toString());
    }

    /**
     * For HTML serialization, cdata-section-elements is ignored per the
     * W3C serialization spec — CDATA sections are not valid in HTML.
     */
    @Override
    protected boolean shouldUseCdataSections() {
        if (isHtmlMethod()) {
            return false;
        }
        return super.shouldUseCdataSections();
    }

    @Override
    protected boolean isInlineTag(final String namespaceURI, final String localName) {
    	return (namespaceURI == null || namespaceURI.isEmpty() || Namespaces.XHTML_NS.equals(namespaceURI))
    			&& inlineTags.contains(localName);
    }

    /**
     * Write a meta content-type tag as the first child of head when
     * include-content-type is enabled (the default per W3C Serialization 3.1).
     */
    protected void writeContentTypeMeta() throws TransformerException {
        if (contentTypeMetaWritten || outputProperties == null) {
            return;
        }
        final String includeContentType = outputProperties.getProperty("include-content-type", "yes");
        if (!"yes".equals(includeContentType)) {
            return;
        }
        contentTypeMetaWritten = true;
        try {
            final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
            final String mediaType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE, "text/html");
            closeStartTag(false);
            final Writer writer = getWriter();
            writer.write("<meta http-equiv=\"Content-Type\" content=\"");
            writer.write(mediaType);
            writer.write("; charset=");
            writer.write(encoding);
            writer.write("\">");
        } catch (IOException e) {
            throw new TransformerException(e.getMessage(), e);
        }
    }
}
