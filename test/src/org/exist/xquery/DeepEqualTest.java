package org.exist.xquery;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;


public class DeepEqualTest extends TestCase {

	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	private XPathQueryService query;
	private Collection c;
	
	public static void main(String[] args) {
		TestRunner.run(DeepEqualTest.class);
	}
	
	public DeepEqualTest(String name) {
		super(name);
	}
	
	public void testAtomic1() {
		assertQuery(true, "deep-equal('hello', 'hello')");
	}
	
	public void testAtomic2() {
		assertQuery(false, "deep-equal('hello', 'goodbye')");
	}
	
	public void testAtomic3() {
		assertQuery(true, "deep-equal(42, 42)");
	}
	
	public void testAtomic4() {
		assertQuery(false, "deep-equal(42, 17)");
	}
	
	public void testAtomic5() {
		assertQuery(false, "deep-equal(42, 'hello')");
	}
	
	public void testAtomic6() {
		assertQuery(true, "deep-equal( 1. , xs:integer(1) )" );
		assertQuery(true, "deep-equal( xs:double(1) , xs:integer(1) )" );
	}
	
	public void testEmptySeq() {
		assertQuery(true, "deep-equal((), ())");
	}
	
	public void testDiffLengthSeq1() {
		assertQuery(false, "deep-equal((), 42)");
	}
	
	public void testDiffLengthSeq2() {
		assertQuery(false, "deep-equal((), (42, 'hello'))");
	}
	
	public void testDiffKindNodes1() {
		createDocument("test", "<test key='value'>hello</test>");
		assertQuery(false, "deep-equal(/test, /test/@key)");
	}
	
	public void testDiffKindNodes2() {
		createDocument("test", "<test key='value'>hello</test>");
		assertQuery(false, "deep-equal(/test, /test/text())");
	}
	
	public void testDiffKindNodes3() {
		createDocument("test", "<test key='value'>hello</test>");
		assertQuery(false, "deep-equal(/test/@key, /test/text())");
	}
	
	public void testSameNode1() {
		createDocument("test", "<test key='value'>hello</test>");
		assertQuery(true, "deep-equal(/test, /test)");
	}
		
	public void testSameNode2() {
		createDocument("test", "<test key='value'>hello</test>");
		assertQuery(true, "deep-equal(/test/@key, /test/@key)");
	}
		
	public void testSameNode3() {
		createDocument("test", "<test key='value'>hello</test>");
		assertQuery(true, "deep-equal(/test/text(), /test/text())");
	}
	
	public void testDocuments1() {
		createDocument("test1", "<test key='value'>hello</test>");
		createDocument("test2", "<test key='value'>hello</test>");
		assertQuery(true, "deep-equal(xmldb:document('test1'), xmldb:document('test2'))");
	}
	
	public void testDocuments2() {
		createDocument("test1", "<test key='value'>hello</test>");
		createDocument("test2", "<notatest/>");
		assertQuery(false, "deep-equal(xmldb:document('test1'), xmldb:document('test2'))");
	}
	
	public void testText1() {
		createDocument("test", "<test><g1><a>1</a><b>2</b></g1><g2><c>1</c><d>2</d></g2></test>");
		assertQuery(true, "deep-equal(//a/text(), //c/text())");
	}
	
	public void testText2() {
		createDocument("test", "<test><g1><a>1</a><b>2</b></g1><g2><c>1</c><d>2</d></g2></test>");
		assertQuery(false, "deep-equal(//a/text(), //b/text())");
	}
	
	public void testText3() {
		createDocument("test", "<test><g1><a>1</a><b>2</b></g1><g2><c>1</c><d>2</d></g2></test>");
		assertQuery(true, "deep-equal(//g1/text(), //g2/text())");
	}
	
	public void testText4() {
		createDocument("test", "<test><a>12</a><b>1<!--blah-->2</b></test>");
		assertQuery(false, "deep-equal(//a/text(), //b/text())");
	}
	
	public void testAttributes1() {
		createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
		assertQuery(true, "deep-equal(//e1/@a, //e2/@a)");
	}
	
