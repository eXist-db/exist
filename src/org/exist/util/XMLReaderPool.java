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
import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.validation.GrammarPool;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

/**
 * Maintains a pool of XMLReader objects. The pool is available through
 * {@link BrokerPool#getParserPool()}.
 * 
 * @author wolf
 */
public class XMLReaderPool extends StackObjectPool {

    private final static Logger LOG = Logger.getLogger(XMLReaderPool.class);

    private final static DefaultHandler2 DUMMY_HANDLER = new DefaultHandler2();

    private Configuration config;

    /**
     * 
     * 
     * @param factory 
     * @param maxIdle 
     * @param initIdleCapacity 
     */
    public XMLReaderPool(Configuration config, PoolableObjectFactory factory, int maxIdle, int initIdleCapacity) {
        super(factory, maxIdle, initIdleCapacity);
        this.config = config;
    }

    public synchronized XMLReader borrowXMLReader() {
        try {
            return (XMLReader) borrowObject();
        } catch (Exception e) {
            throw new IllegalStateException("error while returning XMLReader: " + e.getMessage(), e );
        }
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
            GrammarPool grammarPool =
               (GrammarPool) getReaderProperty(reader,
                                    XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL);
            if(grammarPool!=null){
                grammarPool.clearDTDs();
            }
            
            returnObject(reader);
        } catch (Exception e) {
            throw new IllegalStateException("error while returning XMLReader: " + e.getMessage(), e);
        }
    }

    private Object getReaderProperty(XMLReader xmlReader, String propertyName){

        Object object = null;
        try {
            object=xmlReader.getProperty(propertyName);

        } catch (SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: " + ex.getMessage());

        } catch (SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException:" + ex.getMessage());
        }
        return object;
    }
}
