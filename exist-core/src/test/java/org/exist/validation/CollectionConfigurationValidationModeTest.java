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
 * $Id$
 */

package org.exist.validation;


import org.exist.test.ExistXmldbEmbeddedServer;

import org.junit.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.*;

/**
 *  Switch validation mode yes/no/auto per collection and validate.
 * @author wessels
 */
public class CollectionConfigurationValidationModeTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String valid = "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://jmvanel.free.fr/xsd/addressBook\" elementFormDefault=\"qualified\">" + "<xsd:attribute name=\"uselessAttribute\" type=\"xsd:string\"/>" + "<xsd:complexType name=\"record\">" + "<xsd:sequence>" + "<xsd:element name=\"cname\" type=\"xsd:string\"/>" + "<xsd:element name=\"email\" type=\"xsd:string\"/>" + "</xsd:sequence>" + "</xsd:complexType>" + "<xsd:element name=\"addressBook\">" + "<xsd:complexType>" + "<xsd:sequence>" + "<xsd:element name=\"owner\" type=\"record\"/>" + "<xsd:element name=\"person\" type=\"record\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" + "</xsd:sequence>" + "</xsd:complexType>" + "</xsd:element>" + "</xsd:schema>";
    private static final String invalid = "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://jmvanel.free.fr/xsd/addressBook\" elementFormDefault=\"qualified\">" + "<xsd:attribute name=\"uselessAttribute\" type=\"xsd:string\"/>" + "<xsd:complexType name=\"record\">" + "<xsd:sequence>" + "<xsd:elementa name=\"cname\" type=\"xsd:string\"/>" + "<xsd:elementb name=\"email\" type=\"xsd:string\"/>" + "</xsd:sequence>" + "</xsd:complexType>" + "<xsd:element name=\"addressBook\">" + "<xsd:complexType>" + "<xsd:sequence>" + "<xsd:element name=\"owner\" type=\"record\"/>" + "<xsd:element name=\"person\" type=\"record\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" + "</xsd:sequence>" + "</xsd:complexType>" + "</xsd:element>" + "</xsd:schema>";
    private static final String anonymous = "<schema elementFormDefault=\"qualified\">" + "<attribute name=\"uselessAttribute\" type=\"string\"/>" + "<complexType name=\"record\">" + "<sequence>" + "<elementa name=\"cname\" type=\"string\"/>" + "<elementb name=\"email\" type=\"string\"/>" + "</sequence>" + "</complexType>" + "<element name=\"addressBook\">" + "<complexType>" + "<sequence>" + "<element name=\"owner\" type=\"record\"/>" + "<element name=\"person\" type=\"record\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" + "</sequence>" + "</complexType>" + "</element>" + "</schema>";
    private static final String different = "<asd:schema xmlns:asd=\"http://www.w3.org/2001/XMLSchemaschema\" targetNamespace=\"http://jmvanel.free.fr/xsd/addressBookbook\" elementFormDefault=\"qualified\">" + "<asd:attribute name=\"uselessAttribute\" type=\"asd:string\"/>" + "<asd:complexType name=\"record\">" + "<asd:sequence>" + "<asd:element name=\"cname\" type=\"asd:string\"/>" + "<asd:element name=\"email\" type=\"asd:string\"/>" + "</asd:sequence>" + "</asd:complexType>" + "<asd:element name=\"addressBook\">" + "<asd:complexType>" + "<asd:sequence>" + "<asd:element name=\"owner\" type=\"record\"/>" + "<asd:element name=\"person\" type=\"record\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>" + "</asd:sequence>" + "</asd:complexType>" + "</asd:element>" + "</asd:schema>";

    private static final String xconf_yes = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\"><validation mode=\"yes\"/></collection>";
    private static final String xconf_no = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\"><validation mode=\"no\"/></collection>";
    private static final String xconf_auto = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\"><validation mode=\"auto\"/></collection>";

    @AfterClass
    public static void tearDownClass() throws Exception {
        existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
    }

    @Before
    public void setUp() throws Exception {
        existEmbeddedServer.executeQuery("validation:clear-grammar-cache()");
    }

    private void createCollection(final String collection) throws XMLDBException {
        final CollectionManagementService cmservice = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        Collection testCollection = cmservice.createCollection(collection);
        assertNotNull(testCollection);

        testCollection = cmservice.createCollection("/db/system/config" + collection);
        assertNotNull(testCollection);
    }

    private void storeCollectionXconf(final String collection, final String document) throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("xmldb:store(\"" + collection + "\", \"collection.xconf\", " + document + ")");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("Store xconf", collection + "/collection.xconf", r);
    }

    private void storeDocument(final String collection, final String name, final String document) throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("xmldb:store(\"" + collection + "\", \"" + name + "\", " + document + ")");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("Store doc", collection + "/" + name, r);
    }

    @Test
    public void insertModeFalse() throws XMLDBException {
        createCollection("/db/false");
        storeCollectionXconf("/db/system/config/db/false", xconf_no);

        // namespace provided, valid document; should pass
        storeDocument("/db/false", "valid.xml", valid);

        // namespace provided, invalid document; should pass
        storeDocument("/db/false", "invalid.xml", invalid);

        // no namespace provided, should pass
        storeDocument("/db/false", "anonymous.xml", anonymous);

        // non resolvable namespace provided, should pass
        storeDocument("/db/false", "different.xml", different);
    }

    @Test
    public void insertModeTrue() throws XMLDBException {
        // namespace provided, valid document; should pass
        createCollection("/db/true");
        storeCollectionXconf("/db/system/config/db/true", xconf_yes);

        storeDocument("/db/true", "valid.xml", valid);

        // namespace provided, invalid document; should fail
        try {
            storeDocument("/db/true", "invalid.xml", invalid);
            fail("should have failed");
        } catch (XMLDBException ex) {
            String msg = ex.getMessage();
            if (!msg.contains("cvc-complex-type.2.4.a: Invalid content was found")) {
                fail(msg);
            }
        }

        // no namespace provided; should fail
        try {
            storeDocument("/db/true", "anonymous.xml", anonymous);
            fail("should have failed");
        } catch (XMLDBException ex) {
            String msg = ex.getMessage();
            if (!msg.contains("Cannot find the declaration of element 'schema'.")) {
                fail(msg);
            }
        }


        // non resolvable namespace provided, should fail
        try {
            storeDocument("/db/true", "different.xml", different);
            fail("should have failed");
        } catch (XMLDBException ex) {
            String msg = ex.getMessage();
            if (!msg.contains("Cannot find the declaration of element 'asd:schema'.")) {
                fail(msg);
            }
        }
            
    }

    @Test
    public void insertModeAuto() throws XMLDBException {
        // namespace provided, valid document; should pass
        createCollection("/db/auto");
        storeCollectionXconf("/db/system/config/db/auto", xconf_auto);

        storeDocument("/db/auto", "valid.xml", valid);


        // namespace provided, invalid document, should fail
        try {
            storeDocument("/db/auto", "invalid.xml", invalid);
            fail("should have failed");
        } catch (XMLDBException ex) {
            String msg = ex.getMessage();
            if (!msg.contains("cvc-complex-type.2.4.a: Invalid content was found")) {
                fail(msg);
            }
        }

        // no namespace reference, should pass
        try {
            storeDocument("/db/auto", "anonymous.xml", anonymous);
        } catch (XMLDBException ex) {
            String msg = ex.getMessage();
            if (!msg.contains("Cannot find the declaration of element 'schema'.")) {
                fail(msg);
            }
        }

        // non resolvable namespace provided, should fail
        try {
            storeDocument("/db/auto", "different.xml", different);
//            fail("I expected a failure here. to be checked by DIZZZZ");
        } catch (XMLDBException ex) {
            String msg = ex.getMessage();
            fail(msg);
        }
    }
}