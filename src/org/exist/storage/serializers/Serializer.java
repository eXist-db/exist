/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.storage.serializers;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.XMLUtil;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.serializer.SAXSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 *  This is the base class for all serializers. Serializers are
 *  used to serialize a document or document fragment back to XML.
 *  A serializer may be obtained by calling DBBroker.getSerializer().
 *
 *  The class basically offers two overloaded methods: serialize()
 *  and toSAX(). serialize() returns the XML as a string, while
 *  toSAX() generates a stream of SAX events. The stream of SAX
 *  events is passed to the ContentHandler set by setContentHandler().
 *  serialize() internally calls toSAX() and uses a Xerces XML Writer
 *  to consume the stream.
 *
 *  If the processXInclude property is set, XInclude elements in
 *  the source document will be expanded.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    20. April 2002
 */
public class Serializer implements XMLReader {

	protected final static Logger LOG = Logger.getLogger(Serializer.class);

	public final static String EXIST_NS =
		"http://exist.sourceforge.net/NS/exist";

	// constants to configure the highlighting of matches in text and attributes
	public final static int TAG_NONE = 0x0;
	public final static int TAG_ELEMENT_MATCHES = 0x1;
	public final static int TAG_ATTRIBUTE_MATCHES = 0x2;
	public final static int TAG_BOTH = 0x4;

	public final static String GENERATE_DOC_EVENTS = "sax-document-events";
	public final static String ENCODING = "encoding";

	protected DBBroker broker;
	protected String encoding = "UTF-8";
	private EntityResolver entityResolver = null;
	private ErrorHandler errorHandler = null;
	protected TransformerFactory factory;

	protected boolean createContainerElements = false;
	
	protected boolean processXSL = false;
	
	protected Properties outputProperties = new Properties();
	
	protected Templates templates = null;
	protected TransformerHandler xslHandler = null;
	protected XIncludeFilter xinclude;
	protected ContentHandler contentHandler;
	protected DTDHandler dtdHandler = null;
	protected LexicalHandler lexicalHandler = null;
	protected User user = null;

	/**
	 *  Constructor for the Serializer object
	 *
	 *@param  broker  Description of the Parameter
	 *@param  pool    Description of the Parameter
	 */
	public Serializer(DBBroker broker, Configuration config) {
		this.broker = broker;
		factory = TransformerFactory.newInstance();
		xinclude = new XIncludeFilter(this);
		contentHandler = xinclude;
		String option = (String) config.getProperty("serialization.enable-xsl");
		if (option != null)
			processXSL = option.equalsIgnoreCase("true");
		option = (String) config.getProperty("serialization.enable-xinclude");
		if (option != null)
			outputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, option);
		option = (String) config.getProperty("serialization.indent");
		if (option != null)
			outputProperties.setProperty(OutputKeys.INDENT, option);
			
