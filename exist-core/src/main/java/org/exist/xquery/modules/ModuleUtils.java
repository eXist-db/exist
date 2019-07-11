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
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.HtmlToXmlParser;
import com.evolvedbinary.j8fu.Either;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Document;
import org.xml.sax.*;

/**
 * Utility Functions for XQuery Extension Modules
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @serial 200805202059
 * @version 1.1
 */
public class ModuleUtils {
    private static final Logger LOG = LogManager.getLogger(ModuleUtils.class);
    private static final ContextMapLocks contextMapLocks = new ContextMapLocks();
    private static final Random random = new Random();

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
     * @throws SAXException in case of a SAX error
     * @throws IOException in case of error reading input source
	 */
	public static NodeValue stringToXML(XQueryContext context, String str) throws SAXException, IOException {
        try (final Reader reader = new StringReader(str)) {
            return inputSourceToXML(context, new InputSource(reader));
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
     * @throws SAXException in case of a SAX error
     * @throws IOException in case of error reading input source
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
     * @throws SAXException in case of a SAX error
     * @throws IOException in case of error reading input source
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
     * @throws SAXException in case of a SAX error
     * @throws IOException in case of error reading input source
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
	 * @param src
	 *            The InputSource of XML
	 *
     * @throws SAXException in case of a SAX error
     * @throws IOException in case of error reading input source
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
     * @throws SAXException in case of a SAX error
     * @throws IOException in case of error reading input source
     * @return An in-memory Document representing the XML'ised HTML
	 */
	public static DocumentImpl htmlToXHtml(final XQueryContext context, final Source srcHtml, final Map<String, Boolean> parserFeatures, final Map<String, String>parserProperties) throws IOException, SAXException {
        final InputSource inputSource = SAXSource.sourceToInputSource(srcHtml);

        if(inputSource == null){
            throw new IOException(srcHtml.getClass().getName() + " is unsupported.");
        }

        return htmlToXHtml(context, inputSource, parserFeatures, parserProperties);
	}

    /**
     * Takes a HTML InputSource and creates an XML representation of the HTML by
     * tidying it
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
     * @throws SAXException in case of a SAX error
     * @throws IOException in case of error reading input source
     * @return An in-memory Document representing the XML'ised HTML
     */
    public static DocumentImpl htmlToXHtml(final XQueryContext context, final InputSource srcHtml, final Map<String, Boolean> parserFeatures, final Map<String, String>parserProperties) throws IOException, SAXException {
        // use the configures HTML parser to parse the HTML content to XML
        final Optional<Either<Throwable, XMLReader>> maybeReaderInst = HtmlToXmlParser.getHtmlToXmlParser(context.getBroker().getConfiguration());

        if(maybeReaderInst.isPresent()) {
            final Either<Throwable, XMLReader> readerInst = maybeReaderInst.get();
            if(readerInst.isLeft()) {
                final String msg = "Unable to parse HTML to XML please ensure the parser is configured in conf.xml and is present on the classpath";
                final Throwable t = readerInst.left().get();
                LOG.error(msg, t);
                throw new IOException(msg, t);
            } else {
                final XMLReader reader = readerInst.right().get();

                if(parserFeatures != null) {
                    for(final Map.Entry<String, Boolean> parserFeature : parserFeatures.entrySet()) {
                        reader.setFeature(parserFeature.getKey(), parserFeature.getValue());
                    }
                }

                if(parserProperties != null) {
                    for(final Map.Entry<String, String> parserProperty : parserProperties.entrySet()) {
                        reader.setProperty(parserProperty.getKey(), parserProperty.getValue());
                    }
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("Converting HTML to XML using: " + reader.getClass().getName());
                }

                final SAXAdapter adapter = new SAXAdapter();

                // allow multiple attributes of the same name attached to the same element
                // to enhance resilience against bad HTML. The last attribute value wins.
                adapter.setReplaceAttributeFlag(true);

                reader.setContentHandler(adapter);
                reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                reader.parse(srcHtml);
                final Document doc = adapter.getDocument();
                // we use eXist's in-memory DOM implementation
                final DocumentImpl memtreeDoc = (DocumentImpl) doc;
                memtreeDoc.setContext(context);

                return memtreeDoc;

            }
        } else {
            throw new SAXException("There is no HTML to XML parser configured in conf.xml");
        }
    }

    private static class ContextMapLocks {
        private final Map<String, ReentrantReadWriteLock> locks = new HashMap<>();
        
        public synchronized ReentrantReadWriteLock getLock(final String contextMapName) {
            ReentrantReadWriteLock lock = locks.get(contextMapName);
            if(lock == null) {
                lock = new ReentrantReadWriteLock();
                locks.put(contextMapName, lock);
            }
            return lock;
        }
    }
    
    /**
     * Retrieves a previously stored Object from the Context of an XQuery.
     *
     * @param   context         The Context of the XQuery containing the Object
     * @param   contextMapName  DOCUMENT ME!
     * @param   objectUID       The UID of the Object to retrieve from the Context of the XQuery
     * @param <T> class of the object stored in the context
     * @return  the object stored in the context or null
     */        
    public static <T> T retrieveObjectFromContextMap(XQueryContext context, String contextMapName, long objectUID) {
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(contextMapLocks.getLock(contextMapName), LockMode.READ_LOCK)){
            // get the existing object map from the context
            final Map<Long, T> map = (HashMap<Long, T>)context.getAttribute(contextMapName);

            if(map == null) {
                return null;
            }

            // get the connection
            return map.get(objectUID);
        }
    }
    
    public static <T> void modifyContextMap(XQueryContext context, String contextMapName, ContextMapModifier<T> modifier) {
        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(contextMapLocks.getLock(contextMapName), LockMode.WRITE_LOCK)) {
            // get the existing map from the context
            Map<Long, T> map = (Map<Long, T>)context.getAttribute(contextMapName);
            if(map == null) {
                //create a new map if it doesnt exist
                map = new HashMap<Long, T>();
                context.setAttribute(contextMapName, map);
            }
            
            //modify the map
            modifier.modify(map);
            
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
     * @param <T> the class of the object being stored
     * @return  A unique ID representing the Object
     */
    public static <T> long storeObjectInContextMap(XQueryContext context, String contextMapName, T o) {

        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(contextMapLocks.getLock(contextMapName), LockMode.WRITE_LOCK)) {

            // get the existing map from the context
            Map<Long, T> map = (Map<Long, T>)context.getAttribute(contextMapName);

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
            context.setAttribute(contextMapName, map);

            return (uid);
        }
    }
    
    private static long getUID() {
        final BigInteger bi = new BigInteger(64, random);
        return bi.longValue();
    }
}
