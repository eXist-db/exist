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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.ConfigurationHelper;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.junit.runner.RunWith;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for various standard XQuery functions
 *
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
@RunWith(ParallelRunner.class)
public class XQueryFunctionsTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);
    
    private final static String ROOT_COLLECTION_URI = "xmldb:exist:///db";

    @Test
    public void arguments() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("declare function local:testAnyURI($uri as xs:string) as xs:string { " +
                "concat('Successfully processed as xs:string : ',$uri) " +
                "}; " +
                "let $a := xs:anyURI('http://exist.sourceforge.net/') " +
                "return local:testAnyURI($a)");
        assertEquals(1, result.getSize());
        String r = (String) result.getResource(0).getContent();
        assertEquals("Successfully processed as xs:string : http://exist.sourceforge.net/", r);


        result = existEmbeddedServer.executeQuery("declare function local:testEmpty($blah as xs:string)  as element()* { " +
                "for $a in (1,2,3) order by $a " +
                "return () " +
                "}; " +
                "local:testEmpty('test')");
        assertEquals(0, result.getSize());
    }

    /**
     * Tests the XQuery-/XPath-function fn:round-half-to-even
     * with the rounding value typed xs:integer
     */
    @Test
    public void roundHtE_INTEGER() throws XPathException, XMLDBException {
        String query = "fn:round-half-to-even( xs:integer('1'), 0 )";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals("1", r);

        query = "fn:round-half-to-even( xs:integer('6'), -1 )";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("10", r);

        query = "fn:round-half-to-even( xs:integer('5'), -1 )";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("0", r);
    }

    /**
     * Tests the XQuery-/XPath-function fn:round-half-to-even
     * with the rounding value typed xs:double
     */
    @Test
    public void roundHtE_DOUBLE() throws XPathException, XMLDBException {
        /* List of Values to test with Rounding */
        String[] testvalues =
                {"0.5", "1.5", "2.5", "3.567812E+3", "4.7564E-3", "35612.25"};
        String[] resultvalues =
                {"0", "2", "2", "3567.81", "0", "35600"};
        int[] precision =
                {0, 0, 0, 2, 2, -2};

        for (int i = 0; i < testvalues.length; i++) {
            String query = "fn:round-half-to-even( xs:double('" + testvalues[i] + "'), " + precision[i] + " )";
            ResourceSet result = existEmbeddedServer.executeQuery(query);
            String r = (String) result.getResource(0).getContent();
            assertEquals(resultvalues[i], r);
        }
    }

    /**
     * Tests the XQuery-XPath function fn:tokenize()
     */
    @Test
    public void tokenize() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("count ( tokenize('a/b' , '/') )");
        String r = (String) result.getResource(0).getContent();
        assertEquals("2", r);

        result = existEmbeddedServer.executeQuery("count ( tokenize('a/b/' , '/') )");
        r = (String) result.getResource(0).getContent();
        assertEquals("3", r);

        result = existEmbeddedServer.executeQuery("count ( tokenize('' , '/') )");
        r = (String) result.getResource(0).getContent();
        assertEquals("0", r);

        result = existEmbeddedServer.executeQuery(
                "let $res := fn:tokenize('abracadabra', '(ab)|(a)')" +
                        "let $reference := ('', 'r', 'c', 'd', 'r', '')" +
                        "return fn:deep-equal($res, $reference)");
        r = (String) result.getResource(0).getContent();
        assertEquals("true", r);

        result = existEmbeddedServer.executeQuery("tokenize('firstSecondThirdLast', '[A-Z]')");
        assertEquals(4, result.getSize());
        r = (String) result.getResource(0).getContent();
        assertEquals("first", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("econd", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("hird", r);
        r = (String) result.getResource(3).getContent();
        assertEquals("ast", r);

    }

    @Test
    public void deepEqual() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery(
                "let $res := ('a', 'b')" +
                        "let $reference := ('a', 'b')" +
                        "return fn:deep-equal($res, $reference)");
        String r = (String) result.getResource(0).getContent();
        assertEquals("true", r);
    }

    @Test
    public void compare() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("fn:compare(\"Strasse\", \"Stra\u00DFe\")");
        String r = (String) result.getResource(0).getContent();
        assertEquals("-1", r);
        //result 	= existEmbeddedServer.executeQuery("fn:compare(\"Strasse\", \"Stra\u00DFe\", \"java:GermanCollator\")");
        //r 		= (String) result.getResource(0).getContent();
        //assertEquals( "0", r );

        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[compare(., '+') gt 0]";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void distinctValues() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("declare variable $c { distinct-values(('a', 'a')) }; $c");
        String r = (String) result.getResource(0).getContent();
        assertEquals("a", r);

        result = existEmbeddedServer.executeQuery("declare variable $c { distinct-values((<a>a</a>, <b>a</b>)) }; $c");
        r = (String) result.getResource(0).getContent();
        assertEquals("a", r);

        result = existEmbeddedServer.executeQuery("let $seq := ('A', 2, 'B', 2) return distinct-values($seq) ");
        assertEquals(3, result.getSize());

        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[distinct-values(.)]";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void sum() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("declare variable $c { sum((1, 2)) }; $c");
        String r = (String) result.getResource(0).getContent();
        assertEquals("3", r);

        result = existEmbeddedServer.executeQuery("declare variable $c { sum((<a>1</a>, <b>2</b>)) }; $c");
        r = (String) result.getResource(0).getContent();
        //Any untyped atomic values in the sequence are converted to xs:double values ([MK Xpath 2.0], p. 432)
        assertEquals("3", r);

        result = existEmbeddedServer.executeQuery("declare variable $c { sum((), 3) }; $c");
        r = (String) result.getResource(0).getContent();
        assertEquals("3", r);
    }

    @Test
    public void avg() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("avg((2, 2))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("2", r);

        result = existEmbeddedServer.executeQuery("avg((<a>2</a>, <b>2</b>))");
        r = (String) result.getResource(0).getContent();
        //Any untyped atomic values in the resulting sequence
        //(typically, values extracted from nodes in a schemaless document)
        //are converted to xs:double values ([MK Xpath 2.0], p. 301)
        assertEquals("2", r);

        result = existEmbeddedServer.executeQuery("avg((3, 4, 5))");
        r = (String) result.getResource(0).getContent();
        assertEquals("4", r);

        result = existEmbeddedServer.executeQuery("avg((xdt:yearMonthDuration('P20Y'), xdt:yearMonthDuration('P10M')))");
        r = (String) result.getResource(0).getContent();
        assertEquals("P10Y5M", r);

        String message = "";
        try {
            result = existEmbeddedServer.executeQuery("avg((xdt:yearMonthDuration('P20Y') , (3, 4, 5)))");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("FORG0006") > -1);

        result = existEmbeddedServer.executeQuery("avg(())");
        assertEquals(0, result.getSize());

        result = existEmbeddedServer.executeQuery("avg(((xs:float('INF')), xs:float('-INF')))");
        r = (String) result.getResource(0).getContent();
        assertEquals("NaN", r);

        result = existEmbeddedServer.executeQuery("avg(((3, 4, 5), xs:float('NaN')))");
        r = (String) result.getResource(0).getContent();
        assertEquals("NaN", r);
    }

    @Test
    public void min() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("min((1, 2))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("1", r);

        result = existEmbeddedServer.executeQuery("min((<a>1</a>, <b>2</b>))");
        r = (String) result.getResource(0).getContent();
        assertEquals("1", r);

        result = existEmbeddedServer.executeQuery("min(())");
        assertEquals(0, result.getSize());

        result = existEmbeddedServer.executeQuery("min((xs:dateTime('2005-12-19T16:22:40.006+01:00'), xs:dateTime('2005-12-19T16:29:40.321+01:00')))");
        r = (String) result.getResource(0).getContent();
        assertEquals("2005-12-19T16:22:40.006+01:00", r);

        result = existEmbeddedServer.executeQuery("min(('a', 'b'))");
        r = (String) result.getResource(0).getContent();
        assertEquals("a", r);

        String message = "";
        try {
            result = existEmbeddedServer.executeQuery("min((xs:dateTime('2005-12-19T16:22:40.006+01:00'), 'a'))");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("FORG0006") > -1);

        try {
            message = "";
            result = existEmbeddedServer.executeQuery("min(1, 2)");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        //depends whether we have strict type checking or not
        assertTrue(message.indexOf("XPTY0004") > -1 | message.indexOf("FORG0001") > -1 | message.indexOf("FOCH0002") > -1);
    }

    public void max() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("max((1, 2))");
        String r = (String) result.getResource(0).getContent();
        assertEquals("2", r);

        result = existEmbeddedServer.executeQuery("max((<a>1</a>, <b>2</b>))");
        r = (String) result.getResource(0).getContent();
        assertEquals("2", r);

        result = existEmbeddedServer.executeQuery("max(())");
        assertEquals(0, result.getSize());

        result = existEmbeddedServer.executeQuery("max((xs:dateTime('2005-12-19T16:22:40.006+01:00'), xs:dateTime('2005-12-19T16:29:40.321+01:00')))");
        r = (String) result.getResource(0).getContent();
        assertEquals("2005-12-19T16:29:40.321+01:00", r);

        result = existEmbeddedServer.executeQuery("max(('a', 'b'))");
        r = (String) result.getResource(0).getContent();
        assertEquals("b", r);

        String message = "";
        try {
            result = existEmbeddedServer.executeQuery("max((xs:dateTime('2005-12-19T16:22:40.006+01:00'), 'a'))");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("FORG0006") > -1);

        try {
            message = "";
            result = existEmbeddedServer.executeQuery("max(1, 2)");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        //depends whether we have strict type checking or not
        assertTrue(message.indexOf("XPTY0004") > -1 | message.indexOf("FORG0001") > -1 | message.indexOf("FOCH0002") > -1);
    }

    @Test
    public void exclusiveLock() throws XPathException, XMLDBException {
        String query = "let $query1 := (<a/>)\n" +
                "let $query2 := (2, 3)\n" +
                "let $a := util:exclusive-lock(//*,($query1, $query2))\n" +
                "return $a";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(3, result.getSize());
        String r = (String) result.getResource(0).getContent();
        assertEquals("<a/>", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("2", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("3", r);

        query = "let $query1 := (<a/>)\n" +
                "let $query2 := (2, 3)\n" +
                "let $a := util:exclusive-lock((),($query1, $query2))\n" +
                "return $a";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(3, result.getSize());
        r = (String) result.getResource(0).getContent();
        assertEquals("<a/>", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("2", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("3", r);

        query = "let $query1 := (<a/>)\n" +
                "let $query2 := (2, 3)\n" +
                "let $a := util:exclusive-lock((),($query1, $query2))\n" +
                "return $a";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(3, result.getSize());
        r = (String) result.getResource(0).getContent();
        assertEquals("<a/>", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("2", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("3", r);

        query = "let $a := util:exclusive-lock(//*,<root/>)\n" +
                "return $a";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("<root/>", r);
    }

    @Ignore
    @Test
    public void utilEval1() throws XPathException, XMLDBException {
        String query = "<a><b/></a>/util:eval('*')";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
    }

    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1629363&group_id=17691&atid=117691
     */
    @Test
    public void utilEval2() throws XPathException, XMLDBException {
        String query = "let $context := <item/> " +
                "return util:eval(\"<result>{$context}</result>\")";
        // TODO check result
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
    }

    @Test
    public void utilEvalForFunction() throws XPathException, XMLDBException {

        String query = "declare function local:home()\n"
                + "{\n"
                + "<b>HOME</b>\n"
                + "};\n"
                + "util:eval(\"local:home()\")\n";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
    }

    @Test
    public void sharedLock() throws XPathException, XMLDBException {
        String query = "let $query1 := (<a/>)\n" +
                "let $query2 := (2, 3)\n" +
                "let $a := util:shared-lock(//*,($query1, $query2))\n" +
                "return $a";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(3, result.getSize());
        String r = (String) result.getResource(0).getContent();
        assertEquals("<a/>", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("2", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("3", r);

        query = "let $query1 := (<a/>)\n" +
                "let $query2 := (2, 3)\n" +
                "let $a := util:shared-lock((),($query1, $query2))\n" +
                "return $a";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(3, result.getSize());
        r = (String) result.getResource(0).getContent();
        assertEquals("<a/>", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("2", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("3", r);

        query = "let $query1 := (<a/>)\n" +
                "let $query2 := (2, 3)\n" +
                "let $a := util:shared-lock((),($query1, $query2))\n" +
                "return $a";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(3, result.getSize());
        r = (String) result.getResource(0).getContent();
        assertEquals("<a/>", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("2", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("3", r);

        query = "let $a := util:shared-lock(//*,<root/>)\n" +
                "return $a";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("<root/>", r);
    }

    @Test
    public void encodeForURI() throws XMLDBException {
        String string = "http://www.example.com/00/Weather/CA/Los%20Angeles#ocean";
        String expected = "http%3A%2F%2Fwww.example.com%2F00%2FWeather%2FCA%2FLos%2520Angeles%23ocean";
        String query = "encode-for-uri(\"" + string + "\")";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);

        string = "~b\u00e9b\u00e9";
        expected = "~b%C3%A9b%C3%A9";
        query = "encode-for-uri(\"" + string + "\")";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);

        string = "100% organic";
        expected = "100%25%20organic";
        query = "encode-for-uri(\"" + string + "\")";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);


        query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[encode-for-uri(.) ne '']";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void iriToURI() throws XMLDBException {
        String string = "http://www.example.com/00/Weather/CA/Los%20Angeles#ocean";
        String expected = "http://www.example.com/00/Weather/CA/Los%20Angeles#ocean";
        String query = "iri-to-uri(\"" + string + "\")";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);


        string = "http://www.example.com/~b\u00e9b\u00e9";
        expected = "http://www.example.com/~b%C3%A9b%C3%A9";
        query = "iri-to-uri(\"" + string + "\")";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);

        string = "$";
        expected = "$";
        query = "iri-to-uri(\"" + string + "\")";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);
    }

    @Test
    public void escapeHTMLURI() throws XMLDBException {
        String string = "http://www.example.com/00/Weather/CA/Los Angeles#ocean";
        String expected = "http://www.example.com/00/Weather/CA/Los Angeles#ocean";
        String query = "escape-html-uri(\"" + string + "\")";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);

        string = "javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~b\u00e9b\u00e9');";
        expected = "javascript:if (navigator.browserLanguage == 'fr') window.open('http://www.example.com/~b%C3%A9b%C3%A9');";
        query = "escape-html-uri(\"" + string + "\")";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);

        query = "escape-html-uri('$')";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("$", r);

        query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[escape-html-uri(.) ne '']";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void localName() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b></b></a>" +
                        "return fn:local-name($a)");
        String r = (String) result.getResource(0).getContent();
        assertEquals("a", r);
    }

    @Test
    public void localName_empty() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "fn:local-name(())");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void localName_emptyElement() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "<a>b</a>/fn:local-name(c)");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void localName_emptyText() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "<a>b</a>/fn:local-name(text())");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void localName_contextItem() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b/></a>" +
                        "return $a/b/fn:local-name()");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("b", r);
    }

    @Test
    public void localName_contextItem_empty() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b/></a>" +
                        "return $a/b/c/fn:local-name()");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void name() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b></b></a>" +
                        "return fn:name($a)");
        String r = (String) result.getResource(0).getContent();
        assertEquals("a", r);
    }

    @Test
    public void name_empty() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "fn:name(())");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void name_emptyElement() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "<a>b</a>/fn:name(c)");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void name_emptyText() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "<a>b</a>/fn:local-name(text())");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void name_contextItem() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b/></a>" +
                        "return $a/b/fn:name()");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("b", r);
    }

    @Test
    public void name_contextItem_empty() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b/></a>" +
                        "return $a/b/c/fn:name()");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void dateTimeConstructor() throws XPathException, XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery(
                "let $date := xs:date('2007-05-02+02:00') " +
                        "return dateTime($date, xs:time('15:12:52.421+02:00'))"
        );
        String r = (String) result.getResource(0).getContent();
        assertEquals("2007-05-02T15:12:52.421+02:00", r);
    }

    @Test
    public void currentDateTime() throws XPathException, XMLDBException {
        //Do not use this test around midnight on the last day of a month ;-)
        ResourceSet result = existEmbeddedServer.executeQuery(
                "('Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', " +
                        "'Oct', 'Nov', 'Dec')[month-from-dateTime(current-dateTime())]");
        String r = (String) result.getResource(0).getContent();
        SimpleDateFormat df = new SimpleDateFormat("MMM", new Locale("en", "US"));
        Date date = new Date();
        assertEquals(df.format(date), r);

        String query = "declare option exist:current-dateTime '2007-08-23T00:01:02.062+02:00';" +
                "current-dateTime()";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        r = (String) result.getResource(0).getContent();
        assertEquals("2007-08-23T00:01:02.062+02:00", r);
    }

    /**
     * Bugfix 3070
     *
     * @see http://svn.sourceforge.net/exist/?rev=3070&view=rev
     *
     * seconds-from-dateTime() returned wrong value when dateTime had
     * no millesecs available. Special value was returned.
     */
    @Test
    public void secondsFromDateTime() throws XMLDBException {
        ResourceSet result = existEmbeddedServer.executeQuery("seconds-from-dateTime(xs:dateTime(\"2005-12-22T13:35:21.000\") )");
        String r = (String) result.getResource(0).getContent();
        assertEquals("21", r);

        result = existEmbeddedServer.executeQuery("seconds-from-dateTime(xs:dateTime(\"2005-12-22T13:35:21\") )");
        r = (String) result.getResource(0).getContent();
        assertEquals("21", r);
    }

    @Test
    public void resolveQName() throws XMLDBException {
        String query = "declare namespace a=\"aes\"; " +
                "declare namespace n=\"ns1\"; " +
                "declare variable $d := <c xmlns:x=\"ns1\"><d>x:test</d></c>; " +
                "for $e in $d/d " +
                "return fn:resolve-QName($e/text(), $e)";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals("x:test", r);

        query = "declare namespace a=\"aes\"; " +
                "declare namespace n=\"ns1\"; " +
                "declare variable $d := <c xmlns:x=\"ns1\"><d xmlns:y=\"ns1\">y:test</d></c>; " +
                "for $e in $d/d " +
                "return fn:resolve-QName($e/text(), $e)";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("y:test", r);
    }

    @Test
    public void namespaceURI() throws XMLDBException {
        String query = "let $var := <a xmlns='aaa'/> " +
                "return " +
                "$var[fn:namespace-uri() = 'aaa']/fn:namespace-uri()";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals("aaa", r);

        query = "for $a in <test><a xmlns=\"aaa\"><b><c/></b></a></test>//* " +
                "return namespace-uri($a)";
        result = existEmbeddedServer.executeQuery(query);
        assertEquals(result.getSize(), 3);
        r = (String) result.getResource(0).getContent();
        assertEquals("aaa", r);
        r = (String) result.getResource(1).getContent();
        assertEquals("aaa", r);
        r = (String) result.getResource(2).getContent();
        assertEquals("aaa", r);
    }

    @Test
    public void namespaceURI_contextItem() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><exist:b/></a>" +
                        "return $a/exist:b/fn:namespace-uri()");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("http://exist.sourceforge.net/NS/exist", r);
    }

    @Test
    public void namespaceURI_contextItem_empty() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b/></a>" +
                        "return $a/exist:b/c/fn:namespace-uri()");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("", r);
    }

    @Test
    public void prefixFromQName() throws XMLDBException {
        String query = "declare namespace foo = \"http://example.org\"; " +
                "declare namespace FOO = \"http://example.org\"; " +
                "fn:prefix-from-QName(xs:QName(\"foo:bar\"))";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals("foo", r);
    }

    @Test
    public void stringJoin() throws XMLDBException {
        String query = "let $s := ('','a','b','') " +
                "return string-join($s,'/')";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals("/a/b/", r);
    }

    @Test
    public void nodeName() throws XMLDBException {
        final String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "for $b in $a/b[fn:node-name(.) = xs:QName('b')] return $b";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void noeName_empty() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "fn:node-name(())");
        assertEquals(0, result.getSize());
    }

    @Test
    public void nodeName_emptyElement() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "<a>b</a>/fn:node-name(c)");
        assertEquals(0, result.getSize());
    }

    @Test
    public void nodeName_emptyText() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "<a>b</a>/fn:node-name(text())");
        assertEquals(0, result.getSize());
    }

    @Test
    public void nodeName_contextItem() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b/></a>" +
                        "return $a/b/fn:node-name()");
        final String r = (String) result.getResource(0).getContent();
        assertEquals("b", r);
    }

    @Test
    public void nodeName_contextItem_empty() throws XPathException, XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(
                "let $a := <a><b/></a>" +
                        "return $a/b/c/fn:node-name()");
        assertEquals(0, result.getSize());
    }

    @Test
    public void data0() throws XMLDBException {
        final String query = "let $a := <a><b>1</b><b>1</b></a> " +
                "for $b in $a/b[data() = '1'] return $b";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void data0_atomization() throws XMLDBException {
        final String query = "(<a>1<b>2</b>three</a>, <four>4</four>)/data()";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
        assertEquals("12three", result.getResource(0).getContent().toString());
        assertEquals("4", result.getResource(1).getContent());
    }

    @Test
    public void data1() throws XMLDBException {
        final String query = "let $a := <a><b>1</b><b>1</b></a> " +
                "for $b in $a/b[data() = '1'] return $b";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void data1_atomization() throws XMLDBException {
        final String query = "data((<a>1<b>2</b>three</a>, <four>4</four>, xs:integer(5)))";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(3, result.getSize());
        assertEquals("12three", result.getResource(0).getContent().toString());
        assertEquals("4", result.getResource(1).getContent());
        assertEquals("5", result.getResource(2).getContent());
    }

    @Test
    public void ceiling() throws XMLDBException {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[abs(ceiling(.))]";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void concat() throws XMLDBException {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[concat('+', ., '+') = '+-2+']";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
    }

    @Test
    public void documentURI() throws XMLDBException {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[empty(document-uri(.))]";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void implicitTimezone() throws XMLDBException {
        String query = "declare option exist:implicit-timezone 'PT3H';" +
                "implicit-timezone()";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(1, result.getSize());
        String r = (String) result.getResource(0).getContent();
        assertEquals("PT3H", r);
    }

    @Test
    public void exists() throws XMLDBException {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[exists(.)]";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());

    }

    @Test
    public void floor() throws XMLDBException {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " +
                "return $a/b[abs(floor(.))]";

        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertEquals(2, result.getSize());
    }

    /**
     * ensure the test collection is removed and call collection-available,
     * which should return false, no exception thrown
     */
    @Test
    public void collectionAvailable1() throws XMLDBException {
        //remove the test collection if it already exists
        String collectionName = "testCollectionAvailable1";
        String collectionPath = XmldbURI.ROOT_COLLECTION + "/" + collectionName;
        String collectionURI = ROOT_COLLECTION_URI + "/" + collectionName;

        Collection testCollection = existEmbeddedServer.getRoot().getChildCollection(collectionName);
        if (testCollection != null) {
            CollectionManagementService cms = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
            cms.removeCollection(collectionPath);
        }

        runCollectionAvailableTest(collectionPath, false);
        runCollectionAvailableTest(collectionURI, false);
    }

    /**
     * create a collection and call collection-available, which should return true,
     * no exception thrown
     */
    @Test
    public void collectionAvailable2() throws XMLDBException {
        //add the test collection
        String collectionName = "testCollectionAvailable2";
        String collectionPath = XmldbURI.ROOT_COLLECTION + "/" + collectionName;
        String collectionURI = ROOT_COLLECTION_URI + "/" + collectionName;

        Collection testCollection = existEmbeddedServer.getRoot().getChildCollection(collectionName);
        if (testCollection == null) {
            CollectionManagementService cms = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
            cms.createCollection(collectionPath);
        }

        runCollectionAvailableTest(collectionPath, true);
        runCollectionAvailableTest(collectionURI, true);
    }

    private void runCollectionAvailableTest(String collectionPath, boolean expectedResult) throws XMLDBException {
        //collection-available should not throw an exception and should return expectedResult
        String importXMLDB = "import module namespace xdb=\"http://exist-db.org/xquery/xmldb\";\n";
        String collectionAvailable = "xdb:collection-available('" + collectionPath + "')";
        String query = importXMLDB + collectionAvailable;
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        assertNotNull(result);
        assertTrue(result.getSize() == 1);
        assertNotNull(result.getResource(0));
        String content = (String) result.getResource(0).getContent();
        assertNotNull(content);
        assertEquals(expectedResult, Boolean.valueOf(content).booleanValue());
    }

    @Test
    public void base64BinaryCast() throws XMLDBException, URISyntaxException {
        final String TEST_BINARY_COLLECTION = "testBinary";
        final String TEST_COLLECTION = "/db/" + TEST_BINARY_COLLECTION;
        final String BINARY_RESOURCE_FILENAME = "logo.jpg";
        final String XML_RESOURCE_FILENAME = "logo.xml";

        //create a test collection
        CollectionManagementService colService = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        Collection testCollection = colService.createCollection(TEST_BINARY_COLLECTION);
        assertNotNull(testCollection);

        final Path fLogo = Paths.get(getClass().getResource("value/logo.jpg").toURI());

        //store the eXist logo in the test collection
        BinaryResource br = (BinaryResource) testCollection.createResource(BINARY_RESOURCE_FILENAME, "BinaryResource");
        br.setContent(fLogo);
        testCollection.storeResource(br);

        //create an XML resource with the logo base64 embedded in it
        String queryStore = "xquery version \"1.0\";\n\n"
                + "let $embedded := <logo><image>{util:binary-doc(\"" + TEST_COLLECTION + "/" + BINARY_RESOURCE_FILENAME + "\")}</image></logo> return\n"
                + "xmldb:store(\"" + TEST_COLLECTION + "\", \"" + XML_RESOURCE_FILENAME + "\", $embedded)";

        ResourceSet resultStore = existEmbeddedServer.executeQuery(queryStore);
        assertEquals("store, Expect single result", 1, resultStore.getSize());
        assertEquals("Expect stored filename as result", TEST_COLLECTION + "/" + XML_RESOURCE_FILENAME, resultStore.getResource(0).getContent().toString());

        //retrieve the base64 image from the XML resource and try to cast to xs:base64Binary
        String queryRetreive = "xquery version \"1.0\";\n\n"
                + "let $image := doc(\"" + TEST_COLLECTION + "/" + XML_RESOURCE_FILENAME + "\")/logo/image return\n"
                + "$image/text() cast as xs:base64Binary";

        ResourceSet resultRetreive = existEmbeddedServer.executeQuery(queryRetreive);
        assertEquals("retreive, Expect single result", 1, resultRetreive.getSize());
    }
}
