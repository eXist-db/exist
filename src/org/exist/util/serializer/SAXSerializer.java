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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.exist.util.XMLString;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.NamespaceSupport;

public class SAXSerializer implements ContentHandler, LexicalHandler {

	protected XMLWriter receiver;
	protected Properties outputProperties;
	protected NamespaceSupport nsSupport = new NamespaceSupport();
	protected HashMap namespaceDecls = new HashMap();

	public SAXSerializer(Writer writer, Properties outputProperties) {
		super();
		this.outputProperties = outputProperties;
		if(outputProperties == null)
			outputProperties = new Properties();
		String prop = outputProperties.getProperty(OutputKeys.INDENT, "no");
		this.receiver =
			prop.equals("yes")
				? new XMLIndenter(writer)
				: new XMLWriter(writer);
		this.receiver.setOutputProperties(outputProperties);
	}

	public Writer getWriter() {
		return receiver.writer;
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator arg0) {
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
	public void startPrefixMapping(String arg0, String arg1)
		throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String arg0) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(
		String namespaceURI,
		String localName,
		String qname,
		Attributes attribs)
		throws SAXException {
		try {
			namespaceDecls.clear();
			nsSupport.pushContext();
			receiver.startElement(qname);
			String prefix = "";
			int p = qname.indexOf(':');
			if (p > 0)
				prefix = qname.substring(p + 1);
			if (namespaceURI == null)
				namespaceURI = "";
			if (nsSupport.getURI(prefix) == null) {
				namespaceDecls.put(prefix, namespaceURI);
				nsSupport.declarePrefix(prefix, namespaceURI);
			}

			// check attributes for required namespace declarations
			String attrName;
			String uri;
			for (int i = 0; i < attribs.getLength(); i++) {
				attrName = attribs.getQName(i);
				if (attrName.equals("xmlns")) {
					if (nsSupport.getURI("") == null) {
						uri = attribs.getValue(i);
						namespaceDecls.put("", uri);
						nsSupport.declarePrefix("", uri);
					}
				} else if (attrName.startsWith("xmlns:")) {
					prefix = attrName.substring(6);
					if (nsSupport.getURI(prefix) == null) {
						uri = attribs.getValue(i);
						namespaceDecls.put(prefix, uri);
						nsSupport.declarePrefix(prefix, uri);
					}
				} else if ((p = attrName.indexOf(':')) > 0) {
					prefix = attrName.substring(0, p);
					uri = attribs.getURI(i);
					if (nsSupport.getURI(prefix) == null) {
						namespaceDecls.put(prefix, uri);
						nsSupport.declarePrefix(prefix, uri);
					}
				}
			}
			// output all namespace declarations
			Map.Entry nsEntry;
			for (Iterator i = namespaceDecls.entrySet().iterator();
				i.hasNext();
				) {
				nsEntry = (Map.Entry) i.next();
				receiver.namespace(
					(String) nsEntry.getKey(),
					(String) nsEntry.getValue());
			}
			// output attributes
			for (int i = 0; i < attribs.getLength(); i++) {
				if (!attribs.getQName(i).startsWith("xmlns"))
					receiver.attribute(
						attribs.getQName(i),
						attribs.getValue(i));
			}
		} catch (TransformerException e) {
			throw new SAXException(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qname)
		throws SAXException {
		try {
			nsSupport.popContext();
			receiver.endElement();
		} catch (TransformerException e) {
			throw new SAXException(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] ch, int start, int len) throws SAXException {
		try {
			receiver.characters(ch, start, len);
		} catch (TransformerException e) {
			throw new SAXException(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int len)
		throws SAXException {
		try {
			receiver.characters(ch, start, len);
		} catch (TransformerException e) {
			throw new SAXException(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data)
		throws SAXException {
		try {
			receiver.processingInstruction(target, data);
		} catch (TransformerException e) {
			throw new SAXException(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String arg0) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void startDTD(String arg0, String arg1, String arg2)
		throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endDTD()
	 */
	public void endDTD() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
	 */
	public void startEntity(String arg0) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
	 */
	public void endEntity(String arg0) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startCDATA()
	 */
	public void startCDATA() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endCDATA()
	 */
	public void endCDATA() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
	 */
	public void comment(char[] ch, int start, int len) throws SAXException {
		try {
			receiver.comment(new XMLString(ch, start, len));
		} catch (TransformerException e) {
			throw new SAXException(e.getMessage(), e);
		}
	}

}
