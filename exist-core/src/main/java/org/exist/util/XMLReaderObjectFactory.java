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
package org.exist.util;

import javax.annotation.Nullable;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.Namespaces;
import org.exist.storage.BrokerPoolService;
import org.exist.validation.GrammarPool;
import org.exist.validation.resolver.eXistXMLCatalogResolver;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import java.util.Map;

/**
 * Factory to create new XMLReader objects on demand.
 * The factory is used by {@link org.exist.util.XMLReaderPool}.
 */
public class XMLReaderObjectFactory extends BasePooledObjectFactory<XMLReader> implements BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(XMLReaderObjectFactory.class);

    public static final String CONFIGURATION_ENTITY_RESOLVER_ELEMENT_NAME = "entity-resolver";
    public static final String CONFIGURATION_CATALOG_ELEMENT_NAME = "catalog";
    public static final String CONFIGURATION_ELEMENT_NAME = "validation";

    //TOO : move elsewhere ?
    public static final String VALIDATION_MODE_ATTRIBUTE = "mode";
    public static final String PROPERTY_VALIDATION_MODE = "validation.mode";
    public static final String CATALOG_RESOLVER = "validation.resolver";
    public static final String CATALOG_URIS = "validation.catalog_uris";
    public static final String GRAMMAR_POOL = "validation.grammar_pool";

    // Xerces feature and property names
    public static final String APACHE_FEATURES_VALIDATION_SCHEMA
            = "http://apache.org/xml/features/validation/schema";
    public static final String APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL
            = "http://apache.org/xml/properties/internal/grammar-pool";
    public static final String APACHE_PROPERTIES_LOAD_EXT_DTD
            = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    public static final String APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER
            = "http://apache.org/xml/properties/internal/entity-resolver";

    public static final String APACHE_PROPERTIES_NONAMESPACESCHEMALOCATION
            = "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation";

    @Nullable private GrammarPool grammarPool;
    @Nullable private eXistXMLCatalogResolver resolver;
    private VALIDATION_SETTING validation = VALIDATION_SETTING.UNKNOWN;
    @Nullable private Map<String, Boolean> parserFeatures;
    private SAXParserFactory saxParserFactory;

    @Override
    public void configure(final Configuration configuration) {
        this.grammarPool = (GrammarPool) configuration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL);
        this.resolver = (eXistXMLCatalogResolver) configuration.getProperty(CATALOG_RESOLVER);
        final String option = (String) configuration.getProperty(PROPERTY_VALIDATION_MODE);
        this.validation = VALIDATION_SETTING.fromOption(option);
        this.parserFeatures = (Map<String, Boolean>) configuration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY);

        this.saxParserFactory = ExistSAXParserFactory.getSAXParserFactory();
        saxParserFactory.setValidating(validation == VALIDATION_SETTING.AUTO || validation == VALIDATION_SETTING.ENABLED);
        saxParserFactory.setNamespaceAware(true);
    }

    @Override
    public XMLReader create() throws Exception {
        final SAXParser saxParser = saxParserFactory.newSAXParser();
        final XMLReader xmlReader = saxParser.getXMLReader();

        xmlReader.setErrorHandler(null);  // disable default Xerces Error Handler

        return xmlReader;
    }

    @Override
    public PooledObject<XMLReader> wrap(final XMLReader value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void activateObject(final PooledObject<XMLReader> pooledXmlReader) {
        final XMLReader xmlReader = pooledXmlReader.getObject();

        if (validation.maybe()) {
            // Only need to set Grammar Cache if we are validating
            setReaderProperty(xmlReader, APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, grammarPool);
        }

        // Set XML Catalog Resolver
        setReaderProperty(xmlReader, APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER, resolver);

        setReaderValidationMode(validation, xmlReader);

        // Sets any features for the parser which were defined in conf.xml
        if (parserFeatures != null) {
            for (final Map.Entry<String, Boolean> feature : parserFeatures.entrySet()) {
                setReaderFeature(xmlReader, feature.getKey(), feature.getValue());
            }
        }
    }

    @Override
    public void passivateObject(final PooledObject<XMLReader> pooledXmlReader) throws Exception {
        final XMLReader xmlReader = pooledXmlReader.getObject();

        xmlReader.setContentHandler(null);
        xmlReader.setErrorHandler(null);
        xmlReader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, null);
    }

    /**
     * Setup validation mode of xml reader.
     *
     * @param validation the validation setting
     * @param xmlReader  the reader
     */
    public static void setReaderValidationMode(final VALIDATION_SETTING validation, final XMLReader xmlReader) {
        // Configure XMLReader see http://xerces.apache.org/xerces2-j/features.html
        setReaderFeature(xmlReader, Namespaces.SAX_NAMESPACES_PREFIXES, true);
        setReaderFeature(xmlReader, APACHE_PROPERTIES_LOAD_EXT_DTD, validation.maybe());
        setReaderFeature(xmlReader, Namespaces.SAX_VALIDATION, validation.maybe());
        setReaderFeature(xmlReader, Namespaces.SAX_VALIDATION_DYNAMIC, validation == VALIDATION_SETTING.AUTO);
        setReaderFeature(xmlReader, APACHE_FEATURES_VALIDATION_SCHEMA, validation.maybe());
    }

    private static void setReaderFeature(final XMLReader xmlReader, final String featureName, final boolean value) {
        try {
            xmlReader.setFeature(featureName, value);

        } catch (final SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: {}", ex.getMessage());

        } catch (final SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException: {}", ex.getMessage());
        }
    }

    private static void setReaderProperty(final XMLReader xmlReader, final String propertyName, @Nullable final Object object) {
        try {
            xmlReader.setProperty(propertyName, object);

        } catch (final SAXNotRecognizedException ex) {
            LOG.error("SAXNotRecognizedException: {}", ex.getMessage());

        } catch (final SAXNotSupportedException ex) {
            LOG.error("SAXNotSupportedException:{}", ex.getMessage());
        }
    }

    public enum VALIDATION_SETTING {
        UNKNOWN, ENABLED, AUTO, DISABLED;

        /**
         * Return true if the VALIDATION_SETTING is {@link #AUTO} or {@link #ENABLED}.
         *
         * @return true if validation may happen, false otherwise
         */
        public boolean maybe() {
            return this == VALIDATION_SETTING.AUTO || this == VALIDATION_SETTING.ENABLED;
        }

        /**
         * Convert configuration text (yes,no,true,false,auto) into a magic number.
         *
         * @param option the configuration option
         * @return the validation setting
         */
        public static VALIDATION_SETTING fromOption(final @Nullable String option) {
            VALIDATION_SETTING mode = VALIDATION_SETTING.AUTO;
            if (option != null) {
                if ("auto".equalsIgnoreCase(option)) {
                    mode = VALIDATION_SETTING.AUTO;

                } else if ("true".equalsIgnoreCase(option) || "yes".equalsIgnoreCase(option)) {
                    mode = VALIDATION_SETTING.ENABLED;

                } else if ("false".equalsIgnoreCase(option) || "no".equalsIgnoreCase(option)) {
                    mode = VALIDATION_SETTING.DISABLED;

                } else {
                    mode = VALIDATION_SETTING.UNKNOWN;
                }
            }
            return mode;
        }
    }
}
