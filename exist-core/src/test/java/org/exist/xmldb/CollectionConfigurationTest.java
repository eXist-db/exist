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
 *  $Id: BinaryResourceUpdateTest.java 11148 2010-02-07 14:37:35Z dizzzz $
 */
package org.exist.xmldb;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;
import org.exist.security.Account;

import static org.exist.TestUtils.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.exist.collections.CollectionConfiguration;
import org.exist.test.TestConstants;
import org.exist.xquery.Constants;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

public class CollectionConfigurationTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String TEST_COLLECTION = "testIndexConfiguration";
    
    private final static XmldbURI COLLECTION_SUB1 = XmldbURI.ROOT_COLLECTION_URI.append(TEST_COLLECTION).append("sub1");
    private final static XmldbURI COLLECTION_SUB2 = XmldbURI.ROOT_COLLECTION_URI.append(TEST_COLLECTION).append("sub2");

    private static final XmldbURI CONF_COLL_URI = XmldbURI.CONFIG_COLLECTION_URI.append("/db/" + TEST_COLLECTION);
    private static final XmldbURI CONF_COLL_URI2 = CONF_COLL_URI.append(TestConstants.SPECIAL_NAME);     
    

    private static final XmldbURI TEST_CONFIG_NAME_1 = XmldbURI.create("test1.xconf");
    private static final XmldbURI TEST_CONFIG_NAME_2 = XmldbURI.create(TestConstants.SPECIAL_NAME + ".xconf");

    private final static String DOCUMENT_CONTENT = "<test>" + "<a>001</a>"
    + "<a>01</a>" + "<a>1</a>" + "<b>001</b>" + "<b>01</b>"
    + "<b>1</b>" + "</test>";

    private final static String DOCUMENT_CONTENT2 = "<test x='0'>" + "<c c='2002-12-07T12:20:46.275+01:00'>2002-12-07T12:20:46.275+01:00</c>"
    + "<d d='1'>1</d>" + "<e e='1'>1</e>" + "<f f='true'>true</f>" +" <g g='1'>1</g>" +"<h h='1'>1</h>" 
    + "<test x='1'><test x='2'></test></test></test>";

    private final static String DOCUMENT_CONTENT3 =
        "<test>" +
        "   <a>1</a>" +
        "   <b>1</b>" +
        "   <c>1</c>" +
        "   <d>x</d>" +
        "   <e>xx</e>" +
        "   <f>xxx</f>" +
        "</test>";
    
    private final static String CONFIG1 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create qname=\"a\" type=\"xs:integer\"/>"
        + "    <create qname=\"b\" type=\"xs:string\"/>"
        + "    <create path=\"//a\" type=\"xs:integer\"/>"
        + "    <create path=\"//b\" type=\"xs:string\"/>"
        + "  </index>"
        + "</collection>";

    private final static String CONFIG2 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create path=\"//c\" type=\"xs:dateTime\"/>"
        + "    <create path=\"//d\" type=\"xs:double\"/>"
        + "    <create path=\"//e\" type=\"xs:float\"/>"
        + "    <create path=\"//f\" type=\"xs:boolean\"/>"        
        + "    <create path=\"//g\" type=\"xs:integer\"/>"        
        + "    <create path=\"//h\" type=\"xs:string\"/>"          
        + "    <create path=\"//@c\" type=\"xs:dateTime\"/>"
        + "    <create path=\"//@d\" type=\"xs:double\"/>"
        + "    <create path=\"//@e\" type=\"xs:float\"/>"
        + "    <create path=\"//@f\" type=\"xs:boolean\"/>"        
        + "    <create path=\"//@g\" type=\"xs:integer\"/>"        
        + "    <create path=\"//@h\" type=\"xs:string\"/>"            
        + "    <create path=\"//test/@x\" type=\"xs:integer\"/>"
        + "  </index>"
        + "</collection>";

    private final static String CONFIG3 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create qname=\"a\" type=\"xs:integer\"/>"
        + "    <create path=\"//a\" type=\"xs:integer\"/>"
        + "  </index>"
        + "</collection>";

    private final static String QNAME_CONFIG = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create qname=\"a\" type=\"xs:integer\"/>"
        + "    <create qname=\"b\" type=\"xs:integer\"/>"
        + "    <create path=\"/test/c\" type=\"xs:integer\"/>"
        + "    <create qname=\"d\" type=\"xs:string\"/>"
        + "    <create qname=\"e\" type=\"xs:string\"/>"
        + "    <create path=\"/test/f\" type=\"xs:string\"/>"
        + "  </index>"
        + "</collection>";

    private final String QNAME_CONFIG2 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create qname=\"c\" type=\"xs:dateTime\"/>"
        + "    <create qname=\"d\" type=\"xs:double\"/>"
        + "    <create qname=\"e\" type=\"xs:float\"/>"
        + "    <create qname=\"f\" type=\"xs:boolean\"/>"
        + "    <create qname=\"g\" type=\"xs:integer\"/>"
        + "    <create qname=\"h\" type=\"xs:string\"/>"
        + "    <create qname=\"@c\" type=\"xs:dateTime\"/>"
        + "    <create qname=\"@d\" type=\"xs:double\"/>"
        + "    <create qname=\"@e\" type=\"xs:float\"/>"
        + "    <create qname=\"@f\" type=\"xs:boolean\"/>"
        + "    <create qname=\"@g\" type=\"xs:integer\"/>"
        + "    <create qname=\"@h\" type=\"xs:string\"/>"
        + "    <create qname=\"@x\" type=\"xs:integer\"/>"
        + "  </index>"
        + "</collection>";

    private final static String EMPTY_CONFIG = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "  </index>"
        + "</collection>";

    private final static String INVALID_CONFIG1 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n"
        + " <triggers>\n"
        + "     <trigger event=\"store,update,remove\" class=\"org.exist.NonExistingTrigger\">\n"
        + "     </trigger>\n"
        + " </triggers>\n"
        + " <index>\n"
        + "     <create foo=\"a\" type=\"xs:integer\"/>\n"
        + " </index>\n"
        + "</collection>";

    @Before
    public void setUp() throws Exception {
        final CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");

        final Collection testCollection = service.createCollection(TEST_COLLECTION);
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        // change ownership to guest
        final Account guest = ums.getAccount(GUEST_DB_USER);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod("rwxr-xr-x");

        final Collection testConfCollection = service.createCollection(CONF_COLL_URI.toString());
        ums = (UserManagementService) testConfCollection.getService("UserManagementService", "1.0");
        // change ownership to guest
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod("rwxr-xr-x");

        //  configColl = cms.createCollection(CONF_COLL_URI.toString());
    }

    @After
    public void tearDown() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        service.removeCollection(TEST_COLLECTION);
        service.removeCollection(CONF_COLL_URI.toString()); //Removes the collection config collection *manually*
    }

    @Test
    public void collectionConfigurationService1() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

        //Configure collection automatically
        IndexQueryService idxConf = (IndexQueryService)testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG1);

        //... then index document
        XMLResource doc = (XMLResource)testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
        doc.setContent(DOCUMENT_CONTENT); testCollection.storeResource(doc);

        XPathQueryService service = (XPathQueryService)
        testCollection.getService("XPathQueryService", "1.0");

        //3 numeric values
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals("3", result.getResource(0).getContent());
        //... but 1 string value
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals("1", result.getResource(0).getContent());

            //3 numeric values
        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(3, result.getSize());
        //... but 1 string value
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(1, result.getSize());
   }

    @Test
    public void testCollectionConfigurationService2() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

        // Add document....
        XMLResource doc = (XMLResource) testCollection.createResource(
                TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        testCollection.storeResource(doc);

        // ... then configure collection automatically
        IndexQueryService idxConf = (IndexQueryService) testCollection
                .getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG1);

        XPathQueryService service = (XPathQueryService) testCollection
                .getService("XPathQueryService", "1.0");

        // No numeric values because we have no index
        ResourceSet result = service.query("util:index-key-occurrences( /test/a, 1 ) ");
        assertEquals(0, result.getSize());
        // No string value because we have no index
        result = service.query("util:index-key-occurrences( /test/b, \"1\" ) ");
        assertEquals(0, result.getSize());

        // No numeric values because we have no index
        result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
        assertEquals(0, result.getSize());
        // No string value because we have no index
        result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
        assertEquals(0, result.getSize());

        // ...let's activate the index
        idxConf.reindexCollection();

        //3 numeric values
        result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals("3", result.getResource(0).getContent());
        //... but 1 string value
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals("1", result.getResource(0).getContent());

        // 3 numeric values
        result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
        assertEquals(3, result.getSize());
        // ... but 1 string value
        result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
        assertEquals(1, result.getSize());
    }

    @Test
    public void collectionConfigurationService3() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

        //Configure collection *manually*
        storeConfiguration(CONF_COLL_URI, CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI, CONFIG1);

        //... then index document
        XMLResource doc = (XMLResource)
        testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
        doc.setContent(DOCUMENT_CONTENT); testCollection.storeResource(doc);

        XPathQueryService service = (XPathQueryService)
        testCollection.getService("XPathQueryService", "1.0");

        //3 numeric values
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals(1, result.getSize());
        assertEquals("3", result.getResource(0).getContent());
        //... but 1 string value
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals(1, result.getSize());
        assertEquals("1", result.getResource(0).getContent());

        //3 numeric values
        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(3, result.getSize());
        //... but 1 string value
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(1, result.getSize());
   }
    

   @Test
   public void collectionConfigurationService4() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
   
       // Add document....
       XMLResource doc = (XMLResource) testCollection.createResource(
               TestConstants.TEST_XML_URI.toString(), "XMLResource");
       doc.setContent(DOCUMENT_CONTENT);
       testCollection.storeResource(doc);

       // ... then configure collection *manually*
       storeConfiguration(CONF_COLL_URI, CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI, CONFIG1);

       XPathQueryService service = (XPathQueryService) testCollection
               .getService("XPathQueryService", "1.0");

       // No numeric values because we have no index
       ResourceSet result = service.query("util:index-key-occurrences( /test/a, 1 ) ");
       assertEquals(0, result.getSize());
       // No string value because we have no index
       result = service.query("util:index-key-occurrences( /test/b, \"1\" ) ");
       assertEquals(0, result.getSize());

       // No numeric values because we have no index
       result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
       assertEquals(0, result.getSize());
       // No string value because we have no index
       result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
       assertEquals(0, result.getSize());

       // ...let's activate the index
       IndexQueryService idxConf = (IndexQueryService)
           testCollection.getService("IndexQueryService", "1.0");
       idxConf.reindexCollection();

       //3 numeric values
       result = service.query("util:index-key-occurrences(/test/a, 1)");
       assertEquals("3", result.getResource(0).getContent());
       //... but 1 string value
       result = service.query("util:index-key-occurrences(/test/b, \"1\")");
       assertEquals("1", result.getResource(0).getContent());

       // 3 numeric values
       result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
       assertEquals(3, result.getSize());
       // ... but 1 string value
       result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
       assertEquals(1, result.getSize());
   } 

   @Test
   public void collectionConfigurationService5() throws XMLDBException {
       Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

       //Configure collection *manually*
       XmldbURI configurationFileName = XmldbURI.create(CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE);
       storeConfiguration(CONF_COLL_URI, configurationFileName, CONFIG1);

       // ... then configure collection automatically
       IndexQueryService idxConf = (IndexQueryService) testCollection
               .getService("IndexQueryService", "1.0");
       idxConf.configureCollection(CONFIG1);

       // Add document....
       XMLResource doc = (XMLResource) testCollection.createResource(
               TestConstants.TEST_XML_URI.toString(), "XMLResource");
       doc.setContent(DOCUMENT_CONTENT);
       testCollection.storeResource(doc);

       XPathQueryService service = (XPathQueryService) testCollection
               .getService("XPathQueryService", "1.0");

       //our config file
       ResourceSet result = service.query("xmldb:get-child-resources('" +
               CONF_COLL_URI +
               "')");
       assertEquals(configurationFileName.toString(), result.getResource(0).getContent());

       //3 numeric values
       result = service.query("util:index-key-occurrences(/test/a, 1)");
       assertEquals("3", result.getResource(0).getContent());
       //... but 1 string value
       result = service.query("util:index-key-occurrences(/test/b, \"1\")");
       assertEquals("1", result.getResource(0).getContent());

       // 3 numeric values
       result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
       assertEquals(3, result.getSize());
       // ... but 1 string value
       result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
       assertEquals(1, result.getSize());
   } 

   @Test
   public void collectionConfigurationService6() throws XMLDBException {
       Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

       // Add document....
       XMLResource doc = (XMLResource) testCollection.createResource(
               TestConstants.TEST_XML_URI.toString(), "XMLResource");
       doc.setContent(DOCUMENT_CONTENT);
       testCollection.storeResource(doc);

       //... then configure collection *manually*
       XmldbURI configurationFileName = XmldbURI.create(CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE);
       storeConfiguration(CONF_COLL_URI, configurationFileName, CONFIG1);

       //... then configure collection automatically
       IndexQueryService idxConf = (IndexQueryService)
       testCollection.getService("IndexQueryService", "1.0");
       idxConf.configureCollection(CONFIG1);

       XPathQueryService service = (XPathQueryService) testCollection
       .getService("XPathQueryService", "1.0");

       //our config file
       ResourceSet result = service.query("xmldb:get-child-resources('" +
               CONF_COLL_URI +
               "')");
       assertEquals(configurationFileName.toString(), result.getResource(0).getContent());

       // No numeric values because we have no index
       result = service.query("util:index-key-occurrences( /test/a, 1 ) ");
       assertEquals(0, result.getSize());
       // No string value because we have no index
       result = service.query("util:index-key-occurrences( /test/b, \"1\" ) ");
       assertEquals(0, result.getSize());

       // No numeric values because we have no index
       result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
       assertEquals(0, result.getSize());
       // No string value because we have no index
       result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
       assertEquals(0, result.getSize());

       // ...let's activate the index
       idxConf.reindexCollection();

       //WARNING : the code hereafter used to *not* work whereas
       //testCollectionConfigurationService4 did.
       //Adding confMgr.invalidateAll(getName()); in Collection.storeInternal solved the problem
       //Strange case that needs investigations... -pb

       //3 numeric values
       result = service.query("util:index-key-occurrences(/test/a, 1)");
       assertEquals("3", result.getResource(0).getContent());
       //... but 1 string value
       result = service.query("util:index-key-occurrences(/test/b, \"1\")");
       assertEquals("1", result.getResource(0).getContent());

       // 3 numeric values
       result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
       assertEquals(3, result.getSize());
       // ... but 1 string value
       result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
       assertEquals(1, result.getSize());
   }

    /** Check if configurations are properly passed down the collection hierarchy. */
    @Test
    public void collectionConfigurationService7() throws XMLDBException {

        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        
        CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());
        UserManagementService ums = (UserManagementService) sub2.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        //Configure collection automatically
        // sub2 should inherit its index configuration from the top collection
        IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG1);

        //... then index document
        XMLResource doc = (XMLResource)
                sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        sub2.storeResource(doc);

        XPathQueryService service = (XPathQueryService)
                sub2.getService("XPathQueryService", "1.0");

        //3 numeric values
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals("3", result.getResource(0).getContent());
        //... but 1 string value
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals("1", result.getResource(0).getContent());

            //3 numeric values
        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(3, result.getSize());
        //... but 1 string value
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(1, result.getSize());
   }

    /** Overwrite configuration in a sub collection */
    @Test
    public void collectionConfigurationService8() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

        CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());
        UserManagementService ums = (UserManagementService) sub2.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        //Configure collection automatically
        IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG1);

        // Overwrite main configuration with an empty configuration in the subcollection
        idxConf = (IndexQueryService) sub2.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(EMPTY_CONFIG);

        //... then index document
        XMLResource doc = (XMLResource)
                sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        sub2.storeResource(doc);

        XPathQueryService service = (XPathQueryService)
                sub2.getService("XPathQueryService", "1.0");

        // index should be empty
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals(0, result.getSize());
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals(0, result.getSize());

        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(0, result.getSize());
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(0, result.getSize());
   }

    /** Overwrite configuration in a sub collection 2 times */
    @Test
    public void collectionConfigurationService9() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        Collection sub1 = cms.createCollection(COLLECTION_SUB1.toString());
        UserManagementService ums = (UserManagementService) sub1.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");
        Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());
        ums = (UserManagementService) sub2.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG1);

        // Overwrite main configuration with an empty configuration in the subcollection
        idxConf = (IndexQueryService) sub1.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(EMPTY_CONFIG);

        // Overwrite sub1 configuration in sub2
        idxConf = (IndexQueryService) sub2.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG3);

        //... then store document into sub1
        XMLResource doc = (XMLResource)sub1.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        sub1.storeResource(doc);

        XPathQueryService service = (XPathQueryService)sub1.getService("XPathQueryService", "1.0");

        // sub1 has empty configuration, so index should be empty as well
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals(0, result.getSize());
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals(0, result.getSize());

        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(0, result.getSize());
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(0, result.getSize());

        // remove document in sub1 and restore it in sub2
        sub1.removeResource(doc);
        doc = (XMLResource)sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        sub2.storeResource(doc);

        service = (XPathQueryService) sub2.getService("XPathQueryService", "1.0");

        // sub2 only has an index on /test/a, but not on /test/b

        //3 numeric values
       result = service.query("util:index-key-occurrences(/test/a, 1)");
       assertEquals("3", result.getResource(0).getContent());
       //... but 1 string value
       result = service.query("util:index-key-occurrences(/test/b, \"1\")");
       assertEquals(0, result.getSize());

       // 3 numeric values
       result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
       assertEquals(3, result.getSize());
       // ... but 1 string value
       result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
       assertEquals(0, result.getSize());
   }

    /** Remove config document */
    @Test
    public void collectionConfigurationService10() throws XMLDBException {

        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

        CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());
        UserManagementService ums = (UserManagementService) sub2.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG1);

        //... then index document
        XMLResource doc = (XMLResource)
                sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        sub2.storeResource(doc);

        XPathQueryService service = (XPathQueryService) sub2.getService("XPathQueryService", "1.0");

        //3 numeric values
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals("3", result.getResource(0).getContent());
        //... but 1 string value
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals("1", result.getResource(0).getContent());

            //3 numeric values
        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(3, result.getSize());
        //... but 1 string value
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(1, result.getSize());

        // remove config document thus dropping the configuration
        Collection confCol = DatabaseManager.getCollection("xmldb:exist://" + CONF_COLL_URI.toString(), ADMIN_DB_USER, ADMIN_DB_PWD);
        Resource confDoc = confCol.getResource("collection.xconf");
        assertNotNull(confDoc);
        confCol.removeResource(confDoc);
