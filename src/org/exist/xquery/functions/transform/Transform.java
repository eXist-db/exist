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

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.Receiver;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Transform extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("transform", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX),
			"Applies an XSL stylesheet to the node tree passed as first argument. The stylesheet " +
			"is read from the URI specified in the second argument. Stylesheet parameters " +
			"may be passed in the third argument using an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/>" +
			"</parameters>",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
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
	 * @see org.exist.xpath.BasicFunction#eval(org.exist.xpath.value.Sequence[], org.exist.xpath.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if(args[0].getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		Item inputNode = args[0].itemAt(0);
		String stylesheet = args[1].getStringValue();
		Node options = null;
		if(args[2].getLength() > 0)
			options = ((NodeValue)args[2].itemAt(0)).getNode();
		
		SAXTransformerFactory factory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
		TransformerHandler handler;
		try {
			Templates templates = getSource(factory, stylesheet);
			handler = factory.newTransformerHandler(templates);
			if(options != null)
				parseParameters(options, handler.getTransformer());
		} catch (TransformerConfigurationException e) {
			throw new XPathException("Unable to set up transformer: " + e.getMessage(), e);
		}
		
		context.pushDocumentContext();
		MemTreeBuilder builder = context.getDocumentBuilder();
		Receiver receiver = new Receiver(builder);
		SAXResult result = new SAXResult(receiver);
		handler.setResult(result);
		
		try {
			handler.startDocument();
			inputNode.toSAX(context.getBroker(), handler);
			handler.endDocument();
		} catch (SAXException e) {
			throw new XPathException("SAX exception while transforming node: " + e.getMessage(), e);
		}
		Node first = builder.getDocument().getFirstChild();
		context.popDocumentContext();
		return (NodeValue)first;
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
	
	private Templates getSource(TransformerFactory factory, String stylesheet) 
	throws XPathException, TransformerConfigurationException {
		if(stylesheet.indexOf(':') < 0) {
			File f = new File(stylesheet);
			if(f.canRead())
				stylesheet = f.toURI().toASCIIString();
			else {
				stylesheet = context.getBaseURI() + File.separatorChar + stylesheet;
				f = new File(stylesheet);
				if(f.canRead())
					stylesheet = f.toURI().toASCIIString();
			}
		}
		CachedStylesheet cached = (CachedStylesheet)cache.get(stylesheet);
		try {
			if(cached == null) {
				cached = new CachedStylesheet(factory, new URL(stylesheet));
				cache.put(stylesheet, cached);
			}
			return cached.getTemplates();
		} catch (MalformedURLException e) {
			throw new XPathException("Malformed URL for stylesheet: " + stylesheet, e);
		} catch (IOException e) {
			throw new XPathException("IO error while loading stylesheet: " + stylesheet, e);
		}
	}
	
	private class CachedStylesheet {
		
		TransformerFactory factory;
		long lastModified = -1;
		Templates templates = null;
		URL url;
		
		public CachedStylesheet(TransformerFactory factory, URL url) 
		throws TransformerConfigurationException, IOException {
			this.factory = factory;
			this.url = url;
			getTemplates();
		}
		
		public Templates getTemplates() throws TransformerConfigurationException, IOException {
			URLConnection connection = url.openConnection();
			long modified = connection.getLastModified();
			if(templates == null || modified > lastModified || modified == 0) {
				LOG.debug("compiling stylesheet " + url.toString());
				templates = factory.newTemplates(new StreamSource(connection.getInputStream()));
			}
			lastModified = modified;
			return templates;
		}
	}
}
