package org.exist.xmldb.test;

import junit.framework.TestCase;

import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.storage.DBBroker;
import org.exist.test.TestConstants;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

public class CollectionConfigurationTest extends TestCase {

    private final static String URI = "xmldb:exist://";

    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private final static XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("testIndexConfiguration");
    

    private static final XmldbURI CONF_COLL_URI = XmldbURI.CONFIG_COLLECTION_URI.append(TEST_COLLECTION);     
    private static final XmldbURI CONF_COLL_URI2 = CONF_COLL_URI.append(TestConstants.SPECIAL_NAME);     
    //private static final String CONF_COLL_PATH = CollectionConfigurationManager.CONFIG_COLLECTION
    //        + DBBroker.ROOT_COLLECTION + "/" + TEST_COLLECTION;     

    //private final static String coll1 = CONF_COLL_PATH;
    //private final static String coll2 = coll1 + "/" + TEST_COLLECTION_2;

    //private final static String TestConstants.TEST_XML_URI = "test.xml";

	private static final XmldbURI TEST_CONFIG_NAME_1 = XmldbURI.create("test1.xconf");
	private static final XmldbURI TEST_CONFIG_NAME_2 = XmldbURI.create(TestConstants.SPECIAL_NAME.toString()+".xconf");

    private final static String DOCUMENT_CONTENT = "<test>" + "<a>001</a>"
            + "<a>01</a>" + "<a>1</a>" + "<b>001</b>" + "<b>01</b>"
            + "<b>1</b>" + "</test>";

    private String CONFIG1 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
            + "  <index>"
            + "    <create qname=\"a\" type=\"xs:integer\"/>"
            + "    <create qname=\"b\" type=\"xs:string\"/>"
            + "  </index>"
            + "</collection>";

    private Collection testCollection;

