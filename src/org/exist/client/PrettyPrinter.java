/*
 * PrettyPrinter.java - Apr 2, 2003
 * 
 * @author wolf
 */
package org.exist.client;

import java.awt.Color;
import java.util.Stack;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

public class PrettyPrinter implements ContentHandler, LexicalHandler {

	private final static SimpleAttributeSet elementAttrs =
		new SimpleAttributeSet();
	private final static SimpleAttributeSet textAttrs =
		new SimpleAttributeSet();
	private final static SimpleAttributeSet attributeAttrs =
		new SimpleAttributeSet();
	private final static SimpleAttributeSet valueAttrs =
		new SimpleAttributeSet();
		
	{
		StyleConstants.setForeground(elementAttrs, Color.magenta);
		StyleConstants.setForeground(textAttrs, Color.black);
		StyleConstants.setForeground(attributeAttrs, Color.green);
		StyleConstants.setForeground(valueAttrs, Color.pink);
	}
		
	private StyledDocument doc;
	private int indentAmount = 4;
	private int indent = 0;
	private Stack elementStates = new Stack();
	
	public PrettyPrinter(StyledDocument doc) {
		this.doc = doc;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator) {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri) throws SAXException {

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
		throws SAXException {
		int pos = doc.getLength();
		ElementState state = 
			elementStates.isEmpty() ? null : (ElementState)elementStates.peek();
		try {
			if(state != null && state.isOpen) {
				doc.insertString(pos, ">\n", elementAttrs);
				pos += 2;
				indent += indentAmount;
				state.isOpen = false;
			}
			doc.insertString(pos, '<' + qName, elementAttrs);
			pos += qName.length() + 1;
			for(int i = 0; i < atts.getLength(); i++) {
				doc.insertString(pos, ' ' + atts.getQName(i), attributeAttrs);
				pos += atts.getQName(i).length() + 1;
				doc.insertString(pos++, "=", textAttrs);
				doc.insertString(pos, '"' + atts.getValue(i) + '"', valueAttrs);
				pos += atts.getValue(i).length() + 2;
			}
			state = new ElementState(namespaceURI, localName, qName);
			elementStates.push(state);
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName)
		throws SAXException {
		int pos = doc.getLength();
		ElementState state = 
			elementStates.isEmpty() ? null : (ElementState)elementStates.pop();
		try {
			if(state != null && state.isOpen) {
				doc.insertString(pos, "/>\n", elementAttrs);
				pos += 3;
			} else {
				doc.insertString(pos, "</" + qName + ">\n", elementAttrs);
				pos += qName.length() + 4;
			}
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] ch, int start, int length) throws SAXException {
		int pos = doc.getLength();
		ElementState state = 
			elementStates.isEmpty() ? null : (ElementState)elementStates.peek();
		try {
			if(state != null && state.isOpen) {
				doc.insertString(pos++, ">", elementAttrs);
				state.isOpen = false;
			}
			String str = new String(ch, start, length);
			doc.insertString(pos, str, textAttrs);
			pos += str.length();
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endDTD()
	 */
	public void endDTD() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
	 */
	public void startEntity(String name) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
	 */
	public void endEntity(String name) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startCDATA()
	 */
	public void startCDATA() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endCDATA()
	 */
	public void endCDATA() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
	 */
	public void comment(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub

	}
	
	private final static class ElementState {
		String localName;
		String qName;
		String namespaceURI;
		boolean isOpen = true;
		
		public ElementState(String namespaceURI, String localName, String qName) {
			this.namespaceURI = namespaceURI;
			this.localName = localName;
			this.qName = qName;
		}
	}

}
