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
	public void setWriter(Writer writer) {
		super.setWriter(writer);
		level = 0;
		afterTag = false;
		sameline = false;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.XMLWriter#startElement(java.lang.String)
	 */
	public void startElement(String qname) throws TransformerException {
		if(afterTag)
			indent();
		super.startElement(qname);
		level++;
		afterTag = true;
		sameline = true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.XMLWriter#startElement(org.exist.dom.QName)
	 */
	public void startElement(QName qname) throws TransformerException {
		if(afterTag)
			indent();
		super.startElement(qname);
		level++;
		afterTag = true;
		sameline = true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.XMLWriter#endElement()
	 */
	public void endElement(String qname) throws TransformerException {
		level--;
		if (afterTag && !sameline) indent();
		super.endElement(qname);
		sameline = false;
		afterTag = true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.XMLWriter#endElement(org.exist.dom.QName)
	 */
	public void endElement(QName qname) throws TransformerException {
		level--;
		if (afterTag && !sameline) indent();
		super.endElement(qname);
		sameline = false;
		afterTag = true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.XMLWriter#characters(java.lang.CharSequence)
	 */
	public void characters(CharSequence chars) throws TransformerException {
		int start = 0, length = chars.length();
//		while (length > 0 && isWhiteSpace(chars.charAt(start))) {
//			--length;
//			if(length > 0)
//				++start;
//		}
//		while (length > 0 && isWhiteSpace(chars.charAt(start + length - 1))) {
//			--length;
//		}
		if(length == 0)
			return;	// whitespace only: skip
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
	public void comment(CharSequence data) throws TransformerException {
		super.comment(data);	
		afterTag = true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.XMLWriter#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data)
		throws TransformerException {
		super.processingInstruction(target, data);	
		afterTag = true;
	}
	
	public void documentType(String name, String publicId, String systemId)
		throws TransformerException {	
		super.documentType(name, publicId, systemId);	
		super.characters("\n");
		sameline = false;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.XMLWriter#setOutputProperties(java.util.Properties)
	 */
	public void setOutputProperties(Properties properties) {
		super.setOutputProperties(properties);
		String option = outputProperties.getProperty(EXistOutputKeys.INDENT_SPACES, "4");
		try {
			indentAmount = Integer.parseInt(option);
		} catch(NumberFormatException e) {
		}
		indent = outputProperties.getProperty(OutputKeys.INDENT, "no").equals("yes");
	}
	
	protected void indent() throws TransformerException {
		if(!indent)
			return;
		int spaces = indentAmount * level;
		while(spaces >= indentChars.length())
			indentChars += indentChars;
		super.characters("\n");
		super.characters(indentChars.subSequence(0, spaces));
		sameline = false;
	}
	
	protected final static boolean isWhiteSpace(char ch) {
		return (ch == 0x20) || (ch == 0x09) || (ch == 0xD) || (ch == 0xA);
	}
}