    protected void setUp() {
        try {
            // initialize driver
            Class cl = Class.forName(DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            testCollection = service.createCollection(TEST_COLLECTION.toString());
            assertNotNull(testCollection);

            Collection configColl = DatabaseManager.getCollection(URI + CONF_COLL_URI.toString(), "admin", null);
         	if(configColl == null) {
           	  System.out.println("creating collection '" + CONF_COLL_URI + "'");
           	  CollectionManagementService cms = (CollectionManagementService)testCollection.getService("CollectionManagementService", "1.0");
           	  configColl = cms.createCollection(CONF_COLL_URI.toString());
         	}
         	if(configColl == null) {
         		fail("Could not create config collection: "+CONF_COLL_URI);
         	}
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    protected void tearDown() {
        try {
           
            Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            service.removeCollection(TEST_COLLECTION.toString());
            testCollection = null;
            
            //Removes the collection config collection *manually*          
            service.removeCollection(CONF_COLL_URI.toString());
            
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }


     public void testCollectionConfigurationService1() { 
         ResourceSet result; 
         try {
             //Configure collection automatically
             IndexQueryService idxConf = (IndexQueryService)
             testCollection.getService("IndexQueryService", "1.0");
             idxConf.configureCollection(CONFIG1);
 
             //... then index document 
             XMLResource doc = (XMLResource)
             testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
             doc.setContent(DOCUMENT_CONTENT); testCollection.storeResource(doc);
     
             XPathQueryService service = (XPathQueryService)
             testCollection.getService("XPathQueryService", "1.0");
    
             //3 numeric values 
             result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) "); 
             assertEquals(3, result.getSize()); 
             //... but 1 string value 
             result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) "); 
             assertEquals(1, result.getSize()); }
         catch(Exception e) { 
             fail(e.getMessage());             
         }
    }
     

    public void testCollectionConfigurationService2() {
        ResourceSet result;
        try {
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
            result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
            assertEquals(0, result.getSize());
            // No string value because we have no index
            result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
            assertEquals(0, result.getSize());

            // ...let's activate the index
            idxConf.reindexCollection();            

            // 3 numeric values
            result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
            assertEquals(3, result.getSize());
            // ... but 1 string value
            result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
            assertEquals(1, result.getSize());

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testCollectionConfigurationService3() { 
        ResourceSet result; 
        try {
            //Configure collection *manually*
            storeConfiguration(CONF_COLL_URI, CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE_URI, CONFIG1);
            
            //... then index document 
            XMLResource doc = (XMLResource)
            testCollection.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource" );
            doc.setContent(DOCUMENT_CONTENT); testCollection.storeResource(doc);
    
            XPathQueryService service = (XPathQueryService)
            testCollection.getService("XPathQueryService", "1.0");
   
            //3 numeric values 
            result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) "); 
            assertEquals(3, result.getSize()); 
            //... but 1 string value 
            result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) "); 
            assertEquals(1, result.getSize()); }
        catch(Exception e) { 
            fail(e.getMessage());             
        }
   }
    

   public void testCollectionConfigurationService4() {
       ResourceSet result;
       try {
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
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(0, result.getSize());
           // No string value because we have no index
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(0, result.getSize());

           // ...let's activate the index
           IndexQueryService idxConf = (IndexQueryService) 
               testCollection.getService("IndexQueryService", "1.0");
           idxConf.reindexCollection();            

           // 3 numeric values
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(3, result.getSize());
           // ... but 1 string value
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(1, result.getSize());

       } catch (Exception e) {
           fail(e.getMessage());
       }
   } 
   
   public void testCollectionConfigurationService5() {
       ResourceSet result;
       try {
           //Configure collection *manually*
           XmldbURI configurationFileName = XmldbURI.create("foo" + CollectionConfiguration.COLLECTION_CONFIG_SUFFIX);
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
           result = service.query("xmldb:get-child-resources('" +
                   CONF_COLL_URI +
                   "')");  
           assertEquals(configurationFileName.toString(), result.getResource(0).getContent());           
           
           // 3 numeric values
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(3, result.getSize());
           // ... but 1 string value
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(1, result.getSize());           

       } catch (Exception e) {
           fail(e.getMessage());
       }
   } 
   
   public void testCollectionConfigurationService6() {
       ResourceSet result;
       try {
           // Add document....
           XMLResource doc = (XMLResource) testCollection.createResource(
                   TestConstants.TEST_XML_URI.toString(), "XMLResource");
           doc.setContent(DOCUMENT_CONTENT);
           testCollection.storeResource(doc);

           //... then configure collection *manually*
           XmldbURI configurationFileName = XmldbURI.create("foo" + CollectionConfiguration.COLLECTION_CONFIG_SUFFIX);
           storeConfiguration(CONF_COLL_URI, configurationFileName, CONFIG1);
           
           //... then configure collection automatically 
           IndexQueryService idxConf = (IndexQueryService)
           testCollection.getService("IndexQueryService", "1.0");           
           idxConf.configureCollection(CONFIG1); 
           
           XPathQueryService service = (XPathQueryService) testCollection
           .getService("XPathQueryService", "1.0"); 
           
           //our config file
           result = service.query("xmldb:get-child-resources('" +
                   CONF_COLL_URI +
                   "')");  
           assertEquals(configurationFileName.toString(), result.getResource(0).getContent());

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
           
           // 3 numeric values
           result = service.query("util:qname-index-lookup( xs:QName(\"a\"), 1 ) ");
           assertEquals(3, result.getSize());
           // ... but 1 string value
           result = service.query("util:qname-index-lookup( xs:QName(\"b\"), \"1\" ) ");
           assertEquals(1, result.getSize());           
                 

       } catch (Exception e) {
           fail(e.getMessage());
       }
   } 
   

   public void testMultipleConfigurations00() {     	  
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_1, true);
   }
   public void testMultipleConfigurations01() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_2, false);
   }
   public void testMultipleConfigurations02() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_1, true);
   }
   public void testMultipleConfigurations03() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_2, true);
   }
   public void testMultipleConfigurations04() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_1, false);
   }
   public void testMultipleConfigurations05() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_2, true);
   }
   public void testMultipleConfigurations06() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI2, TEST_CONFIG_NAME_1, true);
   }
   public void testMultipleConfigurations07() {
       checkStoreConf(CONF_COLL_URI, TEST_CONFIG_NAME_2, CONF_COLL_URI2, TEST_CONFIG_NAME_2, true);
   }
   public void testMultipleConfigurations08() {          
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_1, true);
   }
   public void testMultipleConfigurations09() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI, TEST_CONFIG_NAME_2, true);
   }
   public void testMultipleConfigurations10() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_1, true);
   }
   public void testMultipleConfigurations11() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_1, CONF_COLL_URI2, TEST_CONFIG_NAME_2, false);
   }
   public void testMultipleConfigurations12() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_1, true);
   }
   public void testMultipleConfigurations13() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_2, CONF_COLL_URI, TEST_CONFIG_NAME_2, true);
   }
   public void testMultipleConfigurations14() {
       checkStoreConf(CONF_COLL_URI2, TEST_CONFIG_NAME_2, CONF_COLL_URI2, TEST_CONFIG_NAME_1, false);
   }
   public void testMultipleConfigurations15() {
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
       String fullCollPath = URI + collPath.toString();
   	   System.out.println("Storing configuration '" + confName + "' to '" + collPath + "'" );
   	   Collection configColl = DatabaseManager.getCollection(fullCollPath, "admin", null);
   	   if(configColl == null) {
     	   CollectionManagementService cms = (CollectionManagementService)testCollection.getService("CollectionManagementService", "1.0");
           configColl = cms.createCollection(collPath.toString());
   	   }
       assertNotNull(configColl);
       Resource res = configColl.createResource(confName.toString(), "XMLResource");
       assertNotNull(res);
       res.setContent(confContent);            
       configColl.storeResource(res);
   }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(CollectionConfigurationTest.class);        
    }
}