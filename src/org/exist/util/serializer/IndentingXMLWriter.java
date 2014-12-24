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

import java.io.Writer;
import java.util.Properties;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;

public class IndentingXMLWriter extends XMLWriter {

    private boolean indent = false;
    private int indentAmount = 4;
    private String indentChars = "                                                                                           ";
    private int level = 0;
    private boolean afterTag = false;
    private boolean sameline = false;

    public IndentingXMLWriter() {
        super();
    }

    /**
     * @param writer
     */
    public IndentingXMLWriter(Writer writer) {
        super(writer);
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#setWriter(java.io.Writer)
     */
    @Override
    public void setWriter(final Writer writer) {
        super.setWriter(writer);
        level = 0;
        afterTag = false;
        sameline = false;
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#startElement(java.lang.String)
     */
    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        if (afterTag && !isInlineTag(namespaceURI, localName)) {
            indent();
        }
        super.startElement(namespaceURI, localName, qname);
        addIndent();
        afterTag = true;
        sameline = true;
    }
    
    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#startElement(org.exist.dom.QName)
     */
    @Override
    public void startElement(final QName qname) throws TransformerException {
        if (afterTag && !isInlineTag(qname.getNamespaceURI(), qname.getLocalPart())) {
            indent();
        }
        super.startElement(qname);
        addIndent();
        afterTag = true;
        sameline = true;
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#endElement()
     */
    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws TransformerException {
        endIndent(namespaceURI, localName);
        super.endElement(namespaceURI, localName, qname);
        sameline = false;
        afterTag = true;
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#endElement(org.exist.dom.QName)
     */
    @Override
    public void endElement(final QName qname) throws TransformerException {
        endIndent(qname.getNamespaceURI(), qname.getLocalPart());
        super.endElement(qname);
        sameline = false;
        afterTag = true;
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#characters(java.lang.CharSequence)
     */
    @Override
    public void characters(CharSequence chars) throws TransformerException {
        final int start = 0, length = chars.length();
        //while (length > 0 && isWhiteSpace(chars.charAt(start))) {
            //--length;
            //if(length > 0)
            //++start;
        //}
        //while (length > 0 && isWhiteSpace(chars.charAt(start + length - 1))) {
            //--length;
        //}
        if(length == 0) {
            return;	// whitespace only: skip
        }
        if(start > 0 || length < chars.length()) {
            chars = chars.subSequence(start, length);	// drop whitespace
        }
        for(int i = 0; i < chars.length(); i++) {
            if(chars.charAt(i) == '\n') {
                sameline = false;
            }
        }
        afterTag = false;
        super.characters(chars);
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#comment(java.lang.String)
     */
    @Override
    public void comment(final CharSequence data) throws TransformerException {
        super.comment(data);
        afterTag = true;
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#processingInstruction(java.lang.String, java.lang.String)
     */
    @Override
    public void processingInstruction(final String target, final String data) throws TransformerException {
        super.processingInstruction(target, data);	
        afterTag = true;
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId) throws TransformerException {
        super.documentType(name, publicId, systemId);	
        super.characters("\n"); //TODO This should probably be System.getProperty(:line.separator") //???
        sameline = false;
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.XMLWriter#setOutputProperties(java.util.Properties)
     */
    @Override
    public void setOutputProperties(final Properties properties) {
        super.setOutputProperties(properties);
        final String option = outputProperties.getProperty(EXistOutputKeys.INDENT_SPACES, "4");
        try {
            indentAmount = Integer.parseInt(option);
        } catch(final NumberFormatException e) {
            //Nothing to do ?
        }
        indent = "yes".equals(outputProperties.getProperty(OutputKeys.INDENT, "no"));
    }

    protected boolean isInlineTag(final String namespaceURI, final String localName) {
    	return false;
    }
    
    protected void indent() throws TransformerException {
        if(!indent) {
            return;
        }
        final int spaces = indentAmount * level;
        while(spaces >= indentChars.length()) {
            indentChars += indentChars;
        }
        super.characters("\n");
        super.characters(indentChars.subSequence(0, spaces));
        sameline = false;
    }

    protected void addIndent() {
        level++;
    }

    protected void endIndent(String namespaceURI, String localName) throws TransformerException {
        level--;
        if (afterTag && !sameline && !isInlineTag(namespaceURI, localName)){
            indent();
        }
    }

    protected static boolean isWhiteSpace(final char ch) {
        return (ch == 0x20) || (ch == 0x09) || (ch == 0xD) || (ch == 0xA);
    }
}
