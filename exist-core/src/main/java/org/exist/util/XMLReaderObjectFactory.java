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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.Namespaces;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
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
public class XMLReaderObjectFactory extends BasePoolableObjectFactory implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(XMLReaderObjectFactory.class);

    public enum VALIDATION_SETTING {
        UNKNOWN, ENABLED, AUTO, DISABLED
    }

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

    private Configuration configuration;
    private GrammarPool grammarPool;
    private eXistXMLCatalogResolver resolver;

    @Override
    public void configure(final Configuration configuration) {
        this.configuration = configuration;
        this.grammarPool = (GrammarPool) configuration.getProperty(XMLReaderObjectFactory.GRAMMER_POOL);
        this.resolver = (eXistXMLCatalogResolver) configuration.getProperty(CATALOG_RESOLVER);
    }

    /**
     * @see org.apache.commons.pool.BasePoolableObjectFactory#makeObject()
     */
    public Object makeObject() throws Exception {
        final String option = (String) configuration.getProperty(PROPERTY_VALIDATION_MODE);
        final VALIDATION_SETTING validation = convertValidationMode(option);
        final XMLReader xmlReader = createXmlReader(validation, grammarPool, resolver);
        setReaderValidationMode(validation, xmlReader);
        return xmlReader;
    }

    /**
     * Create Xmlreader and setup validation.
     *
     * @param validation the validation setting
     * @param grammarPool the grammar pool
     * @param resolver the catalog resolver
     *
     * @return the configured reader
     *
     * @throws ParserConfigurationException if the parser cannot be configured
     * @throws SAXException if an exception occurs with the parser
     */
    public static XMLReader createXmlReader(VALIDATION_SETTING validation, GrammarPool grammarPool,
            eXistXMLCatalogResolver resolver) throws ParserConfigurationException, SAXException {

        // Create a xmlreader
        final SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
        
        if (validation == VALIDATION_SETTING.AUTO || validation == VALIDATION_SETTING.ENABLED){
            saxFactory.setValidating(true);
        } else {
            saxFactory.setValidating(false);
        }
        saxFactory.setNamespaceAware(true);

        final SAXParser saxParser = saxFactory.newSAXParser();
        final XMLReader xmlReader = saxParser.getXMLReader();

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
     *
     * @param option the configuration option
     *
     * @return the validation setting
     */
    public static VALIDATION_SETTING convertValidationMode(String option) {
        VALIDATION_SETTING mode = VALIDATION_SETTING.AUTO;
        if (option != null) {
            if ("true".equals(option) || "yes".equals(option)) {
                mode = VALIDATION_SETTING.ENABLED;

            } else if ("auto".equals(option)) {
                mode = VALIDATION_SETTING.AUTO;

            } else {
                mode = VALIDATION_SETTING.DISABLED;
            }
        }
        return mode;
    }

    /**
     * Setup validation mode of xml reader.
     *
     * @param validation the validation setting
     * @param xmlReader the reader
     */
    public static void setReaderValidationMode(VALIDATION_SETTING validation, XMLReader xmlReader) {

        if (validation == VALIDATION_SETTING.UNKNOWN) {
            return;
        }

        // Configure xmlreader see http://xerces.apache.org/xerces2-j/features.html
        setReaderFeature(xmlReader, Namespaces.SAX_NAMESPACES_PREFIXES, true);

        setReaderFeature(xmlReader, Namespaces.SAX_VALIDATION,
                validation == VALIDATION_SETTING.AUTO || validation == VALIDATION_SETTING.ENABLED);

        setReaderFeature(xmlReader, Namespaces.SAX_VALIDATION_DYNAMIC,
                validation == VALIDATION_SETTING.AUTO);

        setReaderFeature(xmlReader, APACHE_FEATURES_VALIDATION_SCHEMA,
                (validation == VALIDATION_SETTING.AUTO || validation == VALIDATION_SETTING.ENABLED) );

        setReaderFeature(xmlReader, APACHE_PROPERTIES_LOAD_EXT_DTD,
                (validation == VALIDATION_SETTING.AUTO || validation == VALIDATION_SETTING.ENABLED) );

        // Attempt to make validation function equal to insert mode
        //saxFactory.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);
    }

    private static void setReaderFeature(XMLReader xmlReader, String featureName, boolean value){
        try {
            xmlReader.setFeature(featureName, value);

        } catch (final SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: " + ex.getMessage());

        } catch (final SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException:" + ex.getMessage());
        }
    }

    private static void setReaderProperty(XMLReader xmlReader, String propertyName, Object object){
        try {
            xmlReader.setProperty(propertyName, object);

        } catch (final SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: " + ex.getMessage());

        } catch (final SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException:" + ex.getMessage());
        }
    }


}
