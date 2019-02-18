/*
 * Created on 11 juil. 2004
$Id$
 */
package org.exist.xquery;

import java.io.IOException;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;


/** A test data source producing adjustable tabular data */
class TabularXMLReader implements XMLReader {
	private int lines = 10;
	private int columns = 10;

	TabularXMLReader() {}
	TabularXMLReader( int lines , int columns) {
		this.lines = lines;
		this.columns = columns;
	}
	
	ContentHandler contentHandler;
	private static final boolean DIFFERENT_TAG_EACH_LINE = false;

	void writeDocument( ContentHandler xmldb) throws SAXException {
		xmldb.startDocument();
		AttributesImpl attributesImpl = new AttributesImpl();
		xmldb.startElement(XMLConstants.NULL_NS_URI, "root", "root", attributesImpl);
		for (int i = 0; i < lines; i++) {
			String line = "line";
			if ( DIFFERENT_TAG_EACH_LINE)
				line += i;
			xmldb.startElement(XMLConstants.NULL_NS_URI, line, line, attributesImpl);
			for (int j = 0; j < columns; j++) {
				String column = "col" + j;
				xmldb.startElement(XMLConstants.NULL_NS_URI, column, column, attributesImpl);
				char ch[] = new char[20];
				column.getChars(0, column.length(), ch, 0);
				xmldb.characters(ch, 0, column.length() );
				xmldb.endElement(XMLConstants.NULL_NS_URI, column, column);
			}
			xmldb.endElement(XMLConstants.NULL_NS_URI, line, line);
		}
		xmldb.endElement(XMLConstants.NULL_NS_URI, "root", "root");
		xmldb.endDocument();
	}
	
	/** ? @see org.xml.sax.XMLReader#parse(java.lang.String)		 */
	public void parse(String systemId) throws IOException, SAXException {
		writeDocument(contentHandler);
	}

	/** ? @see org.xml.sax.XMLReader#getFeature(java.lang.String)	 */
	public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
		return false;
	}

	/** ? @see org.xml.sax.XMLReader#setFeature(java.lang.String, boolean)	 */
	public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {}
	

	/** ? @see org.xml.sax.XMLReader#getContentHandler()	 */
	public ContentHandler getContentHandler() {
		return contentHandler;
	}

	/** ? @see org.xml.sax.XMLReader#setContentHandler(org.xml.sax.ContentHandler)
	 */
	public void setContentHandler(ContentHandler handler) {
		this.contentHandler = handler;
	}

	/** ? @see org.xml.sax.XMLReader#getDTDHandler()
	 */
	public DTDHandler getDTDHandler() {
		return null;
	}

	/** ? @see org.xml.sax.XMLReader#setDTDHandler(org.xml.sax.DTDHandler)
	 */
	public void setDTDHandler(DTDHandler handler) {}

	/** ? @see org.xml.sax.XMLReader#getEntityResolver()
	 */
	public EntityResolver getEntityResolver() {
		return null;
	}

	/** ? @see org.xml.sax.XMLReader#setEntityResolver(org.xml.sax.EntityResolver)
	 */
	public void setEntityResolver(EntityResolver resolver) {}

	/** ? @see org.xml.sax.XMLReader#getErrorHandler()
	 */
	public ErrorHandler getErrorHandler() {
		return null;
	}

	/** ? @see org.xml.sax.XMLReader#setErrorHandler(org.xml.sax.ErrorHandler)
	 */
	public void setErrorHandler(ErrorHandler handler) {}

	/** ? @see org.xml.sax.XMLReader#parse(org.xml.sax.InputSource)
	 */
	public void parse(InputSource input) throws IOException, SAXException {
		writeDocument(contentHandler);
	}

	/** ? @see org.xml.sax.XMLReader#getProperty(java.lang.String)
	 */
	public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
		return null;
	}

	/** ? @see org.xml.sax.XMLReader#setProperty(java.lang.String, java.lang.Object)
	 */
	public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {}

	/**
	 * @return Returns the number of lines.
	 */
	public int getLines() {
		return lines;
	}
	/**
	 * @return Returns the number of columns.
	 */
	public int getColumns() {
		return columns;
	}
}