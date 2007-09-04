package org.exist.xmldb;

import junit.framework.TestCase;

import org.exist.collections.CollectionConfiguration;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants;
import org.exist.test.TestConstants;
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
    
    private final static XmldbURI COLLECTION_SUB1 = TEST_COLLECTION.append("sub1");
    private final static XmldbURI COLLECTION_SUB2 = COLLECTION_SUB1.append("sub2");

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

    private final static String DOCUMENT_CONTENT2 = "<test x='0'>" + "<c c='2002-12-07T12:20:46.275+01:00'>2002-12-07T12:20:46.275+01:00</c>"
    + "<d d='1'>1</d>" + "<e e='1'>1</e>" + "<f f='true'>true</f>" +" <g g='1'>1</g>" +"<h h='1'>1</h>" 
    + "<test x='1'><test x='2'></test></test></test>";

    
    private String CONFIG1 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create qname=\"a\" type=\"xs:integer\"/>"
        + "    <create qname=\"b\" type=\"xs:string\"/>"
        + "    <create path=\"//a\" type=\"xs:integer\"/>"
        + "    <create path=\"//b\" type=\"xs:string\"/>"
        + "  </index>"
        + "</collection>";

    private String CONFIG2 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
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

    private String CONFIG3 = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
        + "    <create qname=\"a\" type=\"xs:integer\"/>"
        + "    <create path=\"//a\" type=\"xs:integer\"/>"
        + "  </index>"
        + "</collection>";

    private String EMPTY_CONFIG = "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "  <index>"
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
            Collection confCol = DatabaseManager.getCollection(URI + CONF_COLL_URI, "admin", null);
            if (confCol != null)
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
            result = service.query("util:index-key-occurrences(/test/a, 1)"); 
            assertEquals("3", result.getResource(0).getContent()); 
            //... but 1 string value 
            result = service.query("util:index-key-occurrences(/test/b, \"1\")"); 
            assertEquals("1", result.getResource(0).getContent());
            
        	//3 numeric values 
            result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) "); 
            assertEquals(3, result.getSize()); 
            //... but 1 string value 
            result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) "); 
            assertEquals(1, result.getSize()); }
        catch(Exception e) { 
       	 e.printStackTrace();
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

        } catch (Exception e) {
        	e.printStackTrace();
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
            result = service.query("util:index-key-occurrences(/test/a, 1)");
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
            assertEquals(1, result.getSize()); }
        catch(Exception e) {
            e.printStackTrace();
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
                 

       } catch (Exception e) {
           fail(e.getMessage());
       }
   }

    /** Check if configurations are properly passed down the collection hierarchy. */
    public void testCollectionConfigurationService7() {
        ResourceSet result;
        try {
            CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
            Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());

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
            result = service.query("util:index-key-occurrences(/test/a, 1)");
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
        catch(Exception e) {
       	 e.printStackTrace();
            fail(e.getMessage());
        }
   }

    /** Overwrite configuration in a sub collection */
    public void testCollectionConfigurationService8() {
        ResourceSet result;
        try {
            CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
            Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());
            
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
            result = service.query("util:index-key-occurrences(/test/a, 1)");
            assertEquals(0, result.getSize());
            result = service.query("util:index-key-occurrences(/test/b, \"1\")");
            assertEquals(0, result.getSize());

            result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
            assertEquals(0, result.getSize());
            result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
            assertEquals(0, result.getSize());
        }
        catch(Exception e) {
       	 e.printStackTrace();
            fail(e.getMessage());
        }
   }

    /** Overwrite configuration in a sub collection 2 times */
    public void testCollectionConfigurationService9() {
        ResourceSet result;
        try {
            CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
            Collection sub1 = cms.createCollection(COLLECTION_SUB1.toString());
            Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());

            IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
            idxConf.configureCollection(CONFIG1);

            // Overwrite main configuration with an empty configuration in the subcollection
            idxConf = (IndexQueryService) sub1.getService("IndexQueryService", "1.0");
            idxConf.configureCollection(EMPTY_CONFIG);

            // Overwrite sub1 configuration in sub2
            idxConf = (IndexQueryService) sub2.getService("IndexQueryService", "1.0");
            idxConf.configureCollection(CONFIG3);

            //... then store document into sub1
            XMLResource doc = (XMLResource)
                    sub1.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
            doc.setContent(DOCUMENT_CONTENT);
            sub1.storeResource(doc);

            XPathQueryService service = (XPathQueryService)
                    sub1.getService("XPathQueryService", "1.0");

            // sub1 has empty configuration, so index should be empty as well
            result = service.query("util:index-key-occurrences(/test/a, 1)");
            assertEquals(0, result.getSize());
            result = service.query("util:index-key-occurrences(/test/b, \"1\")");
            assertEquals(0, result.getSize());

            result = service.query("util:qname-index-lookup(xs:QName(\"a\"), 1 ) ");
            assertEquals(0, result.getSize());
            result = service.query("util:qname-index-lookup(xs:QName(\"b\"), \"1\" ) ");
            assertEquals(0, result.getSize());

            // remove document in sub1 and restore it in sub2
            sub1.removeResource(doc);
            doc = (XMLResource)
                    sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
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
        catch(Exception e) {
       	 e.printStackTrace();
            fail(e.getMessage());
        }
   }

    /** Remove config document */
    public void testCollectionConfigurationService10() {
        ResourceSet result;
        try {
            CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
            Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());

            IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
            idxConf.configureCollection(CONFIG1);

            //... then index document
            XMLResource doc = (XMLResource)
                    sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
            doc.setContent(DOCUMENT_CONTENT);
            sub2.storeResource(doc);

            XPathQueryService service = (XPathQueryService) sub2.getService("XPathQueryService", "1.0");

            //3 numeric values
            result = service.query("util:index-key-occurrences(/test/a, 1)");
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
            Collection confCol = DatabaseManager.getCollection(URI + XmldbURI.CONFIG_COLLECTION_URI.append(TEST_COLLECTION), "admin", null);
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
        catch(Exception e) {
       	 e.printStackTrace();
            fail(e.getMessage());
        }
   }

    /** Remove config collection */
    public void testCollectionConfigurationService11() {
        ResourceSet result;
        try {
            CollectionManagementService cms = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
            Collection sub2 = cms.createCollection(COLLECTION_SUB2.toString());

            IndexQueryService idxConf = (IndexQueryService) testCollection.getService("IndexQueryService", "1.0");
            idxConf.configureCollection(CONFIG1);

            //... then index document
            XMLResource doc = (XMLResource)
                    sub2.createResource(TestConstants.TEST_XML_URI.toString(), "XMLResource");
            doc.setContent(DOCUMENT_CONTENT);
            sub2.storeResource(doc);

            XPathQueryService service = (XPathQueryService) sub2.getService("XPathQueryService", "1.0");

            //3 numeric values
            result = service.query("util:index-key-occurrences(/test/a, 1)");
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
            Collection confCol = DatabaseManager.getCollection(URI + XmldbURI.CONFIG_COLLECTION_URI.append(TEST_COLLECTION), "admin", null);
            cms = (CollectionManagementService) confCol.getService("CollectionManagementService", "1.0");
            cms.removeCollection(".");

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
        catch(Exception e) {
       	 e.printStackTrace();
            fail(e.getMessage());
        }
   }

   public void testRangeIndex1() { 
       ResourceSet result; 
       try {
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
           
           result = service.query("util:index-key-occurrences(/test/c, xs:dateTime(\"2002-12-07T12:20:46.275+01:00\") )");
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
       
       } catch(Exception e) { 
      	 	e.printStackTrace();
           fail(e.getMessage());             
       }
  }   
   
   public void testRangeIndexOverAttributes() { 
       ResourceSet result; 
       try {
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
          
           result = service.query("//test[@x = 0]");
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
       
       } catch(Exception e) { 
      	 	e.printStackTrace();
           fail(e.getMessage());             
       }
  }   
   
   public void testMissingRangeIndexes() { 
       ResourceSet result; 
       boolean exceptionThrown = false;
       try {
           //Configure collection automatically
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
                          
       } catch(Exception e) { 
      	 	e.printStackTrace();
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