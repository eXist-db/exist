/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
import javax.annotation.Nullable;
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
                    LOG.debug("Converting HTML to XML using: {}", reader.getClass().getName());
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
     * Stores an Object into the Context of an XQuery.
     *
     * @param context The Context of the XQuery to store the Object in
     * @param contextMapName The name of the context map
     * @param o The Object to store
     * @param <T> the type of the object being stored
     *
     * @return A unique ID representing the Object
     */
    public static <T> long storeObjectInContextMap(final XQueryContext context, final String contextMapName, final T o) {
        return modifyContextMap(context, contextMapName, contextMap -> {
            // get an id for the map
            long uid = 0;
            while (uid == 0 || contextMap.keySet().contains(uid)) {
                uid = getUID();
            }

            // place the object in the map
            contextMap.put(uid, o);

            return uid;
        });
    }

    /**
     * Retrieves a previously stored Object from the Context of an XQuery.
     *
     * @param context The Context of the XQuery containing the Object
     * @param contextMapName The name of the context map
     * @param objectUID The UID of the Object to retrieve from the Context of the XQuery
     *
     * @param <T> the type of the object being retrieved
     *
     * @return the object stored in the context or null
     */
    public static @Nullable <T> T retrieveObjectFromContextMap(final XQueryContext context, final String contextMapName, final long objectUID) {
        return readContextMap(context, contextMapName, contextMap -> {
            // get the object
            return (T) contextMap.get(objectUID);
        });
    }

    /**
     * Removes a previously stored Object from the Context of an XQuery.
     *
     * @param context The Context of the XQuery containing the Object
     * @param contextMapName The name of the context map
     * @param objectUID The UID of the Object to remove from the Context of the XQuery
     *
     * @param <T> the type of the object being removed
     *
     * @return the object that was removed from the context or null if there was no object for the UID
     */
    public static @Nullable <T> T removeObjectFromContextMap(final XQueryContext context, final String contextMapName, final long objectUID) {
        return modifyContextMap(context, contextMapName, contextMap -> {
            // get the object
            return (T) contextMap.remove(objectUID);
        });
    }

    /**
     * Modify a context map.
     *
     * @param context the XQuery context
     * @param contextMapName The name of the context map
     * @param modifier the modification function
     *
     * @param <T> the type of the value in the map
     * @param <U> the type of the return value of the modifier function
     *
     * @return the result of the modification function
     */
    public static @Nullable <T, U> U modifyContextMap(final XQueryContext context, final String contextMapName, final ContextMapModifier<T, U> modifier) {
        try (final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(contextMapLocks.getLock(contextMapName), LockMode.WRITE_LOCK)) {
            // get the existing map from the context
            Map<Long, T> map = (Map<Long, T>)context.getAttribute(contextMapName);
            if(map == null) {
                //create a new map if it doesnt exist
                map = new HashMap<>();
                context.setAttribute(contextMapName, map);
            }
            
            //modify the map
            return modifier.modify(map);
        }
    }

    /**
     * Read a context map.
     *
     * @param context the XQuery context
     * @param contextMapName The name of the context map
     * @param reader the reader function
     *
     * @param <T> the type of the value in the map
     * @param <U> the type of the return value of the reader function
     *
     * @return the result of the reader function
     */
    public static <T, U> U readContextMap(final XQueryContext context, final String contextMapName, final ContextMapReader<T, U> reader) {
        try (final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(contextMapLocks.getLock(contextMapName), LockMode.READ_LOCK)) {
            // get the existing map from the context
            Map<Long, T> map = (Map<Long, T>)context.getAttribute(contextMapName);
            if (map == null) {
                map = Collections.emptyMap();
            }

            //modify the map
            return reader.read(map);
        }
    }

    @FunctionalInterface
    public interface ContextMapModifier<T, U> {
        U modify(final Map<Long, T> map);
    }

    @FunctionalInterface
    public interface ContextMapModifierWithoutResult<T> extends ContextMapModifier<T, Void> {
        @Override
        default Void modify(final Map<Long, T> map) {
            modifyWithoutResult(map);
            return null;
        }

        void modifyWithoutResult(final Map<Long, T> map);
    }

    @FunctionalInterface
    public interface ContextMapReader<T, U> {
        U read(final Map<Long, T> map);
    }

    public static abstract class ContextMapEntryModifier<T> implements ContextMapModifierWithoutResult<T> {
        @Override
        public void modifyWithoutResult(final Map<Long, T> map) {
            for(final Entry<Long, T> entry : map.entrySet()) {
                modifyEntry(entry);
            }
        }

        public abstract void modifyEntry(final Entry<Long, T> entry);
    }

    private static long getUID() {
        final BigInteger bi = new BigInteger(64, random);
        return bi.longValue();
    }
}
