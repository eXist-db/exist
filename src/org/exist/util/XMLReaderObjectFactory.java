/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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

import org.apache.log4j.Logger;

import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.validation.GrammarPool;
import org.exist.validation.resolver.eXistXMLCatalogResolver;

import org.xml.sax.SAXException;
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

    private final static Logger LOG = Logger.getLogger(XMLReaderObjectFactory.class);

    public final static int VALIDATION_UNKNOWN = -1;
    public final static int VALIDATION_ENABLED = 0;
    public final static int VALIDATION_AUTO = 1;
    public final static int VALIDATION_DISABLED = 2;

    public final static String CONFIGURATION_ENTITY_RESOLVER_ELEMENT_NAME = "entity-resolver";
    public final static String CONFIGURATION_CATALOG_ELEMENT_NAME = "catalog";
    public final static String CONFIGURATION_ELEMENT_NAME = "validation";

    //TOO : move elsewhere ?
    public final static String VALIDATION_MODE_ATTRIBUTE = "mode";
    public final static String PROPERTY_VALIDATION_MODE = "validation.mode";
    public final static String CATALOG_RESOLVER = "validation.resolver";
    public final static String CATALOG_URIS = "validation.catalog_uris";
    public final static String GRAMMER_POOL = "validation.grammar_pool";

    // Xerces feature and property names
    public final static String APACHE_FEATURES_VALIDATION_SCHEMA
            ="http://apache.org/xml/features/validation/schema";
    public final static String APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL
            ="http://apache.org/xml/properties/internal/grammar-pool";
    public final static String APACHE_PROPERTIES_LOAD_EXT_DTD
            ="http://apache.org/xml/features/nonvalidating/load-external-dtd";
    public final static String APACHE_PROPERTIES_ENTITYRESOLVER
            ="http://apache.org/xml/properties/internal/entity-resolver";

    public final static String APACHE_PROPERTIES_NONAMESPACESCHEMALOCATION
            ="http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation";

    private BrokerPool pool;



    /**
     *
     */
    public XMLReaderObjectFactory(BrokerPool pool) {
        super();
        this.pool = pool;
    }

    /**
     * @see org.apache.commons.pool.BasePoolableObjectFactory#makeObject()
     */
    public Object makeObject() throws Exception {
        Configuration config = pool.getConfiguration();

        // Get validation settings
        String option = (String) config.getProperty(PROPERTY_VALIDATION_MODE);
        int validation = convertValidationMode(option);

        GrammarPool grammarPool =
                (GrammarPool) config.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);
        eXistXMLCatalogResolver resolver =
                (eXistXMLCatalogResolver) config.getProperty(CATALOG_RESOLVER);

        XMLReader xmlReader = createXmlReader(validation, grammarPool, resolver);

        setReaderValidationMode(validation, xmlReader);

        return xmlReader;
    }

    /**
     * Create Xmlreader and setup validation
     */
    public static XMLReader createXmlReader(int validation, GrammarPool grammarPool,
            eXistXMLCatalogResolver resolver) throws ParserConfigurationException, SAXException{

        // Create a xmlreader
        SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
        
        if (validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED){
            saxFactory.setValidating(true);
        } else {
            saxFactory.setValidating(false);
        }
        saxFactory.setNamespaceAware(true);

        SAXParser saxParser = saxFactory.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();

        // Setup grammar cache
        if(grammarPool!=null){
            setReaderProperty(xmlReader,APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, grammarPool);
        }

        // Setup xml catalog resolver
        if(resolver!=null){
           setReaderProperty(xmlReader,APACHE_PROPERTIES_ENTITYRESOLVER, resolver);
        }

        return xmlReader;
    }

    /**
     * Convert configuration text (yes,no,true,false,auto) into a magic number.
     */
    public static int convertValidationMode(String option) {
        int validation = VALIDATION_AUTO;
        if (option != null) {
            if (option.equals("true") || option.equals("yes")) {
                validation = VALIDATION_ENABLED;

            } else if (option.equals("auto")) {
                validation = VALIDATION_AUTO;

            } else {
                validation = VALIDATION_DISABLED;
            }
        }
        return validation;
    }

    /**
     * Setup validation mode of xml reader.
     */
    public static void setReaderValidationMode(int validation, XMLReader xmlReader) {

        if (validation == VALIDATION_UNKNOWN) {
            return;
        }

        // Configure xmlreader see http://xerces.apache.org/xerces2-j/features.html
        setReaderFeature(xmlReader, Namespaces.SAX_NAMESPACES_PREFIXES, true);

        setReaderFeature(xmlReader, Namespaces.SAX_VALIDATION,
                validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED);

        setReaderFeature(xmlReader, Namespaces.SAX_VALIDATION_DYNAMIC,
                validation == VALIDATION_AUTO);

        setReaderFeature(xmlReader, APACHE_FEATURES_VALIDATION_SCHEMA,
                (validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED) );

        setReaderFeature(xmlReader, APACHE_PROPERTIES_LOAD_EXT_DTD,
                (validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED) );

        // Attempt to make validation function equal to insert mode
        //saxFactory.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);
    }

    private static void setReaderFeature(XMLReader xmlReader, String featureName, boolean value){
        try {
            xmlReader.setFeature(featureName, value);

        } catch (SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: " + ex.getMessage());

        } catch (SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException:" + ex.getMessage());
        }
    }

    private static void setReaderProperty(XMLReader xmlReader, String propertyName, Object object){
        try {
            xmlReader.setProperty(propertyName, object);

        } catch (SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: " + ex.getMessage());

        } catch (SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException:" + ex.getMessage());
        }
    }


}
