/*
 * Created on 04.07.2005 - $Id$
 */
package org.exist.xquery.test;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/** Tests for various XQuery (XML Schema) simple types conversions.
 * @author jmvanel
 */
public class ConvertionsTest extends TestCase {

	private String[] testvalues;
	private String[] resultvalues;
	
	private XPathQueryService service;
	private Collection root = null;
	private Database database = null;
	
	public static void main(String[] args) throws XPathException {
		TestRunner.run(XQueryFunctionsTest.class);
	}
	
	/**
	 * Constructor for XQueryFunctionsTest.
	 * @param arg0
	 */
	public ConvertionsTest(String arg0) {
		super(arg0);
	}	
	

/** test conversion from QName to string
 * >>>>>> currently crashes; when you will want to solve the bug , 
 * just change bugtestQName2string in testQName2string <<<<<<<
 * org.apache.cocoon.ProcessingException: XMLDBException occurred: A QName cannot be converted to xs:string [at line 201, column 12]
In call to function:
f:eval(xs:string) [201, 12]
f:main() [224, 11]: org.xmldb.api.base.XMLDBException: A QName cannot be converted to xs:string [at line 201, column 12]
In call to function:
f:eval(xs:string) [201, 12]
f:main() [224, 11]
 */	
	public void bugtestQName2string() throws XPathException {
		ResourceSet result 	= null;
		String		r		= "";
		String		query	= null;
		try {
			query = "let $a := ( xs:QName('foo:bar'), xs:QName('foo:john'), xs:QName('foo:doe') )" +
						"for $b in $a" +
							"return" +
								"<blah>{string($b)}</blah>" ;
			result 	= service.query( query );
			/* which returns :
				<blah>foo:bar</blah>
				<blah>foo:john</blah>
				<blah>foo:doe</blah>"
			*/
			r = (String) result.getResource(0).getContent();
			assertEquals( "foo:bar", r );
			assertEquals( "XQuery: " + query, 3, result.getSize() );
			
		} catch (XMLDBException e) {
			System.out.println("testQName2string(): " + e);
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
		root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", null);
		service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
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
