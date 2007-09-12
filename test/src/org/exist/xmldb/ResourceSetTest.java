// $Header$

package org.exist.xmldb;

import junit.framework.TestCase;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.exist.storage.DBBroker;

public class ResourceSetTest extends TestCase {

		String XPathPrefix;
		String query1;
		String query2;
	    int expected;
		private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    /** JUnit style constructor */
	public ResourceSetTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName(DRIVER);
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (XMLDBException e) {
			// TODO Auto-generated catch block
			fail(e.getMessage());
		}

        // Currently (2003-04-02) fires an exception in FunPosition:
        XPathPrefix = "xmldb:document('" + DBBroker.ROOT_COLLECTION + "/test/shakes.xsl')/*/*"; // "xmldb:document('" + DBBroker.ROOT_COLLECTION + "/test/macbeth.xml')/*/*";
   		query1 = XPathPrefix + "[position()>=5 ]";
   		query2 = XPathPrefix + "[position()<=10]";
        expected = 87;
// This validates OK:
//   XPathPrefix = "xmldb:document('" +  DBBroker.ROOT_COLLECTION + "/test/hamlet.xml')//LINE";
//		query1 = XPathPrefix + "[ .&='funeral' ]";		// count=4
//		query2 = XPathPrefix + "[.&='dirge']";		// count=1, intersection=1
//		expected = 1;
	}

	public void testIntersection() {
		try {
			// try to get collection
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XPathQueryService service = (XPathQueryService)
				testCollection.getService("XPathQueryService", "1.0");
				
			System.out.println("query1: " + query1);
			ResourceSet result1 = service.query(query1);
			System.out.println("query1: getSize()=" + result1.getSize());
			
			System.out.println("query2: " + query2);
			ResourceSet result2 = service.query(query2);
			System.out.println("query2: getSize()=" + result2.getSize());
			
			assertEquals( "size of intersection of "+query1+" and "+query2+" yields ",
			  expected,
			  ( ResourceSetHelper.intersection(result1, result2) ).getSize() );
		} catch(XMLDBException e) {
			fail(e.getMessage());
		}
	}
	/* useless if we catch the relevant exception in the debugger:
	public static void main(String args[]){
		ResourceSetTest t = new ResourceSetTest("ResourceSetTest");
		t.setUp();
		t.testIntersection();
	} ****/
}
