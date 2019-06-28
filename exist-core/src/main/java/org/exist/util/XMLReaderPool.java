/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.util;

import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.StackObjectPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.validation.GrammarPool;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.parsers.ParserConfigurationException;
import java.util.Map;

/**
 * Maintains a pool of XMLReader objects. The pool is available through
 * {@link BrokerPool#getParserPool()}.
 * 
 * @author wolf
 */
public class XMLReaderPool extends StackObjectPool<XMLReader> implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(XMLReaderPool.class);

    private final static DefaultHandler2 DUMMY_HANDLER = new DefaultHandler2();

    private Configuration configuration = null;

    /**
     * Constructs an XML Reader Pool.
     *
     * @param factory the object factory
     * @param maxIdle the max idle time for a reader
     * @param initIdleCapacity the initial capacity
     */
    public XMLReaderPool(final PoolableObjectFactory<XMLReader> factory, final int maxIdle, final int initIdleCapacity) {
        super(factory, maxIdle, initIdleCapacity);
    }

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        this.configuration = configuration;
    }

    public synchronized XMLReader borrowXMLReader() {
        try {
            final XMLReader reader = super.borrowObject();
            setParserConfigFeatures(reader);
            return reader;
        } catch (final Exception e) {
            throw new IllegalStateException("error while returning XMLReader: " + e.getMessage(), e );
        }
    }

    /**
     * Sets any features for the parser which were defined in conf.xml
     */
    private void setParserConfigFeatures(final XMLReader xmlReader) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        final Map<String, Boolean> parserFeatures = (Map<String, Boolean>)configuration.getProperty(XmlParser.XML_PARSER_FEATURES_PROPERTY);
        if(parserFeatures != null) {
            for(final Map.Entry<String, Boolean> feature : parserFeatures.entrySet()) {
                xmlReader.setFeature(feature.getKey(), feature.getValue());
            }
        }
    }

    @Override
    public synchronized XMLReader borrowObject() throws Exception {
        return borrowXMLReader();
    }
    
   
    public synchronized void returnXMLReader(XMLReader reader) {
        if (reader == null) {
            return;
        }
        
        try {            
            reader.setContentHandler(DUMMY_HANDLER);
            reader.setErrorHandler(DUMMY_HANDLER);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, DUMMY_HANDLER);
            
            // DIZZZ; workaround Xerces bug. Cached DTDs cause for problems during validation parsing.
            final GrammarPool grammarPool =
               (GrammarPool) getReaderProperty(reader,
                                    XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL);
            if(grammarPool!=null){
                grammarPool.clearDTDs();
            }
            
            super.returnObject(reader);
            
        } catch (final Exception e) {
            throw new IllegalStateException("error while returning XMLReader: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void returnObject(XMLReader obj) throws Exception {
        returnXMLReader(obj);
    }   

    private Object getReaderProperty(XMLReader xmlReader, String propertyName){

        Object object = null;
        try {
            object=xmlReader.getProperty(propertyName);

        } catch (final SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: " + ex.getMessage());

        } catch (final SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException:" + ex.getMessage());
        }
        return object;
    }


    // just used for config properties
    public interface XmlParser {
        String XML_PARSER_ELEMENT = "xml";
        String XML_PARSER_FEATURES_ELEMENT = "features";
        String XML_PARSER_FEATURES_PROPERTY = "parser.xml-parser.features";
    }
    
}