	public void testAttributes2() {
		createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
		assertQuery(false, "deep-equal(//e1/@a, //e2/@b)");
	}
	
	public void testAttributes3() {
		createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
		assertQuery(false, "deep-equal(//e1/@a, //e2/@c)");
	}
	
	public void testAttributes4() {
		createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
		assertQuery(false, "deep-equal(//e1/@a, //e3/@a)");
	}
	
	public void testNSAttributes1() {
		createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
		assertQuery(true, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e2/@n:a)");
	}
	
	public void testNSAttributes2() {
		createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
		assertQuery(true, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@q:a, //e4/@n:a)");
	}
	
	public void testNSAttributes3() {
		createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
		assertQuery(false, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e2/@p:a)");
	}
	
	public void testNSAttributes4() {
		createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
		assertQuery(false, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e2/@p:b)");
	}
	
	public void testNSAttributes5() {
		createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
		assertQuery(false, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e3/@n:a)");
	}
	
	public void testElements1() {
		createDocument("test", "<test><a/><a/></test>");
		assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements2() {
		createDocument("test", "<test><a/><b/></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements3() {
		createDocument("test", "<test><a a='1' b='2'/><a b='2' a='1'/></test>");
		assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements4() {
		createDocument("test", "<test><a a='1'/><a b='2' a='1'/></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements5() {
		createDocument("test", "<test><a a='1' c='2'/><a b='2' a='1'/></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements6() {
		createDocument("test", "<test><a a='1' b='2'/><a a='2' b='2'/></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements7() {
		createDocument("test", "<test><a>hello</a><a>hello</a></test>");
		assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements8() {
		createDocument("test", "<test><a>hello</a><a>bye</a></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements9() {
		createDocument("test", "<test><a><!--blah--></a><a/></test>");
		assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements10() {
		createDocument("test", "<test><a><b/><!--blah-->hello</a><a><b/>hello</a></test>");
		assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements11() {
		createDocument("test", "<test><a><b/>hello</a><a>hello</a></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements12() {
		createDocument("test", "<test><a><b/></a><a>hello</a></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testElements13() {
		createDocument("test", "<test><a><b/></a><a><b/>hello</a></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}

	//Courtesy : Dizzz
	public void testElements14() {
		//Includes a reference node
		String query =
			"let $parSpecs1 := <ParameterSpecifications/> " +
			"let $funSpecs2 := " +
			" <FunctionSpecifications>" +
			"  <FunctionName>Func2</FunctionName>" +
			"  { $parSpecs1 }" +
			" </FunctionSpecifications>" +
			"return " +
			" deep-equal("+
			"  <FunctionVerifications>" +
			"   <FunctionName>Func2</FunctionName>" +
			"  </FunctionVerifications>" +
			"," +
			"  <FunctionVerifications>" +
			"   { $funSpecs2/FunctionName }" +
			"  </FunctionVerifications>"+
			" )";			
		assertQuery(true, query);
	}
        
        public void testElements15() {
        
            String query ="let $funSpecs :="+
                    "<FunctionSpecifications>"+
                    "<FunctionName>Func2</FunctionName>"+
                    "</FunctionSpecifications>"+
                    "let $funVers1 :="+
                    "<FunctionVerifications>"+
                    "<FunctionName>Func2</FunctionName>"+
                    "</FunctionVerifications>"+
                    "let $funVers2 :="+
                    "<FunctionVerifications>"+
                    "{$funSpecs/FunctionName}"+
                    "</FunctionVerifications>"+
                    "return "+
                    "deep-equal($funVers1, $funVers2)";
            assertQuery(true, query);
        }

        public void testElements16() {
            // [ 1462061 ] Issue with deep-equal() "DeepestEqualBug"
            String query =
                    "declare namespace ve = \"ournamespace\";"+
                    "declare function ve:functionVerifications($pars as element()*) as element() {"+
                    "<FunctionVerifications>"+
                    "<ParameterVerifications>{$pars[Name eq \"Par1\"]}</ParameterVerifications>"+
                    "</FunctionVerifications>"+
                    "};"+
                    "let $par1 := <Parameter><Name>Par1</Name></Parameter>"+
                    "let $funVers2 := "+
                    "<FunctionVerifications><ParameterVerifications> {$par1}"+
                    "</ParameterVerifications></FunctionVerifications> "+
                    "return "+
                    "deep-equal($funVers2, ve:functionVerifications($par1))";
            assertQuery(true,query);
        }
        
        public void testElements17() {
            // Test deep-equal is used with in-memory nodes 
            String query =
                    "let $one := <foo/> "+
                    "let $two := <bar/> "+
                    "return "+
                    "deep-equal($one, $two)";
            assertQuery(false,query);
        }

        public void testReferenceNode() {
                    String query =
                    "let $expr1 := <Value>Hello</Value> "+
                    "return "+
                    "deep-equal( <Result><Value>Hello</Value></Result>,"+
                    "<Result><Value>{$expr1/node()}</Value></Result> )";
            assertQuery(true,query);    
	}
        
        public void testReferenceNode2() {
        	String query = "declare namespace dst = \"http://www.test.com/DeeperEqualTest\"; "
                            + "declare function dst:value($value as element(Value), "
                            + "$result as element(Result)) as element(Result) { "
                            + "<Result><Value>{($result/Value/node(), $value/node())}</Value> </Result>}; "
                            + "let $value1 := <Value>hello</Value> "
                            + "let $result0 := <Result><Value/></Result> "
                            + "let $result1 := dst:value($value1, $result0) "
                            + "let $value2 := <Value/> "
                            + "let $result2 := dst:value($value2, $result1) "
                            + "return deep-equal($result1, $result2)";
        	assertQuery(true,query);    
	}

    public void testReferenceNode3() {
        createDocument("test", "<root><value>A</value><value>B</value></root>");
        // two adjacent reference text nodes from another document should be merged into one
        assertQuery(true,
                "let $a := <v>{/root/value[1]/node(), /root/value[2]/node()}</v>" +
                "let $b := <v>AB</v>" +
                "return deep-equal($a, $b)");
        // one reference node after a text node
        assertQuery(true,
                "let $a := <v>{/root/value[1]/node(), /root/value[2]/node()}</v>" +
                "let $b := <v>A{/root/value[2]/node()}</v>" +
                "return deep-equal($a, $b)");
        // reference node before a text node
        assertQuery(true,
                "let $a := <v>{/root/value[1]/node(), /root/value[2]/node()}</v>" +
                "let $b := <v>{/root/value[1]/node()}B</v>" +
                "return deep-equal($a, $b)");
        // reference node before an atomic value
        assertQuery(true,
                "let $a := <v>{/root/value[1]/node(), 'B'}</v>" +
                "let $b := <v>AB</v>" +
                "return deep-equal($a, $b)");
        // reference node after an atomic value
        assertQuery(true,
                "let $a := <v>{'A', /root/value[2]/node()}</v>" +
                "let $b := <v>AB</v>" +
                "return deep-equal($a, $b)");
    }

        public void testsiblingCornerCase() {
        	String query = "declare  namespace ve = 'http://www.test.com/deepestEqualError'; " +
            "declare function ve:functionVerifications() as element(FunctionVerifications) { " +
            "let $parVers := " + 
            "<ParameterVerifications> " +
            "  <Parameter/> " +
            "  <PassedLevel>ATP</PassedLevel> " +
            "  <PassedLevel>PE</PassedLevel> " +
            "  <PassedLevel>SPC</PassedLevel> " +
            "  <Specification>ATP</Specification> " +
            "  <Specification>PE</Specification> " +
            "  <Specification>SPC</Specification> " +
            "</ParameterVerifications> " +
            "let $dummy := $parVers/PassedLevel  (: cause deep-equal bug!!! :) " +
            "return " +
            "<FunctionVerifications> " +
            "  <PassedLevel>ATP</PassedLevel> " +
            "  <PassedLevel>PE</PassedLevel> " +
            "  <PassedLevel>SPC</PassedLevel> " +
            "  {$parVers} " +
            "</FunctionVerifications> " +
          "}; " +
          "let $expected := " +
          "  <FunctionVerifications> " +
          "    <PassedLevel>ATP</PassedLevel> " +
          "    <PassedLevel>PE</PassedLevel> " +
          "    <PassedLevel>SPC</PassedLevel> " +
          "    <ParameterVerifications> " +
          "      <Parameter/> " +
          "      <PassedLevel>ATP</PassedLevel> " +
          "      <PassedLevel>PE</PassedLevel> " +
          "      <PassedLevel>SPC</PassedLevel> " +
          "      <Specification>ATP</Specification> " +
          "      <Specification>PE</Specification> " +
          "      <Specification>SPC</Specification> " +
          "    </ParameterVerifications> " +
          "  </FunctionVerifications> " +
          "let $got := ve:functionVerifications() " +
          "return deep-equal($expected, $got)";
        	assertQuery(true,query);
        }
        
         public void testSequenceError1() {
        	String query = "declare namespace ds = \"http://www.test.com/SequenceError\"; "
                        +"declare function ds:result(  $current as element(Result)?, "
                        +"$value  as element(Value)?) as element(Result) {"
                        +"<Result> <Value>{ ($current/Value/node(), $value/node()) }</Value> </Result> };"
                        +"let $v1 := <Value>1234</Value> " 
                        +"let $result1 := ds:result((), $v1) "
                        +"let $v2 := <Value>abcd</Value> "
                        +"let $expected := <Value>{($v1, $v2)/node()}</Value> "
                        +"let $result2 := ds:result($result1, $v2) "
                        +"return deep-equal($expected, $result2/Value)";
                for(int i=1; i<20;i++){
                    System.out.println("nr="+i);
                    assertQuery(true,query);
               }

        };


	
	public void testNSElements1() {
		createDocument("test", "<test xmlns:p='urn:foo' xmlns:q='urn:foo'><p:a/><q:a/></test>");
		assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testNSElements2() {
		createDocument("test", "<test xmlns:p='urn:foo' xmlns:q='urn:bar'><p:a/><q:a/></test>");
		assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
	}
	
	public void testNSElements3() {
		createDocument("test", "<test><a/><a xmlns:z='foo'/></test>");
		assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
	}
        

	
	public void testForLoop() {
		try {
			ResourceSet rs = query.query("let $set := <root><b>test</b><c><a>test</a></c><d><a>test</a></d></root>, $test := <c><a>test</a></c> for $node in $set/* return deep-equal($node, $test)");
			assertEquals(3, rs.getSize());
			assertEquals("false", rs.getResource(0).getContent());
			assertEquals("true", rs.getResource(1).getContent());
			assertEquals("false", rs.getResource(2).getContent());
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }			
	}
	
	private void assertQuery(boolean expected, String q) {
		try {
			ResourceSet rs = query.query(q);
			assertEquals(1, rs.getSize());
			assertEquals(Boolean.toString(expected), rs.getResource(0).getContent());
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }			
	}
	
	private XMLResource createDocument(String name, String content) {
		try {
			XMLResource res = (XMLResource) c.createResource(name, XMLResource.RESOURCE_TYPE);
			res.setContent(content);
			c.storeResource(res);
			return res;
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }
	    return null;
	}

	private Collection setupTestCollection() {
		try {
			Collection root = DatabaseManager.getCollection(URI, "admin", "");
			CollectionManagementService rootcms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			Collection cc = root.getChildCollection("test");
			if(cc != null) rootcms.removeCollection("test");
			rootcms.createCollection("test");
			cc = DatabaseManager.getCollection(URI+"/test", "admin", "");
			assertNotNull(cc);
			return cc;
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }	
	    return null;
	}

	protected void setUp() {
		try {
			// initialize driver
			Database database = (Database) Class.forName(DRIVER).newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);			
			c = setupTestCollection();
			query = (XPathQueryService) c.getService("XPathQueryService", "1.0");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("failed setup", e);
		}
	}
	
	protected void tearDown() {
		try {
			if (c != null) c.close();
            c = null;
            query = null;
		} catch (XMLDBException e) {
			throw new RuntimeException("failed teardown", e);
		}
	}
	
}
