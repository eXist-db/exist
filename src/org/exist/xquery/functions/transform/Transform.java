/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.lock.Lock;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Transform extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("transform", TransformModule.NAMESPACE_URI, TransformModule.PREFIX),
			"Applies an XSL stylesheet to the node tree passed as first argument. The stylesheet " +
			"is specified in the second argument. This should either be an URI or a node. " +
			"Stylesheet parameters " +
			"may be passed in the third argument using an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/>" +
			"</parameters>",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));

	private Map cache = new HashMap();
	
	/**
	 * @param context
	 * @param signature
	 */
	public Transform(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if(args[0].getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		Item inputNode = args[0].itemAt(0);
		Item stylesheetItem = args[1].itemAt(0);
		
		Node options = null;
		if(args[2].getLength() > 0)
			options = ((NodeValue)args[2].itemAt(0)).getNode();
		
		SAXTransformerFactory factory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
		TransformerHandler handler;
		try {
			Templates templates;
			if(Type.subTypeOf(stylesheetItem.getType(), Type.NODE)) {
				NodeValue stylesheetNode = (NodeValue)stylesheetItem;
				templates = getSource(factory, stylesheetNode);
			} else {
				String stylesheet = stylesheetItem.getStringValue();
				templates = getSource(factory, stylesheet);
			}
			handler = factory.newTransformerHandler(templates);
			if(options != null)
				parseParameters(options, handler.getTransformer());
		} catch (TransformerConfigurationException e) {
			throw new XPathException("Unable to set up transformer: " + e.getMessage(), e);
		}
		
		context.pushDocumentContext();
		MemTreeBuilder builder = context.getDocumentBuilder();
		DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
		SAXResult result = new SAXResult(receiver);
		handler.setResult(result);
		try {
			handler.startDocument();
			inputNode.toSAX(context.getBroker(), handler);
			handler.endDocument();
		} catch (SAXException e) {
			throw new XPathException("SAX exception while transforming node: " + e.getMessage(), e);
		}
        ValueSequence seq = new ValueSequence();
		Node next = builder.getDocument().getFirstChild();
        while (next != null) {
            seq.add((NodeValue) next);
            next = next.getNextSibling();
        }
		context.popDocumentContext();
		return seq;
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
						throw new XPathException("Name or value attribute missing for stylesheet parameter");
					handler.setParameter(name, value);
				}
				child = child.getNextSibling();
			}
		}
	}
	
	private Templates getSource(SAXTransformerFactory factory, String stylesheet) 
	throws XPathException, TransformerConfigurationException {
		String base;
		if(stylesheet.indexOf(':') < 0) {
			File f = new File(stylesheet);
			if(f.canRead()) 
				stylesheet = f.toURI().toASCIIString();
			else {
				stylesheet = context.getModuleLoadPath() + File.separatorChar + stylesheet;
				f = new File(stylesheet);
				if(f.canRead()) stylesheet = f.toURI().toASCIIString();
			}
		}
		int p = stylesheet.lastIndexOf('/');
		if(p > -1)
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
			throw new XPathException("Malformed URL for stylesheet: " + stylesheet, e);
		} catch (IOException e) {
			throw new XPathException("IO error while loading stylesheet: " + stylesheet, e);
		}
	}
	
	private Templates getSource(SAXTransformerFactory factory, NodeValue stylesheetRoot)
	throws XPathException, TransformerConfigurationException {
		if(stylesheetRoot.getImplementationType() == NodeValue.PERSISTENT_NODE) {
			factory.setURIResolver(new DatabaseResolver(((NodeProxy)stylesheetRoot).getDocument()));
		}
		TemplatesHandler handler = factory.newTemplatesHandler();
		try {
			handler.startDocument();
			stylesheetRoot.toSAX(context.getBroker(), handler);
			handler.endDocument();
			return handler.getTemplates();
		} catch (SAXException e) {
			throw new XPathException(getASTNode(),
				"A SAX exception occurred while compiling the stylesheet: " + e.getMessage(), e);
		}
	}
	
	private Templates getSource(SAXTransformerFactory factory, DocumentImpl stylesheet)
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
					doc = context.getBroker().openDocument(docPath, Lock.READ_LOCK);
					if (doc != null && (templates == null || doc.getLastModified() > lastModified))
						templates = getSource(factory, doc);
					lastModified = doc.getLastModified();
				} catch (PermissionDeniedException e) {
					throw new XPathException("Permission denied to read stylesheet: " + uri);
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
				url = new URL(baseURI + '/'  + href);
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
			if(href.startsWith("/"))
				path = href;
			else
				path = collection.getName() + '/' + href;
			DocumentImpl xslDoc;
			try {
				xslDoc = (DocumentImpl)
					context.getBroker().getDocument(path);
			} catch (PermissionDeniedException e) {
				throw new TransformerException(e.getMessage(), e);
			}
			if(xslDoc == null) {
				LOG.debug("Document " + href + " not found in collection " + collection.getName());
				return null;
			}
			if(!xslDoc.getPermissions().validate(context.getUser(), Permission.READ))
			    throw new TransformerException("Insufficient privileges to read resource " + path);
			DOMSource source = new DOMSource(xslDoc);
			return source;
		}
	}
}
