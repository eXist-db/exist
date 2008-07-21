/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions.transform;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Option;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.TransformerFactoryAllocator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Transform extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
			"Applies an XSL stylesheet to the node tree passed as first argument. The stylesheet " +
			"is specified in the second argument. This should either be an URI or a node. If it is an " +
			"URI, it can either point to an external location or to an XSL stored in the db by using the " +
			"'xmldb:' scheme. Stylesheets are cached unless they were just created from an XML " +
			"fragment and not from a complete document. " +
			"Stylesheet parameters " +
			"may be passed in the third argument using an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/>" +
			"</parameters>",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)),
        new FunctionSignature(
			new QName("transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
			"Applies an XSL stylesheet to the node tree passed as first argument. The stylesheet " +
			"is specified in the second argument. This should either be an URI or a node. If it is an " +
			"URI, it can either point to an external location or to an XSL stored in the db by using the " +
			"'xmldb:' scheme. Stylesheets are cached unless they were just created from an XML " +
			"fragment and not from a complete document. " +
			"Stylesheet parameters " +
			"may be passed in the third argument using an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/>" +
			"</parameters>",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)),
        new FunctionSignature(
            new QName("stream-transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
            "Applies an XSL stylesheet to the node tree passed as first argument. The parameters are the same " +
            "as for the transform function. stream-transform can only be used within a servlet context. Instead " +
            "of returning the transformed document fragment, it directly streams its output to the servlet's output stream. " +
            "It should thus be the last statement in the XQuery.",
            new SequenceType[] {
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)},
            new SequenceType(Type.ITEM, Cardinality.EMPTY)),
        new FunctionSignature(
            new QName("stream-transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
            "Applies an XSL stylesheet to the node tree passed as first argument. The parameters are the same " +
            "as for the transform function. stream-transform can only be used within a servlet context. Instead " +
            "of returning the transformed document fragment, it directly streams its output to the servlet's output stream. " +
            "It should thus be the last statement in the XQuery.",
            new SequenceType[] {
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
            new SequenceType(Type.ITEM, Cardinality.EMPTY))
    };

	private final Map cache = new HashMap();
	
	/**
	 * @param context
	 * @param signature
	 */
	public Transform(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if(args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		Item inputNode = args[0].itemAt(0);
		Item stylesheetItem = args[1].itemAt(0);
		
		Node options = null;
		if(!args[2].isEmpty())
			options = ((NodeValue)args[2].itemAt(0)).getNode();

        // apply serialization options set on the XQuery context
        Properties serializeOptions = new Properties();
        if (getArgumentCount() == 4) {
            String serOpts = args[3].getStringValue();
            String[] contents = Option.tokenize(serOpts);
            for (int i = 0; i < contents.length; i++) {
                String[] pair = Option.parseKeyValuePair(contents[i]);
                if (pair == null)
                    throw new XPathException(getASTNode(), "Found invalid serialization option: " + pair);
                LOG.debug("Setting serialization property: " + pair[0] + " = " + pair[1]);
                serializeOptions.setProperty(pair[0], pair[1]);
            }
        } else
            context.checkOptions(serializeOptions);

        TransformerHandler handler = createHandler(stylesheetItem, options);
        if (isCalledAs("transform"))
        {
        	//transform:transform()
        	
            ValueSequence seq = new ValueSequence();
    		context.pushDocumentContext();
    		MemTreeBuilder builder = context.getDocumentBuilder();
    		DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
    		SAXResult result = new SAXResult(receiver);
    		result.setLexicalHandler(receiver);		//preserve comments etc... from xslt output
    		handler.setResult(result);
    		try {
    			handler.startDocument();
    			inputNode.toSAX(context.getBroker(), handler, serializeOptions);
    			handler.endDocument();
    		} catch (SAXException e) {
    			throw new XPathException(getASTNode(), "SAX exception while transforming node: " + e.getMessage(), e);
    		}
    		Node next = builder.getDocument().getFirstChild();
            while (next != null) {
                seq.add((NodeValue) next);
                next = next.getNextSibling();
            }
    		context.popDocumentContext();
    		return seq;
        }
        else
        {
        	//transform:stream-transform()
        	
            ResponseModule myModule = (ResponseModule)context.getModule(ResponseModule.NAMESPACE_URI);
            // response object is read from global variable $response
            Variable respVar = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
            if(respVar == null)
                throw new XPathException(getASTNode(), "No response object found in the current XQuery context.");
            if(respVar.getValue().getItemType() != Type.JAVA_OBJECT)
                throw new XPathException(getASTNode(), "Variable $response is not bound to an Java object.");
            JavaObjectValue respValue = (JavaObjectValue)
                respVar.getValue().itemAt(0);
            if (!"org.exist.http.servlets.HttpResponseWrapper".equals(respValue.getObject().getClass().getName()))
                throw new XPathException(getASTNode(), signatures[1].toString() + 
                        " can only be used within the EXistServlet or XQueryServlet");
            ResponseWrapper response = (ResponseWrapper) respValue.getObject();
            try {
                OutputStream os = new BufferedOutputStream(response.getOutputStream());
                StreamResult result = new StreamResult(os);
                handler.setResult(result);
                handler.startDocument();
                inputNode.toSAX(context.getBroker(), handler, serializeOptions);
                handler.endDocument();
                os.close();
                //commit the response
                response.flushBuffer();
            } catch (SAXException e) {
                throw new XPathException(getASTNode(), "SAX exception while transforming node: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new XPathException(getASTNode(), "IO exception while transforming node: " + e.getMessage(), e);
            }
            return Sequence.EMPTY_SEQUENCE;
        }
	}

    /**
     * @param stylesheetItem
     * @param options
     * @return
     * @throws TransformerFactoryConfigurationError
     * @throws XPathException
     */
    private TransformerHandler createHandler(Item stylesheetItem, Node options) throws TransformerFactoryConfigurationError, XPathException
    {
    	SAXTransformerFactory factory = (SAXTransformerFactory)TransformerFactoryAllocator.getTransformerFactory(context.getBroker());
    	
		TransformerHandler handler;
		try
		{
			Templates templates = null;
			if(Type.subTypeOf(stylesheetItem.getType(), Type.NODE))
			{
				NodeValue stylesheetNode = (NodeValue)stylesheetItem;
				// if the passed node is a document node or document root element,
				// we construct an XMLDB URI and use the caching implementation. 
				if(stylesheetNode.getImplementationType() == NodeValue.PERSISTENT_NODE)
				{
					NodeProxy root = (NodeProxy) stylesheetNode;
                    if (root.getNodeId() == NodeId.DOCUMENT_NODE || root.getNodeId().getTreeLevel() == 1)
                    {
						//as this is a persistent node (e.g. a stylesheet stored in the db)
						//set the URI Resolver as a DatabaseResolver
						factory.setURIResolver(new DatabaseResolver(root.getDocument()));
					
						String uri = XmldbURI.XMLDB_URI_PREFIX + context.getBroker().getBrokerPool().getId() + "://" + root.getDocument().getURI();
						templates = getSource(factory, uri);
					}
				}
				if(templates == null)
				{
					templates = getSource(factory, stylesheetNode);
				}
			}
			else
			{
				String stylesheet = stylesheetItem.getStringValue();
				templates = getSource(factory, stylesheet);
			}
			handler = factory.newTransformerHandler(templates);
			
			if(options != null)
			{
				parseParameters(options, handler.getTransformer());
			}
		}
		catch (TransformerConfigurationException e)
		{
			throw new XPathException(getASTNode(), "Unable to set up transformer: " + e.getMessage(), e);
		}
        return handler;
    }

	private void parseParameters(Node options, Transformer handler) throws XPathException {
		if(options.getNodeType() == Node.ELEMENT_NODE && options.getLocalName().equals("parameters")) {
			Node child = options.getFirstChild();
			while(child != null) {
				if(child.getNodeType() == Node.ELEMENT_NODE && child.getLocalName().equals("param")) {
					Element elem = (Element)child;
					String name = elem.getAttribute("name");
					String value = elem.getAttribute("value");
					if(name == null || value == null)
						throw new XPathException(getASTNode(), "Name or value attribute missing for stylesheet parameter");
					handler.setParameter(name, value);
				}
				child = child.getNextSibling();
			}
		}
	}
	
	private Templates getSource(SAXTransformerFactory factory, String stylesheet) 
	throws XPathException, TransformerConfigurationException {
		String base;
		if(stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {
			File f = new File(stylesheet);
			if(f.canRead()) 
				stylesheet = f.toURI().toASCIIString();
			else {
				stylesheet = context.getModuleLoadPath() + File.separatorChar + stylesheet;
				f = new File(stylesheet);
				if(f.canRead()) stylesheet = f.toURI().toASCIIString();
			}
		}
        //TODO : use dedicated function in XmldbURI
		int p = stylesheet.lastIndexOf("/");
		if(p != Constants.STRING_NOT_FOUND)
			base = stylesheet.substring(0, p);
		else
			base = stylesheet;
		CachedStylesheet cached = (CachedStylesheet)cache.get(stylesheet);
		try {
			if(cached == null) {
				cached = new CachedStylesheet(factory, stylesheet, base);
				cache.put(stylesheet, cached);
			}
			return cached.getTemplates();
		} catch (MalformedURLException e) {
			LOG.debug(e.getMessage(), e);
			throw new XPathException(getASTNode(), "Malformed URL for stylesheet: " + stylesheet, e);
		} catch (IOException e) {
			throw new XPathException(getASTNode(), "IO error while loading stylesheet: " + stylesheet, e);
		}
	}
	
	private Templates getSource(SAXTransformerFactory factory, NodeValue stylesheetRoot) throws XPathException, TransformerConfigurationException
	{
		TemplatesHandler handler = factory.newTemplatesHandler();
		try {
			handler.startDocument();
			stylesheetRoot.toSAX(context.getBroker(), handler, null);
			handler.endDocument();
			return handler.getTemplates();
		} catch (SAXException e) {
			throw new XPathException(getASTNode(),
				"A SAX exception occurred while compiling the stylesheet: " + e.getMessage(), e);
		}
	}
	
	//TODO: revisit this class with XmldbURI in mind
	private class CachedStylesheet {
		
		SAXTransformerFactory factory;
		long lastModified = -1;
		Templates templates = null;
		String uri;
		
		public CachedStylesheet(SAXTransformerFactory factory, String uri, String baseURI) 
		throws TransformerConfigurationException, IOException, XPathException {
			this.factory = factory;
			this.uri = uri;
			if (!baseURI.startsWith("xmldb:exist://"))
				factory.setURIResolver(new ExternalResolver(baseURI));
			getTemplates();
		}
		
		public Templates getTemplates() throws TransformerConfigurationException, IOException, XPathException {
			if (uri.startsWith("xmldb:exist://")) {
				String docPath = uri.substring("xmldb:exist://".length());
				DocumentImpl doc = null;
				try {
					doc = context.getBroker().getXMLResource(XmldbURI.create(docPath), Lock.READ_LOCK);
					if (doc != null && (templates == null || doc.getMetadata().getLastModified() > lastModified))
						templates = getSource(doc);
					lastModified = doc.getMetadata().getLastModified();
				} catch (PermissionDeniedException e) {
					throw new XPathException(getASTNode(), "Permission denied to read stylesheet: " + uri);
				} finally {
					doc.getUpdateLock().release(Lock.READ_LOCK);
				}
			} else {
				URL url = new URL(uri);
				URLConnection connection = url.openConnection();
				long modified = connection.getLastModified();
				if(templates == null || modified > lastModified || modified == 0) {
					LOG.debug("compiling stylesheet " + url.toString());
					templates = factory.newTemplates(new StreamSource(connection.getInputStream()));
				}
				lastModified = modified;
			}
			return templates;
		}
		
		private Templates getSource(DocumentImpl stylesheet)
		throws XPathException, TransformerConfigurationException {
			factory.setURIResolver(new DatabaseResolver(stylesheet));
			TemplatesHandler handler = factory.newTemplatesHandler();
			try {
				handler.startDocument();
				Serializer serializer = context.getBroker().getSerializer();
				serializer.reset();
				serializer.setSAXHandlers(handler, null);
				serializer.toSAX(stylesheet);
				handler.endDocument();
				return handler.getTemplates();
			} catch (SAXException e) {
				throw new XPathException(getASTNode(),
					"A SAX exception occurred while compiling the stylesheet: " + e.getMessage(), e);
			}
		}
	}
	
	private class ExternalResolver implements URIResolver {
		
		private String baseURI;
		
		public ExternalResolver(String base) {
			this.baseURI = base;
		}
		
		/* (non-Javadoc)
		 * @see javax.xml.transform.URIResolver#resolve(java.lang.String, java.lang.String)
		 */
		public Source resolve(String href, String base)
				throws TransformerException {
			URL url;
			try {
                //TODO : use dedicated function in XmldbURI
				url = new URL(baseURI + "/"  + href);
				URLConnection connection = url.openConnection();
				return new StreamSource(connection.getInputStream());
			} catch (MalformedURLException e) {
				return null;
			} catch (IOException e) {
				return null;
			}
		}
	}
	
	private class DatabaseResolver implements URIResolver {
		
		DocumentImpl doc;
		
		public DatabaseResolver(DocumentImpl myDoc) {
			this.doc = myDoc;
		}
		
		
		/* (non-Javadoc)
		 * @see javax.xml.transform.URIResolver#resolve(java.lang.String, java.lang.String)
		 */
		public Source resolve(String href, String base)
			throws TransformerException {
			Collection collection = doc.getCollection();
			String path;
            //TODO : use dedicated function in XmldbURI
			if(href.startsWith("/"))
				path = href;
			else
				path = collection.getURI() + "/" + href;
			DocumentImpl xslDoc;
			try {
				xslDoc = (DocumentImpl) context.getBroker().getXMLResource(XmldbURI.create(path));
			} catch (PermissionDeniedException e) {
				throw new TransformerException(e.getMessage(), e);
			}
			if(xslDoc == null) {
				LOG.debug("Document " + href + " not found in collection " + collection.getURI());
				return null;
			}
			if(!xslDoc.getPermissions().validate(context.getUser(), Permission.READ))
			    throw new TransformerException("Insufficient privileges to read resource " + path);
			DOMSource source = new DOMSource(xslDoc);
			return source;
		}
	}
}
