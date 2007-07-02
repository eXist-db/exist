/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  $Id$
 */
package org.exist.util;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.pool.BasePoolableObjectFactory;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.validation.GrammarPool;
import org.exist.validation.resolver.eXistXMLCatalogResolver;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Factory to create new XMLReader objects on demand. The factory is used
 * by {@link org.exist.util.XMLReaderPool}.
 *
 * @author wolf
 */
public class XMLReaderObjectFactory extends BasePoolableObjectFactory {
    
    public final static int VALIDATION_ENABLED = 0;
    public final static int VALIDATION_AUTO = 1;
    public final static int VALIDATION_DISABLED = 2;
    
    public final static String CONFIGURATION_ENTITY_RESOLVER_ELEMENT_NAME = "entity-resolver";
    public final static String CONFIGURATION_CATALOG_ELEMENT_NAME = "catalog";
    public final static String CONFIGURATION_ELEMENT_NAME = "validation";
    
    public final static String PROPERTY_VALIDATION = "validation.mode";
    public final static String CATALOG_RESOLVER = "validation.resolver";
    public final static String CATALOG_URIS = "validation.catalog_uris";
    public final static String GRAMMER_POOL = "validation.grammar_pool";
    
    // Xerces feature and property names
    public final static String FEATURES_VALIDATION_SCHEMA
            ="http://apache.org/xml/features/validation/schema";
    public final static String PROPERTIES_INTERNAL_GRAMMARPOOL
            ="http://apache.org/xml/properties/internal/grammar-pool";
    public final static String PROPERTIES_LOAD_EXT_DTD
            ="http://apache.org/xml/features/nonvalidating/load-external-dtd";
    public final static String PROPERTIES_ENTITYRESOLVER
            ="http://apache.org/xml/properties/internal/entity-resolver";
    
    private BrokerPool pool;

   
    
    /**
     *
     */
    public XMLReaderObjectFactory(BrokerPool pool) {
        super();
        this.pool = pool;
    }
    
    /** (non-Javadoc)
     * @see org.apache.commons.pool.BasePoolableObjectFactory#makeObject()
     */
    public Object makeObject() throws Exception {
        Configuration config = pool.getConfiguration();
        // get validation settings
        int validation = VALIDATION_AUTO;
        String option = (String) config.getProperty(PROPERTY_VALIDATION);
        if (option != null) {
            if (option.equals("true") || option.equals("yes"))
                validation = VALIDATION_ENABLED;
            else if (option.equals("auto"))
                validation = VALIDATION_AUTO;
            else
                validation = VALIDATION_DISABLED;
        }
        // create a SAX parser
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        if (validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED){
            saxFactory.setValidating(true);
        } else {
            saxFactory.setValidating(false);
        }
        saxFactory.setNamespaceAware(true);
        try {
            saxFactory.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);
            try {
                // TODO check does this work?
                // http://xerces.apache.org/xerces2-j/features.html
                saxFactory.setFeature(Namespaces.SAX_VALIDATION,
                    validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED);
                
                saxFactory.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC,
                    validation == VALIDATION_AUTO);
                
                saxFactory.setFeature(FEATURES_VALIDATION_SCHEMA,
                    validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED);            
                
                saxFactory.setFeature(PROPERTIES_LOAD_EXT_DTD,
                    validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED);
                
                // Attempt to make validation function equal to inser mode
                //saxFactory.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);
                
            } catch (SAXNotRecognizedException e1) {
                // ignore: feature only recognized by xerces
            } catch (SAXNotSupportedException e1) {
                // ignore: feature only recognized by xerces
            }
            
            SAXParser saxParser = saxFactory.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            
            // Setup grammar cache
            GrammarPool grammarPool = 
               (GrammarPool) config.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);
            if(grammarPool!=null){
                saxParser.setProperty(PROPERTIES_INTERNAL_GRAMMARPOOL, grammarPool);
            }
            
            eXistXMLCatalogResolver resolver = (eXistXMLCatalogResolver) config.getProperty(CATALOG_RESOLVER);
            if(resolver!=null){
                xmlReader.setProperty(PROPERTIES_ENTITYRESOLVER, resolver);
            }
            return xmlReader;
            
        } catch (ParserConfigurationException e) {
            throw new EXistException(e);
        }
    }
    
}
