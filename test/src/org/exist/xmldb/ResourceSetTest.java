// $Header$

package org.exist.xmldb;

import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ResourceSetTest {

	String XPathPrefix;
	String query1;
	String query2;
    int expected;
	private final static String URI = XmldbURI.LOCAL_DB;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	@Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
		// initialize driver
		Class<?> cl = Class.forName(DRIVER);
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);

        // Currently (2003-04-02) fires an exception in FunPosition:
        XPathPrefix = "xmldb:document('" + XmldbURI.ROOT_COLLECTION + "/test/shakes.xsl')/*/*"; // "xmldb:document('" + DBBroker.ROOT_COLLECTION + "/test/macbeth.xml')/*/*";
   		query1 = XPathPrefix + "[position()>=5 ]";
   		query2 = XPathPrefix + "[position()<=10]";
        expected = 87;
// This validates OK:
//   XPathPrefix = "xmldb:document('" +  DBBroker.ROOT_COLLECTION + "/test/hamlet.xml')//LINE";
//		query1 = XPathPrefix + "[ .&='funeral' ]";		// count=4
//		query2 = XPathPrefix + "[.&='dirge']";		// count=1, intersection=1
//		expected = 1;
	}

    @Test
	public void intersection() throws XMLDBException {
        // try to get collection
        Collection testCollection =
            DatabaseManager.getCollection(URI + "/test");
        assertNotNull(testCollection);
        XPathQueryService service = (XPathQueryService)
            testCollection.getService("XPathQueryService", "1.0");

        ResourceSet result1 = service.query(query1);
        ResourceSet result2 = service.query(query2);

        assertEquals( "size of intersection of "+query1+" and "+query2+" yields ",
          expected,
          ( ResourceSetHelper.intersection(result1, result2) ).getSize() );
	}
	/* useless if we catch the relevant exception in the debugger:
	public static void main(String args[]){
		ResourceSetTest t = new ResourceSetTest("ResourceSetTest");
		t.setUp();
		t.testIntersection();
	} ****/
}
