/*
 * Created on 17.03.2005 - $Id$
 */
package org.exist.xquery.test;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;
import java.text.*;
import java.util.*;

/** Tests for various standart XQuery functions
 * @author jens
 */
public class XQueryFunctionsTest extends TestCase {

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
	public XQueryFunctionsTest(String arg0) {
		super(arg0);
	}
	
	/** Tests the XQuery-/XPath-function fn:round-half-to-even
	 * with the rounding value typed xs:integer
	 */
	public void testRoundHtE_INTEGER() throws XPathException {
		ResourceSet result 		= null;
		String 		query		= null;
		String		r			= "";
		try {
			
			query 	= "fn:round-half-to-even( xs:integer('1'), 0 )";
			result 	= service.query( query );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "1", r );
			
			query 	= "fn:round-half-to-even( xs:integer('6'), -1 )";
			result 	= service.query( query );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "10", r );

			query 	= "fn:round-half-to-even( xs:integer('5'), -1 )";
			result 	= service.query( query );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "0", r );
		
		} catch (XMLDBException e) {
			System.out.println("testRoundHtE_INTEGER(): "+e);
			fail(e.getMessage());
		}
	}
	
	/** Tests the XQuery-/XPath-function fn:round-half-to-even
	 * with the rounding value typed xs:double
	 */
	public void testRoundHtE_DOUBLE() throws XPathException {
		/* List of Values to test with Rounding */
		String[] testvalues 	= 
			{ "0.5", "1.5", "2.5", "3.567812E+3", "4.7564E-3", "35612.25" };
		String[] resultvalues	= 
			{ "0.0", "2.0", "2.0", "3567.81",     "0.0",       "35600.0"    };
		int[]	 precision      = 
			{ 0,     0,     0,     2,             2,           -2         };
		
		ResourceSet result 		= null;
		String 		query		= null;
		
		try {
			XPathQueryService service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
			for (int i=0; i<testvalues.length; i++) {
				query = "fn:round-half-to-even( xs:double('" + testvalues[i] + "'), " + precision[i] + " )";
				result = service.query( query );
				String r = (String) result.getResource(0).getContent();
				assertEquals( resultvalues[i], r );
			}
		} catch (XMLDBException e) {
			System.out.println("testRoundHtE_DOUBLE(): "+e);
			fail(e.getMessage());
		}
	}
	
	/** Tests the XQuery-XPath function fn:tokenize() */
	public void testTokenize() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {
			result 	= service.query( "count ( tokenize('a/b' , '/') )" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2", r );
			
			result 	= service.query( "count ( tokenize('a/b/' , '/') )" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "3", r );
			
			result 	= service.query( "count ( tokenize('' , '/') )" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "0", r );
			
			result 	= service.query(
				"let $res := fn:tokenize('abracadabra', '(ab)|(a)')" +
				"let $reference := ('', 'r', 'c', 'd', 'r', '')" +
				"return fn:deep-equal($res, $reference)" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "true", r );
			
		} catch (XMLDBException e) {
			System.out.println("testTokenize(): " + e);
			fail(e.getMessage());
		}
	}
	
	public void testDeepEqual() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {	
			result 	= service.query(
			"let $res := ('a', 'b')" +
			"let $reference := ('a', 'b')" +
			"return fn:deep-equal($res, $reference)" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "true", r );
		} catch (XMLDBException e) {
			System.out.println("testTokenize(): " + e);
			fail(e.getMessage());
		}
	}
	
	public void testCompare() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {	
			result 	= service.query("fn:compare(\"Strasse\", \"Stra\u00DFe\")");
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "-1", r );
			//result 	= service.query("fn:compare(\"Strasse\", \"Stra\u00DFe\", \"java:GermanCollator\")");
			//r 		= (String) result.getResource(0).getContent();
			//assertEquals( "0", r );			
		} catch (XMLDBException e) {
			System.out.println("testTokenize(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testDistinctValues() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {
			result 	= service.query( "declare variable $c { distinct-values(('a', 'a')) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "a", r );	
			
			result 	= service.query( "declare variable $c { distinct-values((<a>a</a>, <b>a</b>)) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "a", r );	
            
            result  = service.query( "let $seq := ('A', 2, 'B', 2) return distinct-values($seq) " );      
            assertEquals( 3, result.getSize() ); 
		
		} catch (XMLDBException e) {
			System.out.println("testTokenize(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testSum() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {
			result 	= service.query( "declare variable $c { sum((1, 2)) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "3", r );	
			
			result 	= service.query( "declare variable $c { sum((<a>1</a>, <b>2</b>)) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			//Any untyped atomic values in the sequence are converted to xs:double values ([MK Xpath 2.0], p. 432)
			assertEquals( "3.0", r );	
			
			result 	= service.query( "declare variable $c { sum((), 3) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "3", r );			
			

		} catch (XMLDBException e) {
			System.out.println("testTokenize(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testAvg() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {
			result 	= service.query( "declare variable $c { avg((2, 2)) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2", r );	
			
			result 	= service.query( "declare variable $c { avg((<a>2</a>, <b>2</b>)) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			//Any untyped atomic values in the resulting sequence 
			//(typically, values extracted from nodes in a schemaless document)
			//are converted to xs:double values ([MK Xpath 2.0], p. 301)
			assertEquals( "2.0", r );	
			
			result 	= service.query( "declare variable $c { avg(()) }; $c" );		
			assertEquals( 0, result.getSize());				

		} catch (XMLDBException e) {
			System.out.println("testTokenize(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testMin() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";		
		String message;					
		try {
			result 	= service.query("min((1, 2))");
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "1", r );	
			
			result 	= service.query("min((<a>1</a>, <b>2</b>))");
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "1", r );	
			
			result 	= service.query("min(())");		
			assertEquals( 0, result.getSize());	
			
			result 	= service.query("min((xs:dateTime('2005-12-19T16:22:40.006+01:00'), xs:dateTime('2005-12-19T16:29:40.321+01:00')))");		
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2005-12-19T16:22:40.006+01:00", r );	
			
			result 	= service.query("min(('a', 'b'))");		
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "a", r );	
			
			try {
				message = "";
				result 	= service.query("min((xs:dateTime('2005-12-19T16:22:40.006+01:00'), 'a'))");	
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("FORG0006") > -1);
            
			try {
				message = "";
				result 	= service.query("min(1, 2)");	
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("FOCH0002") > -1);		            
			
		} catch (XMLDBException e) {
			System.out.println("testMin(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testMax() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		String message;		
		try {
			result 	= service.query( "max((1, 2))" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2", r );	
			
			result 	= service.query( "max((<a>1</a>, <b>2</b>))" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2", r );	
			
			result 	= service.query( "max(())" );		
			assertEquals( 0, result.getSize());	
			
			result 	= service.query("max((xs:dateTime('2005-12-19T16:22:40.006+01:00'), xs:dateTime('2005-12-19T16:29:40.321+01:00')))");		
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2005-12-19T16:29:40.321+01:00", r );	
			
			result 	= service.query("max(('a', 'b'))");		
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "b", r );	
			
			try {
				message = "";
				result 	= service.query("max((xs:dateTime('2005-12-19T16:22:40.006+01:00'), 'a'))");	
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("FORG0006") > -1);
            
			try {
				message = "";
				result 	= service.query("max(1, 2)");	
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("FOCH0002") > -1);				
			

		} catch (XMLDBException e) {
			System.out.println("testMax(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testExclusiveLock() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {
			String query = "let $query1 := (<a/>)\n" +
				"let $query2 := (2, 3)\n" +
				"let $a := util:exclusive-lock(//*,($query1, $query2))\n" +
				"return $a";
			result 	= service.query( query );			
			assertEquals( 3, result.getSize());	
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<a/>", r );	
			r 		= (String) result.getResource(1).getContent();
			assertEquals( "2", r );	
			r 		= (String) result.getResource(2).getContent();
			assertEquals( "3", r );	
		
			query = "let $query1 := (<a/>)\n" +
				"let $query2 := (2, 3)\n" +
				"let $a := util:exclusive-lock((),($query1, $query2))\n" +
				"return $a";
			result 	= service.query( query );			
			assertEquals( 3, result.getSize());	
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<a/>", r );	
			r 		= (String) result.getResource(1).getContent();
			assertEquals( "2", r );	
			r 		= (String) result.getResource(2).getContent();
			assertEquals( "3", r );	
			
			query = "let $query1 := (<a/>)\n" +
			"let $query2 := (2, 3)\n" +
			"let $a := util:exclusive-lock((),($query1, $query2))\n" +
			"return $a";
			result 	= service.query( query );			
			assertEquals( 3, result.getSize());	
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<a/>", r );	
			r 		= (String) result.getResource(1).getContent();
			assertEquals( "2", r );	
			r 		= (String) result.getResource(2).getContent();
			assertEquals( "3", r );		
		
			query = "let $a := util:exclusive-lock(//*,<root/>)\n" +
				"return $a";
			result 	= service.query( query );			
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<root/>", r );					
			
		} catch (XMLDBException e) {
			System.out.println("testExclusiveLock(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testSharedLock() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {
			String query = "let $query1 := (<a/>)\n" +
				"let $query2 := (2, 3)\n" +
				"let $a := util:shared-lock(//*,($query1, $query2))\n" +
				"return $a";
			result 	= service.query( query );			
			assertEquals( 3, result.getSize());	
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<a/>", r );	
			r 		= (String) result.getResource(1).getContent();
			assertEquals( "2", r );	
			r 		= (String) result.getResource(2).getContent();
			assertEquals( "3", r );	
			
			query = "let $query1 := (<a/>)\n" +
				"let $query2 := (2, 3)\n" +
				"let $a := util:shared-lock((),($query1, $query2))\n" +
				"return $a";
			result 	= service.query( query );			
			assertEquals( 3, result.getSize());	
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<a/>", r );	
			r 		= (String) result.getResource(1).getContent();
			assertEquals( "2", r );	
			r 		= (String) result.getResource(2).getContent();
			assertEquals( "3", r );				
			
			query = "let $query1 := (<a/>)\n" +
				"let $query2 := (2, 3)\n" +
				"let $a := util:shared-lock((),($query1, $query2))\n" +
				"return $a";
			result 	= service.query( query );			
			assertEquals( 3, result.getSize());	
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<a/>", r );	
			r 		= (String) result.getResource(1).getContent();
			assertEquals( "2", r );	
			r 		= (String) result.getResource(2).getContent();
			assertEquals( "3", r );	
			
			query = "let $a := util:shared-lock(//*,<root/>)\n" +
				"return $a";
			result 	= service.query( query );			
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "<root/>", r );	
			
		} catch (XMLDBException e) {
			System.out.println("testSharedLock(): " + e);
			fail(e.getMessage());
		}
	}	
	
	
	public void testEncodeForURI() {
		ResourceSet result 		= null;
		String		r			= "";
		String string;
		String expected;
		String query;
		try {
			string = "http://www.example.com/00/Weather/CA/Los%20Angeles#ocean";
			expected = "http%3A%2F%2Fwww.example.com%2F00%2FWeather%2FCA%2FLos%2520Angeles#ocean";
			query = "encode-for-uri(\"" + string + "\")";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals(expected, r);
			
			string = "~b\u00e9b\u00e9";
			expected = "~b%C3%A9b%C3%A9";
			query = "encode-for-uri(\"" + string + "\")";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals(expected, r);	
			
			string = "100% organic";
			expected = "100%25%20organic";
			query = "encode-for-uri(\"" + string + "\")";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals(expected, r);				
		} catch (XMLDBException e) {
			System.out.println("testEncodeForURI(): " + e);
			fail(e.getMessage());
		}			
	}
	
	public void testIRIToURI() {
		ResourceSet result 		= null;
		String		r			= "";
		String string;
		String expected;
		String query;		
		try {
			string = "http://www.example.com/00/Weather/CA/Los%20Angeles#ocean";
			expected = "http://www.example.com/00/Weather/CA/Los%20Angeles#ocean";
			query = "iri-to-uri(\"" + string + "\")";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals(expected, r);
			
			
			string = "http://www.example.com/~b\u00e9b\u00e9";
			expected = "http://www.example.com/~b%C3%A9b%C3%A9";
			query = "iri-to-uri(\"" + string + "\")";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals(expected, r);			
		} catch (XMLDBException e) {
			System.out.println("testIRIToURI(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testEscapeHTMLURI() {
		ResourceSet result 		= null;
		String		r			= "";
		String string;
		String expected;
		String query;	
		try {
			string = "http://www.example.com/00/Weather/CA/Los Angeles#ocean";
			expected = "http://www.example.com/00/Weather/CA/Los Angeles#ocean";
			query = "escape-html-uri(\"" + string + "\")";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals(expected, r);
			
			string = "javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~b\u00e9b\u00e9');";
			expected = "javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~b%C3%A9b%C3%A9');";
			query = "escape-html-uri(\"" + string + "\")";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals(expected, r);			
			
		} catch (XMLDBException e) {
			System.out.println("EscapeHTMLURI(): " + e);
			fail(e.getMessage());
		}		
	}		
	
	public void testLocalName() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {	
			result 	= service.query(
			"let $a := <a><b></b></a>" +
			"return fn:local-name($a)" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "a", r );
		} catch (XMLDBException e) {
			System.out.println("local-name(): " + e);
			fail(e.getMessage());
		}
	}
    
    public void testCurrentDateTime() throws XPathException {
        ResourceSet result      = null;
        String      r           = "";
        try {   
            //Do not use this test around midnight on the last day of a month ;-)
            result  = service.query(
            "('Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', " +
            "'Oct', 'Nov', 'Dec')[month-from-dateTime(current-dateTime())]");
            r       = (String) result.getResource(0).getContent();
            SimpleDateFormat df = new SimpleDateFormat("MMM", new Locale("en", "US"));
            Date date = new Date();
            assertEquals(df.format(date), r );
        } catch (XMLDBException e) {
            System.out.println("current-dateTime(): " + e);
            fail(e.getMessage());
        }
    }
    //ensure the test collection is removed and call collection-exists,
    //which should return false, no exception thrown
    public void testCollectionExists1() {
    	//remove the test collection if it already exists
    	String collectionName = "testCollectionExists";
    	String collectionPath = DBBroker.ROOT_COLLECTION + "/" + collectionName;
    	try
		{
			Collection testCollection = root.getChildCollection(collectionName);
			if(testCollection != null)
			{
				CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
				cms.removeCollection(collectionPath);
			}
		}
		catch (XMLDBException e)
		{
			System.err.println("Error removing existing test collection:");
			e.printStackTrace();
			fail(e.getMessage());
		}

		runCollectionExistsTest(collectionPath, false);
    }
    //create a collection and call collection-exists, which should return true,
    //no exception thrown
    public void testCollectionExists2() {
    	//add the test collection
    	String collectionName = "testCollectionExists";
    	String collectionPath = DBBroker.ROOT_COLLECTION + "/" + collectionName;
    	try
		{
    		Collection testCollection = root.getChildCollection(collectionName);
			if(testCollection == null)
			{
				CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
				cms.createCollection(collectionPath);
			}
		} catch(XMLDBException xe) {
			System.err.println("Error determining if test collection already exists:");
			xe.printStackTrace();
			fail(xe.getMessage());
		}
		runCollectionExistsTest(collectionPath, true);
    }
    private void runCollectionExistsTest(String collectionPath, boolean expectedResult) {
    	//collection-exists should not throw an exception and should return expectedResult
    	String importXMLDB = "import module namespace xmldb=\"http://exist-db.org/xquery/xmldb\";";
    	String collectionExists = "xmldb:collection-exists('" + collectionPath + "')";
    	String query = importXMLDB + collectionExists;
    	try {
    		ResourceSet result = service.query(query);
    		assertNotNull(result);
    		assertTrue(result.getSize() == 1);
    		assertNotNull(result.getResource(0));
    		String content = (String)result.getResource(0).getContent();
    		assertNotNull(content);
    		assertEquals(expectedResult, Boolean.valueOf(content).booleanValue());
    	} catch(XMLDBException xe) {
    		System.err.println("Error calling xmldb:collection-exists:");
    		xe.printStackTrace();
    		fail(xe.getMessage());
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
