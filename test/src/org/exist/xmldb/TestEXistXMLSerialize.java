/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xmldb;

import org.exist.security.Account;
import org.xmldb.api.modules.CollectionManagementService;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.SAXSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import static org.exist.xmldb.XmldbLocalTests.*;

/**
 *
 * @author  bmadigan
 */
public class TestEXistXMLSerialize {

	private final static String XML_DATA =
    	"<test>" +
    	"<para>ääööüüÄÄÖÖÜÜßß</para>" +
		"<para>\uC5F4\uB2E8\uACC4</para>" +
    	"</test>";
    
    private final static String XSL_DATA =
    	"<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" " +
    	"version=\"1.0\">" +
		"<xsl:param name=\"testparam\"/>" +
		"<xsl:template match=\"test\"><test><xsl:apply-templates/></test></xsl:template>" +
		"<xsl:template match=\"para\">" +
		"<p><xsl:value-of select=\"$testparam\"/>: <xsl:apply-templates/></p></xsl:template>" +
		"</xsl:stylesheet>";
    
    private final static File testFile = new File(getExistDir(),"test/src/org/exist/xmldb/PerformanceTest.xml");

    private final static String TEST_COLLECTION = "testXmlSerialize";

    @Before
    public void setUp() throws Exception {
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        Collection testCollection = service.createCollection(TEST_COLLECTION);
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        // change ownership to guest
        Account guest = ums.getAccount(GUEST_UID);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod("rwxr-xr-x");
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection(TEST_COLLECTION);

        //shutdownDB the db
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
    }

    @Test
    public void serialize1() throws TransformerException, XMLDBException, ParserConfigurationException, SAXException, IOException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
        XMLResource resource = (XMLResource) testCollection.createResource(null, "XMLResource");

        Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).
                        newDocumentBuilder().parse(testFile);

        resource.setContentAsDOM(doc);
        System.out.println("Storing resource: "+resource.getId( ));
        testCollection.storeResource(resource);

        resource = (XMLResource)testCollection.getResource(resource.getId());
        assertNotNull(resource);
        Node node = resource.getContentAsDOM( );
        node = node.getOwnerDocument();

        System.out.println("Attempting serialization 1");
        DOMSource source = new DOMSource(node);
        ByteArrayOutputStream out = new ByteArrayOutputStream( );
        StreamResult result = new StreamResult(out);

        Transformer xformer = TransformerFactory.newInstance().newTransformer();
        xformer.transform(source, result);

        System.out.println("Using javax.xml.transform.Transformer");
        System.out.println("---------------------");
        System.out.println(new String(out.toByteArray()));
        System.out.println("--------------------- ");
    }

    @Test
    public void serialize2() throws ParserConfigurationException, SAXException, IOException, XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
        Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
        XMLResource resource = (XMLResource) testCollection.createResource(null, "XMLResource");
        resource.setContentAsDOM(doc);
        System.out.println("Storing resource: "+resource.getId( ));
        testCollection.storeResource(resource);

        resource = (XMLResource)testCollection.getResource(resource.getId());
        assertNotNull(resource);
        Node node = resource.getContentAsDOM();
        System.out.println("Attempting serialization using XMLSerializer");
        OutputFormat format = new OutputFormat( );
        format.setLineWidth(0);
        format.setIndent(5);
        format.setPreserveSpace(true);
        ByteArrayOutputStream out = new ByteArrayOutputStream( );
        XMLSerializer serializer = new XMLSerializer(out, format);

        if(node instanceof Document){
            serializer.serialize((Document) node);
        }else if(node instanceof Element){
            serializer.serialize((Element) node);
        }else{
            fail("Can't serialize node type: "+node);
        }
        System.out.println("Using org.apache.xml.serialize.XMLSerializer");
        System.out.println("---------------------");
        System.out.println(new String(out.toByteArray()));
        System.out.println("--------------------- ");
    }

    @Test
    public void serialize3() throws ParserConfigurationException, SAXException, IOException, XMLDBException, TransformerException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
        Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
        XMLResource resource = (XMLResource) testCollection.createResource(null, "XMLResource");
        resource.setContentAsDOM(doc);
        System.out.println("Storing resource: "+resource.getId( ));
        testCollection.storeResource(resource);

        resource = (XMLResource)testCollection.getResource(resource.getId());
        assertNotNull(resource);
        Node node = resource.getContentAsDOM();
        System.out.println("Attempting serialization using eXist's serializer");
        StringWriter writer = new StringWriter();
        Properties outputProperties = new Properties();
        outputProperties.setProperty("indent", "yes");
        DOMSerializer serializer = new DOMSerializer(writer, outputProperties);
        serializer.serialize(node);

        System.out.println("Using org.exist.util.serializer.DOMSerializer");
        System.out.println("---------------------");
        System.out.println(writer.toString());
        System.out.println("---------------------");
    }

    @Test
    public void serialize4() throws ParserConfigurationException, SAXException, IOException, XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        System.out.println("Xerces version: "+org.apache.xerces.impl.Version.getVersion( ));
        Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance( ).newDocumentBuilder().parse(testFile);
        XMLResource resource = (XMLResource) testCollection.createResource(null, "XMLResource");
        resource.setContentAsDOM(doc);
        System.out.println("Storing resource: "+resource.getId( ));
        testCollection.storeResource(resource);

        resource = (XMLResource)testCollection.getResource(resource.getId());
        assertNotNull(resource);
        @SuppressWarnings("unused")
                Node node = resource.getContentAsDOM();
        System.out.println("Attempting serialization using eXist's SAX serializer");
        StringWriter writer = new StringWriter();
        Properties outputProperties = new Properties();
        outputProperties.setProperty("indent", "yes");
        SAXSerializer serializer = new SAXSerializer(writer, outputProperties);
        resource.getContentAsSAX(serializer);

        System.out.println("Using org.exist.util.serializer.SAXSerializer");
        System.out.println("---------------------");
        System.out.println(writer.toString());
        System.out.println("---------------------");
    }

    @Test
    public void serialize5() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        XMLResource resource = (XMLResource) testCollection.createResource("test.xml", "XMLResource");
        resource.setContent(XML_DATA);
        System.out.println("Storing resource: "+resource.getId( ));
        testCollection.storeResource(resource);

        XMLResource style = (XMLResource) testCollection.createResource("test.xsl", "XMLResource");
        style.setContent(XSL_DATA);
        System.out.println("Storing resource: "+style.getId( ));
        testCollection.storeResource(style);

        Properties outputProperties = new Properties();
        outputProperties.setProperty("indent", "yes");
        testCollection.setProperty("stylesheet", "test.xsl");
        testCollection.setProperty("stylesheet-param.testparam", "TEST");
        StringWriter writer = new StringWriter();
        SAXSerializer serializer = new SAXSerializer(writer, outputProperties);
        resource.getContentAsSAX(serializer);

        System.out.println("Using org.exist.util.serializer.SAXSerializer");
        System.out.println("---------------------");
        System.out.println(writer.toString());
        System.out.println("---------------------");
    }
}