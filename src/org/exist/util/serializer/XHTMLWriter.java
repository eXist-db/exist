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
    
    private static boolean isEmptyTag(String tag) {
        return emptyTags.contains(tag);
    }
    
    private String currentTag;
    
    /**
     * 
     */
    public XHTMLWriter() {
        super();
    }

    /**
     * @param writer
     */
    public XHTMLWriter(Writer writer) {
        super(writer);
    }

    @Override
    public void startElement(QName qname) throws TransformerException {
        
        final QName xhtmlQName = removeXhtmlPrefix(qname);
        
        super.startElement(xhtmlQName);
        currentTag = xhtmlQName.getStringValue();
    }
    
    @Override
    public void endElement(QName qname) throws TransformerException {
        final QName xhtmlQName = removeXhtmlPrefix(qname);
        
        super.endElement(xhtmlQName);
    }
    
    private QName removeXhtmlPrefix(QName qname) {
        String prefix = qname.getPrefix();
        String namespaceURI = qname.getNamespaceURI();
        if(prefix != null && prefix.length() > 0 && namespaceURI != null && namespaceURI.equals(Namespaces.XHTML_NS)) {
            return new QName(qname.getLocalName(), namespaceURI);   
        }
        
        return qname;
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qname) throws TransformerException {
        
        final String xhtmlQName = removeXhtmlPrefix(namespaceURI, qname);
        
        super.startElement(namespaceURI, localName, xhtmlQName);
        currentTag = xhtmlQName;
    }
    
    @Override
    public void endElement(String namespaceURI, String localName, String qname) throws TransformerException {
        
        final String xhtmlQName = removeXhtmlPrefix(namespaceURI, qname);
        
        super.endElement(namespaceURI, localName, xhtmlQName);
    }
    
    private String removeXhtmlPrefix(String namespaceURI, String qname) {
        
        int pos = qname.indexOf(':');
        if(pos > 0 && namespaceURI != null && namespaceURI.equals(Namespaces.XHTML_NS)) {
            return qname.substring(pos+1);
        }
        
        return qname;
    }
    
    @Override
    protected void closeStartTag(boolean isEmpty) throws TransformerException {
        try {
            if (tagIsOpen) {
                if (isEmpty) {
                    if (isEmptyTag(currentTag))
                        writer.write(" />");
                    else {
                        writer.write('>');
                        writer.write("</");
                        writer.write(currentTag);
                        writer.write('>');
                    }
                } else
                    writer.write('>');
                tagIsOpen = false;
            }
        } catch (IOException e) {
            throw new TransformerException(e.getMessage(), e);
        }
    }
}
