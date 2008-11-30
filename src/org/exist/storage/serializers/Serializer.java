/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.XMLUtil;
import org.exist.indexing.IndexController;
import org.exist.indexing.MatchListener;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.MimeType;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.ReceiverToSAX;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
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

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.HashMap;

/**
 * Serializer base class, used to serialize a document or document fragment 
 * back to XML. A serializer may be obtained by calling DBBroker.getSerializer().
 *
 *  The class basically offers two overloaded methods: serialize()
 *  and toSAX(). serialize() returns the XML as a string, while
 *  toSAX() generates a stream of SAX events. The stream of SAX
 *  events is passed to the ContentHandler set by setContentHandler().
 *  
 * Internally, both types of methods pass events to a {@link org.exist.util.serializer.Receiver}.
 * Subclasses thus have to implement the various serializeToReceiver() methods.
 *
 *  Output can be configured through properties. Property keys are defined in classes
 * {@link javax.xml.transform.OutputKeys} and {@link org.exist.storage.serializers.EXistOutputKeys}
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class Serializer implements XMLReader {

	protected final static Logger LOG = Logger.getLogger(Serializer.class);
	
	public static final String CONFIGURATION_ELEMENT_NAME = "serializer";
	public static final String ENABLE_XINCLUDE_ATTRIBUTE = "enable-xinclude";
	public static final String PROPERTY_ENABLE_XINCLUDE = "serialization.enable-xinclude";
	public static final String ENABLE_XSL_ATTRIBUTE = "enable-xsl";
	public static final String PROPERTY_ENABLE_XSL = "serialization.enable-xsl";
	public static final String INDENT_ATTRIBUTE = "indent";
	public static final String PROPERTY_INDENT = "serialization.indent";
	public static final String COMPRESS_OUTPUT_ATTRIBUTE = "compress-output";
	public static final String PROPERTY_COMPRESS_OUTPUT = "serialization.compress-output";
	public static final String ADD_EXIST_ID_ATTRIBUTE = "add-exist-id";
	public static final String PROPERTY_ADD_EXIST_ID = "serialization.add-exist-id";
	public static final String TAG_MATCHING_ELEMENTS_ATTRIBUTE = "match-tagging-elements";
	public static final String PROPERTY_TAG_MATCHING_ELEMENTS = "serialization.match-tagging-elements";
	public static final String TAG_MATCHING_ATTRIBUTES_ATTRIBUTE = "match-tagging-attributes";
	public static final String PROPERTY_TAG_MATCHING_ATTRIBUTES = "serialization.match-tagging-attributes";
    public static final String PROPERTY_SESSION_ID = "serialization.session-id";

    // constants to configure the highlighting of matches in text and attributes
	public final static int TAG_NONE = 0x0;
	public final static int TAG_ELEMENT_MATCHES = 0x1;
	public final static int TAG_ATTRIBUTE_MATCHES = 0x2;
	public final static int TAG_BOTH = 0x3;

    public final static int EXIST_ID_NONE = 0;
    public final static int EXIST_ID_ELEMENT = 1;
    public final static int EXIST_ID_ALL = 2;

    protected int showId = EXIST_ID_NONE;

    public final static String GENERATE_DOC_EVENTS = "sax-document-events";
    public final static String ENCODING = "encoding";
    
    protected final static QName ATTR_HITS_QNAME = new QName("hits", Namespaces.EXIST_NS, "exist");
    protected final static QName ATTR_START_QNAME = new QName("start", Namespaces.EXIST_NS, "exist");
    protected final static QName ATTR_COUNT_QNAME = new QName("count", Namespaces.EXIST_NS, "exist");
    protected final static QName ELEM_RESULT_QNAME = new QName("result", Namespaces.EXIST_NS, "exist");
    protected final static QName ATTR_SESSION_ID = new QName("session", Namespaces.EXIST_NS, "exist");
    protected final static QName ATTR_TYPE_QNAME = new QName("type", Namespaces.EXIST_NS, "exist");
    protected final static QName ELEM_VALUE_QNAME = new QName("value", Namespaces.EXIST_NS, "exist");
    protected DBBroker broker;
    protected String encoding = "UTF-8";
    private EntityResolver entityResolver = null;

    private ErrorHandler errorHandler = null;

    protected SAXTransformerFactory factory;
    protected boolean createContainerElements = false;

    protected Properties defaultProperties = new Properties();
    protected Properties outputProperties;
    protected Templates templates = null;
    protected TransformerHandler xslHandler = null;
    protected XIncludeFilter xinclude;
    protected Receiver receiver = null;
    protected SAXSerializer xmlout = null;
    protected LexicalHandler lexicalHandler = null;
    protected User user = null;

    public Serializer(DBBroker broker, Configuration config) {
		this.broker = broker;
		factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
		xinclude = new XIncludeFilter(this);
		receiver = xinclude;
		
		String option = (String) config.getProperty(PROPERTY_ENABLE_XSL);
		if (option != null)
			defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, option);
		else
			defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
		
		option = (String) config.getProperty(PROPERTY_ENABLE_XINCLUDE);
		if (option != null) {
			defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, option);
		}
		
		option = (String) config.getProperty(PROPERTY_INDENT);
		if (option != null)
			defaultProperties.setProperty(OutputKeys.INDENT, option);
		
		option = (String) config.getProperty(PROPERTY_COMPRESS_OUTPUT);
		if (option != null)
			defaultProperties.setProperty(EXistOutputKeys.COMPRESS_OUTPUT, option);

        option = (String) config.getProperty(PROPERTY_ADD_EXIST_ID);
        if (option != null)
            defaultProperties.setProperty(EXistOutputKeys.ADD_EXIST_ID, option);

        boolean tagElements = true, tagAttributes = false;
		if ((option =
			(String) config.getProperty(PROPERTY_TAG_MATCHING_ELEMENTS))
			!= null)
			tagElements = option.equals("yes");
		if ((option =
			(String) config.getProperty(PROPERTY_TAG_MATCHING_ATTRIBUTES))
			!= null)
			tagAttributes = option.equals("yes");
		if (tagElements && tagAttributes)
			option = "both";
		else if (tagElements)
			option = "elements";
		else if (tagAttributes)
			option = "attributes";
		else
			option = "none";
		defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, option);
		defaultProperties.setProperty(GENERATE_DOC_EVENTS, "true");
		outputProperties = new Properties(defaultProperties);
	}

	public void setProperties(Properties properties)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if (properties == null)
			return;
		String key;
		for(Enumeration e = properties.propertyNames(); e.hasMoreElements(); ) {
		    key = (String)e.nextElement();
		    if(key.equals(Namespaces.SAX_LEXICAL_HANDLER))
		        lexicalHandler = (LexicalHandler)properties.get(key);
		    else
		        setProperty(key, properties.getProperty(key));
		}
	}

	public void setProperties(HashMap table)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if(table == null)
			return;
		for(Iterator i = table.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry entry = (Map.Entry) i.next();
			setProperty((String)entry.getKey(), entry.getValue().toString());
		}
	}
	
	public void setProperty(String prop, Object value)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if (prop.equals(Namespaces.SAX_LEXICAL_HANDLER)) {
			lexicalHandler = (LexicalHandler) value;
        } else if (EXistOutputKeys.ADD_EXIST_ID.equals(prop)) {
            if (value.equals("element"))
                showId = EXIST_ID_ELEMENT;
            else if (value.equals("all"))
                showId = EXIST_ID_ALL;
            else
                showId = EXIST_ID_NONE;
        } else {
			outputProperties.put(prop, value);
		}
	}

	public String getProperty(String key, String defaultValue) {
		String value = outputProperties.getProperty(key, defaultValue);
		return value;
	}
	
	public boolean isStylesheetApplied() {
		return templates != null;
	}
	
	protected int getHighlightingMode() {
		String option =
			getProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
		if (option.equals("both") || option.equals("all"))
			return TAG_BOTH;
		else if (option.equals("elements"))
			return TAG_ELEMENT_MATCHES;
		else if (option.equals("attributes"))
			return TAG_ATTRIBUTE_MATCHES;
		else
			return TAG_NONE;
	}

	/**
	 *  If an XSL stylesheet is present, plug it into
	 *  the chain.
	 */
	protected void applyXSLHandler(Writer writer) {
		StreamResult result = new StreamResult(writer);
		xslHandler.setResult(result);
		if (getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes")
			.equals("yes")) {
			xinclude.setReceiver(new ReceiverToSAX(xslHandler));
			receiver = xinclude;
		} else
			receiver = new ReceiverToSAX(xslHandler);
	}

	/**
	 *  Return my internal EntityResolver
	 *
	 *@return    The entityResolver value
	 */
	public EntityResolver getEntityResolver() {
		return entityResolver;
	}

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

	public boolean getFeature(String name)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if (name.equals(Namespaces.SAX_NAMESPACES)
			|| name.equals(Namespaces.SAX_NAMESPACES_PREFIXES))
			throw new SAXNotSupportedException(name);
		throw new SAXNotRecognizedException(name);
	}

	public Object getProperty(String name)
		throws SAXNotRecognizedException, SAXNotSupportedException {
		if (name.equals(Namespaces.SAX_LEXICAL_HANDLER))
			return lexicalHandler;
		throw new SAXNotRecognizedException(name);
	}

	public String getStylesheetProperty(String name) {
		if (xslHandler != null)
			return xslHandler.getTransformer().getOutputProperty(name);
		return null;
	}
	
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

    protected void setXQueryContext(XQueryContext context) {
        xinclude.setXQueryContext(context);
    }
    
    public void parse(String systemId) throws IOException, SAXException {
		try {
			// try to load document from eXist
			//TODO: this systemId came from exist, so should be an unchecked create, right?
			DocumentImpl doc = (DocumentImpl) broker.getXMLResource(XmldbURI.create(systemId));
			if (doc == null)
				throw new SAXException("document " + systemId + " not found in database");
			else
				LOG.debug("serializing " + doc.getFileURI());
			if(!doc.getPermissions().validate(broker.getUser(), Permission.READ))
				throw new PermissionDeniedException("Not allowed to read resource");
			toSAX(doc);
		} catch (PermissionDeniedException e) {
			throw new SAXException("permission denied");
		}
	}

	/**
	 * Reset the class to its initial state.
	 */
	public void reset() {
		receiver = xinclude;
		xinclude.setReceiver(null);
		xslHandler = null;
		templates = null;
		outputProperties.clear();
	}

	public String serialize(DocumentImpl doc) throws SAXException {
		StringWriter writer = new StringWriter();
		serialize(doc, writer);
		return writer.toString();
	}
	
	/**
	 *  Serialize a document to the supplied writer.
	 */
	public void serialize(DocumentImpl doc, Writer writer) throws SAXException {
		serialize(doc, writer, true);
	}
	
	public void serialize(DocumentImpl doc, Writer writer, boolean prepareStylesheet) throws SAXException {
		if (prepareStylesheet) {
            try {
                prepareStylesheets(doc);
            } catch (TransformerConfigurationException e) {
                throw new SAXException(e.getMessage(), e);
            }
        }
		if (templates != null)
			applyXSLHandler(writer);
		else
			setPrettyPrinter(writer, outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes").equals("no"), null); //setPrettyPrinter(writer, false);
		
		serializeToReceiver(doc, true);
		releasePrettyPrinter();
	}

	public String serialize(NodeValue n) throws SAXException {
        try {
            setStylesheetFromProperties(n.getOwnerDocument());
        } catch (TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        StringWriter out = new StringWriter();
		if (templates != null)
			applyXSLHandler(out);
		else
			setPrettyPrinter(out, outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes").equals("no"),
                    n.getImplementationType() == NodeValue.PERSISTENT_NODE ? (NodeProxy)n : null); //setPrettyPrinter(out, false);
		serializeToReceiver(n, true);
		releasePrettyPrinter();
		return out.toString();
	}
	
	/**
	 *  Serialize a single NodeProxy.
	 *
	 *@param  p                 Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  SAXException  Description of the Exception
	 */
	public String serialize(NodeProxy p) throws SAXException {
        try {
            setStylesheetFromProperties(p.getOwnerDocument());
        } catch (TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        StringWriter out = new StringWriter();
		if (templates != null)
			applyXSLHandler(out);
		else
			setPrettyPrinter(out, outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "yes").equals("no"), p); //setPrettyPrinter(out, false);
		serializeToReceiver(p, false);
		releasePrettyPrinter();
		return out.toString();
	}

	public void prepareStylesheets(DocumentImpl doc) throws TransformerConfigurationException {
		if (outputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI, "no").equals("yes")) {
			String stylesheet = hasXSLPi(doc);
			if (stylesheet != null)
				setStylesheet(doc, stylesheet);
		}
		setStylesheetFromProperties(doc);
	}
	
	/**
	 *  Set the ContentHandler to be used during serialization.
	 *
	 *@param  contentHandler  The new contentHandler value
	 */
	public void setSAXHandlers(ContentHandler contentHandler, LexicalHandler lexicalHandler) {
		ReceiverToSAX toSAX = new ReceiverToSAX(contentHandler);
		toSAX.setLexicalHandler(lexicalHandler);
		if (getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes")
			.equals("yes")) {
			xinclude.setReceiver(toSAX);
			receiver = xinclude;
		} else
			receiver = toSAX;
	}

    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }
    
	/* (non-Javadoc)
	 * @see org.xml.sax.XMLReader#setContentHandler(org.xml.sax.ContentHandler)
	 */
	public void setContentHandler(ContentHandler handler) {
		setSAXHandlers(handler, null);
	}
	
	/**
	 * Required by interface XMLReader. Always returns null.
	 * 
	 * @see org.xml.sax.XMLReader#getContentHandler()
	 */
	public ContentHandler getContentHandler() {
		return null;
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
		if (name.equals(Namespaces.SAX_NAMESPACES)
			|| name.equals(Namespaces.SAX_NAMESPACES_PREFIXES))
			throw new SAXNotSupportedException(name);
		throw new SAXNotRecognizedException(name);
	}

	protected void setPrettyPrinter(Writer writer, boolean xmlDecl, NodeProxy root) {
		outputProperties.setProperty(	
			OutputKeys.OMIT_XML_DECLARATION,
			xmlDecl ? "no" : "yes");
        xmlout = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
		xmlout.setOutput(writer, outputProperties);
		if (getProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes")
				.equals("yes")) {
			xinclude.setReceiver(xmlout);
			receiver = xinclude;
		} else
			receiver = xmlout;
        if (root != null && getHighlightingMode() != TAG_NONE) {
            IndexController controller = broker.getIndexController();
            MatchListener listener = controller.getMatchListener(root);
            if (listener != null) {
                MatchListener last = (MatchListener) listener.getLastInChain();
                last.setNextInChain(receiver);
                receiver = listener;
            }
        }
    }

    protected Receiver setupMatchListeners(NodeProxy p) {
        Receiver oldReceiver = receiver;
        if (getHighlightingMode() != TAG_NONE) {
            IndexController controller = broker.getIndexController();
            MatchListener listener = controller.getMatchListener(p);
            if (listener != null) {
                MatchListener last = (MatchListener) listener.getLastInChain();
                last.setNextInChain(receiver);
                receiver = listener;
            }
        }
        return oldReceiver;
    }
    
    protected void releasePrettyPrinter() {
		if (xmlout != null)
            SerializerPool.getInstance().returnObject(xmlout);
		xmlout = null;
	}

	protected void setStylesheetFromProperties(Document doc) throws TransformerConfigurationException {
		if(templates != null)
			return;
		String stylesheet = outputProperties.getProperty(EXistOutputKeys.STYLESHEET);
		if(stylesheet != null) {
			if(doc instanceof DocumentImpl)
				setStylesheet((DocumentImpl)doc, stylesheet);
			else
				setStylesheet(null, stylesheet);
		}
	}
	
	protected void checkStylesheetParams() {
		if(xslHandler == null)
			return;
		for(Enumeration e = outputProperties.propertyNames(); e.hasMoreElements(); ) {
			String property = (String)e.nextElement();
			if(property.startsWith(EXistOutputKeys.STYLESHEET_PARAM)) {
				String value = outputProperties.getProperty(property);
				property = property.substring(EXistOutputKeys.STYLESHEET_PARAM.length() + 1);
				xslHandler.getTransformer().setParameter(property, value);
			}
		}
	}

	/**
	 *  Plug an XSL stylesheet into the processing pipeline.
	 *  All output will be passed to this stylesheet.
	 */
	public void setStylesheet(DocumentImpl doc, String stylesheet) throws TransformerConfigurationException {
		if (stylesheet == null) {
			templates = null;
			return;
		}
		long start = System.currentTimeMillis();
		xslHandler = null;
        XmldbURI stylesheetUri = null;
        URI externalUri = null;
        try {
            stylesheetUri = XmldbURI.xmldbUriFor(stylesheet);
            if(!stylesheetUri.toCollectionPathURI().equals(stylesheetUri)) {
                externalUri = stylesheetUri.getXmldbURI();
            }
        } catch (URISyntaxException e) {
            //could be an external URI!
            try {
                externalUri = new URI(stylesheet);
            } catch (URISyntaxException ee) {
                throw new IllegalArgumentException("Stylesheet URI could not be parsed: "+ee.getMessage());
            }
        }
        // does stylesheet point to an external resource?
        if (externalUri!=null) {
            StreamSource source = new StreamSource(externalUri.toString());
            templates = factory.newTemplates(source);
            // read stylesheet from the database
        } else {
            // if stylesheet is relative, add path to the
            // current collection and normalize
            if(doc != null) {
                stylesheetUri = doc.getCollection().getURI().resolveCollectionPath(stylesheetUri).normalizeCollectionPath();
            }

            // load stylesheet from eXist
            DocumentImpl xsl = null;
            try {
                xsl = (DocumentImpl) broker.getXMLResource(stylesheetUri);
            } catch (PermissionDeniedException e) {
                throw new TransformerConfigurationException("permission denied to read " + stylesheetUri);
            }
            if (xsl == null) {
                throw new TransformerConfigurationException("stylesheet not found: " + stylesheetUri);
            }
            if(!xsl.getPermissions().validate(broker.getUser(), Permission.READ)) {
                throw new TransformerConfigurationException("permission denied to read " + stylesheetUri);
            }

            //TODO: use xmldbURI
            if (xsl.getCollection() != null) {
                factory.setURIResolver(
                        new InternalURIResolver(xsl.getCollection().getURI().toString()));
            }

            // save handlers
            Receiver oldReceiver = receiver;

            // compile stylesheet
            factory.setErrorListener(new ErrorListener());
            TemplatesHandler handler = factory.newTemplatesHandler();
            receiver = new ReceiverToSAX(handler);
            try {
                this.serializeToReceiver(xsl, true);
                templates = handler.getTemplates();
            } catch (SAXException e) {
                throw new TransformerConfigurationException(e.getMessage(), e);
            }

            // restore handlers
            receiver = oldReceiver;
            factory.setURIResolver(null);
        }
        LOG.debug(
                "compiling stylesheet took " + (System.currentTimeMillis() - start));
        if(templates != null)
            xslHandler = factory.newTransformerHandler(templates);
//			xslHandler.getTransformer().setOutputProperties(outputProperties);
        checkStylesheetParams();
	}

	/** 
	 * Set stylesheet parameter
	 **/
	public void setStylesheetParam(String param, String value) {
		if (xslHandler != null)
			xslHandler.getTransformer().setParameter(param, value);
	}

	protected void setXSLHandler(NodeProxy root) {
		if (templates != null && xslHandler != null) {
			SAXResult result = new SAXResult();
			boolean processXInclude =
				getProperty(
					EXistOutputKeys.EXPAND_XINCLUDES,
					"yes").equals(
					"yes");
			ReceiverToSAX filter;
			if (processXInclude) {
				filter = (ReceiverToSAX)xinclude.getReceiver();
			} else {
				filter = (ReceiverToSAX) receiver;
			}
			result.setHandler(filter.getContentHandler());
			result.setLexicalHandler(filter.getLexicalHandler());
			filter.setLexicalHandler(xslHandler);
			filter.setContentHandler(xslHandler);
			xslHandler.setResult(result);
			if (processXInclude) {
				xinclude.setReceiver(new ReceiverToSAX(xslHandler));
				receiver = xinclude;
			} else
				receiver = new ReceiverToSAX(xslHandler);
		}
        if (root != null && getHighlightingMode() != TAG_NONE) {
            IndexController controller = broker.getIndexController();
            MatchListener listener = controller.getMatchListener(root);
            if (listener != null) {
                MatchListener last = (MatchListener) listener.getLastInChain();
                last.setNextInChain(receiver);
                receiver = listener;
            }
        }
    }

	public void toSAX(DocumentImpl doc) throws SAXException {
		if (outputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI, "no").equals("yes")) {
			String stylesheet = hasXSLPi(doc);
			if (stylesheet != null)
                try {
                    setStylesheet(doc, stylesheet);
                } catch (TransformerConfigurationException e) {
                    throw new SAXException(e.getMessage(), e);
                }
        }
        try {
            setStylesheetFromProperties(doc);
        } catch (TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        setXSLHandler(null);
		serializeToReceiver(
			doc,
			getProperty(GENERATE_DOC_EVENTS, "false").equals("true"));
	}

	public void toSAX(NodeValue n) throws SAXException {
        try {
            setStylesheetFromProperties(n.getOwnerDocument());
        } catch (TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        setXSLHandler(n.getImplementationType() == NodeValue.PERSISTENT_NODE ? (NodeProxy)n : null);
		serializeToReceiver(
				n,
				getProperty(GENERATE_DOC_EVENTS, "false").equals("true"));
	}
	
	public void toSAX(NodeProxy p) throws SAXException {
	    try {
		setStylesheetFromProperties(p.getOwnerDocument());
	    } catch (TransformerConfigurationException e) {
		throw new SAXException(e.getMessage(), e);
	    }
	    setXSLHandler(p);
	    if (p.getNodeId() == NodeId.DOCUMENT_NODE) {
		serializeToReceiver(p.getDocument(), getProperty(GENERATE_DOC_EVENTS, "false").equals("true"));
	    } else {
		serializeToReceiver(p, getProperty(GENERATE_DOC_EVENTS, "false").equals("true"));
	    }
	}

	/**
	 * Serialize the items in the given sequence to SAX, starting with item start. If parameter
	 * wrap is set to true, output a wrapper element to enclose the serialized items. The
	 * wrapper element will be in namespace {@link org.exist.Namespaces#EXIST_NS} and has the following form:
	 * 
	 * &lt;exist:result hits="sequence length" start="value of start" count="value of count">
	 * 
	 * @param seq
	 * @param start
	 * @param count
	 * @param wrap
	 * @throws SAXException
	 */
	public void toSAX(Sequence seq, int start, int count, boolean wrap) throws SAXException {
        try {
            setStylesheetFromProperties(null);
        } catch (TransformerConfigurationException e) {
            throw new SAXException(e.getMessage(), e);
        }
        setXSLHandler(null);
		AttrList attrs = new AttrList();
		attrs.addAttribute(ATTR_HITS_QNAME, Integer.toString(seq.getItemCount()));
		attrs.addAttribute(ATTR_START_QNAME, Integer.toString(start));
		attrs.addAttribute(ATTR_COUNT_QNAME, Integer.toString(count));
		if (outputProperties.getProperty(PROPERTY_SESSION_ID) != null) {
            attrs.addAttribute(ATTR_SESSION_ID, outputProperties.getProperty(PROPERTY_SESSION_ID));
        }
		receiver.startDocument();
		if(wrap) {
			receiver.startPrefixMapping("exist", Namespaces.EXIST_NS);
			receiver.startElement(ELEM_RESULT_QNAME, attrs);
		}
		
		Item item;
		for(int i = --start; i < start + count; i++) {
			item = seq.itemAt(i);
			if (item == null) {
				LOG.debug("item " + i + " not found");
				continue;
			}
			if (Type.subTypeOf(item.getType(), Type. NODE)) {
				NodeValue node = (NodeValue) item;
				serializeToReceiver(node, false);
			} else {
				if(wrap) {
					attrs = new AttrList();
					attrs.addAttribute(ATTR_TYPE_QNAME, Type.getTypeName(item.getType()));
					receiver.startElement(ELEM_VALUE_QNAME, attrs);
				}
				try {
					receiver.characters(item.getStringValue());
				} catch (XPathException e) {
					throw new SAXException(e.getMessage(), e);
				}
				if(wrap) {
					receiver.endElement(ELEM_VALUE_QNAME);
				}
			}
		}
		
		if(wrap) {
			receiver.endElement(ELEM_RESULT_QNAME);
			receiver.endPrefixMapping("exist");
		}
		receiver.endDocument();
	}

    public void toReceiver(NodeProxy p, boolean highlightMatches) throws SAXException {
        toReceiver(p, highlightMatches, true);
    }

    public void toReceiver(NodeProxy p, boolean highlightMatches, boolean checkAttributes) throws SAXException {
        Receiver oldReceiver = highlightMatches ? setupMatchListeners(p) : receiver;
        serializeToReceiver(p, false, checkAttributes);
        receiver = oldReceiver;
    }

    protected abstract void serializeToReceiver(NodeProxy p, boolean generateDocEvent, boolean checkAttributes) throws SAXException;

    protected abstract void serializeToReceiver(DocumentImpl doc, boolean generateDocEvent)
    throws SAXException;
	
	protected void serializeToReceiver(NodeValue v, boolean generateDocEvents)
	throws SAXException {
		if(v.getImplementationType() == NodeValue.PERSISTENT_NODE)
			serializeToReceiver((NodeProxy)v, generateDocEvents, true);
		else
			serializeToReceiver((org.exist.memtree.NodeImpl)v, generateDocEvents);
	}
	
	protected void serializeToReceiver(org.exist.memtree.NodeImpl n, boolean generateDocEvents)
	throws SAXException {
		if (generateDocEvents)
			receiver.startDocument();
        setDocument(null);
        setXQueryContext(n.getDocument().getContext());
        n.streamTo(this, receiver);
		if (generateDocEvents)
			receiver.endDocument();
	}
	
	/**
	 * Inherited from XMLReader. Ignored.
	 * 
	 * @see org.xml.sax.XMLReader#setDTDHandler(org.xml.sax.DTDHandler)
	 */
	public void setDTDHandler(DTDHandler handler) {
	}
	
	/**
	 * Inherited from XMLReader. Ignored. Returns always null.
	 * 
	 * @see org.xml.sax.XMLReader#getDTDHandler()
	 */
	public DTDHandler getDTDHandler() {
		return null;
	}
	
    /**
     * Check if the document has an xml-stylesheet processing instruction
     * that references an XSLT stylesheet. Return the link to the stylesheet.
     *  
     * @param doc
     * @return link to the stylesheet
     */
	public String hasXSLPi(Document doc) {
        boolean applyXSLPI = 
            outputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI, "no").equalsIgnoreCase("yes");
        if (!applyXSLPI) return null;
        
		NodeList docChildren = doc.getChildNodes();
		Node node;
		String xsl, type, href;
		for (int i = 0; i < docChildren.getLength(); i++) {
			node = docChildren.item(i);
			if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
				&& ((ProcessingInstruction) node).getTarget().equals("xml-stylesheet")) {
				// found <?xml-stylesheet?>
				xsl = ((ProcessingInstruction) node).getData();
				type = XMLUtil.parseValue(xsl, "type");
				if(type != null && (type.equals(MimeType.XML_TYPE.getName()) || type.equals(MimeType.XSL_TYPE.getName()) || type.equals(MimeType.XSLT_TYPE.getName()))) {
					href = XMLUtil.parseValue(xsl, "href");
					if (href == null)
						continue;
					return href;
				}
			}
		}
		return null;
	}

	/**
	 *  URIResolver is called by the XSL transformer to handle <xsl:include>,
	 *  <xsl:import> ...
	 *
	 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 */
	private class InternalURIResolver implements URIResolver {

		private String collectionId = null;

		public InternalURIResolver(String collection) {
			collectionId = collection;
		}

		public Source resolve(String href, String base) throws TransformerException {
			LOG.debug("resolving stylesheet ref " + href);
			if (href.indexOf(':') != Constants.STRING_NOT_FOUND)
				// href is an URL pointing to an external resource
				return null;
            ///TODO : use dedicated function in XmldbURI
			URI baseURI = URI.create(collectionId + "/");
			URI uri = URI.create(href);
			href = baseURI.resolve(uri).toString();
			Serializer serializer = broker.newSerializer();
			return new SAXSource(serializer, new InputSource(href));
		}
	}

    /**
     * An error listener that just rethrows the exception
     */
    private class ErrorListener implements javax.xml.transform.ErrorListener {

        public void warning(TransformerException exception) throws TransformerException {
            throw exception;
        }

        public void error(TransformerException exception) throws TransformerException {
            throw exception;
        }

        public void fatalError(TransformerException exception) throws TransformerException {
            throw exception;
        }
    }
}
