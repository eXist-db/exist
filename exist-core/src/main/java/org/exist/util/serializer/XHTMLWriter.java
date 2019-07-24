/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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

import java.io.IOException;
import java.io.Writer;
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

    protected static final ObjectSet<String> EMPTY_TAGS = new ObjectOpenHashSet<>(31);
    static {
        EMPTY_TAGS.add("area");
        EMPTY_TAGS.add("base");
        EMPTY_TAGS.add("br");
        EMPTY_TAGS.add("col");
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

    protected boolean isEmptyTag(final String tag) {
        return emptyTags.contains(tag);
    }

    boolean haveCollapsedXhtmlPrefix = false;

    @Override
    public void startElement(final QName qname) throws TransformerException {
        
        final QName xhtmlQName = removeXhtmlPrefix(qname);
        
        super.startElement(xhtmlQName);
        currentTag = xhtmlQName.getStringValue();
    }
    
    @Override
    public void endElement(final QName qname) throws TransformerException {
        final QName xhtmlQName = removeXhtmlPrefix(qname);
        
        super.endElement(xhtmlQName);
        
        haveCollapsedXhtmlPrefix = false;
    }
    
    protected QName removeXhtmlPrefix(final QName qname) {
        final String prefix = qname.getPrefix();
        final String namespaceURI = qname.getNamespaceURI();
        if(prefix != null && prefix.length() > 0 && namespaceURI != null && namespaceURI.equals(Namespaces.XHTML_NS)) {
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
    }
    
    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        
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
        if(haveCollapsedXhtmlPrefix && prefix != null && prefix.length() > 0 && nsURI.equals(Namespaces.XHTML_NS)) {
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
                        getWriter().write(" />");
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
    protected boolean isInlineTag(final String namespaceURI, final String localName) {
    	return (namespaceURI == null || "".equals(namespaceURI) || Namespaces.XHTML_NS.equals(namespaceURI))
    			&& inlineTags.contains(localName);
    }
}
