/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class DeepEqualTest {

    private static Collection c;
    private static XPathQueryService query;

    @Test
    public void atomic1() throws XMLDBException {
        assertQuery(true, "deep-equal('hello', 'hello')");
    }

    @Test
    public void atomic2() throws XMLDBException {
        assertQuery(false, "deep-equal('hello', 'goodbye')");
    }

    @Test
    public void atomic3() throws XMLDBException {
        assertQuery(true, "deep-equal(42, 42)");
    }

    @Test
    public void atomic4() throws XMLDBException {
        assertQuery(false, "deep-equal(42, 17)");
    }

    @Test
    public void atomic5() throws XMLDBException {
        assertQuery(false, "deep-equal(42, 'hello')");
    }

    @Test
    public void atomic6() throws XMLDBException {
        assertQuery(true, "deep-equal( 1. , xs:integer(1) )");
        assertQuery(true, "deep-equal( xs:double(1) , xs:integer(1) )");
    }

    @Test
    public void emptySeq() throws XMLDBException {
        assertQuery(true, "deep-equal((), ())");
    }

    @Test
    public void diffLengthSeq1() throws XMLDBException {
        assertQuery(false, "deep-equal((), 42)");
    }

    @Test
    public void diffLengthSeq2() throws XMLDBException {
        assertQuery(false, "deep-equal((), (42, 'hello'))");
    }

    @Test
    public void diffKindNodes1() throws XMLDBException {
        createDocument("test", "<test key='value'>hello</test>");
        assertQuery(false, "deep-equal(/test, /test/@key)");
    }

    @Test
    public void diffKindNodes2() throws XMLDBException {
        createDocument("test", "<test key='value'>hello</test>");
        assertQuery(false, "deep-equal(/test, /test/text())");
    }

    @Test
    public void diffKindNodes3() throws XMLDBException {
        createDocument("test", "<test key='value'>hello</test>");
        assertQuery(false, "deep-equal(/test/@key, /test/text())");
    }

    @Test
    public void sameNode1() throws XMLDBException {
        createDocument("test", "<test key='value'>hello</test>");
        assertQuery(true, "deep-equal(/test, /test)");
    }

    @Test
    public void sameNode2() throws XMLDBException {
        createDocument("test", "<test key='value'>hello</test>");
        assertQuery(true, "deep-equal(/test/@key, /test/@key)");
    }

    @Test
    public void sameNode3() throws XMLDBException {
        createDocument("test", "<test key='value'>hello</test>");
        assertQuery(true, "deep-equal(/test/text(), /test/text())");
    }

    @Test
    public void documents1() throws XMLDBException {
        createDocument("test1", "<test key='value'>hello</test>");
        createDocument("test2", "<test key='value'>hello</test>");
        assertQuery(true, "deep-equal(doc('test1'), doc('test2'))");
    }

    @Test
    public void documents2() throws XMLDBException {
        createDocument("test1", "<test key='value'>hello</test>");
        createDocument("test2", "<notatest/>");
        assertQuery(false, "deep-equal(doc('test1'), doc('test2'))");
    }

    @Test
    public void text1() throws XMLDBException {
        createDocument("test", "<test><g1><a>1</a><b>2</b></g1><g2><c>1</c><d>2</d></g2></test>");
        assertQuery(true, "deep-equal(//a/text(), //c/text())");
    }

    @Test
    public void text2() throws XMLDBException {
        createDocument("test", "<test><g1><a>1</a><b>2</b></g1><g2><c>1</c><d>2</d></g2></test>");
        assertQuery(false, "deep-equal(//a/text(), //b/text())");
    }

    @Test
    public void text3() throws XMLDBException {
        createDocument("test", "<test><g1><a>1</a><b>2</b></g1><g2><c>1</c><d>2</d></g2></test>");
        assertQuery(true, "deep-equal(//g1/text(), //g2/text())");
    }

    @Test
    public void text4() throws XMLDBException {
        createDocument("test", "<test><a>12</a><b>1<!--blah-->2</b></test>");
        assertQuery(false, "deep-equal(//a/text(), //b/text())");
    }

    @Test
    public void attributes1() throws XMLDBException {
        createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
        assertQuery(true, "deep-equal(//e1/@a, //e2/@a)");
    }

    @Test
    public void attributes2() throws XMLDBException {
        createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
        assertQuery(false, "deep-equal(//e1/@a, //e2/@b)");
    }

    @Test
    public void attributes3() throws XMLDBException {
        createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
        assertQuery(false, "deep-equal(//e1/@a, //e2/@c)");
    }

    @Test
    public void attributes4() throws XMLDBException {
        createDocument("test", "<test><e1 a='1'/><e2 a='1' b='2' c='1'/><e3 a='2'/></test>");
        assertQuery(false, "deep-equal(//e1/@a, //e3/@a)");
    }

    @Test
    public void nsAttributes1() throws XMLDBException {
        createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
        assertQuery(true, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e2/@n:a)");
    }

    @Test
    public void nsAttributes2() throws XMLDBException {
        createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
        assertQuery(true, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@q:a, //e4/@n:a)");
    }

    @Test
    public void nsAttributes3() throws XMLDBException {
        createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
        assertQuery(false, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e2/@p:a)");
    }

    @Test
    public void nsAttributes4() throws XMLDBException {
        createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
        assertQuery(false, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e2/@p:b)");
    }

    @Test
    public void nsAttributes5() throws XMLDBException {
        createDocument("test", "<test xmlns:n='urn:blah' xmlns:p='urn:foo' xmlns:q='urn:blah'><e1 n:a='1'/><e2 n:a='1' p:a='1' p:b='1'/><e3 n:a='2'/><e4 q:a='1'/></test>");
        assertQuery(false, "declare namespace n = 'urn:blah'; declare namespace p = 'urn:foo'; declare namespace q = 'urn:blah'; deep-equal(//e1/@n:a, //e3/@n:a)");
    }

    @Test
    public void elements1() throws XMLDBException {
        createDocument("test", "<test><a/><a/></test>");
        assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements2() throws XMLDBException {
        createDocument("test", "<test><a/><b/></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements3() throws XMLDBException {
        createDocument("test", "<test><a a='1' b='2'/><a b='2' a='1'/></test>");
        assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements4() throws XMLDBException {
        createDocument("test", "<test><a a='1'/><a b='2' a='1'/></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements5() throws XMLDBException {
        createDocument("test", "<test><a a='1' c='2'/><a b='2' a='1'/></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements6() throws XMLDBException {
        createDocument("test", "<test><a a='1' b='2'/><a a='2' b='2'/></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements7() throws XMLDBException {
        createDocument("test", "<test><a>hello</a><a>hello</a></test>");
        assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements8() throws XMLDBException {
        createDocument("test", "<test><a>hello</a><a>bye</a></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements9() throws XMLDBException {
        createDocument("test", "<test><a><!--blah--></a><a/></test>");
        assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements10() throws XMLDBException {
        createDocument("test", "<test><a><b/><!--blah-->hello</a><a><b/>hello</a></test>");
        assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements11() throws XMLDBException {
        createDocument("test", "<test><a><b/>hello</a><a>hello</a></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements12() throws XMLDBException {
        createDocument("test", "<test><a><b/></a><a>hello</a></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void elements13() throws XMLDBException {
        createDocument("test", "<test><a><b/></a><a><b/>hello</a></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    //Courtesy : Dizzz
    @Test
    public void elements14() throws XMLDBException {
        //Includes a reference node
        String query =
                "let $parSpecs1 := <ParameterSpecifications/> " +
                        "let $funSpecs2 := " +
                        " <FunctionSpecifications>" +
                        "  <FunctionName>Func2</FunctionName>" +
                        "  { $parSpecs1 }" +
                        " </FunctionSpecifications>" +
                        "return " +
                        " deep-equal(" +
                        "  <FunctionVerifications>" +
                        "   <FunctionName>Func2</FunctionName>" +
                        "  </FunctionVerifications>" +
                        "," +
                        "  <FunctionVerifications>" +
                        "   { $funSpecs2/FunctionName }" +
                        "  </FunctionVerifications>" +
                        " )";
        assertQuery(true, query);
    }

    @Test
    public void elements15() throws XMLDBException {

        String query = "let $funSpecs :=" +
                "<FunctionSpecifications>" +
                "<FunctionName>Func2</FunctionName>" +
                "</FunctionSpecifications>" +
                "let $funVers1 :=" +
                "<FunctionVerifications>" +
                "<FunctionName>Func2</FunctionName>" +
                "</FunctionVerifications>" +
                "let $funVers2 :=" +
                "<FunctionVerifications>" +
                "{$funSpecs/FunctionName}" +
                "</FunctionVerifications>" +
                "return " +
                "deep-equal($funVers1, $funVers2)";
        assertQuery(true, query);
    }

    @Test
    public void elements16() throws XMLDBException {
        // [ 1462061 ] Issue with deep-equal() "DeepestEqualBug"
        String query =
                "declare namespace ve = \"ournamespace\";" +
                        "declare function ve:functionVerifications($pars as element()*) as element() {" +
                        "<FunctionVerifications>" +
                        "<ParameterVerifications>{$pars[Name eq \"Par1\"]}</ParameterVerifications>" +
                        "</FunctionVerifications>" +
                        "};" +
                        "let $par1 := <Parameter><Name>Par1</Name></Parameter>" +
                        "let $funVers2 := " +
                        "<FunctionVerifications><ParameterVerifications> {$par1}" +
                        "</ParameterVerifications></FunctionVerifications> " +
                        "return " +
                        "deep-equal($funVers2, ve:functionVerifications($par1))";
        assertQuery(true, query);
    }

    @Test
    public void elements17() throws XMLDBException {
        // Test deep-equal is used with in-memory nodes
        String query =
                "let $one := <foo/> " +
                        "let $two := <bar/> " +
                        "return " +
                        "deep-equal($one, $two)";
        assertQuery(false, query);
    }

    @Test
    public void referenceNode() throws XMLDBException {
        String query =
                "let $expr1 := <Value>Hello</Value> " +
                        "return " +
                        "deep-equal( <Result><Value>Hello</Value></Result>," +
                        "<Result><Value>{$expr1/node()}</Value></Result> )";
        assertQuery(true, query);
    }

    @Test
    public void referenceNode2() throws XMLDBException {
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
        assertQuery(true, query);
    }

    @Test
    public void referenceNode3() throws XMLDBException {
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

    @Test
    public void siblingCornerCase() throws XMLDBException {
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
        assertQuery(true, query);
    }

    @Test
    public void sequenceError1() throws XMLDBException {
        String query = "declare namespace ds = \"http://www.test.com/SequenceError\"; "
                + "declare function ds:result(  $current as element(Result)?, "
                + "$value  as element(Value)?) as element(Result) {"
                + "<Result> <Value>{ ($current/Value/node(), $value/node()) }</Value> </Result> };"
                + "let $v1 := <Value>1234</Value> "
                + "let $result1 := ds:result((), $v1) "
                + "let $v2 := <Value>abcd</Value> "
                + "let $expected := <Value>{($v1, $v2)/node()}</Value> "
                + "let $result2 := ds:result($result1, $v2) "
                + "return deep-equal($expected, $result2/Value)";
        for (int i = 1; i < 20; i++) {
            assertQuery(true, query);
        }

    }

    @Test
    public void nsElements1() throws XMLDBException {
        createDocument("test", "<test xmlns:p='urn:foo' xmlns:q='urn:foo'><p:a/><q:a/></test>");
        assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void nsElements2() throws XMLDBException {
        createDocument("test", "<test xmlns:p='urn:foo' xmlns:q='urn:bar'><p:a/><q:a/></test>");
        assertQuery(false, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void nsElements3() throws XMLDBException {
        createDocument("test", "<test><a/><a xmlns:z='foo'/></test>");
        assertQuery(true, "deep-equal(/test/*[1], /test/*[2])");
    }

    @Test
    public void forLoop() throws XMLDBException {
        ResourceSet rs = query.query("let $set := <root><b>test</b><c><a>test</a></c><d><a>test</a></d></root>, $test := <c><a>test</a></c> for $node in $set/* return deep-equal($node, $test)");
        assertEquals(3, rs.getSize());
        assertEquals("false", rs.getResource(0).getContent());
        assertEquals("true", rs.getResource(1).getContent());
        assertEquals("false", rs.getResource(2).getContent());
    }

    @Test
    public void notDeepEqual() throws XMLDBException {
        assertQuery(true, "not(deep-equal((true(), 2, 3), (1, 2, 3)))");
    }

    @Test
    public void fnDeepEqualMaps7() throws XMLDBException {
        assertQuery(true, "fn:deep-equal(map{xs:double('NaN'):true()}, map{xs:double('NaN'):true()})");
    }

    @Test
    public void fnDeepEqualMaps8() throws XMLDBException {
        assertQuery(true, "fn:deep-equal(map{xs:double('NaN'):true()}, map{xs:float('NaN'):true()})");
    }

    @Test
    public void fnDeepEqualMixArgs020() throws XMLDBException {
        assertQuery(true, "fn:deep-equal(xs:float('INF'), xs:double('INF'))");
    }

    @Test
    public void fnDeepEqualMixArgs021() throws XMLDBException {
        assertQuery(true, "fn:deep-equal(xs:float('-INF'), xs:double('-INF'))");
    }

    @Test
    public void fnDeepEqualEquivalentIntAndString() throws XMLDBException {
        assertQuery(false, "fn:deep-equal(xs:integer(1), xs:string('1'))");
    }

    @Test
    public void fnDeepEqualEquivalentStringAndInt() throws XMLDBException {
        assertQuery(false, "fn:deep-equal(xs:string('1'), xs:integer(1))");
    }

    private void assertQuery(boolean expected, String q) throws XMLDBException {
        ResourceSet rs = query.query(q);
        assertEquals(1, rs.getSize());
        assertEquals(Boolean.toString(expected), rs.getResource(0).getContent());
    }

    private XMLResource createDocument(String name, String content) throws XMLDBException {
        XMLResource res = c.createResource(name, XMLResource.class);
        res.setContent(content);
        c.storeResource(res);
        return res;
    }

    @ClassRule
    public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeClass
    public static void setupTestCollection() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService rootcms = root.getService(CollectionManagementService.class);
        c = root.getChildCollection("test");
        if (c != null) {
            rootcms.removeCollection("test");
        }
        c = rootcms.createCollection("test");
        assertNotNull(c);
        query = c.getService(XPathQueryService.class);
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        if (c != null) {
            final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
            final CollectionManagementService rootcms = root.getService(CollectionManagementService.class);
            rootcms.removeCollection("test");
            query = null;
            c = null;
        }
    }

}
