/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.xslt;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.xslt.expression.ApplyTemplates;
import org.w3c.dom.Node;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class TransformerImpl extends Transformer {

	public final double version = 2.0;

	private XSLStylesheet compiled = null;
	
	private URIResolver resolver;
	
	private Properties outputPropertys = null;
	private Map<String, Object> parameters = null;
	
	private ErrorListener listener = null;
	
	public Map<String, Object> getParameters() {
		if (parameters == null)
			parameters = new HashMap<String, Object>();
		
		return parameters;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#clearParameters()
	 */
	@Override
	public void clearParameters() {
		getParameters().clear();
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#getErrorListener()
	 */
	@Override
	public ErrorListener getErrorListener() {
		return listener;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#getOutputProperties()
	 */
	@Override
	public Properties getOutputProperties() {
		if (outputPropertys == null)
			outputPropertys = new Properties();
		
		return outputPropertys;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#getOutputProperty(java.lang.String)
	 */
	@Override
	public String getOutputProperty(String name) throws IllegalArgumentException {
		return getOutputProperties().getProperty(name);
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#getParameter(java.lang.String)
	 */
	@Override
	public Object getParameter(String name) {
		return getParameters().get(name);
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#getURIResolver()
	 */
	@Override
	public URIResolver getURIResolver() {
		return resolver;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#setErrorListener(javax.xml.transform.ErrorListener)
	 */
	@Override
	public void setErrorListener(ErrorListener listener) throws IllegalArgumentException {
		this.listener = listener;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#setOutputProperties(java.util.Properties)
	 */
	@Override
	public void setOutputProperties(Properties oformat) {
		outputPropertys = oformat;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#setOutputProperty(java.lang.String, java.lang.String)
	 */
	@Override
	public void setOutputProperty(String name, String value) throws IllegalArgumentException {
		getOutputProperties().put(name, value);
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#setParameter(java.lang.String, java.lang.Object)
	 */
	@Override
	public void setParameter(String name, Object value) {
		getParameters().put(name, value);
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#setURIResolver(javax.xml.transform.URIResolver)
	 */
	@Override
	public void setURIResolver(URIResolver resolver) {
		this.resolver = resolver;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Transformer#transform(javax.xml.transform.Source, javax.xml.transform.Result)
	 */
	@Override
	public void transform(Source xmlSource, Result outputTarget) throws TransformerException {
		if (compiled == null)
			throw new TransformerException("Stylesheet has not been prepared.");

		if (xmlSource instanceof StAXSource && outputTarget instanceof StAXResult) {
			StAXSource in = (StAXSource) xmlSource;
			StAXResult out = (StAXResult) outputTarget;
			
			transformStream(in, out);
			
		} else {
			throw new TransformerException("The source type "+xmlSource.getClass()+" do not supported.");
		}
	}

	private void transformStream(StAXSource source, StAXResult out) throws TransformerException {
		try {
			final BrokerPool db = BrokerPool.getInstance();
			try(final DBBroker broker = db.getBroker()) {

				StAXSequenceIterator sequenceIterator = new StAXSequenceIterator(source);

				XSLContext context = new XSLContext(db);
				context.setOutput(out);

				context.getResultWriter().writeStartDocument("UTF-8", "1.0");

				if (compiled.rootTemplate != null) {
					compiled.rootTemplate.process(context, sequenceIterator);
				} else {
					ApplyTemplates.searchAndProcess(sequenceIterator, context);
				}

				context.getResultWriter().writeEndDocument();
			}
		} catch (final EXistException | XMLStreamException e) {
			e.printStackTrace();
		}

//		try {
//			for (int event = staxXmlReader.next(); event != XMLStreamConstants.END_DOCUMENT; event = staxXmlReader.next()) {
//				switch (event) {
//				  case XMLStreamConstants.START_DOCUMENT:
//				    System.out.println("Start document " + staxXmlReader.getLocalPart());
//				    break;
//				  case XMLStreamConstants.START_ELEMENT:
//				    System.out.println("Start element " + staxXmlReader.getLocalPart());
////				 	System.out.println("Element text " + staxXmlReader.getElementText());
//				    break;
//				  case XMLStreamConstants.END_ELEMENT:
//				    System.out.println("End element " + staxXmlReader.getLocalPart());
//				    break;
//				  default:
//				    break;
//				  }
//				}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}

	public Sequence transform(Item xmlSource) throws XPathException {
		if (compiled == null)
			throw new XPathException("Stylesheet has not been prepared.");
		
		//work around for top xpath child::
		Sequence source = new ValueSequence();

		Item xslItem = xmlSource;
		if (xmlSource instanceof Node) {
			xslItem = (Item) ((Node) xmlSource).getOwnerDocument();
		}
		source.add(xslItem);
		
		return compiled.eval(source);
	}

	public Sequence transform(Sequence xmlSource) throws XPathException {
		if (compiled == null)
			throw new XPathException("Stylesheet has not been prepared.");
		
		//work around for top xpath child::
		Sequence source = new ValueSequence();
//		for (Item item : xmlSource) {
		for (SequenceIterator iterInner = xmlSource.iterate(); iterInner.hasNext();) {
			Item item = iterInner.nextItem();   
			
			Item xslItem = item;
			if (item instanceof Node) {
				xslItem = (Item) ((Node) item).getOwnerDocument();
			}
			source.add(xslItem);
		}
		
		return compiled.eval(source);
	}

	public void setPreparedStylesheet(XSLStylesheet compiled) {
		this.compiled = compiled;
	}

}
