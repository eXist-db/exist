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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.source.SourceFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.EXistXPathQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.*;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.exist.test.XmlStringDiffMatcher.hasIdenticalXml;
import static org.exist.test.XmlStringDiffMatcher.hasSimilarXml;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * I propose that we put here in XQueryTest the tests involving all the
 * others constructs of the XQuery language, besides XPath expressions.
 * And in {@link XPathQueryTest} we will put the tests involving only XPath expressions.
 *
 * TODO maybe move the various eXist XQuery extensions in another class ...
 */
public class XQueryTest {

    private final static Logger LOG = LogManager.getLogger(XQueryTest.class);

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String NUMBERS_XML = "numbers.xml";
    private static final String BOWLING_XML = "bowling.xml";
    private static final String attributesSERIALIZATION = "attributes_serialization.xml";
    private static final String MODULE1_NAME = "module1.xqm";
    private static final String MODULE2_NAME = "module2.xqm";
    private static final String MODULE3_NAME = "module3.xqm";
    private static final String MODULE4_NAME = "module4.xqm";
    private static final String MODULE5_NAME = "module5.xqm";
    private static final String MODULE6_NAME = "module6.xqm";
    private static final String MODULE7_NAME = "module7.xqm";
    private static final String MODULE8_NAME = "module8.xqm";
    private static final String FATHER_MODULE_NAME = "father.xqm";
    private static final String CHILD1_MODULE_NAME = "child1.xqm";
    private static final String CHILD2_MODULE_NAME = "child2.xqm";
    private static final String NAMESPACED_NAME = "namespaced.xml";
    private static final String LOCAL_DB_URI = XmldbURI.LOCAL_DB;
    private final static String numbers =
            "<test>" + "<item id='1'><price>5.6</price><stock>22</stock></item>" + "<item id='2'><price>7.4</price><stock>43</stock></item>" + "<item id='3'><price>18.4</price><stock>5</stock></item>" + "<item id='4'><price>65.54</price><stock>16</stock></item>" + "</test>";
    private final static String module1 =
            """
            module namespace blah="blah";
            declare variable $blah:param := "value-1";\
            """;
    private final static String module2 =
            """
            module namespace foo="";
            declare variable $foo:bar := "bar";\
            """;
    private final static String module3 =
            """
            module namespace foo="foo";
            declare variable $bar:bar := "bar";\
            """;
    private static final String module4 =
            """
            module namespace foo="foo";
            declare variable $exist:bar external;
            declare function foo:bar() {
            $exist:bar
            };
            """;
    private final static String module5 =
            """
            module namespace foo="foo";
            declare variable $foo:bar := "bar";\
            """;
    private final static String module6 =
            """
            module namespace foo="foo";
            declare variable $foo:bar := "bar";\
            declare variable $foo:bar := "bar";\
            """;
    private final static String module7 =
            """
            module namespace foo="foo";
            declare namespace xhtml="http://www.w3.org/1999/xhtml";
            declare function foo:link() { <a href='#'>Link</a> };\
            declare function foo:copy($node) { element { node-name($node) } { $node/text() } };\
            """;
    private final static String module8 =
            """
            module namespace dr = "double-root2";\s
            declare function dr:documentIn() as document-node() {\s
             let $doc :=  <root> <contents/> </root>\s
             return document { $doc }\s
            };\
            """;
    
