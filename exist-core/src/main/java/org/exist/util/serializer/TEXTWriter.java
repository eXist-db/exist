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
import org.exist.util.CharSlice;
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
    
    private char[] charref = new char[10];
    
    public TEXTWriter() {
        // empty
        super();
    }
    
    public TEXTWriter(final Writer writer) {
        super(writer);
    }
    
    @Override
    protected void reset() {
        super.reset();
    }
    
    /**
     * Set the output properties.
     *
     * @param properties outputProperties
     */
    @Override
    public void setOutputProperties(final Properties properties) {
        if (properties == null) {
            outputProperties = defaultProperties;
        } else {
            outputProperties = properties;
        }
        final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
        charSet = CharacterSet.getCharacterSet(encoding);
    }

    @Override
    public void setWriter(final Writer writer) {
        this.writer = writer;
    }
    
    @Override
    public void startDocument() throws TransformerException {
        // empty
    }
    
    @Override
    public void endDocument() throws TransformerException {
        // empty
    }
    
    
    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        // empty
    }
    
    @Override
    public void startElement(final QName qname) throws TransformerException {
        // empty
    }
    
    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        // empty
    }
    
    @Override
    public void endElement(final QName qname) throws TransformerException {
        // empty
    }
    
    @Override
    public void namespace(final String prefix, final String nsURI) throws TransformerException {
        // empty
    }
    
    @Override
    public void attribute(final String qname, final String value) throws TransformerException {
        // empty
    }
    
    @Override
    public void attribute(final QName qname, final String value) throws TransformerException {
        // empty
    }
    
    @Override
    public void characters(final CharSequence chars) throws TransformerException {
        
        try {
            writeChars(chars, false);
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }
    
    @Override
    public void characters(final char[] ch, final int start, final int len) throws TransformerException {
        characters(new CharSlice(ch, start, len));
    }
    
    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        // empty
    }
    
    @Override
    public void comment(final CharSequence data) throws TransformerException {
        // empty
    }

    @Override
    public void startCdataSection() {
        // empty
    }

    @Override
    public void endCdataSection() {
        // empty
    }

    @Override
    public void cdataSection(final char[] ch, final int start, final int len) throws TransformerException {
        
        try {
            writer.write(ch, start, len);
            
        } catch(final IOException ioe) {
            throw new TransformerException(ioe.getMessage(), ioe);
        }
    }


    @Override
    public void startDocumentType(final String name, final String publicId, final String systemId) {
        // empty
    }

    @Override
    public void endDocumentType() {
        // empty
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId) throws TransformerException {
        // empty
    }
    
    @Override
    protected void closeStartTag(final boolean isEmpty) throws TransformerException {
        // empty
    }
    
    @Override
    protected void writeDeclaration() throws TransformerException {
        // empty
    }
    
    @Override
    protected void writeDoctype(final String rootElement) throws TransformerException {
        // empty
    }

    @Override
    protected void writeChars(final CharSequence s, final boolean inAttribute) throws IOException {
        final int len = s.length();
        writeCharSeq(s, 0, len);
    }
    
    private void writeCharSeq(final CharSequence ch, final int start, final int end) throws IOException {
        for (int i = start; i < end; i++) {
            writer.write(ch.charAt(i));
        }
    }
    
    @Override
    protected void writeCharacterReference(final char charval) throws IOException {
        int o = 0;
        charref[o++] = '&';
        charref[o++] = '#';
        charref[o++] = 'x';
        final String code = Integer.toHexString(charval);
        final int len = code.length();
        for(int k = 0; k < len; k++) {
            charref[o++] = code.charAt(k);
        }
        charref[o++] = ';';
        writer.write(charref, 0, o);
    }
}
