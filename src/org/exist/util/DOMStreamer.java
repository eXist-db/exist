/*
 * DOMStreamer.java - Mar 21, 2003
 * 
 * @author wolf
 */
package org.exist.util;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class DOMStreamer {

	private ContentHandler contentHandler = null;
	private LexicalHandler lexicalHandler = null;
	
	public DOMStreamer(ContentHandler handler, LexicalHandler lexical) {
		this.contentHandler = handler;
		this.lexicalHandler = lexical;
	}
	
	public void stream(Node node) throws SAXException {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer transformer = factory.newTransformer();
			DOMSource source = new DOMSource(node);
			SAXResult result = new SAXResult(contentHandler);
			if(lexicalHandler != null)
				result.setLexicalHandler(lexicalHandler);
			try {
				transformer.transform(source, result);
			} catch(TransformerException e) {
				throw new SAXException("error while generating SAX from DOM", e);
			}
		} catch (TransformerConfigurationException e) {
			throw new SAXException("error while generating SAX from DOM", e);
		} catch (TransformerFactoryConfigurationError e) {
			throw new SAXException(e.getMessage());
		}
	}
}
