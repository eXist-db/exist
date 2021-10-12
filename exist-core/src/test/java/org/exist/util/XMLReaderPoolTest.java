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

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.exist.Namespaces;
import org.exist.validation.GrammarPool;
import org.exist.validation.resolver.eXistXMLCatalogResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class XMLReaderPoolTest {

    @ParameterizedTest
    @ValueSource(strings = {"yes", "YES", "true", "TRUE", "auto", "AUTO"})
    public void xmlReaderWithEnabledValidation(final String validationMode) throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn(validationMode);

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertTrue(xmlreader.getFeature(Namespaces.SAX_NAMESPACES_PREFIXES));
            assertTrue(xmlreader.getFeature(Namespaces.SAX_VALIDATION));
            if ("auto".equalsIgnoreCase(validationMode)) {
                assertTrue(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            } else {
                assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            }
            assertTrue(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA));
            assertTrue(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "NO", "false", "FALSE"})
    public void xmlReaderWithDisabledValidation(final String validationMode) throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn(validationMode);

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertTrue(xmlreader.getFeature(Namespaces.SAX_NAMESPACES_PREFIXES));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderWithUnknownValidation() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("unknown");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertTrue(xmlreader.getFeature(Namespaces.SAX_NAMESPACES_PREFIXES));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderWithGrammarPool() throws SAXNotSupportedException, SAXNotRecognizedException {
        final GrammarPool mockGrammarPool = createMock(GrammarPool.class);

        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(mockGrammarPool);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertSame(mockGrammarPool, xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderWithoutGrammarPool() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderWithResolver() throws SAXNotSupportedException, SAXNotRecognizedException {
        final eXistXMLCatalogResolver mockResolver = createMock(eXistXMLCatalogResolver.class);

        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(mockResolver);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertSame(mockResolver, xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderWithoutResolver() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderWithConfiguredFeatures() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        final Map<String, Boolean> features = new HashMap<>();
        features.put("http://xml.org/sax/features/external-general-entities", false);
        features.put("http://xml.org/sax/features/external-parameter-entities", false);
        features.put("http://javax.xml.XMLConstants/feature/secure-processing", true);

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(features);

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertFalse(xmlreader.getFeature("http://xml.org/sax/features/external-general-entities"));
            assertFalse(xmlreader.getFeature("http://xml.org/sax/features/external-parameter-entities"));
            assertTrue(xmlreader.getFeature("http://javax.xml.XMLConstants/feature/secure-processing"));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderWithoutConfiguredFeatures() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertTrue(xmlreader.getFeature("http://xml.org/sax/features/external-general-entities"));
            assertTrue(xmlreader.getFeature("http://xml.org/sax/features/external-parameter-entities"));
            assertFalse(xmlreader.getFeature("http://javax.xml.XMLConstants/feature/secure-processing"));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderHasNoContentHandler()  {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getContentHandler());
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderHasNoErrorHandler()  {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getErrorHandler());
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void xmlReaderHasNoLexicalHandler() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        final XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getProperty(Namespaces.SAX_LEXICAL_HANDLER));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "YES", "true", "TRUE", "auto", "AUTO" })
    public void reusedXmlReaderStillHasEnabledValidation(final String validationMode) throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn(validationMode);

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly disable validation properties, before returning to pool
            xmlreader.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, false);
            xmlreader.setFeature(Namespaces.SAX_VALIDATION, false);
            if ("auto".equalsIgnoreCase(validationMode)) {
                xmlreader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, false);
            } else {
                xmlreader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, true);
            }
            xmlreader.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, false);
            xmlreader.setFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD, false);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertTrue(xmlreader.getFeature(Namespaces.SAX_NAMESPACES_PREFIXES));
            assertTrue(xmlreader.getFeature(Namespaces.SAX_VALIDATION));
            if ("auto".equalsIgnoreCase(validationMode)) {
                assertTrue(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            } else {
                assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            }
            assertTrue(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA));
            assertTrue(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "NO", "false", "FALSE"})
    public void reusedXmlReaderStillHasDisabledValidation(final String validationMode) throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn(validationMode);

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly enable validation properties, before returning to pool
            xmlreader.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);
            xmlreader.setFeature(Namespaces.SAX_VALIDATION, true);
            if ("auto".equalsIgnoreCase(validationMode)) {
                xmlreader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, true);
            } else {
                xmlreader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, false);
            }
            xmlreader.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);
            xmlreader.setFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD, true);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertTrue(xmlreader.getFeature(Namespaces.SAX_NAMESPACES_PREFIXES));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasUnknownValidation() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("unknown");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly enable validation properties, before returning to pool
            xmlreader.setFeature(Namespaces.SAX_NAMESPACES_PREFIXES, true);
            xmlreader.setFeature(Namespaces.SAX_VALIDATION, true);
            xmlreader.setFeature(Namespaces.SAX_VALIDATION_DYNAMIC, true);
            xmlreader.setFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA, true);
            xmlreader.setFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD, true);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertTrue(xmlreader.getFeature(Namespaces.SAX_NAMESPACES_PREFIXES));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION));
            assertFalse(xmlreader.getFeature(Namespaces.SAX_VALIDATION_DYNAMIC));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_FEATURES_VALIDATION_SCHEMA));
            assertFalse(xmlreader.getFeature(XMLReaderObjectFactory.APACHE_PROPERTIES_LOAD_EXT_DTD));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasGrammarPool() throws SAXNotSupportedException, SAXNotRecognizedException {
        final GrammarPool mockGrammarPool = createMock(GrammarPool.class);

        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(mockGrammarPool);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly disable the grammar pool
            xmlreader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, null);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertSame(mockGrammarPool, xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasNoGrammarPool() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly enable the grammar pool
            final GrammarPool mockGrammarPool = createMock(GrammarPool.class);
            xmlreader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL, mockGrammarPool);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_GRAMMARPOOL));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasResolver() throws SAXNotSupportedException, SAXNotRecognizedException {
        final eXistXMLCatalogResolver mockResolver = createMock(eXistXMLCatalogResolver.class);

        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(mockResolver);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly disable the resolver
            xmlreader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER, null);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertSame(mockResolver, xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasNoResolver() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly enable the resolver
            final eXistXMLCatalogResolver mockResolver = createMock(eXistXMLCatalogResolver.class);
            xmlreader.setProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER, mockResolver);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getProperty(XMLReaderObjectFactory.APACHE_PROPERTIES_INTERNAL_ENTITYRESOLVER));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasConfiguredFeatures() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        final Map<String, Boolean> features = new HashMap<>();
        features.put("http://xml.org/sax/features/external-general-entities", false);
        features.put("http://xml.org/sax/features/external-parameter-entities", false);
        features.put("http://javax.xml.XMLConstants/feature/secure-processing", true);

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(features);

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly switch the features
            xmlreader.setFeature("http://xml.org/sax/features/external-general-entities", true);
            xmlreader.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
            xmlreader.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", false);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertFalse(xmlreader.getFeature("http://xml.org/sax/features/external-general-entities"));
            assertFalse(xmlreader.getFeature("http://xml.org/sax/features/external-parameter-entities"));
            assertTrue(xmlreader.getFeature("http://javax.xml.XMLConstants/feature/secure-processing"));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasNoContentHandler()  {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly set a content handler
            final ContentHandler mockContentHandler = createMock(ContentHandler.class);
            xmlreader.setContentHandler(mockContentHandler);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getContentHandler());
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasNoErrorHandler()  {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly set an error handler
            final ErrorHandler mockErrorHandler = createMock(ErrorHandler.class);
            xmlreader.setErrorHandler(mockErrorHandler);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getErrorHandler());
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void reusedXmlReaderStillHasNoLexicalHandler() throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, 1, 0);
        xmlReaderPool.configure(mockConfiguration);

        XMLReader xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            // explicitly set a lexical handler
            final LexicalHandler mockLexicalHandler = createMock(LexicalHandler.class);
            xmlreader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, mockLexicalHandler);
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        // borrow again after return...

        xmlreader = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlreader);
        try {
            assertNull(xmlreader.getProperty(Namespaces.SAX_LEXICAL_HANDLER));
        } finally {
            xmlReaderPool.returnXMLReader(xmlreader);
        }

        verify(mockConfiguration);
    }

    @Test
    public void exceedMaxIdle() {
        final int maxIdle = 3;
        final int initialCapacity = 0;

        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMAR_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, maxIdle, initialCapacity);
        xmlReaderPool.configure(mockConfiguration);

        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(0, xmlReaderPool.getNumActive());

        // borrow 1
        final XMLReader xmlReader1 = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlReader1);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(1, xmlReaderPool.getNumActive());
        final PooledObject<XMLReader> pooledXmlReader1 = getPooledObject(xmlReaderPool, xmlReader1);
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader1));

        // borrow 2
        final XMLReader xmlReader2 = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlReader2);
        assertNotSame(xmlReader2, xmlReader1);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(2, xmlReaderPool.getNumActive());
        final PooledObject<XMLReader> pooledXmlReader2 = getPooledObject(xmlReaderPool, xmlReader2);
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader1));
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader2));

        // borrow 3
        final XMLReader xmlReader3 = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlReader3);
        assertNotSame(xmlReader3, xmlReader2);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(3, xmlReaderPool.getNumActive());
        final PooledObject<XMLReader> pooledXmlReader3 = getPooledObject(xmlReaderPool, xmlReader3);
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader1));
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader2));
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader3));

        // borrow 4 -- will exceed `maxIdle`
        final XMLReader xmlReader4 = xmlReaderPool.borrowXMLReader();
        assertNotSame(xmlReader4, xmlReader3);
        assertNotNull(xmlReader4);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(4, xmlReaderPool.getNumActive());
        final PooledObject<XMLReader> pooledXmlReader4 = getPooledObject(xmlReaderPool, xmlReader4);
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader1));
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader2));
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader3));
        assertTrue(xmlReaderObjectFactory.validateObject(pooledXmlReader4));

        // now try and return the readers...

        // return 4
        xmlReaderPool.returnXMLReader(xmlReader4);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(1, xmlReaderPool.getNumIdle());
        assertEquals(3, xmlReaderPool.getNumActive());

        // return 3
        xmlReaderPool.returnXMLReader(xmlReader3);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(2, xmlReaderPool.getNumIdle());
        assertEquals(2, xmlReaderPool.getNumActive());

        // return 2
        xmlReaderPool.returnXMLReader(xmlReader2);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(3, xmlReaderPool.getNumIdle());
        assertEquals(1, xmlReaderPool.getNumActive());

        // return 1 --  will exceed `maxIdle`
        xmlReaderPool.returnXMLReader(xmlReader1);
        assertEquals(maxIdle, xmlReaderPool.getMaxIdle());
        assertEquals(maxIdle, xmlReaderPool.getNumIdle());  // NOTE: that getNumIdle() can never exceed maxIdle
        assertEquals(0, xmlReaderPool.getNumActive());

        verify(mockConfiguration);
    }

    private PooledObject<XMLReader> getPooledObject(final XMLReaderPool xmlReaderPool, final XMLReader xmlReader) {
        try {
            final Method mGetPooledObject = GenericObjectPool.class.getDeclaredMethod("getPooledObject", Object.class);
            mGetPooledObject.setAccessible(true);
            return (PooledObject<XMLReader>) mGetPooledObject.invoke(xmlReaderPool, xmlReader);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            fail(e.getMessage(), e);
            return null;
        }
    }
}
