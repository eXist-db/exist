/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id$
 */
package org.exist.util.serializer;

import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.exist.dom.QName;
import org.exist.util.XMLString;
import org.exist.util.serializer.encodings.CharacterSet;

/**
 * Write PLAIN TEXT to a writer. This class defines methods similar to SAX.
 * It deals with opening and closing tags, writing attributes and so on: they
 * are all ignored. Only real content is written!
 *
 * Note this is an initial version. Code cleanup needed. Original code is
 * commented for fast repair.
 *
 * @author dizzz
 * @author wolf
 */
public class TEXTWriter extends XMLWriter {
    
    protected final static Properties defaultProperties = new Properties();
    
    protected Writer writer = null;
    
    protected CharacterSet charSet = null;
    
    protected Properties outputProperties;
    
    private char[] charref = new char[10];
    
    public TEXTWriter() {
        // empty
    }
    
    public TEXTWriter(Writer writer) {
        super();
        this.writer = writer;
    }
    
    protected void reset() {
        super.reset();
        writer = null;
    }
    
    /**
     * Set the output properties.
     *
     * @param properties outputProperties
     */
    public void setOutputProperties(Properties properties) {
        if (properties == null)
            outputProperties = defaultProperties;
        else
            outputProperties = properties;
        String encoding = outputProperties.getProperty(OutputKeys.ENCODING,
                "UTF-8");
        charSet = CharacterSet.getCharacterSet(encoding);
    }
    
    /**
     * Set a new writer. Calling this method will reset the state of the object.
     *
     * @param writer
     */
    public void setWriter(Writer writer) {
        this.writer = writer;
        
    }
    
    public void startDocument() throws TransformerException {
        // empty
    }
    
    public void endDocument() throws TransformerException {
        // empty
    }
    
    public void startElement(String qname) throws TransformerException {
        // empty
    }
    
    public void startElement(QName qname) throws TransformerException {
        // empty
    }
    
    public void endElement(String qname) throws TransformerException {
        // empty
    }
    
    public void endElement(QName qname) throws TransformerException {
        // empty
    }
    
    public void namespace(String prefix, String nsURI) throws TransformerException {
        // empty
    }
    
    public void attribute(String qname, String value) throws TransformerException {
        // empty
    }
    
    public void attribute(QName qname, String value) throws TransformerException {
        // empty
    }
    
    public void characters(CharSequence chars) throws TransformerException {
        
        try {
            writeChars(chars, false);
        } catch (IOException e) {
            throw new TransformerException(e.getMessage(), e);
        }
    }
    
    public void characters(char[] ch, int start, int len) throws TransformerException {
        
        XMLString s = new XMLString(ch, start, len);
        characters(s);
        s.release();
    }
    
    public void processingInstruction(String target, String data) throws TransformerException {
        // empty
    }
    
    public void comment(CharSequence data) throws TransformerException {
        // empty
    }
    
    public void cdataSection(char[] ch, int start, int len) throws TransformerException {
        
        try {
            writer.write(ch, start, len);
            
        } catch (IOException e) {
            throw new TransformerException(e.getMessage(), e);
        }
    }
    
    public void documentType(String name, String publicId, String systemId) throws TransformerException {
        // empty
    }
    
    protected void closeStartTag(boolean isEmpty) throws TransformerException {
        // empty
    }
    
    protected void writeDeclaration() throws TransformerException {
        // empty
    }
    
    protected void writeDoctype(String rootElement) throws TransformerException {
        // empty
    }
    
    private final void writeChars(CharSequence s, boolean inAttribute) throws IOException {
        
        final int len = s.length();
        writeCharSeq(s, 0, len);
    }
    
    private void writeCharSeq(CharSequence ch, int start, int end) throws IOException {
        for (int i = start; i < end; i++) {
            writer.write(ch.charAt(i));
        }
    }
    
    protected void writeCharacterReference(char charval) throws IOException {
        int o = 0;
        charref[o++] = '&';
        charref[o++] = '#';
        charref[o++] = 'x';
        String code = Integer.toHexString(charval);
        int len = code.length();
        for (int k = 0; k < len; k++) {
            charref[o++] = code.charAt(k);
        }
        charref[o++] = ';';
        writer.write(charref, 0, o);
    }
}
