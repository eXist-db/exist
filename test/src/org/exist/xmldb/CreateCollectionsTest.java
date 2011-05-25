package org.exist.xmldb;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import junit.framework.TestCase;

import org.exist.TestUtils;
import org.exist.dom.XMLUtil;
import org.exist.storage.DBBroker;
import org.exist.util.XMLFilenameFilter;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

public class CreateCollectionsTest extends TestCase {

	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	public Collection root = null;

	public CreateCollectionsTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName(DRIVER);
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

			// get root collection
			root = DatabaseManager.getCollection(URI);			
			assertNotNull(root);			
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

    protected void tearDown() throws Exception {
    	TestUtils.cleanupDB();
        root = null;
    }
    
	public void testCreateCollection() {		
		try {
			System.out.println(
				"Created Collection: "
					+ root.getName()
					+ "( "
					+ root.getClass()
					+ " )");

			Service[] services = root.getServices();
			System.out.println("services array: " + services);
			assertTrue(
				"Collection must provide at least one Service",
				services != null && services.length > 0);
			System.out.println("  number of services: " + services.length);
			for (int i = 0; i < services.length; i++) {
				System.out.println(
					"  Service: "
						+ services[i].getName()
						+ "( "
						+ services[i].getClass()
						+ " )");
			}

			Collection parentCollection = root.getParentCollection();
			System.out.println("root parentCollection: " + parentCollection);
			assertNull("root collection has no parent", parentCollection);

			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			assertNotNull(service);
			Collection testCollection = service.createCollection("test");
			assertNotNull(testCollection);
			int ccc = testCollection.getChildCollectionCount();
			assertTrue(
				"Collection just created: ChildCollectionCount==0",
				ccc == 0);
			assertTrue(
				"Collection state should be Open after creation",
				testCollection.isOpen());

			String directory = "samples/shakespeare";
			System.out.println("---------------------------------------");
			System.out.println("storing all XML files in directory " +directory+"...");
			System.out.println("---------------------------------------");
                        String existHome = System.getProperty("exist.home");
                        File existDir = existHome==null ? new File(".") : new File(existHome);
			File f = new File(existDir,directory);
			File files[] = f.listFiles(new XMLFilenameFilter());

			for (int i = 0; i < files.length; i++) {
				storeResourceFromFile(files[i], testCollection);
			}

			HashSet fileNamesJustStored = new HashSet();
			for (int i = 0; i < files.length; i++) {
				String file = files[i].toString();
				int lastSeparator = file.lastIndexOf(File.separatorChar);
				fileNamesJustStored.add(file.substring(lastSeparator + 1));
			}
			System.out.println("fileNames stored: " + fileNamesJustStored.toString());

			String[] resourcesNames = testCollection.listResources();
			int resourceCount = testCollection.getResourceCount();
			System.out.println(  "testCollection.getResourceCount()=" + resourceCount);

			ArrayList fileNamesPresentInDatabase = new ArrayList();
			for (int i = 0; i < resourcesNames.length; i++) {
				fileNamesPresentInDatabase.add( resourcesNames[i]);
			}
			assertTrue( "resourcesNames must contain fileNames just stored",
					fileNamesPresentInDatabase. containsAll( fileNamesJustStored) );

			String fileToRemove = "macbeth.xml";
			Resource resMacbeth = testCollection.getResource(fileToRemove);
			assertNotNull("getResource(" + fileToRemove + "\")", resMacbeth);
			testCollection.removeResource(resMacbeth);
			assertTrue(
				"After removal resource count must decrease",
				testCollection.getResourceCount() == resourceCount - 1);
			// restore the resource just removed :
			storeResourceFromFile(
				new File(existDir,
					directory + File.separatorChar + fileToRemove),
				testCollection);

			byte[] data = storeBinaryResourceFromFile( new File( existDir,"webapp/logo.jpg"), testCollection);
			Object content = testCollection.getResource("logo.jpg").getContent();
			byte[] dataStored = (byte[])content;
			assertTrue("After storing binary resource, data out==data in", 
					Arrays.equals(dataStored, data) );
			
		} catch (Exception e) {			
			fail(e.getMessage());
		}
	}

	private XMLResource storeResourceFromFile(File file, Collection testCollection) {
		XMLResource res = null;
		try {
			System.out.println("storing " + file.getAbsolutePath());			
			String xml;
			res = (XMLResource) testCollection.createResource(file.getName(), "XMLResource");
			assertNotNull("storeResourceFromFile", res);
			xml = XMLUtil.readFile(file, "UTF-8");
			res.setContent(xml);
			testCollection.storeResource(res);
			System.out.println("stored " + file.getAbsolutePath());			
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return res;
	}

	private byte[] storeBinaryResourceFromFile(File file, Collection testCollection) {
		byte[] data = null;
		try {
			System.out.println("storing " + file.getAbsolutePath());

			Resource res = (BinaryResource)testCollection.createResource(file.getName(), "BinaryResource");
			assertNotNull("store binary Resource From File", res);
			
			// Get an array of bytes from the file:
			 FileInputStream istr = new FileInputStream(file); 
			 BufferedInputStream bstr = new BufferedInputStream( istr ); // promote
			 int size = (int) file.length();  // get the file size (in bytes)
			 data = new byte[size]; // allocate byte array of right size
			 bstr.read( data, 0, size );   // read into byte array
			 bstr.close();
			 
			res.setContent(data);
			testCollection.storeResource(res);
			System.out.println("stored " + file.getAbsolutePath());
        } catch (Exception e) {
            fail(e.getMessage());
        }			
		return data;
	}
	
	public void testMultipleCreates() {
		try {
        	Collection rootColl = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION);
        	CollectionManagementService cms = (CollectionManagementService)
				rootColl.getService("CollectionManagementService", "1.0");
			assertNotNull(cms);
        	cms.createCollection("dummy1");
        	printChildren(rootColl);
        	Collection c1 = rootColl.getChildCollection("dummy1");
			assertNotNull(c1);
        	cms.setCollection(c1);
        	cms.createCollection("dummy2");
        	Collection c2 = c1.getChildCollection("dummy2");
			assertNotNull(c2);
        	cms.setCollection(c2);
        	cms.createCollection("dummy3");
        	Collection c3 = c2.getChildCollection("dummy3");
			assertNotNull(c3);
        	cms.setCollection(rootColl);
        	cms.removeCollection("dummy1");
        	printChildren(rootColl);
        	c1 = rootColl.getChildCollection("dummy1");
        	assertNull(c1);
		} catch(Exception e) {			
			fail(e.getMessage());
		}
	}

    private static void printChildren(Collection c) {
        try{
        	System.out.print("Children of " + c.getName() + ":");	        
	        String[] names = c.listChildCollections();
	        for (int i = 0; i < names.length; i++)
	            System.out.print(" " + names[i]);
	        System.out.println();
		} catch(Exception e) {			
			fail(e.getMessage());
		}	        
    }
    
	public static void main(String[] args) {
		junit.textui.TestRunner.run(CreateCollectionsTest.class);
	}
}
