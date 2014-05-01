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
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.apache.log4j.Logger;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.SAXAdapter;
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
	public static NodeValue stringToXML(XQueryContext context, String str) throws SAXException, IOException {
            final Reader reader = new StringReader(str);
            try {
                return inputSourceToXML(context, new InputSource(reader));
            } finally {
                reader.close();
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
	public static NodeValue streamToXML(XQueryContext context, InputStream is) throws SAXException, IOException {
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
        public static NodeValue sourceToXML(XQueryContext context, Source src) throws SAXException, IOException {
            if(src instanceof SAXSource && ((SAXSource)src).getXMLReader() != null) {
                //Handles the case where a SAXSource may already have an
                //XMLReader allocated, for example EXPath httpclient
                //where it wants to tidy html using TagSoup
                return inputSourceToXML(context, (SAXSource)src);
            } else {
                final InputSource inputSource = SAXSource.sourceToInputSource(src);
                if(inputSource == null){
                    throw new IOException(src.getClass().getName() + " is unsupported.");
                }

                return inputSourceToXML(context, inputSource);
            }
        }
	

        /**
	 * Takes a InputSource of XML and Creates an XML Node from it using SAX in the
	 * context of the query
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 * @param inputSource
	 *            The InputSource of XML
	 * 
	 * @return The NodeValue of XML
	 */
	public static NodeValue inputSourceToXML(XQueryContext context, InputSource inputSource) throws SAXException, IOException  {
            context.pushDocumentContext();

            XMLReader reader = null;
            try {
                // try and construct xml document from input stream, we use eXist's
                // in-memory DOM implementation
                reader = context.getBroker().getBrokerPool().getParserPool().borrowXMLReader();
                LOG.debug( "Parsing XML response ..." );

                // TODO : we should be able to cope with context.getBaseURI()
                final MemTreeBuilder builder = context.getDocumentBuilder();
                final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver( builder, true );
                reader.setContentHandler(receiver);
                reader.setProperty("http://xml.org/sax/properties/lexical-handler", receiver);
                reader.parse(inputSource);
                final Document doc = receiver.getDocument();
                // return (NodeValue)doc.getDocumentElement();
                return((NodeValue)doc);
            }  finally {
                context.popDocumentContext();

                if(reader != null){
                    context.getBroker().getBrokerPool().getParserPool().returnXMLReader(reader);
                }
            }
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
	private static NodeValue inputSourceToXML(XQueryContext context, SAXSource src) throws SAXException, IOException  {
            if(src.getXMLReader() == null) {
                throw new SAXException("No XML Reader specified.");
            }
            final XMLReader reader = src.getXMLReader();
            
            context.pushDocumentContext();

            try {
                // try and construct xml document from input stream, we use eXist's
                // in-memory DOM implementation

                // TODO : we should be able to cope with context.getBaseURI()
                final MemTreeBuilder builder = context.getDocumentBuilder();
                final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
                reader.setContentHandler(receiver);
                reader.parse(src.getInputSource());
                final Document doc = receiver.getDocument();
                return((NodeValue)doc);
            }  finally {
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
	 *            The Source for the HTML
         * @param parserFeatures
         *            The features to set on the Parser
         * @param parserProperties
         *            The properties to set on the Parser
	 * 
	 * @return An in-memory Document representing the XML'ised HTML
	 */
	public static DocumentImpl htmlToXHtml(XQueryContext context, String url, Source srcHtml, Map<String, Boolean> parserFeatures, Map<String, String>parserProperties) throws IOException, SAXException {
		
            final InputSource inputSource = SAXSource.sourceToInputSource(srcHtml);
        
            if(inputSource == null){
                throw new IOException(srcHtml.getClass().getName() + " is unsupported.");
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
	public static DocumentImpl htmlToXHtml(XQueryContext context, String url, InputSource srcHtml, Map<String, Boolean> parserFeatures, Map<String, String>parserProperties) throws IOException, SAXException {
            // we use eXist's in-memory DOM implementation
            org.exist.memtree.DocumentImpl memtreeDoc = null;

            // use Neko to parse the HTML content to XML
            XMLReader reader = null;
            try {
                LOG.debug("Converting HTML to XML using NekoHTML parser for: " + url);
                reader = (XMLReader) Class.forName("org.cyberneko.html.parsers.SAXParser").newInstance();
                
                if(parserFeatures != null) {
                    for(final Entry<String, Boolean> parserFeature : parserFeatures.entrySet()) {
                        reader.setFeature(parserFeature.getKey(), parserFeature.getValue());
                    }
                }

                if(parserProperties == null) {
                    //default: do not modify the case of elements and attributes
                    reader.setProperty("http://cyberneko.org/html/properties/names/elems","match");
                    reader.setProperty("http://cyberneko.org/html/properties/names/attrs","no-change");
                } else {
                    for(final Entry<String, String> parserProperty : parserProperties.entrySet()) {
                        reader.setProperty(parserProperty.getKey(), parserProperty.getValue());
                    }
                }
            } catch(final Exception e) {
                    final String errorMsg = "Error while invoking NekoHTML parser. ("
                                    + e.getMessage()
                                    + "). If you want to parse non-wellformed HTML files, put "
                                    + "nekohtml.jar into directory 'lib/user'.";
                    LOG.error(errorMsg, e);

                    throw new IOException(errorMsg, e);
            }

            final SAXAdapter adapter = new SAXAdapter();
            reader.setContentHandler(adapter);
            reader.parse(srcHtml);
            final Document doc = adapter.getDocument();
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
	public static Properties parseParameters(Node nParameters){
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
	public static Properties parseProperties(Node nProperties) {
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
	private static Properties parseProperties(Node container, String elementName) {
            final Properties properties = new Properties();

            if(container != null && container.getNodeType() == Node.ELEMENT_NODE) {
                final NodeList params = ((Element) container).getElementsByTagName(elementName);
                for(int i = 0; i < params.getLength(); i++) {
                    final Element param = ((Element) params.item(i));

                    final String name = param.getAttribute("name");
                    final String value = param.getAttribute("value");

                    if(name != null && value != null) {
                        properties.setProperty(name, value);
                    } else {
                        LOG.warn("Name or value attribute missing for " + elementName);
                    }
                }
            }
            return properties;
	}
    
        
    private static class ContextMapLocks {
        private final Map<String, ReentrantReadWriteLock> locks = new HashMap<String, ReentrantReadWriteLock>();
        
        private synchronized ReentrantReadWriteLock getLock(String contextMapName) {
            ReentrantReadWriteLock lock = locks.get(contextMapName);
            if(lock == null) {
                lock = new ReentrantReadWriteLock();
                locks.put(contextMapName, lock);
            }
            return lock;
        }
        
        public ReadLock getReadLock(String contextMapName) {
            return getLock(contextMapName).readLock();
        }

        public WriteLock getWriteLock(String contextMapName) {
            return getLock(contextMapName).writeLock();
        }
    }    
    
    private final static ContextMapLocks contextMapLocks = new ContextMapLocks();
    
    /**
     * Retrieves a previously stored Object from the Context of an XQuery.
     *
     * @param   context         The Context of the XQuery containing the Object
     * @param   contextMapName  DOCUMENT ME!
     * @param   objectUID       The UID of the Object to retrieve from the Context of the XQuery
     *
     * @return  DOCUMENT ME!
     */        
    public static <T> T retrieveObjectFromContextMap(XQueryContext context, String contextMapName, long objectUID) {
        
        contextMapLocks.getReadLock(contextMapName).lock();
        try{
            // get the existing object map from the context
            final Map<Long, T> map = (HashMap<Long, T>)context.getXQueryContextVar(contextMapName);

            if(map == null) {
                return null;
            }

            // get the connection
            return map.get(objectUID);
        } finally {
            contextMapLocks.getReadLock(contextMapName).unlock();
        }
    }
    
    public static <T> void modifyContextMap(XQueryContext context, String contextMapName, ContextMapModifier<T> modifier) {
        contextMapLocks.getWriteLock(contextMapName).lock();
        try {
            // get the existing map from the context
            Map<Long, T> map = (Map<Long, T>)context.getXQueryContextVar(contextMapName);
            if(map == null) {
                //create a new map if it doesnt exist
                map = new HashMap<Long, T>();
                context.setXQueryContextVar(contextMapName, map);
            }
            
            //modify the map
            modifier.modify(map);
            
        } finally {
            contextMapLocks.getWriteLock(contextMapName).unlock();
        }
    }
    
    public interface ContextMapModifier<T> {
        public void modify(Map<Long, T> map);
    }
    
    public static abstract class ContextMapEntryModifier<T> implements ContextMapModifier<T> {
        
        @Override
        public void modify(Map<Long, T> map) {
            for(final Entry<Long, T> entry : map.entrySet()) {
                modify(entry);
            }
        }
        
        public abstract void modify(Entry<Long, T> entry);
    }

    /**
     * Stores an Object in the Context of an XQuery.
     *
     * @param   context         The Context of the XQuery to store the Object in
     * @param   contextMapName  The name of the context map
     * @param   o               The Object to store
     *
     * @return  A unique ID representing the Object
     */
    public static <T> long storeObjectInContextMap(XQueryContext context, String contextMapName, T o) {
        
        contextMapLocks.getWriteLock(contextMapName).lock();
        try{

            // get the existing map from the context
            Map<Long, T> map = (Map<Long, T>)context.getXQueryContextVar(contextMapName);

            if(map == null) {
                // if there is no map, create a new one
                map = new HashMap<Long, T>();
            }

            // get an id for the map
            long uid = 0;
            while(uid == 0 || map.keySet().contains(uid)) {
                uid = getUID();
            }

            // place the object in the map
            map.put(uid, o);

            // store the map back in the context
            context.setXQueryContextVar(contextMapName, map);

            return (uid);
        } finally {
            contextMapLocks.getWriteLock(contextMapName).unlock();
        }
    }
    
    private final static Random random = new Random();
    
    private static long getUID() {
        final BigInteger bi = new BigInteger(64, random);
        return bi.longValue();
    }
}
