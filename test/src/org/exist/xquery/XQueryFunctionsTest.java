/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2010 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XPathQueryService;

/** Tests for various standard XQuery functions
 * @author jens
 * @author perig
 * @author wolf
 * @author adam
 * @author dannes
 * @author dmitriy
 * @author ljo
 * @author chrisdutz
 * @author harrah
 * @author gvalentino
 * @author jmvanel

 */
public class XQueryFunctionsTest extends TestCase {

	@SuppressWarnings("unused")
	private String[] testvalues;
	@SuppressWarnings("unused")
	private String[] resultvalues;
	
	private XPathQueryService service;
	private Collection root = null;
	private Database database = null;
    private final static String ROOT_COLLECTION_URI = "xmldb:exist:///db";
	
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
	
	public void testArguments() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {
			result 	= service.query( "declare function local:testAnyURI($uri as xs:string) as xs:string { " +
					"concat('Successfully processed as xs:string : ',$uri) " +
					"}; " +
					"let $a := xs:anyURI('http://exist.sourceforge.net/') " +
					"return local:testAnyURI($a)" );
			assertEquals(1,result.getSize());
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "Successfully processed as xs:string : http://exist.sourceforge.net/", r );	
			
			
			result 	= service.query( "declare function local:testEmpty($blah as xs:string)  as element()* { " +
					"for $a in (1,2,3) order by $a " +
					"return () " +
					"}; " +
					"local:testEmpty('test')" );
			assertEquals(0,result.getSize());

		} catch (XMLDBException e) {
			System.out.println("testSum(): " + e);
			fail(e.getMessage());
		}
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
			{ "0", "2", "2", "3567.81",     "0",       "35600"    };
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
			System.out.println("testDeepEqual(): " + e);
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
			
	        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[compare(., '+') gt 0]";        
	        result = service.query(query);          
	        assertEquals(2, result.getSize());			
			
			
		} catch (XMLDBException e) {
			System.out.println("testCompare(): " + e);
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
            
            String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[distinct-values(.)]";
            result = service.query(query);          
            assertEquals(2, result.getSize());
		
		} catch (XMLDBException e) {
			System.out.println("testDistinctValues(): " + e);
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
			assertEquals( "3", r );	
			
			result 	= service.query( "declare variable $c { sum((), 3) }; $c" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "3", r );			
			

		} catch (XMLDBException e) {
			System.out.println("testSum(): " + e);
			fail(e.getMessage());
		}
	}	
	
	public void testAvg() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		String message;
		try {
			result 	= service.query( "avg((2, 2))" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2", r );	
			
			result 	= service.query( "avg((<a>2</a>, <b>2</b>))" );
			r 		= (String) result.getResource(0).getContent();
			//Any untyped atomic values in the resulting sequence 
			//(typically, values extracted from nodes in a schemaless document)
			//are converted to xs:double values ([MK Xpath 2.0], p. 301)
			assertEquals( "2", r );
			
			result 	= service.query( "avg((3, 4, 5))" );
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "4", r );	
			
			result 	= service.query( "avg((xdt:yearMonthDuration('P20Y'), xdt:yearMonthDuration('P10M')))");
			r 		= (String) result.getResource(0).getContent();
			assertEquals("P10Y5M", r );
			
			try {
				message = "";
				result 	= service.query( "avg((xdt:yearMonthDuration('P20Y') , (3, 4, 5)))");
			} catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("FORG0006") > -1);
            
			result 	= service.query("avg(())");		
			assertEquals( 0, result.getSize());	
			
			result 	= service.query( "avg(((xs:float('INF')), xs:float('-INF')))");
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "NaN", r );
			
			result 	= service.query( "avg(((3, 4, 5), xs:float('NaN')))");
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "NaN", r );

		} catch (XMLDBException e) {
			e.printStackTrace();
			System.out.println("testAvg(): " + e);
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
            //depends whether we have strict type checking or not
            assertTrue(message.indexOf("XPTY0004") > -1 | message.indexOf("FORG0001") > -1 | message.indexOf("FOCH0002") > -1);		            
			
		} catch (XMLDBException e) {
			e.printStackTrace();
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
            //depends whether we have strict type checking or not
            assertTrue(message.indexOf("XPTY0004") > -1 | message.indexOf("FORG0001") > -1 | message.indexOf("FOCH0002") > -1);		            

		} catch (XMLDBException e) {
			e.printStackTrace();
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
			e.printStackTrace();
			fail(e.getMessage());
		}
	}	
	
	public void bugtestUtilEval1() throws XPathException {
		ResourceSet result 		= null;
		@SuppressWarnings("unused")
		String		r			= "";
		try {
			String query = "<a><b/></a>/util:eval('*')";
			result 	= service.query( query );			
			assertEquals( 1, result.getSize());	
			
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}	
        
        // @see http://sourceforge.net/tracker/index.php?func=detail&aid=1629363&group_id=17691&atid=117691
	public void testUtilEval2() throws XPathException {
		ResourceSet result 		= null;
		@SuppressWarnings("unused")
		String		r			= "";
		try {
			String query = "let $context := <item/> "+
                                "return util:eval(\"<result>{$context}</result>\")";
                        // TODO check result
			result 	= service.query( query );			
			assertEquals( 1, result.getSize());	
			
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
        

	public void testUtilEvalForFunction() throws XPathException
	{
			ResourceSet result = null;
		
			String query = "declare function local:home()\n"
			+ "{\n"
			+ "<b>HOME</b>\n"
			+ "};\n"
			+ "util:eval(\"local:home()\")\n";
			
			try
			{
				result = service.query(query);
				assertEquals(1, result.getSize());
			}
			catch(XMLDBException e)
			{
				e.printStackTrace();
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
			e.printStackTrace();
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
			expected = "http%3A%2F%2Fwww.example.com%2F00%2FWeather%2FCA%2FLos%2520Angeles%23ocean";
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
			
			
	        query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[encode-for-uri(.) ne '']";
	        result = service.query(query);          
	        assertEquals(2, result.getSize());
	        
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

			string = "$";
			expected = "$";
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
			
			query = "escape-html-uri('$')";
			result = service.query(query);
			r 	= (String) result.getResource(0).getContent();
			assertEquals("$", r);
			
	        query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[escape-html-uri(.) ne '']";
	        result = service.query(query);          
	        assertEquals(2, result.getSize());			

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
	
    public void testDateTimeConstructor() throws XPathException {
		ResourceSet result 		= null;
		String		r			= "";
		try {	
			result 	= service.query(
			"let $date := xs:date('2007-05-02+02:00') " +
			"return dateTime($date, xs:time('15:12:52.421+02:00'))"
			);
			r 		= (String) result.getResource(0).getContent();
			assertEquals( "2007-05-02T15:12:52.421+02:00", r );
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
		    
		    String query = "declare option exist:current-dateTime '2007-08-23T00:01:02.062+02:00';" +
		    "current-dateTime()";
		    result = service.query(query);          
		    assertEquals(1, result.getSize());
		    r = (String) result.getResource(0).getContent();
		    assertEquals("2007-08-23T00:01:02.062+02:00", r);          
        
        } catch (XMLDBException e) {
            System.out.println("current-dateTime(): " + e);
            fail(e.getMessage());
        }
    }
    
    /*
     *  Bugfix 3070
     *  @see http://svn.sourceforge.net/exist/?rev=3070&view=rev
     *
     *  seconds-from-dateTime() returned wrong value when dateTime had
     * no millesecs available. Special value was returned.
     */
    public void testSecondsFromDateTime(){
        //
        ResourceSet result =  null;
        String r = "";
        @SuppressWarnings("unused")
		String message;
        
        try {
            result 	= service.query("seconds-from-dateTime(xs:dateTime(\"2005-12-22T13:35:21.000\") )");
            r 		= (String) result.getResource(0).getContent();
            assertEquals( "21", r );
            
            result 	= service.query("seconds-from-dateTime(xs:dateTime(\"2005-12-22T13:35:21\") )");
            r 		= (String) result.getResource(0).getContent();
            assertEquals( "21", r );
            
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }
    
//    public void testResolveQName() {
//      try {
//          String query = "declare namespace a=\"aes\"; " +
//          "declare namespace n=\"ns1\"; " +
//          "declare variable $d := <c xmlns:x=\"ns1\"><d>x:test</d></c>; " +
//          "for $e in $d/d " +
//          "return fn:resolve-QName($e/text(), $e)";
//
//        ResourceSet result = service.query(query);
//        String r = (String) result.getResource(0).getContent();
//        assertEquals("x:test", r);
//
//        query = "declare namespace a=\"aes\"; " +
//        	"declare namespace n=\"ns1\"; " +
//        	"declare variable $d := <c xmlns:x=\"ns1\"><d xmlns:y=\"ns1\">y:test</d></c>; " +
//        	"for $e in $d/d " +
//        	"return fn:resolve-QName($e/text(), $e)";
//        result = service.query(query);
//        r = (String) result.getResource(0).getContent();
//        assertEquals("y:test", r);
//
//      } catch (XMLDBException e) {
//        e.printStackTrace();
//        fail(e.getMessage());
//      }
//    }
    
    public void testNamespaceURI() {
        try {
        	String query = "let $var := <a xmlns='aaa'/> " +
        	"return " + 
        	"$var[fn:namespace-uri() = 'aaa']/fn:namespace-uri()";        	
          ResourceSet result = service.query(query);
          String r = (String) result.getResource(0).getContent();
          assertEquals("aaa", r);
          
          query = "for $a in <test><a xmlns=\"aaa\"><b><c/></b></a></test>//* " +
          	"return namespace-uri($a)";
          result = service.query(query);
          assertEquals(result.getSize(), 3);
          r = (String) result.getResource(0).getContent();
          assertEquals("aaa", r);
          r = (String) result.getResource(1).getContent();
          assertEquals("aaa", r);
          r = (String) result.getResource(2).getContent();
          assertEquals("aaa", r);
         
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }    
    
    public void testPrefixFromQName() {
        try {
        	String query = "declare namespace foo = \"http://example.org\"; " + 
        		"declare namespace FOO = \"http://example.org\"; " + 
        		"fn:prefix-from-QName(xs:QName(\"foo:bar\"))";        	
          ResourceSet result = service.query(query);
          String r = (String) result.getResource(0).getContent();
          assertEquals("foo", r);         
        
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }    

    public void testStringJoin() {
        try {
        	String query = "let $s := ('','a','b','') " +
        		"return string-join($s,'/')";        	
          ResourceSet result = service.query(query);
          String r = (String) result.getResource(0).getContent();
          assertEquals("/a/b/", r);         
        
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }    
    
    
    public void testNodeName() {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " + 
        "for $b in $a/b[node-name(.) = xs:QName('b')] return $b";
        
        try {
          ResourceSet result = service.query(query);          
          assertEquals(2, result.getSize());
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }    
    
    public void testData() {
        String query = "let $a := <a><b>1</b><b>1</b></a> " +
        	"for $b in $a/b[data(.) = '1'] return $b";
        
        try {
          ResourceSet result = service.query(query);          
          assertEquals(2, result.getSize());
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }  
    
    public void testCeiling() {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[abs(ceiling(.))]";
        
        try {
          ResourceSet result = service.query(query);          
          assertEquals(2, result.getSize());
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }    
    
    public void testConcat() {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[concat('+', ., '+') = '+-2+']";
        
        try {
          ResourceSet result = service.query(query);          
          assertEquals(1, result.getSize());
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }  
    
    public void testDocumentURI() {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[empty(document-uri(.))]";
        
        try {
          ResourceSet result = service.query(query);          
          assertEquals(2, result.getSize());
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }   
    
    public void testImplicitTimezone() {
        String query = "declare option exist:implicit-timezone 'PT3H';" +
        "implicit-timezone()";
        try {
          ResourceSet result = service.query(query);          
          assertEquals(1, result.getSize());
          String r = (String) result.getResource(0).getContent();
          assertEquals("PT3H", r);          
         } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      } 
   
    public void testExists() {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[exists(.)]";
        
        try {
          ResourceSet result = service.query(query);          
          assertEquals(2, result.getSize());
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }   

    public void testFloor() {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
        	"return $a/b[abs(floor(.))]";
        
        try {
          ResourceSet result = service.query(query);          
          assertEquals(2, result.getSize());
        } catch (XMLDBException e) {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }    

    //ensure the test collection is removed and call collection-available,
    //which should return false, no exception thrown
    public void testCollectionAvailable1() {
    	//remove the test collection if it already exists
    	String collectionName = "testCollectionAvailable";
    	String collectionPath = XmldbURI.ROOT_COLLECTION + "/" + collectionName;
    	String collectionURI = ROOT_COLLECTION_URI + "/" + collectionName;

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

		runCollectionAvailableTest(collectionPath, false);
		runCollectionAvailableTest(collectionURI, false);
    }
    //create a collection and call collection-available, which should return true,
    //no exception thrown
    public void testCollectionAvailable2() {
    	//add the test collection
    	String collectionName = "testCollectionAvailable";
    	String collectionPath = XmldbURI.ROOT_COLLECTION + "/" + collectionName;
    	String collectionURI = ROOT_COLLECTION_URI + "/" + collectionName;
    	try {
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
		runCollectionAvailableTest(collectionPath, true);
		runCollectionAvailableTest(collectionURI, true);
    }
        
    private void runCollectionAvailableTest(String collectionPath, boolean expectedResult) {
    	//collection-available should not throw an exception and should return expectedResult
    	String importXMLDB = "import module namespace xdb=\"http://exist-db.org/xquery/xmldb\";\n";
    	String collectionAvailable = "xdb:collection-available('" + collectionPath + "')";
    	String query = importXMLDB + collectionAvailable;
    	try {
    		ResourceSet result = service.query(query);
    		assertNotNull(result);
    		assertTrue(result.getSize() == 1);
    		assertNotNull(result.getResource(0));
    		String content = (String)result.getResource(0).getContent();
    		assertNotNull(content);
    		assertEquals(expectedResult, Boolean.valueOf(content).booleanValue());
    	} catch(XMLDBException xe) {
    		System.err.println("Error calling xdb:collection-available:");
    		xe.printStackTrace();
    		fail(xe.getMessage());
    	}
    }    
    
    public void testBase64BinaryCast()
	{
    	final String TEST_BINARY_COLLECTION = "testBinary";
    	final String TEST_COLLECTION = "/db/" + TEST_BINARY_COLLECTION;
    	final String BINARY_RESOURCE_FILENAME = "logo.jpg";
    	final String XML_RESOURCE_FILENAME = "logo.xml";
    	
    	try
    	{
    		//create a test collection
	    	CollectionManagementService colService = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			Collection testCollection = colService.createCollection(TEST_BINARY_COLLECTION);
			assertNotNull(testCollection);

			File home = ConfigurationHelper.getExistHome();
            File fLogo;
            if (home != null)
                fLogo = new File(home, "webapp/" + BINARY_RESOURCE_FILENAME);
            else
                fLogo = new File("webapp/" + BINARY_RESOURCE_FILENAME);
            //store the eXist logo in the test collection
			BinaryResource br = (BinaryResource)testCollection.createResource(BINARY_RESOURCE_FILENAME, "BinaryResource");
			br.setContent(fLogo);
			testCollection.storeResource(br);
	    	
			//create an XML resource with the logo base64 embedded in it
			String queryStore = "xquery version \"1.0\";\n\n"
			+ "let $embedded := <logo><image>{util:binary-doc(\"" + TEST_COLLECTION + "/" + BINARY_RESOURCE_FILENAME + "\")}</image></logo> return\n"
			+ "xmldb:store(\"" + TEST_COLLECTION + "\", \""+ XML_RESOURCE_FILENAME + "\", $embedded)";
		
			ResourceSet resultStore = service.query(queryStore);
			assertEquals("store, Expect single result", 1, resultStore.getSize());
			assertEquals("Expect stored filename as result", TEST_COLLECTION + "/" + XML_RESOURCE_FILENAME, resultStore.getResource(0).getContent().toString());
		
			//retreive the base64 image from the XML resource and try to cast to xs:base64Binary
			String queryRetreive = "xquery version \"1.0\";\n\n"
			+ "let $image := doc(\"" + TEST_COLLECTION +"/" + XML_RESOURCE_FILENAME + "\")/logo/image return\n"
			+ "$image/text() cast as xs:base64Binary";
		
			ResourceSet resultRetreive = service.query(queryRetreive);
			assertEquals("retreive, Expect single result", 1, resultRetreive.getSize());
		}
		catch(XMLDBException e)
		{
			System.out.println("testBase64Binary: XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
    
	
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		// initialize driver
		Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
		database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);
		root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
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
        
        // clear instance variables
        service = null;
        root = null;
		//System.out.println("tearDown PASSED");
	}

}