//            cms = (CollectionManagementService) confCol.getService("CollectionManagementService", "1.0");
//            cms.removeCollection(".");

        idxConf.reindexCollection();

        // index should be empty since configuration was removed
        result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals(0, result.getSize());
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals(0, result.getSize());

        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(0, result.getSize());
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(0, result.getSize());
   }

    /** Remove config collection */
    @Test
    public void collectionConfigurationService11() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

        CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());
        UserManagementService ums = (UserManagementService) sub2.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(CONFIG1);

        //... then index document
        XMLResource doc = (XMLResource)
                sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        sub2.storeResource(doc);

        XPathQueryService service = (XPathQueryService) sub2.getService("XPathQueryService", "1.0");

        //3 numeric values
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals("3", result.getResource(0).getContent());
        //... but 1 string value
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals("1", result.getResource(0).getContent());

            //3 numeric values
        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(3, result.getSize());
        //... but 1 string value
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(1, result.getSize());

        // remove config document thus dropping the configuration
        Collection confCol = DatabaseManager.getCollection("xmldb:exist://" + CONF_COLL_URI.toString(), ADMIN_DB_USER, ADMIN_DB_PWD);
        Resource confDoc = confCol.getResource("collection.xconf");
        assertNotNull(confDoc);
        confCol.removeResource(confDoc);

        idxConf.reindexCollection();

        // index should be empty since configuration was removed
        result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals(0, result.getSize());
        result = service.query("util:index-key-occurrences(/test/b, \"1\")");
        assertEquals(0, result.getSize());

        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(0, result.getSize());
        result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
        assertEquals(0, result.getSize());
   }

    @Test
    public void invalidConfiguration1() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

        CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());
        UserManagementService ums = (UserManagementService) sub2.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(INVALID_CONFIG1);

        //... then index document
        XMLResource doc = (XMLResource)
                sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
        doc.setContent(DOCUMENT_CONTENT);
        sub2.storeResource(doc);

        XPathQueryService service = (XPathQueryService) sub2.getService("XPathQueryService", "1.0");

        // index should be empty since configuration was invalid
        ResourceSet result = service.query("util:index-key-occurrences(/test/a, 1)");
        assertEquals(0, result.getSize());

        result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
        assertEquals(0, result.getSize());
    }

   @Test @Ignore
   public void rangeIndex1() throws XMLDBException {
       Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
       
       //Configure collection automatically
       IndexQueryService idxConf = (IndexQueryService)
       testCollection.getService("IndexQueryService", "1.0");
       idxConf.configureCollection(CONFIG2);

       //... then index document
       XMLResource doc = (XMLResource)
       testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
       doc.setContent(DOCUMENT_CONTENT2);
       testCollection.storeResource(doc);

       XPathQueryService service = (XPathQueryService)
       testCollection.getService("XPathQueryService", "1.0");

       ResourceSet result = service.query("util:index-key-occurrences(/test/c, xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/c)");
       assertEquals("xs:dateTime", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/d, xs:double(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/d)");
       assertEquals("xs:double", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/e, xs:float(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/e)");
       assertEquals("xs:float", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/f, true())");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/f)");
       assertEquals("xs:boolean", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/g, xs:integer(1))");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/g)");
       assertEquals("xs:integer", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/h, '1')");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/h)");
       assertEquals("xs:string", result.getResource(0).getContent());

       result = service.query("/test/c[(# exist:force-index-use #) { . = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { c = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/d[(# exist:force-index-use #) { . = xs:double(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { d = xs:double(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/e[(# exist:force-index-use #) { . = xs:float(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { e = xs:float(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/f[(# exist:force-index-use #) { . = true() }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { f = true() }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/g[(# exist:force-index-use #) { . = 1 }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { g = 1 }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/h[(# exist:force-index-use #) { . = '1' }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { h = '1' }]");
       assertEquals(1, result.getSize());

       boolean exceptionCaught = false;
       try {
           result = service.query("/test[(# exist:force-index-use #) { contains(d, '1') }]");
           assertEquals(0, result.getSize());
       } catch (XMLDBException e) {
           exceptionCaught = true;
       }
       assertTrue("contains() should not use index of type xs:double", exceptionCaught);
       exceptionCaught = false;
       try {
           result = service.query("/test[(# exist:force-index-use #) { matches(d, '1') }]");
           assertEquals(0, result.getSize());
       } catch (XMLDBException e) {
           exceptionCaught = true;
       }
       assertTrue("matches() should not use index of type xs:double", exceptionCaught);

       result = service.query("/test[matches(h, '1')]");
       assertEquals(1, result.getSize());
  }   

   @Test @Ignore
    public void rangeIndex2() throws XMLDBException {
       Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);

       //Configure collection automatically
       IndexQueryService idxConf = (IndexQueryService)
       testCollection.getService("IndexQueryService", "1.0");
       idxConf.configureCollection(QNAME_CONFIG2);

       //... then index document
       XMLResource doc = (XMLResource)
       testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
       doc.setContent(DOCUMENT_CONTENT2);
       testCollection.storeResource(doc);

       XPathQueryService service = (XPathQueryService)
       testCollection.getService("XPathQueryService", "1.0");

       ResourceSet result = service.query("util:index-key-occurrences(/test/c, xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/c)");
       assertEquals("xs:dateTime", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/d, xs:double(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/d)");
       assertEquals("xs:double", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/e, xs:float(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/e)");
       assertEquals("xs:float", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/f, true())");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/f)");
       assertEquals("xs:boolean", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/g, xs:integer(1))");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/g)");
       assertEquals("xs:integer", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/h, '1')");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/h)");
       assertEquals("xs:string", result.getResource(0).getContent());

       result = service.query("(# exist:force-index-use #) { /test/c[. = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\")] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test[c = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\")] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test/d[. = xs:double(1)] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test[d = xs:double(1)] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test/e[. = xs:float(1)] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test[e = xs:float(1)] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test/f[. = true()] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test[f = true()] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test/g[. = 1] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test[g = 1] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test/h[. = '1'] }");
       assertEquals(1, result.getSize());

       result = service.query("(# exist:force-index-use #) { /test[h = '1'] }");
       assertEquals(1, result.getSize());

       boolean exceptionCaught = false;
       try {
           result = service.query("(# exist:force-index-use #) { /test[contains(d, '1')] }");
           assertEquals(0, result.getSize());
       } catch (XMLDBException e) {
           exceptionCaught = true;
       }
       assertTrue("contains() should not use index of type xs:double", exceptionCaught);
       exceptionCaught = false;
       try {
           result = service.query("(# exist:force-index-use #) { /test[matches(d, '1')] }");
           assertEquals(0, result.getSize());
       } catch (XMLDBException e) {
           exceptionCaught = true;
       }
       assertTrue("matches() should not use index of type xs:double", exceptionCaught);

       result = service.query("/test[matches(h, '1')]");
       assertEquals(1, result.getSize());
  }

   @Test @Ignore
    public void rangeIndex3() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        
        //Configure collection automatically
        IndexQueryService idxConf = (IndexQueryService)
                testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(QNAME_CONFIG);

        //... then index document
        XMLResource doc = (XMLResource)
                testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
        doc.setContent(DOCUMENT_CONTENT3);
        testCollection.storeResource(doc);

        EXistXQueryService service = (EXistXQueryService) testCollection.getService("XQueryService", "1.0");
        // the query optimizer cannot optimize the following general comparison as
        // the context qname is unknown. however, the available qname index should still be used.
        ResourceSet result = service.query("(# exist:force-index-use #) { for $t in /test/a where $t = 1 return $t}");
        assertEquals(1, result.getSize());
        result = service.query("(# exist:force-index-use #) { for $t in /test/d where contains($t, 'x') return $t}");
        assertEquals(1, result.getSize());
        result = service.query("(# exist:force-index-use #) { for $t in /test/d where matches($t, 'x$') return $t}");
        assertEquals(1, result.getSize());

        // left operand to comparison uses nodes from different elements, both having an index defined
        // by qname: use qname index
        result = service.query("(# exist:force-index-use #) { for $t in /test/(a|b) where $t = 1 return $t}");
        assertEquals(2, result.getSize());
        result = service.query("(# exist:force-index-use #) { for $t in /test/(d|e) where contains($t, 'x') return $t}");
        assertEquals(2, result.getSize());
        result = service.query("(# exist:force-index-use #) { for $t in /test/(d|e) where matches($t, 'x$') return $t}");
        assertEquals(2, result.getSize());

        // left operand to comparison uses nodes from different elements with mixed indexes,
        // some defined on qname, one defined by path: comparison needs to scan all 3 indexes.
        result = service.query("(# exist:force-index-use #) { for $t in /test/(a|b|c) where $t = 1 return $t}");
        assertEquals(3, result.getSize());
        result = service.query("(# exist:force-index-use #) { for $t in /test/(d|e|f) where contains($t, 'x') return $t}");
        assertEquals(3, result.getSize());
        result = service.query("(# exist:force-index-use #) { for $t in /test/(d|e|f) where matches($t, 'x$') return $t}");
        assertEquals(3, result.getSize());

        // left operand has index defined on path. other elements in the collection use indexes
        // on qname: comparison needs to scan all index types.
        result = service.query("(# exist:force-index-use #) { for $t in /test/c where $t = 1 return $t}");
        assertEquals(1, result.getSize());

        // simple comparison, left operand has index defined on path.
        result = service.query("(# exist:force-index-use #) { /test[c = 1] }");
        assertEquals(1, result.getSize());
        result = service.query("(# exist:force-index-use #) { /test[matches(d, 'x')] }");
        assertEquals(1, result.getSize());

        // wrong index type: can't use fn:contains with an integer index
        boolean exceptionCaught = false;
        try {
            result = service.query("(# exist:force-index-use #) { for $t in /test/c where contains($t, '1') return $t}");
            assertEquals(1, result.getSize());
        } catch (XMLDBException e) {
            exceptionCaught = true;
        }
        assertTrue(exceptionCaught);

        // wrong index type: can't use fn:matches with an integer index
        exceptionCaught = false;
        try {
            result = service.query("(# exist:force-index-use #) { for $t in /test/c where matches($t, '1') return $t}");
            assertEquals(1, result.getSize());
        } catch (XMLDBException e) {
            exceptionCaught = true;
        }
        assertTrue(exceptionCaught);

        // wrong index type: can't use fn:matches with an integer index
        exceptionCaught = false;
        try {
            result = service.query("(# exist:force-index-use #) { /test[matches(c, '1')] }");
            assertEquals(1, result.getSize());
        } catch (XMLDBException e) {
            exceptionCaught = true;
        }
        assertTrue(exceptionCaught);
    }

   @Test @Ignore
   public void rangeIndexOverAttributes() throws XMLDBException {
       Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
       
       //Configure collection automatically
       IndexQueryService idxConf = (IndexQueryService)
       testCollection.getService("IndexQueryService", "1.0");
       idxConf.configureCollection(CONFIG2);

       //... then index document
       XMLResource doc = (XMLResource)
       testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
       doc.setContent(DOCUMENT_CONTENT2);
       testCollection.storeResource(doc);

       XPathQueryService service = (XPathQueryService)
       testCollection.getService("XPathQueryService", "1.0");

       ResourceSet result = service.query("//test[@x = 0]");
       assertEquals(1, result.getSize());

       result = service.query("//test[@x eq 0]");
       assertEquals(1, result.getSize());

       result = service.query("//test[(# exist:force-index-use #) { @x = 0 }]");
       assertEquals(1, result.getSize());

       result = service.query("//test[(# exist:force-index-use #) { @x eq 0 }]");
       assertEquals(1, result.getSize());

       result = service.query("util:index-key-occurrences(/test//@c, xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test//@c)");
       assertEquals("xs:dateTime", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/c/@c, xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/c/@c)");
       assertEquals("xs:dateTime", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test//@d, xs:double(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test//@d)");
       assertEquals("xs:double", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/d/@d, xs:double(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/d/@d)");
       assertEquals("xs:double", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test//@e, xs:float(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test//@e)");
       assertEquals("xs:float", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/e/@e, xs:float(1) )");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/e/@e)");
       assertEquals("xs:float", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test//@f, true())");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test//@f)");
       assertEquals("xs:boolean", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/f/@f, true())");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/f/@f)");
       assertEquals("xs:boolean", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test//@g, xs:integer(1))");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test//@g)");
       assertEquals("xs:integer", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/g/@g, xs:integer(1))");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/g/@g)");
       assertEquals("xs:integer", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test//@h, '1')");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test//@h)");
       assertEquals("xs:string", result.getResource(0).getContent());

       result = service.query("util:index-key-occurrences(/test/h/@h, '1')");
       assertEquals(1, result.getSize());
       assertEquals("1", result.getResource(0).getContent());

       result = service.query("util:index-type(/test/h/@h)");
       assertEquals("xs:string", result.getResource(0).getContent());

       result = service.query("/test//@c[(# exist:force-index-use #) { . = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { .//@c = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/c/@c[(# exist:force-index-use #) { . = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { ./c/@c = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") }]");
       assertEquals(1, result.getSize());

       result = service.query("/test//@d[(# exist:force-index-use #) { . = xs:double(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { .//@d = xs:double(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/d/@d[(# exist:force-index-use #) { . = xs:double(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { ./d/@d = xs:double(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test//@e[(# exist:force-index-use #) { . = xs:float(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { .//@e = xs:float(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/e/@e[(# exist:force-index-use #) { . = xs:float(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { ./e/@e = xs:float(1) }]");
       assertEquals(1, result.getSize());

       result = service.query("/test//@f[(# exist:force-index-use #) { . = true() }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { .//@f = true() }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/f/@f[(# exist:force-index-use #) { . = true() }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { ./f/@f = true() }]");
       assertEquals(1, result.getSize());

       result = service.query("/test//@g[(# exist:force-index-use #) { . = 1 }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { .//@g = 1 }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/g/@g[(# exist:force-index-use #) { . = 1 }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { ./g/@g = 1 }]");
       assertEquals(1, result.getSize());

       result = service.query("/test//@h[(# exist:force-index-use #) { . = '1' }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { .//@h = '1' }]");
       assertEquals(1, result.getSize());

       result = service.query("/test/h/@h[(# exist:force-index-use #) { . = '1' }]");
       assertEquals(1, result.getSize());

       result = service.query("/test[(# exist:force-index-use #) { ./h/@h = '1' }]");
       assertEquals(1, result.getSize());
  }   

   @Test
   public void missingRangeIndexes() throws Exception {
       Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
       @SuppressWarnings("unused")
       ResourceSet result; 
       boolean exceptionThrown = false;
       //Configure collection automatically
       @SuppressWarnings("unused")
       IndexQueryService idxConf = (IndexQueryService)
       testCollection.getService("IndexQueryService", "1.0");

       //... then index document
       XMLResource doc = (XMLResource)
       testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
       doc.setContent(DOCUMENT_CONTENT2);
       testCollection.storeResource(doc);

       XPathQueryService service = (XPathQueryService)
       testCollection.getService("XPathQueryService", "1.0");

       try {
               exceptionThrown = false;
               result = service.query("/test/c[(# exist:force-index-use #) { . = xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") }]");
       } catch (Exception e) {
           //e.printStackTrace();
           if (e.getMessage().indexOf("XQDYxxxx") != Constants.STRING_NOT_FOUND)
                       exceptionThrown = true;
               else throw e;
       }
       assertTrue("Exception expected : missing index", exceptionThrown);

       try {
               exceptionThrown = false;
               result = service.query("/test/d[(# exist:force-index-use #) { . = xs:double(1) }]");
           } catch (Exception e) {
               if (e.getMessage().indexOf("XQDYxxxx") != Constants.STRING_NOT_FOUND)
                       exceptionThrown = true;
               else throw e;
           }
           assertTrue("Exception expected : missing index", exceptionThrown);

           try {
               exceptionThrown = false;
               result = service.query("/test/e[(# exist:force-index-use #) { . = xs:float(1) }]");
               } catch (Exception e) {
               if (e.getMessage().indexOf("XQDYxxxx") != Constants.STRING_NOT_FOUND)
                       exceptionThrown = true;
               else throw e;
               }
               assertTrue("Exception expected : missing index", exceptionThrown);

           try {
               exceptionThrown = false;
               result = service.query("/test/f[(# exist:force-index-use #) { . = true() }]");
                    } catch (Exception e) {
               if (e.getMessage().indexOf("XQDYxxxx") != Constants.STRING_NOT_FOUND)
                       exceptionThrown = true;
               else throw e;
                    }
                    assertTrue("Exception expected : missing index", exceptionThrown);

            try {
               exceptionThrown = false;
               result = service.query("/test/g[(# exist:force-index-use #) { . = 1 }]");
               } catch (Exception e) {
               if (e.getMessage().indexOf("XQDYxxxx") != Constants.STRING_NOT_FOUND)
                       exceptionThrown = true;
               else throw e;
               }
               assertTrue("Exception expected : missing index", exceptionThrown);

           try {
               exceptionThrown = false;
               result = service.query("/test/h[(# exist:force-index-use #) { . = '1' }]");
           } catch (Exception e) {
               if (e.getMessage().indexOf("XQDYxxxx") != Constants.STRING_NOT_FOUND)
                       exceptionThrown = true;
               else throw e;
           }
           assertTrue("Exception expected : missing index", exceptionThrown);
  }   

   @Test
   public void multipleConfigurations00() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_1, true);
   }

   @Test
   public void multipleConfigurations01() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_2, false);
   }

   @Test
   public void multipleConfigurations02() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_1, true);
   }

   @Test
   public void multipleConfigurations03() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_2, true);
   }

   @Test
   public void multipleConfigurations04() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_1, false);
   }

   @Test
   public void multipleConfigurations05() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_2, true);
   }

   @Test
   public void multipleConfigurations06() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI2, TEST_CONFIG_NAME_1, true);
   }

   @Test
   public void multipleConfigurations07() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI2, TEST_CONFIG_NAME_2, true);
   }

   @Test
   public void multipleConfigurations08() {          
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_1, true);
   }

   @Test
   public void multipleConfigurations09() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_2, true);
   }

   @Test
   public void multipleConfigurations10() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_1, true);
   }

   @Test
   public void multipleConfigurations11() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_2, false);
   }

   @Test
   public void multipleConfigurations12() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_1, true);
   }

   @Test
   public void multipleConfigurations13() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_2, true);
   }

   @Test
   public void multipleConfigurations14() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_2, CONF_COLL_URI2, TEST_CONFIG_NAME_1, false);
   }

   @Test
   public void multipleConfigurations15() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_2, CONF_COLL_URI2, TEST_CONFIG_NAME_2, true);
   }
  
   private void checkStoreConf(XmldbURI coll1, XmldbURI confName1, XmldbURI coll2, XmldbURI confName2, boolean shouldSucceed) {
   	  try {
	   	  storeConfiguration(coll1, confName1, CONFIG1);
	   	  storeConfiguration(coll2, confName2, CONFIG1);
	   	  if(!shouldSucceed) {
	   	  	fail("Should not have been able to store '" + confName1 + "' to '" + coll1 +
	   	  			"'\n\tand then '" + confName2 + "' to '" + coll2 + "'");
	   	  }
	   	  	
   	  } catch (XMLDBException xe) {
   	  	  if(shouldSucceed) {
   	  	      fail("Should have been able to store '" + confName1 + "' to '" + coll1 +
   	  	      		"'\n\tand then '" + confName2 + "' to '" + coll2 + "': " + xe.getMessage());
   	  	  }
   	  }
   }
   private void storeConfiguration(XmldbURI collPath, XmldbURI confName, String confContent) throws XMLDBException {
       Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
       String fullCollPath = XmldbURI.LOCAL_DB + collPath.toString();
       Collection configColl = DatabaseManager.getCollection(fullCollPath, "admin", "");
       if(configColl == null) {
     	   CollectionManagementService cms = (CollectionManagementService)testCollection.getService("CollectionManagementService", "1.0");
            configColl = cms.createCollection(collPath.toString());
            UserManagementService ums = (UserManagementService) configColl.getService("UserManagementService", "1.0");
            ums.chmod("rwxr-xr-x");
       }
       assertNotNull(configColl);
       Resource res = configColl.createResource(confName.toString(), "XMLResource");
       assertNotNull(res);
       res.setContent(confContent);            
       configColl.storeResource(res);
       UserManagementService ums = (UserManagementService)configColl.getService("UserManagementService", "1.0");
       ums.chmod(res, 0744);
   }
}