		boolean tagElements = true, tagAttributes = false;
		if((option = (String) config.getProperty("serialization.match-tagging-elements")) != null)
			tagElements = option.equals("true");
		if((option = (String) config.getProperty("serialization.match-tagging-attributes")) != null)
			tagAttributes = option.equals("true");
		if(tagElements && tagAttributes)
			option = "both";
		else if(tagElements)
			option = "elements";
		else if(tagAttributes)
			option = "attributes";
		else
			option = "none";
		outputProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, option);
	}

	public void setProperties(Properties properties) throws SAXNotRecognizedException, SAXNotSupportedException {
		if (properties == null)
			return;
		for (Enumeration e = properties.propertyNames(); e.hasMoreElements(); ) {
			String key = (String)e.nextElement();
			setProperty(key, properties.getProperty(key));
		}
	}

	public void setProperty(String prop, Object value) 
	throws SAXNotRecognizedException, SAXNotSupportedException {
		if (prop.equals("http://xml.org/sax/properties/lexical-handler")) {
			lexicalHandler = (LexicalHandler) value;
		} else {
			outputProperties.setProperty(prop, (String)value);
		}
	}

	protected int getHighlightingMode() {
		String option = outputProperties.getProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
		if(option.equals("both"))
			return TAG_BOTH;
		else if(option.equals("elements"))
			return TAG_ELEMENT_MATCHES;
		else if(option.equals("attributes"))
			return TAG_ATTRIBUTE_MATCHES;
		else
			return TAG_NONE;
	}
	
	/**
	 *  If an XSL stylesheet is present, plug it into
	 *  the chain.
	 *
	 *@return StringWriter containing the generated XML
	 */
	protected StringWriter applyXSLHandler() {
		StringWriter sout = new StringWriter();
		StreamResult result = new StreamResult(sout);
		xslHandler.setResult(result);
		if (outputProperties.getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes").equals("yes")) {
			xinclude.setContentHandler(xslHandler);
		} else
			contentHandler = xslHandler;
		lexicalHandler = null;
		return sout;
	}

	/**
	 *  Return the current ContentHandler
	 *
	 *@return    The contentHandler value
	 */
	public ContentHandler getContentHandler() {
		return contentHandler;
	}

	/**
	 *  Return the current DTDHandler
	 *
	 *@return    The dTDHandler value
	 */
	public DTDHandler getDTDHandler() {
		return dtdHandler;
	}

	/**
	 *  Return my internal EntityResolver
	 *
	 *@return    The entityResolver value
	 */
	public EntityResolver getEntityResolver() {
		return entityResolver;
	}

	/**
	 *  Return my ErrorHandler
	 *
	 *@return    The errorHandler value
	 */
	public ErrorHandler getErrorHandler() {
		return errorHandler;
	}

	/**
	 * Set the current User. A valid user is required to
	 * process XInclude elements.
	 */
	public void setUser(User user) {
		this.user = user;
	}

	/**
	 * Get the current User.
	 */
	public User getUser() {
		return user;
	}

	/**
	 *  Gets the feature attribute of the Serializer object
	 *
	 *@param  name                           Description of the Parameter
	 *@return                                The feature value
	 *@exception  SAXNotRecognizedException  Description of the Exception
	 *@exception  SAXNotSupportedException   Description of the Exception
	 */
	public boolean getFeature(String name)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if (name.equals("http://xml.org/sax/features/namespaces")
			|| name.equals("http://xml.org/sax/features/namespace-prefixes"))
			throw new SAXNotSupportedException(name);
		throw new SAXNotRecognizedException(name);
	}

	/**
	 *  Gets the property attribute of the Serializer object
	 *
	 *@param  name                           Description of the Parameter
	 *@return                                The property value
	 *@exception  SAXNotRecognizedException  Description of the Exception
	 *@exception  SAXNotSupportedException   Description of the Exception
	 */
	public Object getProperty(String name)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if (name.equals("http://xml.org/sax/properties/lexical-handler"))
			return lexicalHandler;
		throw new SAXNotRecognizedException(name);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  input             Description of the Parameter
	 *@exception  IOException   Description of the Exception
	 *@exception  SAXException  Description of the Exception
	 */
	public void parse(InputSource input) throws IOException, SAXException {
		// only system-ids are handled
		String doc = input.getSystemId();
		if (doc == null)
			throw new SAXException("source is not an eXist document");
		parse(doc);
	}

	protected void setDocument(DocumentImpl doc) {
		xinclude.setDocument(doc);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  systemId          Description of the Parameter
	 *@exception  IOException   Description of the Exception
	 *@exception  SAXException  Description of the Exception
	 */
	public void parse(String systemId) throws IOException, SAXException {
		if (contentHandler == null)
			throw new SAXException("no content handler");
		try {
			// try to load document from eXist
			DocumentImpl doc = (DocumentImpl) broker.getDocument(systemId);
			if (doc == null)
				throw new SAXException(
					"document " + systemId + " not found in database");
			else
				LOG.debug("serializing " + doc.getFileName());

			serializeToSAX(doc, true);
		} catch (PermissionDeniedException e) {
			throw new SAXException("permission denied");
		}
	}

	/**
	 * Reset the class to its initial state.
	 */
	public void reset() {
		contentHandler = xinclude;
		xinclude.setContentHandler(null);
		xslHandler = null;
		templates = null;
	}

	/**
	 *  Serialize a set of nodes
	 *
	 *@param  set               Description of the Parameter
	 *@param  start             Description of the Parameter
	 *@param  howmany           Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 */
	public String serialize(NodeSet set, int start, int howmany)
		throws SAXException {
		return serialize(set, start, howmany, 0);
	}

	/**
	 *  Serialize a set of nodes
	 *
	 *@param  set               Description of the Parameter
	 *@param  start             Description of the Parameter
	 *@param  howmany           Description of the Parameter
	 *@param  queryTime         Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 */
	public String serialize(
		NodeSet set,
		int start,
		int howmany,
		long queryTime)
		throws SAXException {
		StringWriter out;
		if (templates != null)
			out = applyXSLHandler();
		else
			out = setPrettyPrinter(false);

		serializeToSAX(set, start, howmany, queryTime);
		return out.toString();
	}

	/**
	 *  Serialize a document
	 *
	 *@param  doc               Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 */
	public String serialize(Document doc) throws SAXException {
		if (processXSL) {
			String stylesheet = hasXSLPi(doc);
			if (stylesheet != null)
				setStylesheet((DocumentImpl) doc, stylesheet);
		}
		StringWriter out;
		if (templates != null)
			out = applyXSLHandler();
		else
			out = setPrettyPrinter(true);

		serializeToSAX(doc, true);
		return out.toString();
	}

	/**
	 *  Serialize a single node
	 *
	 *@param  n                 Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 */
	public String serialize(Node n) throws SAXException {
		StringWriter out;
		if (templates != null)
			out = applyXSLHandler();
		else
			out = setPrettyPrinter(false);

		serializeToSAX(n, true);
		return out.toString();
	}

	/**
	 *  Serialize a single NodeProxy
	 *
	 *@param  p                 Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 */
	public String serialize(NodeProxy p) throws SAXException {
		StringWriter out;
		if (templates != null)
			out = applyXSLHandler();
		else
			out = setPrettyPrinter(false);
		serializeToSAX(p, true);
		return out.toString();
	}

	/**
	 *  Serialize a document to a SAX stream
	 *
	 *@param  doc               Description of the Parameter
	 *@param  generateDocEvent  Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(Document doc, boolean generateDocEvent)
		throws SAXException {
		long startTime = System.currentTimeMillis();
		setDocument((DocumentImpl) doc);
		NodeList children = doc.getChildNodes();
		if (generateDocEvent)
			contentHandler.startDocument();

		contentHandler.startPrefixMapping("exist", EXIST_NS);
		for (int i = 0; i < children.getLength(); i++)
			((NodeImpl) children.item(i)).toSAX(
				contentHandler,
				lexicalHandler,
				false);

		contentHandler.endPrefixMapping("exist");
		if (generateDocEvent)
			contentHandler.endDocument();

		LOG.debug(
			"serializing document took "
				+ (System.currentTimeMillis() - startTime));
	}

	/**
	 *  Serialize a NodeSet to the SAX stream
	 *
	 *@param  set               Description of the Parameter
	 *@param  start             Description of the Parameter
	 *@param  howmany           Description of the Parameter
	 *@param  queryTime         Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(
		NodeSet set,
		int start,
		int howmany,
		long queryTime)
		throws SAXException {
		NodeImpl n;
		long startTime = System.currentTimeMillis();
		contentHandler.startDocument();
		contentHandler.startPrefixMapping("exist", EXIST_NS);
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute(
			"",
			"hitCount",
			"hitCount",
			"CDATA",
			Integer.toString(set.getLength()));
		if (queryTime >= 0)
			attribs.addAttribute(
				"",
				"queryTime",
				"queryTime",
				"CDATA",
				Long.toString(queryTime));

		contentHandler.startElement(
			EXIST_NS,
			"result",
			"exist:result",
			attribs);
		for (int i = start - 1;
			i < start + howmany - 1 && i < set.getLength();
			i++) {
			n = (NodeImpl) set.item(i);
			setDocument((DocumentImpl) n.getOwnerDocument());
			if (n != null)
				n.toSAX(contentHandler, lexicalHandler, true);

		}
		contentHandler.endElement(EXIST_NS, "result", "exist:result");
		contentHandler.endPrefixMapping("exist");
		contentHandler.endDocument();
	}

	/**
	 *  Serialize a single Node to the SAX stream
	 *
	 *@param  n                  Description of the Parameter
	 *@param  generateDocEvents  Description of the Parameter
	 *@exception  SAXException   Description of the Exception
	 */
	protected void serializeToSAX(Node n, boolean generateDocEvents)
		throws SAXException {
		if (generateDocEvents)
			contentHandler.startDocument();

		contentHandler.startPrefixMapping("exist", EXIST_NS);
		setDocument((DocumentImpl) n.getOwnerDocument());
		((NodeImpl) n).toSAX(contentHandler, lexicalHandler, true);
		contentHandler.endPrefixMapping("exist");
		if (generateDocEvents)
			contentHandler.endDocument();

	}

	/**
	 *  Serialize a single NodeProxy to the SAX stream
	 *
	 *@param  p                  Description of the Parameter
	 *@param  generateDocEvents  Description of the Parameter
	 *@exception  SAXException   Description of the Exception
	 */
	protected void serializeToSAX(NodeProxy p, boolean generateDocEvents)
		throws SAXException {
		Node n = p.getNode();
		if (n != null)
			serializeToSAX(n, generateDocEvents);

	}

	/**
	 *  Set the ContentHandler to be used during serialization.
	 *
	 *@param  contentHandler  The new contentHandler value
	 */
	public void setContentHandler(ContentHandler handler) {
		if (outputProperties.getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes").equals("yes")) {
			xinclude.setContentHandler(handler);
			contentHandler = xinclude;
		} else
			contentHandler = handler;
	}

	/**
	 *  Set the DTDHandler to be used during serialization.
	 *
	 *@param  handler  The new dTDHandler value
	 */
	public void setDTDHandler(DTDHandler handler) {
		dtdHandler = handler;
	}

	/**
	 *  Sets the entityResolver attribute of the Serializer object
	 *
	 *@param  resolver  The new entityResolver value
	 */
	public void setEntityResolver(EntityResolver resolver) {
		entityResolver = resolver;
	}

	/**
	 *  Sets the errorHandler attribute of the Serializer object
	 *
	 *@param  handler  The new errorHandler value
	 */
	public void setErrorHandler(ErrorHandler handler) {
		errorHandler = handler;
	}

	/**
	 *  Sets the feature attribute of the Serializer object
	 *
	 *@param  name                           The new feature value
	 *@param  value                          The new feature value
	 *@exception  SAXNotRecognizedException  Description of the Exception
	 *@exception  SAXNotSupportedException   Description of the Exception
	 */
	public void setFeature(String name, boolean value)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if (name.equals("http://xml.org/sax/features/namespaces")
			|| name.equals("http://xml.org/sax/features/namespace-prefixes"))
			throw new SAXNotSupportedException(name);
		throw new SAXNotRecognizedException(name);
	}

	/**
	 *  Sets the lexicalHandler attribute of the Serializer object
	 *
	 *@param  lexicalHandler  The new lexicalHandler value
	 */
	public void setLexicalHandler(LexicalHandler lexicalHandler) {
		this.lexicalHandler = lexicalHandler;
	}

	/**
	 *  Sets the prettyPrinter attribute of the Serializer object
	 *
	 *@param  xmlDecl  The new prettyPrinter value
	 *@return          Description of the Return Value
	 */
	protected StringWriter setPrettyPrinter(boolean xmlDecl) {
		StringWriter sout = new StringWriter();
//		OutputFormat format = new OutputFormat("xml", encoding, false);
//		format.setOmitXMLDeclaration(!xmlDecl);
//		format.setOmitComments(false);
//		if (indent) {
//			format.setIndenting(true);
//			format.setPreserveSpace(false);
//			format.setLineWidth(60);
//		} else
//			format.setPreserveSpace(true);
//		XMLSerializer xmlout = new XMLSerializer(sout, format);
		outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, xmlDecl ? "no" : "yes");
		SAXSerializer xmlout = new SAXSerializer(sout, outputProperties);
		setContentHandler(xmlout);
		setLexicalHandler(xmlout);
		return sout;
	}

	public void setStylesheet(String stylesheet) {
		setStylesheet(null, stylesheet);
	}

	/**
	 *  Sets the stylesheet attribute of the Serializer object
	 *
	 *@param  stylesheet                             The new stylesheet value
	 *@exception  SAXException                       Description of the
	 *      Exception
	 *@exception  TransformerConfigurationException  Description of the
	 *      Exception
	 */
	public void setStylesheet(DocumentImpl doc, String stylesheet) {
		if (stylesheet == null) {
			templates = null;
			return;
		}
		long start = System.currentTimeMillis();
		xslHandler = null;
		try {
			// does stylesheet point to an external resource?
			if (stylesheet.indexOf(":") > -1) {
				StreamSource source = new StreamSource(stylesheet);
				templates = factory.newTemplates(source);
			} else
				try {
					// if stylesheet is relative, add path to the
					// current collection
					if (stylesheet.indexOf('/') < 0 && doc != null)
						stylesheet =
							doc.getCollection().getName() + '/' + stylesheet;

					// load stylesheet from eXist
					DocumentImpl xsl =
						(DocumentImpl) broker.getDocument(stylesheet);
					if (xsl == null) {
						LOG.debug("stylesheet not found");
						return;
					}
					if (xsl.getCollection() != null)
						factory.setURIResolver(
							new InternalURIResolver(
								xsl.getCollection().getName()));

					// save handlers
					ContentHandler oldHandler = contentHandler;
					LexicalHandler oldLexical = lexicalHandler;
					SAXSource source =
						new SAXSource(this, new InputSource(stylesheet));
					// compile stylesheet
					templates = factory.newTemplates(source);
					// restore handlers
					contentHandler = oldHandler;
					lexicalHandler = oldLexical;
					factory.setURIResolver(null);
				} catch (PermissionDeniedException e) {
					LOG.debug("permission denied", e);
					return;
				}

			LOG.debug(
				"compiling stylesheet took "
					+ (System.currentTimeMillis() - start));
			xslHandler =
				((SAXTransformerFactory) factory).newTransformerHandler(
					templates);
		} catch (TransformerConfigurationException e) {
			LOG.debug("error compiling stylesheet", e);
			return;
		}
	}

    	/** 
    	 * Set stylesheet parameter
    	 **/
		public void setStylesheetParamameter(String valore, String valore1) {
            		xslHandler.getTransformer().setParameter(valore, valore1);	
			       }


	/**  Sets the xSLHandler attribute of the Serializer object */
	protected void setXSLHandler() {
		if (templates == null)
			return;
		if (xslHandler != null) {
			SAXResult result = new SAXResult();
			result.setLexicalHandler(lexicalHandler);
			boolean processXInclude = 
				outputProperties.getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes").equals("yes");
			if (processXInclude)
				result.setHandler(xinclude.getContentHandler());
			else
				result.setHandler(contentHandler);
			xslHandler.setResult(result);
			if (processXInclude) {
				xinclude.setContentHandler(xslHandler);
				contentHandler = xinclude;
			} else
				contentHandler = xslHandler;
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  set               Description of the Parameter
	 *@param  start             Description of the Parameter
	 *@param  howmany           Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	public void toSAX(NodeSet set, int start, int howmany)
		throws SAXException {
		toSAX(set, start, howmany, 0);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  set               Description of the Parameter
	 *@param  start             Description of the Parameter
	 *@param  howmany           Description of the Parameter
	 *@param  queryTime         Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	public void toSAX(NodeSet set, int start, int howmany, long queryTime)
		throws SAXException {
		setXSLHandler();
		serializeToSAX(set, start, howmany, queryTime);
	}

	/**
	 *  Serialize document to SAX stream
	 *
	 *@param  doc               Description of the Parameter
	 *@param  generateDocEvent  call start-document, end-document
	 *@exception  SAXException  Description of the Exception
	 */
	public void toSAX(Document doc) throws SAXException {
		if (processXSL) {
			String stylesheet = hasXSLPi(doc);
			if (stylesheet != null)
				setStylesheet((DocumentImpl) doc, stylesheet);
		}
		setXSLHandler();
		serializeToSAX(doc, outputProperties.getProperty(GENERATE_DOC_EVENTS, "true").equals("true"));
	}

	/**
	 *  Description of the Method
	 *
	 *@param  n                  Description of the Parameter
	 *@param  generateDocEvents  Description of the Parameter
	 *@exception  SAXException   Description of the Exception
	 */
	public void toSAX(Node n) throws SAXException {
		setXSLHandler();
		serializeToSAX(n, outputProperties.getProperty(GENERATE_DOC_EVENTS, "false").equals("true"));
	}

	/**
	 *  Description of the Method
	 *
	 *@param  p                  Description of the Parameter
	 *@param  generateDocEvents  Description of the Parameter
	 *@exception  SAXException   Description of the Exception
	 */
	public void toSAX(NodeProxy p) throws SAXException {
		setXSLHandler();
		serializeToSAX(p, outputProperties.getProperty(GENERATE_DOC_EVENTS, "false").equals("true"));
	}

	private String hasXSLPi(Document doc) {
		NodeList docChildren = doc.getChildNodes();
		Node node;
		String xsl, type, href;
		for (int i = 0; i < docChildren.getLength(); i++) {
			node = docChildren.item(i);
			if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
				&& ((ProcessingInstruction) node).getTarget().equals(
					"xml-stylesheet")) {
				// found <?xml-stylesheet?>
				xsl = ((ProcessingInstruction) node).getData();
				type = XMLUtil.parseValue(xsl, "type");
				if (type == null || (!type.equals("text/xsl")))
					continue;
				href = XMLUtil.parseValue(xsl, "href");
				if (href == null)
					continue;
				LOG.debug("stylesheet = " + href);
				return href;
			}
		}
		return null;
	}

	/**
	 *  URIResolver is called by the XSL transformer to handle <xsl:include>,
	 *  <xsl:import> ...
	 *
	 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 *@created    20. April 2002
	 */
	private class InternalURIResolver implements URIResolver {

		private String collectionId = null;

		/**
		 *  Constructor for the InternalURIResolver object
		 *
		 *@param  collection  Description of the Parameter
		 */
		public InternalURIResolver(String collection) {
			collectionId = collection;
		}

		/**
		 *  Description of the Method
		 *
		 *@param  href                      Description of the Parameter
		 *@param  base                      Description of the Parameter
		 *@return                           Description of the Return Value
		 *@exception  TransformerException  Description of the Exception
		 */
		public Source resolve(String href, String base)
			throws TransformerException {
			if (href.indexOf(':') > -1)
				// href is an URL pointing to an external resource
				return null;
			if ((!href.startsWith("/")) && collectionId != null)
				href =
					(collectionId.equals("/")
						? '/' + href
						: collectionId + '/' + href);

			LOG.debug("resolving stylesheet ref " + href);
			Serializer serializer = broker.newSerializer();
			return new SAXSource(serializer, new InputSource(href));
		}
	}
}
