/*
 * Created on 17.03.2005 - $Id: XQueryFunctionsTest.java 3080 2006-04-07 22:17:14Z dizzzz $
 */
package org.exist.xquery;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.xmldb.DatabaseInstanceManager;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/** Tests for various standart XQuery functions
 * @author jens
 */
public class JavaFunctionsTest extends TestCase {

	private XPathQueryService service;
	private Collection root = null;
	private Database database = null;
	
	private boolean javabindingenabled = false;
	
	
	public static void main(String[] args) throws XPathException {
		TestRunner.run(JavaFunctionsTest.class);
	}
	
	/** Tests simple list functions to make sure java functions are being
	 * called properly
	 */
	public void testLists() throws XPathException {
		try
		{
			String query = "declare namespace list='java:java.util.ArrayList'; " +
					"let $list := list:new() "+
					"let $actions := (list:add($list,'a'),list:add($list,'b'),list:add($list,'c')) "+
					"return list:get($list,1)";
			ResourceSet result = service.query( query );
			String r = (String) result.getResource(0).getContent();
			assertEquals( "b", r );
		}
		catch (XMLDBException e)
		{
			//if exception is a java binding exception and java binding is disabled then this is a success
			if(e.getMessage().indexOf("Java binding is disabled in the current configuration") > -1 && !javabindingenabled)
			{
				return;
			}
			
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		// initialize driver
		Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
		database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);
		root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
		service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
		
		//Check the configuration file to see if Java binding is enabled
		//if it is not enabled then we expect an exception when trying to
		//perform Java binding.
		Configuration config = new Configuration();
		String javabinding = (String)config.getProperty(FunctionFactory.PROPERTY_ENABLE_JAVA_BINDING);
		if(javabinding != null)
		{
			if(javabinding.equals("yes"))
			{
				javabindingenabled = true;
			}
		}
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
		dim.shutdown();
		//System.out.println("tearDown PASSED");
	}

}
