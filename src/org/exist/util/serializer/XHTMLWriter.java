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
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.util.hashtable.ObjectHashSet;

/**
 * @author wolf
 *
 */
public class XHTMLWriter extends IndentingXMLWriter {

    private final static ObjectHashSet<String> emptyTags = new ObjectHashSet<String>(31);
    
    static {
        emptyTags.add("area");
        emptyTags.add("base");
        emptyTags.add("br");
        emptyTags.add("col");
        emptyTags.add("hr");
        emptyTags.add("img");
        emptyTags.add("input");
        emptyTags.add("link");
        emptyTags.add("meta");
        emptyTags.add("basefont");
        emptyTags.add("frame");
        emptyTags.add("isindex");
        emptyTags.add("param");
    }
    
    private final static ObjectHashSet<String> inlineTags = new ObjectHashSet<String>(31);
    
    static {
    	inlineTags.add("a");
    	inlineTags.add("abbr");
    	inlineTags.add("acronym");
    	inlineTags.add("b");
    	inlineTags.add("bdo");
    	inlineTags.add("big");
    	inlineTags.add("br");
    	inlineTags.add("button");
    	inlineTags.add("cite");
    	inlineTags.add("code");
    	inlineTags.add("del");
    	inlineTags.add("dfn");
    	inlineTags.add("em");
    	inlineTags.add("i");
    	inlineTags.add("img");
    	inlineTags.add("input");
    	inlineTags.add("kbd");
    	inlineTags.add("label");
    	inlineTags.add("q");
    	inlineTags.add("samp");
    	inlineTags.add("select");
    	inlineTags.add("small");
    	inlineTags.add("span");
    	inlineTags.add("strong");
    	inlineTags.add("sub");
    	inlineTags.add("sup");
    	inlineTags.add("textarea");
    	inlineTags.add("tt");
    	inlineTags.add("var");
    }
    
    private static boolean isEmptyTag(final String tag) {
        return emptyTags.contains(tag);
    }
    
    protected String currentTag;
    
    /**
     * 
     */
    public XHTMLWriter() {
        super();
    }

    /**
     * @param writer
     */
    public XHTMLWriter(final Writer writer) {
        super(writer);
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
    
    private QName removeXhtmlPrefix(final QName qname) {
        final String prefix = qname.getPrefix();
        final String namespaceURI = qname.getNamespaceURI();
        if(prefix != null && prefix.length() > 0 && namespaceURI != null && namespaceURI.equals(Namespaces.XHTML_NS)) {
            haveCollapsedXhtmlPrefix = true;
            return new QName(qname.getLocalName(), namespaceURI);   
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
    
    private String removeXhtmlPrefix(final String namespaceURI, final String qname) {
        
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
