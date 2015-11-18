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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.XPathException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.XMLFilter;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class TransformerFactoryImpl extends SAXTransformerFactory {
	
    private final static Logger LOG = LogManager.getLogger(TransformerFactoryImpl.class);

    private BrokerPool pool = null;
    
    private URIResolver resolver;
    
    private Map<String, Object> attributes = new HashMap<String, Object>();
    
    private ErrorListener errorListener = null;
    
	public TransformerFactoryImpl() {
	}
	
	public void setBrokerPool(BrokerPool pool) {
		this.pool = pool;
	}
	
	public DBBroker getBroker() throws EXistException {
		if (pool != null) {
			return pool.getBroker();
		}
		
		throw new EXistException("that shouldn't happend. internal error.");
	}

	public void releaseBroker(final DBBroker broker) throws EXistException {
		if (pool == null) {
			throw new EXistException("Database wan't set properly.");
		}

		broker.close();
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#getAssociatedStylesheet(javax.xml.transform.Source, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Source getAssociatedStylesheet(Source source, String media, String title, String charset)
			throws TransformerConfigurationException {
    	throw new RuntimeException("Not implemented: TransformerFactory.getAssociatedStylesheet");
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#getAttribute(java.lang.String)
	 */
	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#getErrorListener()
	 */
	@Override
	public ErrorListener getErrorListener() {
		return errorListener;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#getFeature(java.lang.String)
	 */
	@Override
	public boolean getFeature(String name) {
    	throw new RuntimeException("Not implemented: TransformerFactory.getFeature");
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#getURIResolver()
	 */
	@Override
	public URIResolver getURIResolver() {
		return resolver;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#newTemplates(javax.xml.transform.Source)
	 */
	@Override
	public Templates newTemplates(Source source) throws TransformerConfigurationException {
		//XXX: handle buffered input stream
		if (source instanceof SourceImpl) {
			try {
				return XSL.compile(((Document) ((SourceImpl) source).source).getDocumentElement());
			} catch (XPathException e) {
				LOG.debug(e);
		    	throw new TransformerConfigurationException("Compilation error.",e);
			}
		} else if (source instanceof Element) {
			try {
				return XSL.compile((Element)source);
			} catch (XPathException e) {
				LOG.debug(e);
		    	throw new TransformerConfigurationException("Compilation error.",e);
			}
		} else if (source instanceof InputStream) {
			DBBroker broker = null;
			try {
				broker = getBroker();
				return XSL.compile((InputStream)source, broker);
			} catch (XPathException e) {
				LOG.debug(e);
		    	throw new TransformerConfigurationException("Compilation error.",e);
			} catch (EXistException e) {
				LOG.debug(e);
		    	throw new TransformerConfigurationException("Compilation error.",e);
			} finally {
				try {
					releaseBroker(broker);
				} catch (EXistException e) {
			    	throw new TransformerConfigurationException("Compilation error.",e);
				}
			}
		} else if (source instanceof StreamSource) {
			DBBroker broker = null;
			try {
				broker = getBroker();
				return XSL.compile(((StreamSource)source).getInputStream(), broker);
			} catch (XPathException e) {
				LOG.debug(e);
				throw new TransformerConfigurationException("Compilation error.",e);
			} catch (EXistException e) {
				LOG.debug(e);
		    	throw new TransformerConfigurationException("Compilation error.",e);
			} finally {
				try {
					releaseBroker(broker);
				} catch (EXistException e) {
			    	throw new TransformerConfigurationException("Compilation error.",e);
				}
			} 
		}
		throw new TransformerConfigurationException("Not supported source "+source.getClass());
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#newTransformer()
	 */
	@Override
	public Transformer newTransformer() throws TransformerConfigurationException {
		//TODO: setURIresolver ???
		return new org.exist.xslt.TransformerImpl();
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#newTransformer(javax.xml.transform.Source)
	 */
	@Override
	public Transformer newTransformer(Source source) throws TransformerConfigurationException {
    	throw new RuntimeException("Not implemented: TransformerFactory.newTransformer");
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#setAttribute(java.lang.String, java.lang.Object)
	 */
	@Override
	public void setAttribute(String name, Object value) {
		if (name.equals(TransformerFactoryAllocator.PROPERTY_BROKER_POOL))
			pool = (BrokerPool) value;
		
		attributes.put(name, value);
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#setErrorListener(javax.xml.transform.ErrorListener)
	 */
	@Override
	public void setErrorListener(ErrorListener listener) {
		errorListener = listener;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#setFeature(java.lang.String, boolean)
	 */
	@Override
	public void setFeature(String name, boolean value) throws TransformerConfigurationException {
    	throw new RuntimeException("Not implemented: TransformerFactory.setFeature");
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.TransformerFactory#setURIResolver(javax.xml.transform.URIResolver)
	 */
	@Override
	public void setURIResolver(URIResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public TemplatesHandler newTemplatesHandler() throws TransformerConfigurationException {
		return new TemplatesHandlerImpl();
	}

	@Override
	public TransformerHandler newTransformerHandler() throws TransformerConfigurationException {
		return new TransformerHandlerImpl(new XSLContext(pool), newTransformer());
	}

	@Override
	public TransformerHandler newTransformerHandler(Source src) throws TransformerConfigurationException {
    	throw new RuntimeException("Not implemented: TransformerFactory.newTransformerHandler");
	}

	@Override
	public TransformerHandler newTransformerHandler(Templates templates) throws TransformerConfigurationException {
		if (templates == null)
            throw new TransformerConfigurationException("Templates object can not be null.");
        if (!(templates instanceof XSLStylesheet))
            throw new TransformerConfigurationException("Templates object was not created by exist xslt ("+templates.getClass()+")");

		return new TransformerHandlerImpl(new XSLContext(pool), templates.newTransformer());
	}

	@Override
	public XMLFilter newXMLFilter(Source src) throws TransformerConfigurationException {
    	throw new RuntimeException("Not implemented: TransformerFactory.newXMLFilter");
	}

	@Override
	public XMLFilter newXMLFilter(Templates templates) throws TransformerConfigurationException {
    	throw new RuntimeException("Not implemented: TransformerFactory.newXMLFilter");
	}

}