    private static final String fatherModule =
            """
            module namespace foo="foo";
            import module namespace foo1="foo1" at "%s/test/%s";
            import module namespace foo2="foo2" at "%s/test/%s";
            declare variable $foo:bar := "bar";
            declare variable $foo:bar1 := $foo1:bar;
            declare variable $foo:bar2 := $foo2:bar;
            """.formatted(LOCAL_DB_URI, CHILD1_MODULE_NAME, LOCAL_DB_URI, CHILD2_MODULE_NAME);
    private static final String child1Module =
            """
            module namespace foo="foo1";
            import module namespace blah="blah" at "%s/test/%s";
            declare variable $foo:bar := "bar1";
            """.formatted(LOCAL_DB_URI, MODULE1_NAME);
    private static final String child2Module =
            """
            module namespace foo="foo2";
            import module namespace blah="blah" at "%s/test/%s";
            declare variable $foo:bar := "bar2";
            """.formatted(LOCAL_DB_URI, MODULE1_NAME);
    private final static String namespacedDocument =
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            xmlns:x="http://exist.sourceforge.net/dc-ext">
                <rdf:Description id="3">
                    <dc:title>title</dc:title>
                    <dc:creator>creator</dc:creator>
                    <x:place>place</x:place>
                    <x:edition>place</x:edition>
                </rdf:Description>
            </rdf:RDF>""";
    private final static String bowling =
            "<series>" +
            "<game>" +
            "<frame/>" +
            "</game>" +
            "<game>" +
            "<frame/>" +
            "</game>" +
            "</series>";
    private final static String attributes =
        "<blob>" +
        "<test att='a' />" +
        "<test att='b' />" +
        "<test att='c' />" +
        "</blob>";

    private static int stringSize = 512;
    private static int nbElem = 1;
    private String file_name = "detail_xml.xml";
    private String xml;

    @BeforeEach
    void setup() throws XMLDBException {
        final CollectionManagementService service =
                existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        service.createCollection("test");
    }

    @AfterEach
    void tearDown() throws XMLDBException {
        final CollectionManagementService service =
                existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        service.removeCollection("test");
    }

    private Collection getTestCollection() throws XMLDBException {
        return DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", "");
    }

    @org.junit.jupiter.api.Test
    void let() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

        //Non null context sequence
        query = "/test/item[let $id := ./@id return $id]";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(4, result.getSize(), "XQuery: " + query);
        query = "/test/item[let $id := ./@id return not(/test/set[@id=$id])]";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(4, result.getSize(), "XQuery: " + query);
        query = "let $test := <test><a> a </a><a>a</a></test> " +
                "return distinct-values($test/a/normalize-space(.))";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(1, result.getSize(), "XQuery: " + query);

        //Ordered value sequence
        query = "let $unordset := (for $val in reverse(1 to 100) return " +
                "<value>{$val}</value>)" +
                "let $ordset := (for $newval in $unordset " +
                "where $newval mod 2 eq 1 " +
                "order by $newval " +
                "return $newval/text()) " +
                "return $ordset/ancestor::node()";

        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(50, result.getSize(), "XQuery: " + query);

        //WARNING : the return order CHANGES !!!!!!!!!!!!!!!!!!

        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<value>99</value>"));
        assertThat(result.getResource(49).getContent().toString(), hasSimilarXml("<value>1</value>"));
    }

    @org.junit.jupiter.api.Test
    void testFor() throws XMLDBException {
        ResourceSet result;
        String query;
        XMLResource resu;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        query = "for $f in /*/item return $f";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(4, result.getSize(), "XQuery: " + query);
        query = "for $f in /*/item  order by $f ascending  return $f";
        result = service.queryResource(NUMBERS_XML, query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("3", ((Element) resu.getContentAsDOM()).getAttribute("id"), "XQuery: " + query);
        query = "for $f in /*/item  order by $f descending  return $f";
        result = service.queryResource(NUMBERS_XML, query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("2", ((Element) resu.getContentAsDOM()).getAttribute("id"), "XQuery: " + query);
        query = "for $f in /*/item  order by xs:double($f/price) descending  return $f";
        result = service.queryResource(NUMBERS_XML, query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("4", ((Element) resu.getContentAsDOM()).getAttribute("id"), "XQuery: " + query);
        query = "for $f in //item where $f/@id = '3' return $f";
        result = service.queryResource(NUMBERS_XML, query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("3", ((Element) resu.getContentAsDOM()).getAttribute("id"), "XQuery: " + query);

        //Non null context sequence
        query = "/test/item[for $id in ./@id return $id]";
        result = service.queryResource(NUMBERS_XML, query);
        resu = (XMLResource) result.getResource(0);
        assertEquals(4, result.getSize(), "XQuery: " + query);

        //Ordered value sequence
        query = "let $doc := <doc><value>Z</value><value>Y</value><value>X</value></doc> " +
                "return " +
                "let $ordered_values := " +
                "	for $value in $doc/value order by $value ascending " +
                "	return $value " +
                "for $value in $doc/value " +
                "	return $value[. = $ordered_values[position() = 1]]";

        result = service.queryResource(NUMBERS_XML, query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("<value>X</value>", resu.getContent(), "XQuery: " + query);

        //Ordered value sequence
        query = "for $e in (1) order by $e return $e";
        result = service.queryResource(NUMBERS_XML, query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("1", resu.getContent(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void recursion() throws XMLDBException {
        String q1 =
                """
                declare function local:append($head, $i) {
                   if ($i < 5000) then
                       local:append(($head, $i), $i + 1)
                   else
                       $head
                };
                local:append((), 0)""";
        XPathQueryService service =
                getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(q1);
        assertEquals(5000, result.getSize());
    }

    @org.junit.jupiter.api.Test
    void constructedNode1() throws XMLDBException {
        String q1 =
                "let $a := <A/> for $b in $a//B/string() return \"Oops!\"";
        XPathQueryService service =
                getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(q1);
        assertEquals(0, result.getSize());
    }

    @org.junit.jupiter.api.Test
    void combiningNodeSequences() throws XMLDBException {
        ResourceSet result;
        String query;

        XPathQueryService service =
                getTestCollection().getService(XPathQueryService.class);
        query = """
                let $a := <a/>\s
                let $aa := ($a, $a)\s
                for $b in ($aa intersect $aa\s
                )\
                return $b""";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<a/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = """
                let $a := <a/>\s
                let $aa := ($a, $a)\s
                for $b in ($aa union $aa\s
                )\
                return $b""";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<a/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = """
                let $a := <a/>\s
                let $aa := ($a, $a)\s
                for $b in ($aa except $aa\s
                )\
                return $b""";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
    }

    /**
     * @author Gev
     */
    @org.junit.jupiter.api.Test
    void inMemoryNodeSequences() throws XMLDBException {
        ResourceSet result;
        String query;

        XPathQueryService service =
                getTestCollection().getService(XPathQueryService.class);

        query = "let $c := (<a/>,<b/>) return <t>text{$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>text<a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,<b/>) return <t><text/>{$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>\n    <text/>\n    <a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,<b/>) return <t>{\"text\"}{$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>text<a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,\"b\") return <t>text{$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>text<a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,\"b\") return <t><text/>{$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>\n    <text/>\n    <a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,\"b\") return <t>{\"text\"}{$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>text<a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,<b/>) return <t>{<text/>,$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>\n    <text/>\n    <a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,<b/>) return <t>{\"text\",$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>text<a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,\"b\") return <t>{<text/>,$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>\n    <text/>\n    <a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);

        query = "let $c := (<a/>,\"b\") return <t>{\"text\",$c[1]}</t>";
        result = service.query(query);
        assertEquals("<t>text<a/>\n</t>", result.getResource(0).getContent(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void variable() throws XMLDBException {
        ResourceSet result;
        String query;
        XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        String message;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        query = """
                xquery version "1.0";
                declare namespace param="param";
                declare variable $param:a := "a";
                declare function param:a() {$param:a};
                let $param:a := "b"
                return ($param:a, $param:a)
                """;
        result = service.query(query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        assertEquals("b", result.getResource(0).getContent(), "XQuery: " + query);
        assertEquals("b", result.getResource(1).getContent(), "XQuery: " + query);
        query = """
                xquery version "1.0";
                declare namespace param="param";
                declare variable $param:a := "a";
                declare function param:a() {$param:a};
                let $param:a := "b"
                return param:a(), param:a()
                """;
        result = service.query(query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        assertEquals("a", result.getResource(0).getContent(), "XQuery: " + query);
        assertEquals("a", result.getResource(1).getContent(), "XQuery: " + query);
        query = """
                declare variable $foo := "foo1";
                let $foo := "foo2"
                for $bar in (1 to 1)
                  let $foo := "foo3"
                  return $foo
                """;
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("foo3", result.getResource(0).getContent(), "XQuery: " + query);

        try {
            message = "";
            query = """
                    xquery version "1.0";
                    declare variable $a := "1st instance";
                    declare variable $a := "2nd instance";
                    $a
                    """;
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XQST0049") > -1);
        query = """
                xquery version "1.0";
                declare namespace param="param";
                declare function param:f() { $param:a };
                declare variable $param:a := "a";
                param:f()
                """;
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("a", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <root> " +
                "<b name='1'>" +
                "  <c name='x'> " +
                "    <bar name='2'/> " +
                "    <bar name='3'> " +
                "      <bar name='4'/> " +
                "    </bar> " +
                "  </c> " +
                "</b> " +
                "</root> " +
                "let $b := for $bar in $a/b/c/bar " +
                "where ($bar/../@name = 'x') " +
                "return $bar " +
                "return $b";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("2", ((Element) resu.getContentAsDOM()).getAttribute("name"), "XQuery: " + query);
        resu = (XMLResource) result.getResource(1);
        assertEquals("3", ((Element) resu.getContentAsDOM()).getAttribute("name"), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void virtualNodesets() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        service.setProperty(OutputKeys.INDENT, "no");

        query = "let $node := (<c id='OK'><b id='cool'/></c>)/descendant::*/attribute::id " +
                "return <a>{$node}</a>";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<a id='cool'/>"));

        query = "let $node := (<c id='OK'><b id='cool'/></c>)/descendant-or-self::*/child::b " +
                "return <a>{$node}</a>";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<a><b id='cool'/></a>"));

        query = "let $node := (<c id='OK'><b id='cool'/></c>)/descendant-or-self::*/descendant::b " +
                "return <a>{$node}</a>";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<a><b id='cool'/></a>"));

        query = "let $doc := <a id='a'><b id='b'/></a> " +
                "return $doc/*/(<id>{@id}</id>)";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<id id='b' />"));
    }

    @org.junit.jupiter.api.Test
    void whereClause() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        service.setProperty(OutputKeys.INDENT, "no");

        query = "let $a := element node1 { " +
                "attribute id {'id'}, " +
                "element node1 {'1'}, " +
                "element node2 {'2'} " +
                "} " +
                "for $x in $a " +
                "where $x/@id eq 'id' " +
                "return $x";
        result = service.queryResource(NUMBERS_XML, query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<node1 id='id'><node1>1</node1><node2>2</node2></node1>"));
    }

    @org.junit.jupiter.api.Test
    void typedVariables() throws XMLDBException {
        ResourceSet result;
        String query;
        boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        query = """
                let $v as element()* := ( <assign/> , <assign/> )
                let $w := <r>{ $v }</r>
                let $x as element()* := $w/assign
                return $x
                """;
        result = service.query(query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        assertEquals(Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType(), "XQuery: " + query);
        assertEquals("assign", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName(), "XQuery: " + query);
        query = "let $v as node()* := ()\n" + "return $v";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "let $v as item()* := ()\n" + "return $v";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "let $v as empty-sequence() := ()\n" + "return $v";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "let $v as item() := ()\n" + "return $v";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown, "XQuery: " + query);
        query = "let $v as item()* := ( <a/> , 1 )\n" + "return $v";
        result = service.query(query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        assertEquals(Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType(), "XQuery: " + query);
        assertEquals("a", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName(), "XQuery: " + query);
        assertEquals("1", result.getResource(1).getContent(), "XQuery: " + query);
        query = "let $v as node()* := ( <a/> , 1 )\n" + "return $v";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);
        query = """
                let $v as item()* := ( <a/> , 1 )
                let $w as element()* := $v
                return $w
                """;
        try {
            exceptionThrown = false;
            result = service.query(query);
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);
        query = """
                declare variable $v as element()* := ( <assign/> , <assign/> );
                declare variable $w := <r>{ $v }</r>;
                declare variable $x as element()* := $w/assign;
                $x
                """;
        result = service.query(query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        assertEquals(Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType(), "XQuery: " + query);
        assertEquals("assign", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName(), "XQuery: " + query);
        query = "declare variable $v as node()* := ();\n" + "$v";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "declare variable $v as item()* := ();\n" + "$v";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "declare variable $v as empty-sequence() := ();\n" + "$v";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "declare variable $v as item() := ();\n" + "$v";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown, "XQuery: " + query);
        query = "declare variable $v as item()* := ( <a/> , 1 );\n" + "$v";
        result = service.query(query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        assertEquals(Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType(), "XQuery: " + query);
        assertEquals("a", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName(), "XQuery: " + query);
        assertEquals("1", result.getResource(1).getContent(), "XQuery: " + query);
        query = "declare variable $v as node()* := ( <a/> , 1 );\n" + "$v";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);
        query = """
                declare variable $v as item()* := ( <a/> , 1 );
                declare variable $w as element()* := $v;
                $w
                """;
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);
        query = """
                let $v as document-node() := doc('%s/test/%s')
                return $v
                """.formatted(XmldbURI.ROOT_COLLECTION, NUMBERS_XML);
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        //TODO : no way to test the node type ?
        //assertEquals( "XQuery: " + query, Node.DOCUMENT_NODE, ((XMLResource)result.getResource(0)));
        assertEquals("test", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void precedence() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        query = """
                xquery version "1.0";
                declare namespace blah="blah";
                declare variable $blah:param := "value-1";
                let $blah:param := "value-2"
                (:: FLWOR expressions have a higher precedence than the comma operator ::)
                return $blah:param, $blah:param
                """;
        result = service.query(query);
        assertEquals(2, result.getSize(), "XQuery: " + query);
        assertEquals("value-2", result.getResource(0).getContent(), "XQuery: " + query);
        assertEquals("value-1", result.getResource(1).getContent(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void improbableAxesAndNodeTestsCombinations() throws XMLDBException {
        ResourceSet result;
        String query;
        boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        query = "let $a := <x>a<!--b-->c</x>/self::comment() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/parent::comment() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/ancestor::comment() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/ancestor-or-self::comment() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);

//			This one is intercepted by the parser
        query = "let $a := <x>a<!--b-->c</x>/attribute::comment() return <z>{$a}</z>";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);

//			This one is intercepted by the parser
        query = "let $a := <x>a<!--b-->c</x>/namespace::comment() return <z>{$a}</z>";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);
        query = "let $a := <x>a<!--b-->c</x>/self::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/parent::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/ancestor::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/ancestor-or-self::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/child::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/descendant::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/descendant-or-self::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/preceding::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/preceding-sibling::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/following::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $a := <x>a<!--b-->c</x>/following-sibling::attribute() return <z>{$a}</z>";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<z/>", result.getResource(0).getContent(), "XQuery: " + query);

//			This one is intercepted by the parser
        query = "let $a := <x>a<!--b-->c</x>/namespace::attribute() return <z>{$a}</z>";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);

        //TODO : uncomment when PI are OK

        /*
        query = "let $a := <x>a<?foo ?>c</x>/self::processing-instruction('foo') return <z>{$a}</z>";
        result = service.query(query);
        assertEquals( "XQuery: " + query, 1, result.getSize() );
        assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());

        query = "let $a := <x>a<?foo ?>c</x>/parent::processing-instruction('foo') return <z>{$a}</z>";
        result = service.query(query);
        assertEquals( "XQuery: " + query, 1, result.getSize() );
        assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());

        query = "let $a := <x>a<?foo ?>c</x>/ancestor::processing-instruction('foo') return <z>{$a}</z>";
        result = service.query(query);
        assertEquals( "XQuery: " + query, 1, result.getSize() );
        assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());

        query = "let $a := <x>a<?foo ?>c</x>/ancestor-or-self::processing-instruction('foo') return <z>{$a}</z>";
        result = service.query(query);
        assertEquals( "XQuery: " + query, 1, result.getSize() );
        assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
         */

//			This one is intercepted by the parser
        query = "let $a := <x>a<?foo ?>c</x>/attribute::processing-instruction('foo') return <z>{$a}</z>";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);

//			This one is intercepted by the parser
        query = "let $a := <x>a<?foo ?>c</x>/namespace::processing-instruction('foo') return <z>{$a}</z>";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue(exceptionThrown);
    }

    @org.junit.jupiter.api.Test
    void namespace() throws XMLDBException {
        Resource doc;
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        String message;

        Collection testCollection = getTestCollection();
        doc = testCollection.createResource(MODULE1_NAME, BinaryResource.class);
        doc.setContent(module1);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(MODULE2_NAME, BinaryResource.class);
        doc.setContent(module2);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(NAMESPACED_NAME, XMLResource.class);
        doc.setContent(namespacedDocument);
        ((EXistResource) doc).setMimeType("application/xml");
        testCollection.storeResource(doc);

        XPathQueryService service =
                testCollection.getService(XPathQueryService.class);
        query = """
                xquery version "1.0";
                import module namespace blah="blah" at "%s/test/%s";
                (:: redefine existing prefix ::)
                declare namespace blah="bla";
                $blah:param
                """.formatted(LOCAL_DB_URI, MODULE1_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XQST0033") > -1);
        query = """
                xquery version "1.0";
                import module namespace blah="blah" at "%s/test/%s";
                (:: redefine existing prefix with same getUri ::)
                declare namespace blah="blah";
                declare variable $blah:param := "value-2";
                $blah:param
                """.formatted(LOCAL_DB_URI, MODULE1_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XQST0033") > -1);
        query = """
                xquery version "1.0";
                import module namespace foo="ho" at "%s/test/%s";
                $foo:bar
                """.formatted(LOCAL_DB_URI, MODULE1_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("does not match namespace URI") > -1);
        // module2 declares `module namespace foo=""`, which is a static error per
        // XQuery 3.1 section 4.18 (XQST0088: zero-length target namespace literal).
        // The error originates while compiling the imported module, regardless of
        // the prefix or URI used in the importing module's `import module` clause.
        query = """
                xquery version "1.0";
                import module namespace foo="ho" at "%s/test/%s";
                $bar
                """.formatted(LOCAL_DB_URI, MODULE2_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XQST0088") > -1,
                "Expected XQST0088 for empty module namespace literal, got: " + message);
        query = """
                xquery version "1.0";
                import module namespace foo="blah" at "%s/test/%s";
                $bar
                """.formatted(LOCAL_DB_URI, MODULE2_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XQST0088") > -1,
                "Expected XQST0088 for empty module namespace literal, got: " + message);
        query = "declare namespace x = \"http://www.foo.com\"; \n" +
                "let $a := doc('" + XmldbURI.ROOT_COLLECTION + "/test/" + NAMESPACED_NAME + "') \n" +
                "return $a//x:edition";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "declare namespace x = \"http://www.foo.com\"; \n" +
                "declare namespace y = \"http://exist.sourceforge.net/dc-ext\"; \n" +
                "let $a := doc('" + XmldbURI.ROOT_COLLECTION + "/test/" + NAMESPACED_NAME + "') \n" +
                "return $a//y:edition";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("<x:edition xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:edition>", result.getResource(0).getContent(),
                "XQuery: " + query);
        query = "<result xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>{//rdf:Description}</result>";
        result = service.query(query);
        assertEquals("""
                <result xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description id="3">
                        <dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">title</dc:title>
                        <dc:creator xmlns:dc="http://purl.org/dc/elements/1.1/">creator</dc:creator>
                        <x:place xmlns:x="http://exist.sourceforge.net/dc-ext">place</x:place>
                        <x:edition xmlns:x="http://exist.sourceforge.net/dc-ext">place</x:edition>
                    </rdf:Description>
                </result>""",
                result.getResource(0).getContent(),
                query);
        query = "<result xmlns='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>{//Description}</result>";
        result = service.query(query);
        assertEquals("""
                <result xmlns="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" id="3">
                        <dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">title</dc:title>
                        <dc:creator xmlns:dc="http://purl.org/dc/elements/1.1/">creator</dc:creator>
                        <x:place xmlns:x="http://exist.sourceforge.net/dc-ext">place</x:place>
                        <x:edition xmlns:x="http://exist.sourceforge.net/dc-ext">place</x:edition>
                    </rdf:Description>
                </result>""",
                result.getResource(0).getContent(),
                "XQuery: " + query);

        //Interesting one : let's see with XQuery gurus :-)
        //declare namespace fn="";
        //fn:current-time()
        /*
        If the URILiteral part of a namespace declaration is a zero-length string,
        any existing namespace binding for the given prefix is removed from
        the statically known namespaces. This feature provides a way
        to remove predeclared namespace prefixes such as local.
         */
        query = "declare option exist:serialize 'indent=no';" +
                "for $x in <parent4 xmlns=\"http://www.example.com/parent4\"><child4/></parent4> " +
                "return <new>{$x//*:child4}</new>";
        result = service.query(query);
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<new><child4 xmlns='http://www.example.com/parent4'/></new>"));
    }

    @org.junit.jupiter.api.Test
    void namespaceWithTransform() throws XMLDBException {
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);

        String query =
                """
                xquery version "1.0";
                declare namespace transform="http://exist-db.org/xquery/transform";
                declare variable $xml :=\s
                	<node>text</node>
                ;
                declare variable $xslt :=\s
                	<xsl:stylesheet xmlns="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
                		<xsl:template match="node">
                			<div><xsl:value-of select="."/></div>
                		</xsl:template>
                	</xsl:stylesheet>
                ;
                <html xmlns="http://www.w3.org/1999/xhtml">
                	<body>
                		{transform:transform($xml, $xslt, ())}
                	</body>
                </html>""";

        ResourceSet result = service.query(query);

        //check there is one result
        assertEquals(1, result.getSize());

        String content = (String) result.getResource(0).getContent();

        //check the namespace
        assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

        //check the content
        assertTrue(content.indexOf("<div>text</div>") > -1);
    }

    @org.junit.jupiter.api.Test
    void module() throws XMLDBException {
        Resource doc;
        ResourceSet result;
        String query;
        String message;

        Collection testCollection = getTestCollection();
        doc = testCollection.createResource(MODULE1_NAME, BinaryResource.class);
        doc.setContent(module1);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(MODULE3_NAME, BinaryResource.class);
        doc.setContent(module3);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(MODULE4_NAME, BinaryResource.class);
        doc.setContent(module4);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(FATHER_MODULE_NAME, BinaryResource.class);
        doc.setContent(fatherModule);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(CHILD1_MODULE_NAME, BinaryResource.class);
        doc.setContent(child1Module);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(CHILD2_MODULE_NAME, BinaryResource.class);
        doc.setContent(child2Module);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        XPathQueryService service =
                testCollection.getService(
                XPathQueryService.class);
        query = """
                xquery version "1.0";
                import module namespace blah="blah" at "%s/test/%s";
                $blah:param
                """.formatted(LOCAL_DB_URI, MODULE1_NAME);
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("value-1", result.getResource(0).getContent(), "XQuery: " + query);

//            query = "xquery version \"1.0\";\n" + "import module namespace blah=\"blah\" at \"" + getUri + "/test/" + MODULE1_NAME + "\";\n" + "(:: redefine variable ::)\n" + "declare variable $blah:param := \"value-2\";\n" + "$blah:param";
//            try {
//                message = "";
//                result = service.query(query);
//            } catch (XMLDBException e) {
//                message = e.getMessage();
//            }
//            assertTrue(message.indexOf("XQST0049") > -1);
        query = """
                xquery version "1.0";
                import module namespace blah="blah" at "%s/test/%s";
                declare namespace blah2="blah";
                $blah2:param
                """.formatted(LOCAL_DB_URI, MODULE1_NAME);
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("value-1", result.getResource(0).getContent(), "XQuery: " + query);
        query = """
                xquery version "1.0";
                import module namespace blah="bla" at "%s/test/%s";
                $blah:param
                """.formatted(LOCAL_DB_URI, MODULE1_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("does not match namespace URI") > -1);
        query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                $foo:bar, $foo:bar1, $foo:bar2
                """.formatted(LOCAL_DB_URI, FATHER_MODULE_NAME);
        result = service.query(query);
        assertEquals(3, result.getSize(), "XQuery: " + query);
        assertEquals("bar", result.getResource(0).getContent(), "XQuery: " + query);
        assertEquals("bar1", result.getResource(1).getContent(), "XQuery: " + query);
        assertEquals("bar2", result.getResource(2).getContent(), "XQuery: " + query);

//			Non-transitive inheritance check
        query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                declare namespace foo1="foo1";
                $foo1:bar
                """.formatted(LOCAL_DB_URI, FATHER_MODULE_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPST0008") > -1);

//			Non-transitive inheritance check
        query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                declare namespace foo2="foo2";
                $foo2:bar
                """.formatted(LOCAL_DB_URI, FATHER_MODULE_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPST0008") > -1);
        query = """
                xquery version "1.0";
                import module namespace foo1="foo" at "%s/test/%s";
                import module namespace foo2="foo" at "%s/test/%s";
                $foo1:bar
                """.formatted(LOCAL_DB_URI, CHILD1_MODULE_NAME, LOCAL_DB_URI, CHILD1_MODULE_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
//			Should be a XQST0047 error
        assertTrue(message.indexOf("does not match namespace URI") > -1);
        query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                $bar:bar
                """.formatted(LOCAL_DB_URI, MODULE3_NAME);
        try {
            message = "";
            result = service.query(query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("No namespace defined for prefix") > -1);
        query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                foo:bar()
                """.formatted(LOCAL_DB_URI, MODULE4_NAME);
        try {
            message = "";
            result = service.query(query);
            //WARNING !
            //This result is false ! The external vairable has not been resolved
            //Furthermore it is not in the module's namespace !
            assertEquals(0, result.getSize(), "XQuery: " + query);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        //This is the good result !
        //assertTrue(message.indexOf("XQST0048") > -1);
    }

    @org.junit.jupiter.api.Test
    void modulesAndNS() throws XMLDBException {
        Collection testCollection = getTestCollection();
        Resource doc = testCollection.createResource(MODULE7_NAME, BinaryResource.class);
        doc.setContent(module7);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        XPathQueryService service = testCollection.getService(XPathQueryService.class);
        service.setProperty(OutputKeys.INDENT, "no");
        String query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                <div xmlns='http://www.w3.org/1999/xhtml'>{ foo:link() }</div>
                """.formatted(LOCAL_DB_URI, MODULE7_NAME);
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize());
        result.getResource(0).getContent();
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<div xmlns='http://www.w3.org/1999/xhtml'><a xmlns=\"\" href='#'>Link</a></div>"));

        query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                <div xmlns='http://www.w3.org/1999/xhtml'>{ foo:copy(<a>Link</a>) }</div>
                """.formatted(LOCAL_DB_URI, MODULE7_NAME);
        result = service.query(query);
        assertEquals(1, result.getSize());
        result.getResource(0).getContent();
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<div xmlns='http://www.w3.org/1999/xhtml'><a>Link</a></div>"));
    }

    @org.junit.jupiter.api.Test
    void importExternalClasspathMainModule() throws EXistException, IOException, PermissionDeniedException, XPathException, QName.IllegalQNameException {
        final long timestamp = System.currentTimeMillis();
        final BrokerPool brokerPool = BrokerPool.getInstance();
        try (final DBBroker broker = brokerPool.getBroker()) {
            final org.exist.source.Source source = SourceFactory.getSource(broker, "/", "resource:org/exist/xquery/external-classpath-main-module.xq", false);

            final XQuery xquery = brokerPool.getXQueryService();
            final XQueryPool queryPool = brokerPool.getXQueryPool();

            CompiledXQuery compiled = null;
            XQueryContext context = null;
            try {
                compiled = queryPool.borrowCompiledXQuery(broker, source);
                if (compiled == null) {
                    context = new XQueryContext(brokerPool);
                } else {
                    context = compiled.getContext();
                    context.prepareForReuse();
                }

                context.declareVariable(new QName("s"), new IntegerValue(timestamp));

                if(compiled == null) {
                    compiled = xquery.compile(context, source);
                }

                final Sequence result = xquery.execute(broker, compiled, null, null);
                assertEquals(1, result.getItemCount());
                final Item item = result.itemAt(0);
                assertTrue(Type.subTypeOf(item.getType(), Type.NODE));

                final Source expected = Input.fromString("<echo>" + timestamp + "</echo>").build();
                final Source actual = Input.fromNode((Node)item).build();
                final Diff diff = DiffBuilder.compare(expected)
                        .withTest(actual)
                        .checkForSimilar()
                        .build();
                assertFalse(diff.hasDifferences(), diff.toString());

            } finally {
                if (compiled != null) {
                    compiled.reset();
                }
                if (context != null) {
                    context.reset();
                }
                if (compiled != null) {
                    queryPool.returnCompiledXQuery(source, compiled);
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void importExternalClasspathLibraryModule() throws XMLDBException {
        final long timestamp = System.currentTimeMillis();
        final Collection testCollection = getTestCollection();
        final Resource doc = testCollection.createResource("import-external-classpath.xq", BinaryResource.class);
        doc.setContent(
                "import module namespace ext1 = \"http://import-external-classpath-library-module-test.com\" at \"resource:org/exist/xquery/external-classpath-library-module.xqm\";\n"
                + "ext1:echo(" + timestamp + ")"
        );
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        final EXistXPathQueryService service = (EXistXPathQueryService) testCollection.getService(XPathQueryService.class);
        final ResourceSet resourceSet = service.executeStoredQuery("/db/test/import-external-classpath.xq");

        assertEquals(1, resourceSet.getSize());

        final Resource resource = resourceSet.getResource(0);
        final Source expected = Input.fromString("<echo>" + timestamp + "</echo>").build();
        final Source actual = Input.fromString(resource.getContent().toString()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForIdentical()
                .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    @org.junit.jupiter.api.Test
    void doubleDocNode2078755() throws XMLDBException {
        Collection testCollection = getTestCollection();
        Resource doc = testCollection.createResource(MODULE8_NAME, BinaryResource.class);
        doc.setContent(module8);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        XPathQueryService service = testCollection.getService(XPathQueryService.class);
        service.setProperty(OutputKeys.INDENT, "no");
        String query = """
                import module namespace dr = "double-root2" at "%s/test/%s";
                let $doc1 := dr:documentIn()
                let $count1 := count($doc1/element())
                let $doc2 := dr:documentIn()
                let $count2 := count($doc2/element())
                return ($count1, $count2)
                """.formatted(LOCAL_DB_URI, MODULE8_NAME);

        ResourceSet result = service.query(query);
        assertEquals(2, result.getSize());
        assertEquals("1", result.getResource(0).getContent().toString());
        assertEquals("1", result.getResource(1).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void globalVars() throws XMLDBException {
        Collection testCollection = getTestCollection();
        Resource doc = testCollection.createResource(MODULE5_NAME, BinaryResource.class);
        doc.setContent(module5);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        doc = testCollection.createResource(MODULE6_NAME, BinaryResource.class);
        doc.setContent(module6);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);
        XQueryService service = (XQueryService) testCollection.getService(XPathQueryService.class);
        String query = """
                xquery version "1.0";
                import module namespace foo="foo" at "%s/test/%s";
                $foo:bar
                """.formatted(LOCAL_DB_URI, MODULE5_NAME);
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize());
        assertEquals("bar", result.getResource(0).getContent());
        query = "xquery version \"1.0\";\n" + "declare variable $local:a := 'abc';" + "$local:a";
        result = service.query(query);
        assertEquals(1, result.getSize());
        assertEquals("abc", result.getResource(0).getContent());
        boolean gotException = false;
        try {
            query = """
                    xquery version "1.0";
                    import module namespace foo="foo" at "%s/test/%s";
                    $foo:bar
                    """.formatted(LOCAL_DB_URI, MODULE6_NAME);
            result = service.query(query);
        } catch (XMLDBException e) {
            assertTrue(e.getMessage().indexOf("err:XQST0049") > -1, "Test should generate err:XQST0049, got: " + e.getMessage());
            gotException = true;
        }
        assertTrue(gotException, "Duplicate global variable should generate error");
        gotException = false;
        try {
            query = "xquery version \"1.0\";\n" + "declare variable $local:a := 'abc';" + "declare variable $local:a := 'abc';" + "$local:a";
            result = service.query(query);
        } catch (XMLDBException e) {
            assertTrue(e.getMessage().indexOf("err:XQST0049") > -1, "Test should generate err:XQST0049, got: " + e.getMessage());
            gotException = true;
        }
        assertTrue(gotException, "Duplicate global variable should generate error");
    }

    @org.junit.jupiter.api.Test
    void functionDoc() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        query = "doc('" + XmldbURI.ROOT_COLLECTION + "/test/" + NUMBERS_XML + "')";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);

        Node n = ((XMLResource) result.getResource(0)).getContentAsDOM();
        assertThat(n.toString(), hasIdenticalXml(numbers));
        //ignore eXist namespace's attributes
        //assertEquals(1, d.getAllDifferences().size());

        query = "let $v := ()\n" + "return doc($v)";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "doc('" + XmldbURI.ROOT_COLLECTION + "/test/dummy" + NUMBERS_XML + "')";
        try {
            exceptionThrown = false;
            result = service.query(query);
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        //TODO : to be decided !
        //assertTrue(exceptionThrown);
        assertEquals(0, result.getSize());
        query = "doc-available('" + XmldbURI.ROOT_COLLECTION + "/test/" + NUMBERS_XML + "')";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("true", result.getResource(0).getContent(), "XQuery: " + query);
        query = "let $v := ()\n" + "return doc-available($v)";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("false", result.getResource(0).getContent(), "XQuery: " + query);
        query = "doc-available('" + XmldbURI.ROOT_COLLECTION + "/test/dummy" + NUMBERS_XML + "')";
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("false", result.getResource(0).getContent(), "XQuery: " + query);
    }

    /**
     * This test only works if there is an Internet access
     */
    @org.junit.jupiter.api.Test
    void functionDocExternal() throws XMLDBException {
        boolean hasInternetAccess = false;

        //Checking that we have an Internet Access
        try {
            URL url = URI.create("http://www.w3.org/").toURL();
            URLConnection con = url.openConnection();
            if (con instanceof HttpURLConnection httpConnection) {
                hasInternetAccess = (httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK);
            }
        } catch(MalformedURLException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            //Ignore
        }
        assumeTrue(hasInternetAccess, "No Internet access: skipping 'functionDocExternal' tests");

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        String query = "if (doc-available(\"http://www.w3.org/XML/Core/\")) then doc(\"http://www.w3.org/XML/Core/\") else ()";
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        query = "if (doc-available(\"http://www.w3.org/XML/dummy\")) then doc(\"http://www.w3.org/XML/dummy\") else ()";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "doc-available(\"http://www.w3.org/XML/Core/\")";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("true", result.getResource(0).getContent(), "XQuery: " + query);
        query = "doc-available(\"http://www.google.com/404\")";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("false", result.getResource(0).getContent(), "XQuery: " + query);
        //A redirected 404
        query = "doc-available(\"http://java.sun.com/404\")";
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("false", result.getResource(0).getContent(), "XQuery: " + query);
        query = "if (doc-available(\"file:////doesnotexist.xml\")) then doc(\"file:////doesnotexist.xml\") else ()";
        result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
        query = "doc-available(\"file:////doesnotexist.xml\")";
        result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals("false", result.getResource(0).getContent(), "XQuery: " + query);
    }

    private String makeString(final int n) {
        final char buf[] = new char[n];
        Arrays.fill(buf, 'a');
        return new String(buf);
    }

    @org.junit.jupiter.api.Test
    void textConstructor() throws XMLDBException {
        String query = "text{ \"a\" }, text{ \"b\" }, text{ \"c\" }, text{ \"d\" }";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(4, result.getSize(), "XQuery: " + query);

        assertEquals("a", result.getResource(0).getContent().toString(), "XQuery: " + query);
        assertEquals("b", result.getResource(1).getContent().toString(), "XQuery: " + query);
        assertEquals("c", result.getResource(2).getContent().toString(), "XQuery: " + query);
        assertEquals("d", result.getResource(3).getContent().toString(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void userEscalationForInMemoryNodes() throws XMLDBException {
        String query = "xmldb:login(\"xmldb:exist:///db\", \"guest\", \"guest\"), sm:id()/sm:id/sm:effective/sm:username/text(), let $node := <node id=\"1\">value</node>, $null := $node[@id eq '1'] return sm:id()/sm:id/sm:effective/sm:username/text()";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        Resource loggedIn = result.getResource(0);
        Resource currentUser = result.getResource(1);
        Resource currentUserAfterInMemoryOp = result.getResource(2);

        //check the login as guest worked
        assertEquals("true", loggedIn.getContent().toString(), "Logged in as quest: " + loggedIn.getContent().toString());

        //check that we are guest
        assertEquals("guest", currentUser.getContent().toString(), "After Login as guest, User should be guest and is: " + currentUser.getContent().toString());

        //check that we are still guest
        assertEquals("guest", currentUserAfterInMemoryOp.getContent().toString(), "After Query, User should still be guest and is: " + currentUserAfterInMemoryOp.getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void constructedAttributeValue() throws XMLDBException {
        String query = "let $attr := attribute d { \"xxx\" } " + "return string($attr)";
        XPathQueryService service = getTestCollection().getService(
                XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize());
        assertEquals("xxx", result.getResource(0).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void attributeAxis() throws XMLDBException {
        ResourceSet result;
        String query;
        XMLResource resu;

        @SuppressWarnings("unused")
        String large = createXMLContentWithLargeString();
        XPathQueryService service =
                storeXMLStringAndGetQueryService(file_name, xml);

        query = "let $node := (<c id=\"OK\">b</c>)/descendant-or-self::*/attribute::id " +
                "return <a>{$node}</a>";
        result = service.query(query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("OK", ((Element) resu.getContentAsDOM()).getAttribute("id"), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void instanceOfDocumentNode() throws XMLDBException {
        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

        String query = "let $doc := document { <element/> } " +
                "return $doc/root() instance of document-node()";
        ResourceSet result = service.query(query);
        assertEquals("true", result.getResource(0).getContent().toString(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void instanceOfNamespaceNode() throws XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("namespace test { 'test' } instance of namespace-node()");
        assertEquals(1,result.getSize());
        assertEquals("true", result.getResource(0).getContent().toString());

        result = existEmbeddedServer.executeQuery("<x/> instance of namespace-node()");
        assertEquals(1, result.getSize());
        assertEquals("false", result.getResource(0).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void largeAttributeSimple() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;

        String large = createXMLContentWithLargeString();
        XPathQueryService service =
                storeXMLStringAndGetQueryService(file_name, xml);

        query = "doc('" + file_name + "') / details/metadata[@docid= '" + large + "' ]";
        result = service.queryResource(file_name, query);
        assertEquals(nbElem, result.getSize(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void cdataSerialization() throws XMLDBException {
        ResourceSet result;
        String query;
        XMLResource resu;

        XPathQueryService service = getTestCollection().getService(
                XPathQueryService.class);

        query = "let $doc := document{ <root><![CDATA[gaga]]></root> } " +
                "return $doc/root/string()";
        result = service.query(query);
        resu = (XMLResource) result.getResource(0);
        assertEquals("gaga", resu.getContent().toString(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void cdataQuery() throws XMLDBException {
        ResourceSet result;
        String query;
        XMLResource resu;
        final String xml = "<root><node><![CDATA[world]]></node></root>";

        XPathQueryService service =
                storeXMLStringAndGetQueryService("cdata.xml", xml);
        service.setProperty(OutputKeys.INDENT, "no");
        query = "//text()";
        result = service.queryResource("cdata.xml", query);
        assertEquals(1, result.getSize());
        resu = (XMLResource) result.getResource(0);
        assertEquals("world", resu.getContent().toString(), "XQuery: " + query);

        query = "//node/text()";
        result = service.queryResource("cdata.xml", query);
        assertEquals(1, result.getSize());
        resu = (XMLResource) result.getResource(0);
        assertEquals("world", resu.getContent().toString(), "XQuery: " + query);

        query = "//node/node()";
        result = service.queryResource("cdata.xml", query);
        assertEquals(1, result.getSize());
        resu = (XMLResource) result.getResource(0);
        assertEquals("world", resu.getContent().toString(), "XQuery: " + query);

        query = "/root[node = 'world']";
        result = service.queryResource("cdata.xml", query);
        assertEquals(1, result.getSize());

        // NOTE - no cdata-section-elements specified for XDM serialization
        resu = (XMLResource) result.getResource(0);
        assertEquals("<root><node>world</node></root>", resu.getContent().toString(), "XQuery: " + query);
    }

    /**
     * Tests that no result will be returned if an attribute's value is selected on a node which wasn't found
     */
    @org.junit.jupiter.api.Test
    void attributeForNoResult() throws XMLDBException {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " + //
                "return /a[./c]/@id/string()";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(0, result.getSize());
    }

    @org.junit.jupiter.api.Test
    void largeAttributeContains() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;

        @SuppressWarnings("unused")
        String large = createXMLContentWithLargeString();
        XPathQueryService service =
                storeXMLStringAndGetQueryService(file_name, xml);

        query = "doc('" + file_name + "') / details/metadata[ contains(@docid, 'aa') ]";
        result = service.queryResource(file_name, query);
        assertEquals(nbElem, result.getSize(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void largeAttributeKeywordOperator() throws XMLDBException {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;

        String large = createXMLContentWithLargeString();
        XPathQueryService service =
                storeXMLStringAndGetQueryService(file_name, xml);

        query = "doc('" + file_name + "') / details/metadata[ @docid = '" + large + "' ]";
        result = service.queryResource(file_name, query);
        assertEquals(nbElem, result.getSize(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void attributeNamespace() throws XMLDBException {

        String query = "declare function local:copy($nodes as node()*) as node()* {" + "for $n in $nodes return " + "if ($n instance of element()) then " + "  element {node-name($n)} {(local:copy($n/@*), local:copy($n/node()))} " + "else if ($n instance of attribute()) then " + "  attribute {node-name($n)} {$n} " + "else if ($n instance of text()) then " + "  text {$n} " + "else " + "  <Other/>" + "};" + "let $c :=" + "<c:C  xmlns:c=\"http://c\" xmlns:d=\"http://d\" d:d=\"ddd\">" + "ccc" + "</c:C>" + "return local:copy($c)";

        XPathQueryService service = getTestCollection().getService(
                XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize());
        // XQuery 3.1 §2: "the relative order of namespace nodes that share a parent is also implementation dependent."
        final Source expected = Input.fromString("<c:C xmlns:c=\"http://c\" xmlns:d=\"http://d\" d:d=\"ddd\">ccc</c:C>").build();
        final Source actual = Input.fromString(result.getResource(0).getContent().toString()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForIdentical()
                .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    @org.junit.jupiter.api.Test
    void nameConflicts() throws XMLDBException {
        String query = "let $a := <name name=\"Test\"/> return <wrap>{$a//@name}</wrap>";

        XPathQueryService service = getTestCollection().getService(
                XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize());
        assertEquals("<wrap name=\"Test\"/>", result.getResource(0).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void serialization() throws XMLDBException {
        @SuppressWarnings("unused")
		ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        String message;

            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            query = "let $a := <test><foo name='bar'/><foo name='bar'/></test>" +
                    "return <attribute>{$a/foo/@name}</attribute>";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XQDY0025") > -1);

            query = "let $a := <foo name='bar'/> return $a/@name";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
        //TODO : how toserialize this resultand get the error ? -pb
        //assertTrue(message.indexOf("XQDY0025") > -1);
    }

    /** CAUTION side effect on field xml
     * @return the large string contained in the atrbute(s)
     */
    private String createXMLContentWithLargeString() {
        String large = makeString(stringSize);
        String head = "<details format='xml'>";
        String elem = "<metadata docid='" + large + "'></metadata>";
        String tail = "</details>";
        xml = head;
        for (int i = 0; i < nbElem; i++) {
            xml += elem;
        }
        xml += tail;
        return large;
    }

    @org.junit.jupiter.api.Test
    void retrieveLargeAttribute() throws XMLDBException {
        createXMLContentWithLargeString();
        storeXMLStringAndGetQueryService(file_name, xml);
        final XMLResource res = (XMLResource) getTestCollection().getResource(file_name);
        assertTrue(res != null);
    }

    @org.junit.jupiter.api.Test
    void largeAttributeText() throws XMLDBException {
        final String large = "challengesininformationretrievalandlanguagemodelingreportofaworkshopheldatthecenterforintelligentinformationretrievaluniversityofmassachusettsamherstseptember2002-extdocid-howardturtlemarksandersonnorbertfuhralansmeatonjayaslamdragomirradevwesselkraaijellenvoorheesamitsinghaldonnaharmanjaypontejamiecallannicholasbelkinjohnlaffertylizliddyronirosenfeldvictorlavrenkodavidjharperrichschwartzjohnpragerchengxiangzhaijinxixusalimroukosstephenrobertsonandrewmccallumbrucecroftrmanmathasuedumaisdjoerdhiemstraeduardhovyralphweischedelthomashofmannjamesallanchrisbuckleyphilipresnikdavidlewis2003";
        String xml = "<details format='xml'><metadata docid='" + large +
                "'></metadata></details>";
        final String FILE_NAME = "detail_xml.xml";
        XPathQueryService service = storeXMLStringAndGetQueryService(FILE_NAME, xml);

        String query = "doc('" + FILE_NAME + "') / details/metadata[@docid= '" + large + "' ]";
        ResourceSet result = service.queryResource(FILE_NAME, query);
        assertEquals(1, result.getSize());

        xml = "<details format='xml'><metadata><docid>" + large +
                "</docid></metadata></details>";
        service = storeXMLStringAndGetQueryService(FILE_NAME, xml);

        query = "doc('"+ FILE_NAME+"') / details/metadata[ docid= '" + large + "' ]";
        result = service.queryResource(FILE_NAME, query);
        assertEquals(1, result.getSize());
    }

    @org.junit.jupiter.api.Test
    void xupdateWithAdjacentTextNodes() throws XMLDBException {
        String query = "let $name := xmldb:store('/db' , 'xupdateTest.xml', <test>aaa</test>)" +
                "let $xu :=" +
                "<xu:modifications xmlns:xu='http://www.xmldb.org/xupdate' version='1.0'>" +
                "<xu:append select='/test'>" +
                "<xu:text>yyy</xu:text>" +
                "</xu:append>" +
                "</xu:modifications>" +
                "let $count := xmldb:update('/db' , $xu)" +
                "for $textNode in doc('/db/xupdateTest.xml')/test/text()" +
                "	return <text id='{util:node-id($textNode)}'>{$textNode}</text>";

        XPathQueryService service =
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
    }

    //TODO : understand this test and make sure that the expected result is correct
    //expected:<3> but was:<2>
    @Disabled
    @org.junit.jupiter.api.Test
    void xupdateAttributesAndElements() throws XMLDBException {
        ResourceSet result;
        String query;

        query =
                "declare function local:update-game($game) {\n" +
                "local:update-frames($game),\n" +
                "update insert\n" +
                "<stats>\n" +
                "<strikes>4</strikes>\n" +
                "<spares>\n" +
                "<attempted>4</attempted>\n" +
                "</spares>\n" +
                "</stats>\n" +
                "into $game\n" +
                "};\n" +
                "declare function local:update-frames($game) {\n" +
                // Uncomment this, and it works:
                //"for $frame in $game/frame return update insert <processed/> into $frame,\n" +
                "for $frame in $game/frame\n" +
                "return update insert attribute points {4} into $frame\n" +
                "};\n" +
                "let $series := doc('bowling.xml')/series\n" +
                "let $nul1 := for $game in $series/game return local:update-game($game)\n" +
                "return $series/game/stats\n";

        XPathQueryService service =
                storeXMLStringAndGetQueryService(BOWLING_XML, bowling);
        result = service.query(query);
        assertEquals(3, result.getSize(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void nodeName() throws XMLDBException {
        String query = "declare function local:name($node as node()) as xs:string? { " + " if ($node/self::element() != '') then name($node) else () }; " + " let $n := <!-- Just a comment! --> return local:name($n) ";
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
    }

    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1691112&group_id=17691&atid=117691
     */
    //DWES Funny in sandbox and REST it fails ; here it is OK... sometimes
    @org.junit.jupiter.api.Test
    void order1691112() throws XMLDBException {

        String query = "declare namespace tt = \"http://example.com\";" +
                "declare function tt:function( $function as element(Function)) {" +
                "  let $functions :=" +
                "    for $subfunction in $function/Function" +
                "    return tt:function($subfunction)" +
                "   let $unused := distinct-values($functions/NonExistingElement)" +
                "  return" +
                "  <Function>" +
                "  {" +
                "    $function/Name," +
                "    $functions" +
                "  }" +
                "  </Function>" +
                "};" +
                "let $funcs :=" +
                "  <Function>" +
                "      <Name>Airmount 1</Name>" +
                "      <Function>" +
                "          <Name>Position</Name>" +
                "      </Function>" +
                "      <Function>" +
                "          <Name>Velocity</Name>" +
                "      </Function>" +
                "  </Function>" +
                "return" +
                "  tt:function($funcs)";

        String expectedresult =
                """
                <Function>
                    <Name>Airmount 1</Name>
                    <Function>
                        <Name>Position</Name>
                    </Function>
                    <Function>
                        <Name>Velocity</Name>
                    </Function>
                </Function>""";

        for (int i = 0; i < 25; i++) { // repeat a few times

            XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());
            assertEquals(expectedresult, result.getResource(0).getContent().toString());
        }
    }

    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1691177&group_id=17691&atid=117691
     */
    @org.junit.jupiter.api.Test
    void attribute1691177() throws XMLDBException {
        String query = "declare namespace xmldb = \"http://exist-db.org/xquery/xmldb\"; " + "let $uri := xmldb:store(\"/db\", \"insertAttribDoc.xml\", <C/>) " + "let $node := doc($uri)/element() " + "let $attrib := <Value f=\"ATTRIB VALUE\"/>/@* " + "return update insert $attrib into $node  ";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(0, result.getSize(), "XQuery: " + query);
    }

    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1691174&group_id=17691&atid=117691
     */
    @org.junit.jupiter.api.Test
    void attribute1691174() throws XMLDBException {
        String query = "declare function local:show($el1, $el2) { " 
                + "	<Foobar> "
                + "	{ (\"first: \", $el1, \" second: \", $el2) } "
                + "	</Foobar> " + "}; "
                + "declare function local:attrib($n as node()) { "
                + "	<Attrib>{$n}</Attrib> "
                + "}; "
                + "local:show( "
                + "	<Attrib name=\"value\"/>, "
                + "	local:attrib(attribute name {\"value\"})  (: Exist bug! :) "
                + ")  ";
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void qnameToString1632365() throws XMLDBException {
        String query = "let $qname := QName(\"http://test.org\", \"test:name\") " +
                "return xs:string($qname)";
        String expectedresult = "test:name";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(expectedresult, result.getResource(0).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void comments1715035() throws XMLDBException {
        String query = "<!-- < aa > -->";
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals(query, result.getResource(0).getContent().toString());

        query = "<?pi \"<\"aa\">\"?>";
        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);
        assertEquals(query, result.getResource(0).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void documentNode1730690() throws XMLDBException {
        String query = "let $doc := document { <element/> } " +
                "return $doc/root() instance of document-node()";
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals("true", result.getResource(0).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void enclosedExpressions() throws XMLDBException {
        String query = "let $a := <docum><titolo>titolo</titolo><autor>giulio</autor></docum> " +
                "return <row>{$a/titolo/text()} {' '} {$a/autor/text()}</row>";
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertThat(result.getResource(0).getContent().toString(), hasSimilarXml("<row>titolo giulio</row>"));
    }

    @org.junit.jupiter.api.Test
    void orderCompareAtomicType1733265() throws XMLDBException {
        String query = "( ) = \"A\"";
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals("false", result.getResource(0).getContent().toString());

        query = "\"A\" = ( )";
        result = service.query(query);
        assertEquals("false", result.getResource(0).getContent().toString());
    }

    @org.junit.jupiter.api.Test
    void positionInPredicate() throws XMLDBException {
        String query = "let $example := <Root> <Element>1</Element> <Element>2</Element> </Root>" +
                "return  $example/Element[1] ";
        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals("<Element>1</Element>", result.getResource(0).getContent().toString());

        query = "let $example := <Root> <Element>1</Element> <Element>2</Element> </Root>" +
                "return  $example/Element[position() = 1] ";
        result = service.query(query);
        assertEquals("<Element>1</Element>", result.getResource(0).getContent().toString());
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1740880
     */
    @org.junit.jupiter.api.Test
    void elementConstructionWithNamespace1740880() throws XMLDBException {
        String query = "let $a := <foo:Bar xmlns:foo=\"urn:foo\"/> " +
                "let $b := element { QName(\"urn:foo\", \"foo:Bar\") } { () } " +
                "return deep-equal($a, $b) ";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);
        assertEquals("true", result.getResource(0).getContent().toString(), "Oops");
    }

    /**
     * http://sourceforge.net/support/tracker.php?aid=1740883
     */
    @org.junit.jupiter.api.Test
    void noErrorNeOperatorWithSequence1740883() {
        try {
            String query = "let $foo := <Foo> <Bar>A</Bar> <Bar>B</Bar> <Bar>C</Bar> </Foo> " +
                    "return $foo[Bar ne \"B\"]";

            XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
            @SuppressWarnings("unused")
			ResourceSet result = service.query(query);

            fail("result should have yielded into an error like " +
                    "'A sequence of more than one item is not allowed as the first " + "operand of 'ne'");
        } catch (XMLDBException e) {
            if (!e.getMessage().contains("one item")) {
                LOG.error(e.getMessage(), e);
                fail(e.getMessage());
            }
        }
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1740885
     */
    @org.junit.jupiter.api.Test
    void neOperatorDoesNotWork1740885() throws XMLDBException {
        String query = "let $foo := <Foo> <Bar>A</Bar> <Bar>B</Bar> <Bar>C</Bar> </Foo>" +
                "return $foo/Bar[. ne \"B\"]";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals("<Bar>A</Bar>", result.getResource(0).getContent().toString(), "First");
        assertEquals("<Bar>C</Bar>", result.getResource(1).getContent().toString(), "Second");
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1740891
     */
    @org.junit.jupiter.api.Test
    void evalLoosesContext1740891() throws XMLDBException {
        String module = "module namespace tst = \"urn:test\"; " +
                "declare namespace util = \"http://exist-db.org/xquery/util\";" +
                "declare function tst:bar() as element(Bar)* { " +
                "let $foo := <Foo><Bar/><Bar/><Bar/></Foo> " +
                "let $query := \"$foo/Bar\" " +
                "let $bar := util:eval($query) " +
                "return $bar };";

        String module_name = "module.xqy";
        Resource doc;

        // Store module
        Collection testCollection = getTestCollection();
        doc = testCollection.createResource(module_name, BinaryResource.class);
        doc.setContent(module);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        String query = "import module namespace tst = \"urn:test\"" +
                "at \"xmldb:exist:///db/test/module.xqy\"; " +
                "tst:bar()";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(3, result.getSize());
        assertEquals("<Bar/>", result.getResource(0).getContent().toString(), "First");
        assertEquals("<Bar/>", result.getResource(1).getContent().toString(), "Second");
        assertEquals("<Bar/>", result.getResource(2).getContent().toString(), "Third");
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1740886
     */
    @org.junit.jupiter.api.Test
    void cardinalityIssues1740886() throws XMLDBException {
        String xmldoc = "<Foo><Bar/><Bar/><Bar/></Foo>";
        String query =
                "declare namespace tst = \"urn:test\"; " +
                "declare option exist:serialize 'indent=no';" +
                //======
                "declare function tst:bar( $foo as element(Foo) ) as element(Foo) { " +
                "let $dummy := $foo/Bar " +
                "return $foo }; " +
                //====== if you leave /test out......
                "let $foo := doc(\"/db/test/foo.xml\")/element() " +
                "return tst:bar($foo)";

        XPathQueryService service = storeXMLStringAndGetQueryService("foo.xml", xmldoc);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertThat("Oops", result.getResource(0).getContent().toString(), hasSimilarXml(xmldoc));
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1755910
     */
    @org.junit.jupiter.api.Test
    void qnameString1755910() throws XMLDBException {
        String query = "let $qname1 := QName(\"http://www.w3.org/2001/XMLSchema\", \"xs:element\") " + "let $qname2 := QName(\"http://foo.com\", \"foo:bar\") " + "return (xs:string($qname1), xs:string($qname2))";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());

        assertEquals("xs:element", result.getResource(0).getContent().toString(), "First");
        assertEquals("foo:bar", result.getResource(1).getContent().toString(), "Second");
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1665215
     */
    @org.junit.jupiter.api.Test
    void predicateMinLast1665215() throws XMLDBException {
        String query = "declare option exist:serialize 'indent=no';" +
                "let $data :=<parent><child>1</child><child>2</child><child>3</child><child>4</child></parent>" +
                "return <result>{$data/child[min((last(),3))]}</result>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<result><child>3</child></result>", result.getResource(0).getContent().toString(), "First");
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1665213
     */
    @org.junit.jupiter.api.Test
    void predicatePositionLast1665213() throws XMLDBException {
        // OK, regression
        String query = "(1, 2, 3)[ position() = last() ]";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("3", result.getResource(0).getContent().toString(), "First");


        query = "(1, 2, 3)[(position()=last() and position() < 4)]";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("3", result.getResource(0).getContent().toString(), "First");


        query = "(1, 2, 3)[(position()=last())]";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("3", result.getResource(0).getContent().toString(), "First");
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1769086
     */
    @org.junit.jupiter.api.Test
    void cceIndexOf1769086() throws XMLDBException {
        String query = "(\"One\", \"Two\", \"Three\")[index-of((\"1\", \"2\", \"3\"), \"2\")]";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("Two", result.getResource(0).getContent().toString(), "First");
    }

    @org.junit.jupiter.api.Test
    void shortVersionPositionPredicate() throws XMLDBException {
        String query = "declare option exist:serialize 'indent=no';" + "let $foo :=  <foo>    <bar baz=\"\"/>  </foo>" + "let $bar1 := $foo/bar[exists(@baz)][1]" + "let $bar2 := $foo/bar[exists(@baz)][position() = 1]" + "return  <found> <bar1>{$bar1}</bar1> <bar2>{$bar2}</bar2> </found>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<found><bar1><bar baz=\"\"/></bar1><bar2><bar baz=\"\"/></bar2></found>", result.getResource(0).getContent().toString(), query);
    }

    /***
     * An exception occurred during query execution: XPTY0004: Invalid type for
     * variable $arg1. Expected xs:string, got xs:integer
     *
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1787285&group_id=17691&atid=117691
     */
    @org.junit.jupiter.api.Test
    void wrongInvalidTypeError1787285() throws XMLDBException {
        String query = "let $arg1 as xs:string := \"A String\"" + "let $arg2 as xs:integer := 3 return $arg2";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("3", result.getResource(0).getContent().toString(), query);
    }

    /**
     * Regression
     *
     * @see http://sourceforge.net/support/tracker.php?aid=1805612
     *
     * Same as {@link #asDouble1840775()}
     */
    @Disabled
    @org.junit.jupiter.api.Test
    void wrongAttributeTypeCheck1805612() throws XMLDBException {

        // OK
        String query = "declare namespace tst = \"http://test\"; "
                + "declare function tst:foo($a as element()?) {   $a }; "
                + "tst:foo( <result/> )";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<result/>", result.getResource(0).getContent().toString(), query);

        // NOK
        query = "declare namespace tst = \"http://test\"; "
                + "declare function tst:foo($a as element()?) {   $a }; "
                + "tst:foo( "
                + "  let $a as xs:boolean := true()  "
                + "  return <result/> "
                + ")";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<result/>", result.getResource(0).getContent().toString(), query);
    }

    /**
     * Regression
     *
     * @see http://sourceforge.net/support/tracker.php?aid=1805609
     */
    @org.junit.jupiter.api.Test
    void wrongAttributeCardinalityCount1805609() throws XMLDBException {

        // OK
        String query = "element {\"a\"} { <element b=\"\" c=\"\" />/attribute()[namespace-uri(.) != " + "\"http://www.asml.com/metainformation\"]}";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<a b=\"\" c=\"\"/>", result.getResource(0).getContent().toString(), query);

        // NOK
        query = "element {\"a\"} { <element b=\"\" c=\"\"/>" + "/attribute()[namespace-uri(.) != \"http://www.asml.com/metainformation\"]}";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<a b=\"\" c=\"\"/>", result.getResource(0).getContent().toString(), query);
    }

    /**
     * Regression
     *
     * @see http://sourceforge.net/support/tracker.php?aid=1806901
     */
    @org.junit.jupiter.api.Test
    void doubleDefaultNamespace1806901() throws XMLDBException {
        // OK
        String query = "declare namespace xf = \"http://a\"; " + "declare option exist:serialize 'indent=no';" + "<html xmlns=\"http://b\"><xf:model><xf:instance xmlns=\"\"/></xf:model></html>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<html xmlns=\"http://b\"><xf:model xmlns:xf=\"http://a\">" + "<xf:instance xmlns=\"\"/></xf:model></html>", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1828168
     */
    @org.junit.jupiter.api.Test
    void predicateInPredicateEmptyResult1828168() throws XMLDBException {
        String query = "let $docs := <Document/> return $docs[a[1] = 'b']";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(0, result.getSize());

        query = "<a/>[() = 'b']";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(0, result.getSize());
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1846228
     */
    @org.junit.jupiter.api.Test
    void namespaceHandlingSameModule1846228() throws XMLDBException {
        String query = "declare option exist:serialize 'indent=no';" +
                "declare function local:table () {" +
                "<d>Bar</d>};" +
                "<foobar xmlns=\"http://www.w3.org/1999/xhtml\">" +
                "<a><b>Foo</b></a>" +
                "<c>{local:table()}</c>" +
                "</foobar>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<foobar xmlns=\"http://www.w3.org/1999/xhtml\">" +
                "<a><b>Foo</b></a>" +
                "<c><d xmlns=\"\">Bar</d></c>" +
                "</foobar>",
                result.getResource(0).getContent().toString(), query);
    }

    /**
     * In a path expression, a step returning an empty sequence stops the evaluation
     * (and return an empty sequence) as confirmed by Michael Kay on the XQuery mailing list
     *
     * @see  http://sourceforge.net/support/tracker.php?aid=1841105
     */
    @org.junit.jupiter.api.Test
    void stringOfEmptySequence1841105() throws XMLDBException {
        // OK
        String query = "empty( ()/string() )";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("true", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=2871975
     */
    @Disabled
    @org.junit.jupiter.api.Test
    void stringOfEmptySequenceWithExplicitContext2871975() throws XMLDBException {

        // OK
        String query = "empty( ()/string() )";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("true", result.getResource(0).getContent().toString(),
                query);

        // NOK
        query = "empty( ()/string(.) )";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("true", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1970717
     */
    @org.junit.jupiter.api.Test
    void constructTextNodeWithEmptyString1970717() throws XMLDBException {
        String query = "text {\"\"} =\"\"";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("true", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1848497
     */
    @Disabled
    @org.junit.jupiter.api.Test
    void attributeNamespaceDeclaration1848497() throws XMLDBException {
        String query = "declare namespace foo = \"foo\";" +
                "declare function foo:boe() { \"boe\" };" +
                "<xml xmlns:foo2=\"foo\">{ foo2:boe() }</xml>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<xml xmlns:foo2=\"foo\">boe</xml>", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1884403
     */
    @org.junit.jupiter.api.Test
    void atomization1884403() throws XMLDBException {
        String query = "declare namespace tst = \"tt\"; " +
                "declare function tst:foo() as xs:string { <string>myTxt</string> }; " +
                "tst:foo()";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("myTxt", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1884360
     */
    @org.junit.jupiter.api.Test
    void cardinalityAttributeNamespace1884360() throws XMLDBException {
        String query = "let $el := <element a=\"1\" b=\"2\"/> " +
                "for $attr in $el/attribute()[namespace-uri(.) ne \"h\"] " +
                "return <c>{$attr}</c>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals("<c a=\"1\"/>", result.getResource(0).getContent().toString(),
                query);
        assertEquals("<c b=\"2\"/>", result.getResource(1).getContent().toString(),
                query);
    }

    @org.junit.jupiter.api.Test
    void currentDateTimeInModules1894009() throws XMLDBException {
        String module = """
                module namespace dt = "dt";
                
                declare function dt:fib($n) {
                  if ($n < 2) then $n else dt:fib($n - 1) + dt:fib($n - 2)\s
                };
                
                declare function dt:dateTime() {
                  (: Do something time consuming first. :) \s
                  let $a := dt:fib(25)\
                  return current-dateTime()
                };\
                """;

        String module_name = "dt.xqm";
        Resource doc;

        // Store module
        Collection testCollection = getTestCollection();
        doc = testCollection.createResource(module_name, BinaryResource.class);
        doc.setContent(module);
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        String query = "import module namespace dt = \"dt\" at" +
                "  \"xmldb:exist:///db/test/dt.xqm\"; " +
                "(<this>{current-dateTime()}</this>, <this>{dt:dateTime()}</this>)";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals(result.getResource(0).getContent().toString(), result.getResource(1).getContent().toString(),
                "First");
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1909505
     */
    @org.junit.jupiter.api.Test
    void testXmldbStoreComment1909505() throws XMLDBException {
        String query = "declare option exist:serialize 'indent=no';" +
                "let $docIn := <a><!-- b --></a>" +
                "let $uri := xmldb:store(\"/db\", \"commenttest.xml\", $docIn)" +
                "let $docOut := doc($uri) return $docOut";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<a><!-- b --></a>", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1938498
     */
    @org.junit.jupiter.api.Test
    void memproc1938498() throws XMLDBException {
        String xmldocument = "<Root><Child/></Root>";
        String location = "1938498.xml";
        String query =
                "let $test := doc(\"1938498.xml\")" + "let $inmems := <InMem>{$test}</InMem>" + "return <Test>{$inmems/X}</Test>";
        String output = "<Test/>";

        XPathQueryService service =
                storeXMLStringAndGetQueryService(location, xmldocument);

        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals(output, result.getResource(0).getContent().toString(),
                "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void cceSaxException() throws XMLDBException {
        String xmldocument = "<a><b><c>mmm</c></b></a>";
        String location = "ccesax.xml";
        String query =
                "declare namespace xmldb = \"http://exist-db.org/xquery/xmldb\"; "
                + "declare option exist:serialize 'indent=no';"
                + "let $results := doc(\"ccesax.xml\")/element() "
                + "let $output := let $body := <e>{$results/b/c}</e>  return <d>{$body}</d> "
                + "let $id := $output/e/c "
                + "let $store := xmldb:store(\"/db\", \"output.xml\", $output)"
                + "return doc('/db/output.xml')";
//            String output = "<d><b><c>mmm</c></b></d>";
        String output = "<d><e><c>mmm</c></e></d>";

        XPathQueryService service =
                storeXMLStringAndGetQueryService(location, xmldocument);

        ResourceSet result = service.query(query);
        assertEquals(1, result.getSize(), "XQuery: " + query);
        assertEquals(output, result.getResource(0).getContent().toString(),
                "XQuery: " + query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=2003042
     */
    @org.junit.jupiter.api.Test
    void xpty0018MixNodesAtomicValues2003042() throws XMLDBException {
        String query = "declare option exist:serialize 'indent=no'; <a>{2}<b/></a>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<a>2<b/></a>", result.getResource(0).getContent().toString(), //checked with saxon
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1816496
     */
    @org.junit.jupiter.api.Test
    void divYieldsWrongInf1816496() throws XMLDBException {
        String query = "let $negativeZero := xs:double(-1.0e-1024) let $positiveZero := xs:double(1.0e-1024) "
                +"return ("
                +"(xs:double(1)  div xs:double(0)),   (xs:double(1)  div $positiveZero),  (xs:double(1)  div $negativeZero), "
                +"(xs:double(-1) div xs:double(0)),   (xs:double(-1) div $positiveZero),  (xs:double(-1) div $negativeZero), "
                +"($negativeZero div $positiveZero),  ($positiveZero div $negativeZero), "
                +"(xs:double(0) div $positiveZero),   (xs:double(0) div $negativeZero),  "
                +"(xs:double(0) div xs:double(0))  "
                +")";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(11, result.getSize());

        assertEquals("INF", result.getResource(0).getContent().toString(), query);
        assertEquals("INF", result.getResource(1).getContent().toString(), query);
        assertEquals("-INF", result.getResource(2).getContent().toString(), query);

        assertEquals("-INF", result.getResource(3).getContent().toString(), query);
        assertEquals("-INF", result.getResource(4).getContent().toString(), query);
        assertEquals("INF", result.getResource(5).getContent().toString(), query);

        assertEquals("NaN", result.getResource(6).getContent().toString(), query);
        assertEquals("NaN", result.getResource(7).getContent().toString(), query);
        assertEquals("NaN", result.getResource(8).getContent().toString(), query);
        assertEquals("NaN", result.getResource(9).getContent().toString(), query);
        assertEquals("NaN", result.getResource(10).getContent().toString(), query);

        query = "xs:float(2) div xs:float(0)";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("INF", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see https://github.com/eXist-db/exist/issues/3441
     */
    @org.junit.jupiter.api.Test
    void divErrorArgVariable() throws XMLDBException {
        String query = "let $x := 2 " +
                "return 1 div $x * 4";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());

        assertEquals("2", result.getResource(0).getContent().toString(), query);

    }

    /**
     * @see https://github.com/eXist-db/exist/issues/3441
     */
    @org.junit.jupiter.api.Test
    void divErrorArgVariable2() throws XMLDBException {
        String query = """
                let $x := 2\s
                let $y := 1 div $x * 4
                return $y""";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());

        assertEquals("2", result.getResource(0).getContent().toString(), query);

    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1841635
     */
    @org.junit.jupiter.api.Test
    void resolveBaseURI1841635() throws XMLDBException {
        String xmldoc = "<Root><Node1><Node2><Node3></Node3></Node2></Node1></Root>";

        XPathQueryService service = storeXMLStringAndGetQueryService("baseuri.xml", xmldoc);
            
        String query="doc('/db/test/baseuri.xml')/Root/Node1/base-uri()";


        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());

        query = "doc('/db/test/baseuri.xml')/Root/Node1/base-uri()";

        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());


        query = "doc('/db/test/baseuri.xml')/Root/Node1/Node2/base-uri()";

        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());

        query = "doc('/db/test/baseuri.xml')/Root/Node1/Node2/Node3/base-uri()";

        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());
    }

    /**
     * @see <a href="https://github.com/eXist-db/exist/issues/3497">[BUG] ()/fn:base-uri() incorrectly raises XPDY0002</a>
     */
    @org.junit.jupiter.api.Test
    void resolveBaseURIErrorCases() throws XMLDBException {
        final XPathQueryService service = existEmbeddedServer.getRoot().getService(XPathQueryService.class);

        String query = "()/fn:base-uri()";
        ResourceSet result = service.query(query);
        assertEquals(0, result.getSize());

        query = "()/base-uri(.)";
        result = service.query(query);
        assertEquals(0, result.getSize());

        query = "base-uri(.)";
        try {
            result = service.query(query);
            assertEquals(0, result.getSize());

            fail("Should have raised error: XPDY0002");

        } catch (final XMLDBException e) {
            assertEquals(org.xmldb.api.base.ErrorCodes.VENDOR_ERROR, e.errorCode);
            final Throwable cause = e.getCause();
            assertEquals(XPathException.class, cause.getClass());
            assertEquals(ErrorCodes.XPDY0002, ((XPathException) cause).getErrorCode());
        }
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=2429093
     */
    @org.junit.jupiter.api.Test
    void xpty0018Mixedsequences2429093() throws XMLDBException {
        String query = """
                declare variable $a := <A><B/></A>;
                ($a/B, "delete") """;

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals("<B/>", result.getResource(0).getContent().toString(),
                query);
        assertEquals("delete", result.getResource(1).getContent().toString(),
                query);
    }

    @org.junit.jupiter.api.Test
    void messageDigester() throws XMLDBException {
        String query = """
                let $value:="ABCDEF"
                let $alg:="MD5"
                return
                (util:hash($value, $alg), util:hash($value, $alg, xs:boolean('true')))""";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals("8827a41122a5028b9808c7bf84b9fcf6", result.getResource(0).getContent().toString(),
                query);
        assertEquals("iCekESKlAouYCMe/hLn89g==", result.getResource(1).getContent().toString(),
                query);

        query = """
                let $value:="ABCDEF"
                let $alg:="SHA-1"
                return
                (util:hash($value, $alg), util:hash($value, $alg, xs:boolean('true')))""";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals("970093678b182127f60bb51b8af2c94d539eca3a", result.getResource(0).getContent().toString(),
                query);
        assertEquals("lwCTZ4sYISf2C7UbivLJTVOeyjo=", result.getResource(1).getContent().toString(),
                query);

        query = """
                let $value:="ABCDEF"
                let $alg:="SHA-256"
                return
                (util:hash($value, $alg), util:hash($value, $alg, xs:boolean('true')))""";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals("e9c0f8b575cbfcb42ab3b78ecc87efa3b011d9a5d10b09fa4e96f240bf6a82f5", result.getResource(0).getContent().toString(),
                query);
        assertEquals("6cD4tXXL/LQqs7eOzIfvo7AR2aXRCwn6TpbyQL9qgvU=", result.getResource(1).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/tracker/?func=detail&aid=2846187&group_id=17691&atid=317691
     */
    @org.junit.jupiter.api.Test
    void dynamicallySizedNamePool() throws XMLDBException {
        String query = "<root> { for $i in 1 to 2000  "
                + "return element {concat(\"elt-\", $i)} {} } </root>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        assertDoesNotThrow(() -> service.query(query));
    }


    /**
     * @see http://sourceforge.net/support/tracker.php?aid=2903815
     */
    @org.junit.jupiter.api.Test
    void replaceBug2903815() throws XMLDBException {
        String query = "let $f := <z>fred</z>" +
                "let $s:= <s>xxxxtxxx</s>" +
                "let $t := <t>t</t>" +
                "return replace($s,$t,$f)";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("xxxxfredxxx", result.getResource(0).getContent().toString(),
                query);

        query = "let $f := \"fred\"" +
                "let $s:= <s>xxxxtxxx</s>" +
                "let $t := <t>t</t>" +
                "return replace($s,$t,$f)";

        service = getTestCollection().getService(XPathQueryService.class);
        result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("xxxxfredxxx", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1840775
     *
     * Same as {@link #wrongAttributeTypeCheck1805612()}
     */
    @Disabled
    @org.junit.jupiter.api.Test
    void asDouble1840775() throws XMLDBException {
        String query = "declare function local:testCase($failure as element(Failure)?)"
                + "as element(TestCase) { <TestCase/> };"
                + "local:testCase("
                + "(: work-around for this eXist 1.1.2dev-rev:6992-20071127 bug: let $ltValue := 0.0 :)"
                + "let $ltValue as xs:double := 0.0e0 return <Failure/>)";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        assertDoesNotThrow(() -> service.query(query));
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=2117655
     */
    @org.junit.jupiter.api.Test
    void typeMismatch2117655() throws XMLDBException {
        String query = "declare namespace t = \"test\"; "
                +"declare function t:foo() as xs:string{"
                + "<Value>23</Value>}; "
                + "t:foo()";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("23", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1959010
     */
    @org.junit.jupiter.api.Test
    void noNamepaceDefinedForPrefix1959010() throws XMLDBException {
        String query =
                 "declare function local:copy($nodes as node()*) as node()* "
                +"{ "
                +"for $n in $nodes "
                +"return "
                +"   if ($n instance of element()) then "
                +"       element {node-name($n)} {(local:copy($n/@*), local:copy($n/node()))} "
                +"   else if ($n instance of attribute()) then "
                +"       attribute {node-name($n)} {$n} "
                +"   else if ($n instance of text()) then "
                +"       text {$n} "
                +"   else "
                +"       <Other/> "
                +"}; "

                +"let $c := <c:C xmlns:c=\"http://c\" xmlns:d=\"http://d\" d:d=\"ddd\">ccc</c:C> "
                +"return local:copy($c)";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        // XQuery 3.1 §2: "the relative order of namespace nodes that share a parent is also implementation dependent."
        final Source expected = Input.fromString("<c:C xmlns:c=\"http://c\" xmlns:d=\"http://d\" d:d=\"ddd\">ccc</c:C>").build();
        final Source actual = Input.fromString(result.getResource(0).getContent().toString()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForIdentical()
                .build();
        assertFalse(diff.hasDifferences(), query + "\n" + diff.toString());
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1807014
     */
    @org.junit.jupiter.api.Test
    void wrongAddNamespace1807014() throws XMLDBException {
        Collection testCollection = getTestCollection();
        Resource doc = testCollection.createResource("a.xqy", BinaryResource.class);
        doc.setContent("module namespace a = \"http://www.a.com\"; "
                        +"declare function a:selectionList() as element(ul) { "
                        +"<ul class=\"a\"/> "
                        +"};");
        ((EXistResource) doc).setMimeType("application/xquery");
        testCollection.storeResource(doc);

        String query =
                "declare option exist:serialize 'indent=no';"
                +"import module namespace a = \"http://www.a.com\" at \"xmldb:exist://db/test/a.xqy\"; "
                +"<html xmlns=\"http://www.w3.org/1999/xhtml\"> "
                +"{ a:selectionList() } "
                +"</html>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                +"<ul xmlns=\"\" class=\"a\"/></html>", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1789370
     */
    @org.junit.jupiter.api.Test
    void orderBy1789370() throws XMLDBException {
        String query =
                 "(for $vi in <elem>text</elem> order by $vi return $vi)/text()";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals("text", result.getResource(0).getContent().toString(),
                query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1817822
     */
    @org.junit.jupiter.api.Test
    void variableScopeBug1817822() throws XMLDBException {
        String query =
                     "declare namespace test = \"http://example.com\"; "
                    +"declare function test:expression($expr) as xs:double? { "
                    +" typeswitch($expr) "
                    +"   case element(Value) return test:value($expr) "
                    +"   case element(SomethingRandom) return test:product($expr/*) "
                    +"   default return () "
                    +"}; "

                    +"declare function test:value($expr) { "
                    +"   xs:double($expr) "
                    +"}; "

                    +"declare function test:product($expressions) { "
                    +"   test:expression($expressions[1]) "
                    +"   * "
                    +"   test:expression($expressions[2]) "
                    +"}; "

                    +"let $values := (<Value>2</Value>,<Value>3</Value>) "
                    +"let $a := test:expression(<AnotherSomethingRandom/>) "
                    +"let $b := test:product($values) "
                    +"return <Result>{$b}</Result>";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(1, result.getSize());
        assertEquals(
                "<Result>6</Result>", result.getResource(0).getContent().toString(), query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1718626
     */
    @org.junit.jupiter.api.Test
    void constructednodePosition1718626() throws XMLDBException {
        String query =
                 "declare variable $categories := "
                +" <categories> "
                +"         <category uid=\"1\">Fruit</category> "
                +"         <category uid=\"2\">Vegetable</category> "
                +"         <category uid=\"3\">Meat</category> "
                +"         <category uid=\"4\">Dairy</category> "
                +" </categories> "
                +" ; "

                +" $categories/category[1], "
                +" $categories/category[position() eq 1]";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals(
                "<category uid=\"1\">Fruit</category>", result.getResource(0).getContent().toString(), query);
        assertEquals(
                "<category uid=\"1\">Fruit</category>", result.getResource(1).getContent().toString(), query);
    }

    /**
     * @see http://sourceforge.net/support/tracker.php?aid=1460791
     */
    @org.junit.jupiter.api.Test
    void descendantOrSelf1460791() throws XMLDBException {
        String query =
                 "declare option exist:serialize 'indent=no';"
                +"let $test:=<z> <a> aaa </a> <z> zzz </z> </z> "
                +"return "
                +"( "
                +"<one> {$test//z} </one>, "
                +"<two> {$test/descendant-or-self::node()/child::z} </two> "
                +"(: note that these should be the same *by definition* :) "
                +")";

        XPathQueryService service = getTestCollection().getService(XPathQueryService.class);
        ResourceSet result = service.query(query);

        assertEquals(2, result.getSize());
        assertEquals(
                "<one><z> zzz </z></one>", result.getResource(0).getContent().toString(), query);
        assertEquals(
                "<two><z> zzz </z></two>", result.getResource(1).getContent().toString(), query);
    }

    @org.junit.jupiter.api.Test
    void attributesSerialization() throws XMLDBException {
        final XPathQueryService service =
                storeXMLStringAndGetQueryService(attributesSERIALIZATION, attributes);

        String query = "//@* \n";
        ResourceSet result = null;
        try {
            result = service.query(query);
        } catch (Exception e) {
            //SENR0001 : OK - this is expected
        }
        query = """
            declare option exist:serialize 'method=text';\s
            //@*\s
            """;
        result = service.query(query);
        assertEquals(3, result.getSize(), "XQuery: " + query);
    }

    @org.junit.jupiter.api.Test
    void pathOperatorContainingNodesAndNonNodes() throws XMLDBException {
        final String query = """
                declare function local:test() { (1,<n/>) };
                <x/>/local:test()""";
        assertThrows(XPathException.class, () -> {
            try {
                existEmbeddedServer.executeQuery(query);
            } catch (final XMLDBException e) {
                if (e.getCause() instanceof XPathException xpe) {
                    assertEquals(ErrorCodes.XPTY0018, xpe.getErrorCode());
                    throw xpe;
                } else {
                    throw e;
                }
            }
        });
    }

    @org.junit.jupiter.api.Test
    void exprContainingNodesAndNonNodes() throws XMLDBException {
        final String query = """
                declare function local:test() { (1,<n/>) };
                local:test()""";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);

        assertEquals(2, result.getSize());
        assertEquals("1", result.getResource(0).getContent().toString());
        assertEquals("<n/>", result.getResource(1).getContent().toString());
    }

    /**
     * @see https://github.com/eXist-db/exist/issues/1121
     */
    @org.junit.jupiter.api.Test
    void multipleExprsContainingNodesAndNonNodes() throws XMLDBException {
        final String query = """
                declare variable $a := 'a';
                declare function local:test() { (1,<n/>) };
                local:test()""";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);

        assertEquals(2, result.getSize());
        assertEquals("1", result.getResource(0).getContent().toString());
        assertEquals("<n/>", result.getResource(1).getContent().toString());
    }

    // ======================================
    /**
     * @return
     * @throws XMLDBException
     */
    private XPathQueryService storeXMLStringAndGetQueryService(String documentName,
            String content) throws XMLDBException {
        Collection testCollection = getTestCollection();
        XMLResource doc =
                testCollection.createResource(
                documentName, XMLResource.class);
        doc.setContent(content);
        testCollection.storeResource(doc);
        XPathQueryService service =
                testCollection.getService(
                XPathQueryService.class);
        return service;
    }
}
