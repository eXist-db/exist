package org.exist.util;

import org.exist.Namespaces;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import java.util.Collections;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

public class XMLReaderPoolTest {

    @ParameterizedTest
    @ValueSource(strings = {"yes", "YES", "true", "TRUE", "auto", "AUTO"})
    public void xmlReaderWithEnabledValidation(final String validationMode) throws SAXNotSupportedException, SAXNotRecognizedException {
        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMER_POOL)).andReturn(null);
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
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMER_POOL)).andReturn(null);
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
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMER_POOL)).andReturn(null);
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
    public void exceedMaxIdle() {
        final int maxIdle = 3;
        final int initialCapacity = 0;

        final Configuration mockConfiguration = createMock(Configuration.class);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.GRAMMER_POOL)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER)).andReturn(null);
        expect(mockConfiguration.getProperty(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE)).andReturn("auto");

        expect(mockConfiguration.getProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY)).andReturn(Collections.emptyMap());

        replay(mockConfiguration);

        final XMLReaderObjectFactory xmlReaderObjectFactory = new XMLReaderObjectFactory();
        xmlReaderObjectFactory.configure(mockConfiguration);
        final XMLReaderPool xmlReaderPool = new XMLReaderPool(xmlReaderObjectFactory, maxIdle, initialCapacity);
        xmlReaderPool.configure(mockConfiguration);

        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(0, xmlReaderPool.getNumActive());

        // borrow 1
        final XMLReader xmlReader1 = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlReader1);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(1, xmlReaderPool.getNumActive());
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader1));

        // borrow 2
        final XMLReader xmlReader2 = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlReader2);
        assertTrue(xmlReader2 != xmlReader1);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(2, xmlReaderPool.getNumActive());
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader1));
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader2));

        // borrow 3
        final XMLReader xmlReader3 = xmlReaderPool.borrowXMLReader();
        assertNotNull(xmlReader3);
        assertTrue(xmlReader3 != xmlReader2);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(3, xmlReaderPool.getNumActive());
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader1));
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader2));
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader3));

        // borrow 4 -- will exceed `maxIdle`
        final XMLReader xmlReader4 = xmlReaderPool.borrowXMLReader();
        assertTrue(xmlReader4 != xmlReader3);
        assertNotNull(xmlReader4);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(0, xmlReaderPool.getNumIdle());
        assertEquals(4, xmlReaderPool.getNumActive());
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader1));
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader2));
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader3));
        assertTrue(xmlReaderObjectFactory.validateObject(xmlReader4));

        // now try and return the readers...

        // return 4
        xmlReaderPool.returnXMLReader(xmlReader4);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(1, xmlReaderPool.getNumIdle());
        assertEquals(3, xmlReaderPool.getNumActive());

        // return 3
        xmlReaderPool.returnXMLReader(xmlReader3);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(2, xmlReaderPool.getNumIdle());
        assertEquals(2, xmlReaderPool.getNumActive());

        // return 2
        xmlReaderPool.returnXMLReader(xmlReader3);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(3, xmlReaderPool.getNumIdle());
        assertEquals(1, xmlReaderPool.getNumActive());

        // return 1 --  will exceed `maxIdle`
        xmlReaderPool.returnXMLReader(xmlReader3);
        assertEquals(maxIdle, xmlReaderPool.getMaxSleeping());
        assertEquals(maxIdle, xmlReaderPool.getNumIdle());  // NOTE: that getNumIdle() can never exceed maxIdle
        assertEquals(0, xmlReaderPool.getNumActive());

        verify(mockConfiguration);
    }
}
