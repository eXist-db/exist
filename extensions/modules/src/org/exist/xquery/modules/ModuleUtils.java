/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.modules;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.SAXAdapter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Utility Functions for XQuery Extension Modules
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @serial 200805202059
 * @version 1.1
 */
public class ModuleUtils {
	protected final static Logger LOG = Logger.getLogger(ModuleUtils.class);

	/**
	 * Takes a String of XML and Creates an XML Node from it using SAX in the
	 * context of the query
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param xml
	 *            The String of XML
	 * 
	 * @return The NodeValue of XML
	 */
	public static NodeValue stringToXML(XQueryContext context, String xml)
			throws XPathException, SAXException {
		context.pushDocumentContext();
		try {
			// try and construct xml document from input stream, we use eXist's
			// in-memory DOM implementation
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			// TODO : we should be able to cope with context.getBaseURI()
			InputSource src = new InputSource(new ByteArrayInputStream(xml
					.getBytes()));
			SAXParser parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
			MemTreeBuilder builder = context.getDocumentBuilder();
			DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(
					builder);
			reader.setContentHandler(receiver);
			reader.parse(src);
			Document doc = receiver.getDocument();
			// return (NodeValue)doc.getDocumentElement();
			return (NodeValue) doc;
		} catch (ParserConfigurationException e) {
			throw new XPathException(e.getMessage());
		} catch (IOException e) {
			throw new XPathException(e.getMessage());
		} finally {
			context.popDocumentContext();
		}
	}

	/**
	 * Takes a HTML InputSource and creates an XML representation of the HTML by
	 * tidying it (uses NekoHTML)
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param srcHtml
	 *            The InputSource for the HTML
	 * 
	 * @return An in-memory Document representing the XML'ised HTML
	 */
	public static DocumentImpl htmlToXHtml(XQueryContext context, String url,
			InputSource srcHtml) throws XPathException, SAXException {
		// we use eXist's in-memory DOM implementation
		org.exist.memtree.DocumentImpl memtreeDoc = null;

		// use Neko to parse the HTML content to XML
		XMLReader reader = null;
		try {
			LOG.debug("Converting HTML to XML using NekoHTML parser for: "
					+ url);
			reader = (XMLReader) Class.forName(
					"org.cyberneko.html.parsers.SAXParser").newInstance();

			// do not modify the case of elements and attributes
			reader
					.setProperty(
							"http://cyberneko.org/html/properties/names/elems",
							"match");
			reader.setProperty(
					"http://cyberneko.org/html/properties/names/attrs",
					"no-change");
		} catch (Exception e) {
			String errorMsg = "Error while involing NekoHTML parser. ("
					+ e.getMessage()
					+ "). If you want to parse non-wellformed HTML files, put "
					+ "nekohtml.jar into directory 'lib/user'.";
			LOG.error(errorMsg, e);

			throw new XPathException(errorMsg, e);
		}

		SAXAdapter adapter = new SAXAdapter();
		reader.setContentHandler(adapter);
		try {
			reader.parse(srcHtml);
		} catch (IOException e) {
			throw new XPathException(e.getMessage(), e);
		}
		Document doc = adapter.getDocument();
		memtreeDoc = (DocumentImpl) doc;
		memtreeDoc.setContext(context);
		return memtreeDoc;
	}

	/**
	 * Parses a structure like <parameters><param name="a" value="1"/><param
	 * name="b" value="2"/></parameters> into a set of Properties
	 * 
	 * @param parameters
	 *            The parameters Node
	 * @return a set of name value properties for representing the XML
	 *         parameters
	 */
	public static Properties parseParameters(Node nParameters)
			throws XPathException {

		return parseProperties(nParameters, "param");
	}

	/**
	 * Parses a structure like <properties><property name="a" value="1"/><property
	 * name="b" value="2"/></properties> into a set of Properties
	 * 
	 * @param nProperties
	 *            The properties Node
	 * @return a set of name value properties for representing the XML
	 *         properties
	 */
	public static Properties parseProperties(Node nProperties)
			throws XPathException {

		return parseProperties(nProperties, "property");
	}

	/**
	 * Parses a structure like <properties><property name="a" value="1"/><property
	 * name="b" value="2"/></properties> into a set of Properties
	 * 
	 * @param container
	 *            The container of the properties
	 * @param elementName
	 *            The name of the property element
	 * @return a set of name value properties for representing the XML
	 *         properties
	 */
	private final static Properties parseProperties(Node container,
			String elementName) throws XPathException {
		Properties properties = new Properties();

		if (container != null && container.getNodeType() == Node.ELEMENT_NODE) {
			NodeList params = ((Element) container)
					.getElementsByTagName(elementName);

			for (int i = 0; i < params.getLength(); i++) {
				Element param = ((Element) params.item(i));

				String name = param.getAttribute("name");
				String value = param.getAttribute("value");

				if (name != null && value != null) {
					properties.setProperty(name, value);
				} else {
					LOG.warn("Name or value attribute missing for "
							+ elementName);
				}
			}
		}

		return properties;
	}
}
