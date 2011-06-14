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

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

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
	 * @param str
	 *            The String of XML
	 * 
	 * @return The NodeValue of XML
	 */
	public static NodeValue stringToXML(XQueryContext context, String str) throws XPathException, SAXException {
            Reader reader = new StringReader(str);
            try {
                return inputSourceToXML(context, new InputSource(reader));
            } finally {
                try { 
                    reader.close();
                } catch(IOException ioe) {
                    LOG.warn("Unable to close reader: " + ioe.getMessage(), ioe);
                }
            }
	}
	
	
	/**
	 * Takes an InputStream of XML and Creates an XML Node from it using SAX in the
	 * context of the query
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param is
	 *            The InputStream of XML
	 * 
	 * @return The NodeValue of XML
	 */
	public static NodeValue streamToXML(XQueryContext context, InputStream is) throws XPathException, SAXException {
            return inputSourceToXML(context, new InputSource(is));
	}
        
        /**
	 * Takes a Source of XML and Creates an XML Node from it using SAX in the
	 * context of the query
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param src
	 *            The Source of XML
	 * 
	 * @return The NodeValue of XML
	 */
        public static NodeValue sourceToXML(XQueryContext context, Source src)  throws XPathException, SAXException {
            InputSource inputSource = SAXSource.sourceToInputSource(src);
        
            if(inputSource == null){
                throw new XPathException(src.getClass().getName() + " is unsupported.");
            }
            
            return inputSourceToXML(context, inputSource);
        }
	

        /**
	 * Takes a InputSource of XML and Creates an XML Node from it using SAX in the
	 * context of the query
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param xml
	 *            The InputSource of XML
	 * 
	 * @return The NodeValue of XML
	 */
	public static NodeValue inputSourceToXML(XQueryContext context, InputSource inputSource) throws XPathException, SAXException  {
            context.pushDocumentContext();

            XMLReader reader = null;
            try {
                // try and construct xml document from input stream, we use eXist's
                // in-memory DOM implementation
                reader = context.getBroker().getBrokerPool().getParserPool().borrowXMLReader();
                LOG.debug( "Parsing XML response ..." );

                // TODO : we should be able to cope with context.getBaseURI()
                MemTreeBuilder builder = context.getDocumentBuilder();
                DocumentBuilderReceiver receiver = new DocumentBuilderReceiver( builder, true );
                reader.setContentHandler(receiver);
                reader.parse(inputSource);
                Document doc = receiver.getDocument();
                // return (NodeValue)doc.getDocumentElement();
                return((NodeValue)doc);
            } catch(IOException e) {
                throw(new XPathException(e.getMessage(), e));
            }  finally {
                context.popDocumentContext();

                if(reader != null){
                    context.getBroker().getBrokerPool().getParserPool().returnXMLReader(reader);
                }
            }
	}
        
        /**
	 * Takes a HTML InputSource and creates an XML representation of the HTML by
	 * tidying it (uses NekoHTML)
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param srcHtml
	 *            The Source for the HTML
         * @param parserFeatures
         *            The features to set on the Parser
         * @param parserProperties
         *            The properties to set on the Parser
	 * 
	 * @return An in-memory Document representing the XML'ised HTML
	 */
	public static DocumentImpl htmlToXHtml(XQueryContext context, String url, Source srcHtml, Map<String, Boolean> parserFeatures, Map<String, String>parserProperties) throws XPathException, SAXException {
		
            InputSource inputSource = SAXSource.sourceToInputSource(srcHtml);
        
            if(inputSource == null){
                throw new XPathException(srcHtml.getClass().getName() + " is unsupported.");
            }
            
            return htmlToXHtml(context, url, inputSource, parserFeatures, parserProperties);
	}
        
	/**
	 * Takes a HTML InputSource and creates an XML representation of the HTML by
	 * tidying it (uses NekoHTML)
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param srcHtml
	 *            The InputSource for the HTML
         * @param parserFeatures
         *            The features to set on the Parser
         * @param parserProperties
         *            The properties to set on the Parser
	 * 
	 * @return An in-memory Document representing the XML'ised HTML
	 */
	public static DocumentImpl htmlToXHtml(XQueryContext context, String url, InputSource srcHtml, Map<String, Boolean> parserFeatures, Map<String, String>parserProperties) throws XPathException, SAXException {
            // we use eXist's in-memory DOM implementation
            org.exist.memtree.DocumentImpl memtreeDoc = null;

            // use Neko to parse the HTML content to XML
            XMLReader reader = null;
            try {
                LOG.debug("Converting HTML to XML using NekoHTML parser for: " + url);
                reader = (XMLReader) Class.forName("org.cyberneko.html.parsers.SAXParser").newInstance();
                
                if(parserFeatures != null) {
                    for(Entry<String, Boolean> parserFeature : parserFeatures.entrySet()) {
                        reader.setFeature(parserFeature.getKey(), parserFeature.getValue());
                    }
                }

                if(parserProperties == null) {
                    //default: do not modify the case of elements and attributes
                    reader.setProperty("http://cyberneko.org/html/properties/names/elems","match");
                    reader.setProperty("http://cyberneko.org/html/properties/names/attrs","no-change");
                } else {
                    for(Entry<String, String> parserProperty : parserProperties.entrySet()) {
                        reader.setProperty(parserProperty.getKey(), parserProperty.getValue());
                    }
                }
            } catch(Exception e) {
                    String errorMsg = "Error while invoking NekoHTML parser. ("
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
	 * @param nParameters
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
