/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

import org.exist.dom.QName;
import org.exist.util.hashtable.ObjectHashSet;

/**
 * @author wolf
 *
 */
public class XHTMLWriter extends IndentingXMLWriter {

    private static ObjectHashSet emptyTags = new ObjectHashSet(31);
    
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

    public void startElement(QName qname) throws TransformerException {
        super.startElement(qname);
        currentTag = qname.getStringValue();
    }
    
    public void startElement(String qname) throws TransformerException {
        super.startElement(qname);
        currentTag = qname;
    }
    
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
