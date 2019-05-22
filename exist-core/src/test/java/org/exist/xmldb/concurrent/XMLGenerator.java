/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.xmldb.concurrent;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Random;

/**
 * @author wolf
 */
public class XMLGenerator {
    private final String[] words;
    private final int elementCnt;
    private final int attrCnt;
    private final int depth;
    private final Random random;
    private final boolean useNamespaces;
    
    public XMLGenerator(final int elementCnt, final int attrCnt, final int depth, final String[] words, final boolean useNamespaces) {
        this.elementCnt = elementCnt;
        this.attrCnt = attrCnt;
        this.depth = depth;
        this.words = words;
        this.useNamespaces = useNamespaces;
        this.random = new Random(System.currentTimeMillis());
    }
    
    public void generateXML(final Writer writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.write("<ROOT-ELEMENT>");
        
        for(int i = 0; i < elementCnt; i++) {
            writeElement(writer, 0);
        }
        
        writer.write("</ROOT-ELEMENT>");
    }
    
    public String generateElement() throws IOException {
        StringWriter writer = new StringWriter();
        writeElement(writer, 0);
        return writer.toString();
    }
    
    protected void writeElement(final Writer writer, final int level) throws IOException {
        writer.write('<');
        if (useNamespaces) {
            writer.write("t:");
        }
        writer.write("ELEMENT");
        if(level > 0) {
            writer.write('-');
            writer.write(Integer.toString(level));
        }
        if (useNamespaces) {
            writer.write(" xmlns:t=\"urn:test\"");
        }
        for(int i = 0; i < attrCnt; i++) {
            writer.write(' ');
            if (useNamespaces) {
                writer.write("t:");
            }
            writer.write("attribute-");
            writer.write(Integer.toString(i));
            writer.write("=\"");
            writer.write(generateText(1));
            writer.write("\"");
        }
        writer.write(">\n");
        if(level < depth - 1)
            writeElement(writer, level + 1);
        else
            writer.write(generateText(20));
        writer.write("\n</");
        if (useNamespaces)
            writer.write("t:");
        writer.write("ELEMENT");
        if(level > 0) {
            writer.write('-');
            writer.write(Integer.toString(level));
        }
        writer.write(">\n");
    }
    
    public String generateText(final int len) {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if(i > 0) {
                buf.append(' ');
            }
            final int n = random.nextInt(words.length);
            buf.append(words[n]);
        }
        return buf.toString();
    }
}
