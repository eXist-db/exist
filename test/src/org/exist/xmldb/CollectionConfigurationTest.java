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
import static org.exist.xmldb.XmldbLocalTests.*;

public class CollectionConfigurationTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer();

    private final static String TEST_COLLECTION = "testIndexConfiguration";

    private static final XmldbURI CONF_COLL_URI = XmldbURI.CONFIG_COLLECTION_URI.append("/db/" + TEST_COLLECTION);
    private static final XmldbURI CONF_COLL_URI2 = CONF_COLL_URI.append(TestConstants.SPECIAL_NAME);     
    

    private static final XmldbURI TEST_CONFIG_NAME_1 = XmldbURI.create("test1.xconf");
    private static final XmldbURI TEST_CONFIG_NAME_2 = XmldbURI.create(TestConstants.SPECIAL_NAME.toString()+".xconf");

    private final static String DOCUMENT_CONTENT2 = "<test x='0'>" + "<c c='2002-12-07T12:20:46.275+01:00'>2002-12-07T12:20:46.275+01:00</c>"
    + "<d d='1'>1</d>" + "<e e='1'>1</e>" + "<f f='true'>true</f>" +" <g g='1'>1</g>" +"<h h='1'>1</h>" 
    + "<test x='1'><test x='2'></test></test></test>";
    
    private final static String CONFIG1 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create qname=\"a\" type=\"xs:integer\"/>"
        + "    <create qname=\"b\" type=\"xs:string\"/>"
        + "    <create path=\"//a\" type=\"xs:integer\"/>"
        + "    <create path=\"//b\" type=\"xs:string\"/>"
        + "  </index>"
        + "</collection>";

    @Before
    public void setUp() throws Exception {
        final CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");

        final Collection testCollection = service.createCollection(TEST_COLLECTION);
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        // change ownership to guest
        final Account guest = ums.getAccount(GUEST_UID);
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
        service.removeCollection(TEST_COLLECTION.toString());
        service.removeCollection(CONF_COLL_URI.toString()); //Removes the collection config collection *manually*
    }

    @Test
    public void missingRangeIndexes() throws Exception {
       Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
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
       Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
       String fullCollPath = ROOT_URI + collPath.toString();
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
