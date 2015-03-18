package org.exist.xquery.functions.xquery3;

import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XMLAssert;
import org.xmldb.api.base.ResourceSet;

import org.exist.test.EmbeddedExistTester;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.junit.AfterClass;
import org.junit.BeforeClass;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;



/**
 *
 * @author wessels
 */
public class TryCatchTest extends EmbeddedExistTester {

    public TryCatchTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void encapsulated_1() {

        // *******************************************

        String query1 = "xquery version '3.0';"
                + "<a>{ try { 'b' + 7 } catch * { 'c' } }</a>";
        try {
            ResourceSet results = executeQuery(query1);
            String r = (String) results.getResource(0).getContent();

            assertEquals("<a>c</a>", r);

        } catch (Throwable t) {

                t.printStackTrace();
                fail(t.getMessage());
        }
    }

       @Test
    public void encapsulated_2() {

        // *******************************************

        String query1 = "xquery version '3.0';"
                + "for $i in (1,2,3,4) return <a>{ try { 'b' + $i } catch * { 'c' } }</a>";
        try {
            ResourceSet results = executeQuery(query1);

            assertEquals(4, results.getSize());
            String r = (String) results.getResource(0).getContent();

            assertEquals("<a>c</a>", r);

        } catch (Throwable t) {

                t.printStackTrace();
                fail(t.getMessage());
        }
    }

       @Test
    public void encapsulated_3() {

        // *******************************************

        String query1 = "xquery version '3.0';"
                + "<foo>{ for $i in (1,2,3,4) return <a>{ try { 'b' + $i } catch * { 'c' } }</a> }</foo>";
        try {
            ResourceSet results = executeQuery(query1);

            assertEquals(1, results.getSize());
            String r = (String) results.getResource(0).getContent();

            XMLUnit.setIgnoreWhitespace(true);
            XMLAssert.assertXMLEqual("<foo><a>c</a><a>c</a><a>c</a><a>c</a></foo>", r);

        } catch (Throwable t) {

                t.printStackTrace();
                fail(t.getMessage());
        }
    }

    @Test
    public void xQuery3_1() {

        // *******************************************

        String query1 = "xquery version '1.0';"
                + "try { a + 7 } catch * { 1 }";
        try {
            ResourceSet results = executeQuery(query1);
            String r = (String) results.getResource(0).getContent();

            assertEquals("1", r);
            fail("exception expected");

        } catch (Throwable t) {

            Throwable cause = t.getCause();
            if (cause instanceof XPathException) {
                XPathException ex = (XPathException) cause;
                assertEquals("exerr:EXXQDY0003", ex.getErrorCode().getErrorQName().getStringValue());

            } else {
                t.printStackTrace();
                fail(t.getMessage());
            }
        }
    }

    @Test
    public void simpleCatch() {

        String query = "xquery version '3.0';"
                + "try { a + 7 } catch * { 1 }";
        try {
            ResourceSet results = executeQuery(query);
            String r = (String) results.getResource(0).getContent();

            assertEquals("1", r);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }

    @Test
    public void catchWithCodeAndDescription() {

        String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch * "
                + "{  $err:code, $err:description } ";
        try {
            ResourceSet results = executeQuery(query);

            assertEquals(2, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals(ErrorCodes.XPDY0002.getErrorQName().getStringValue(), r1);

            String r2 = (String) results.getResource(1).getContent();
            assertEquals(ErrorCodes.XPDY0002.getDescription(), r2);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }

    @Test
    public void catchWithError3Matches() {

        String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 { 1 }"
                + "catch err:XPDY0002 { 2 }"
                + "catch err:XPDY0003 { 3 }";
        try {
            ResourceSet results = executeQuery(query);
            String r = (String) results.getResource(0).getContent();

            assertEquals("2", r);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    @Test
    public void catchWithErrorNoMatches() {

        String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 { 1 }"
                + "catch err:XPDY0002 { a }"
                + "catch err:XPDY0003 { 3 }";
        try {
            ResourceSet results = executeQuery(query);
            String r = (String) results.getResource(0).getContent();

            assertEquals("2", r);

            fail("Exception expected");

        } catch (Throwable ex) {
            // expected
        }
    }

    @Test
    public void catchWithMultipleMatches() {

        String query1 = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 | err:XPDY0003 { 13 }"
                + "catch err:XPDY0002 { 2 }"
                + "catch err:XPDY0004 | err:XPDY0005 { 45 }";
        try {
            ResourceSet results = executeQuery(query1);
            String r = (String) results.getResource(0).getContent();

            assertEquals("2", r);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        String query2 = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 | * { 13 }"
                + "catch err:XPDY0002 { 2 }"
                + "catch err:XPDY0004 | err:XPDY0005 { 45 }";
        try {
            ResourceSet results = executeQuery(query2);
            String r = (String) results.getResource(0).getContent();

            assertEquals("13", r);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }


    @Test
    public void catchFnError() {

        String query1 = "xquery version '3.0';"
                + "try {"
                + " fn:error( fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000') ) "
                + "} catch * "
                + "{ $err:code }";
        try {
            ResourceSet results = executeQuery(query1);

            assertEquals(1, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("err:FOER0000", r1);


        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        // *******************************************

        String query2 = "xquery version '3.0';"
                + "try {"
                + " fn:error( fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000') ) "
                + "} catch * "
                + "{ $err:code }";
        try {
            ResourceSet results = executeQuery(query2);

            assertEquals(1, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("err:FOER0000", r1);


        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        // *******************************************
        
        String query3 = "xquery version '3.0';"
                + "try {"
                + " fn:error(fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000'), 'TEST') "
                + "} catch * "
                + "{ $err:code, $err:description }";
        try {
            ResourceSet results = executeQuery(query3);


            assertEquals(2, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("err:FOER0000", r1);

            String r2 = (String) results.getResource(1).getContent();
            assertEquals("TEST", r2); //

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        // *******************************************
        
        String query4 = "xquery version '3.0';"
                + "try {"
                + " fn:error(fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000'), 'TEST') "
                + "} catch *  "
                + "{ $err:code, $err:description }";
        try {
            ResourceSet results = executeQuery(query4);


            assertEquals(2, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("err:FOER0000", r1);

            String r2 = (String) results.getResource(1).getContent();
            assertEquals("TEST", r2);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        // *******************************************
        
        String query5 = "xquery version '3.0';"
                + "try {"
                + " fn:error(fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000'), 'TEST', <ab/>) "
                + "} catch *  "
                + "{ $err:code, $err:description, $err:value }";
        try {
            ResourceSet results = executeQuery(query5);

            assertEquals(3, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("err:FOER0000", r1);

            String r2 = (String) results.getResource(1).getContent();
            assertEquals("TEST", r2);

            String r3 = (String) results.getResource(2).getContent();
            assertEquals("<ab/>", r3);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    @Test
    public void catchFullErrorCode() {

        String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch *  "
                + "{  $err:code, $err:description, empty($err:value) } ";
        try {
            ResourceSet results = executeQuery(query);

            assertEquals(3, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals(ErrorCodes.XPDY0002.getErrorQName().getStringValue(), r1);

            String r2 = (String) results.getResource(1).getContent();
            assertEquals(ErrorCodes.XPDY0002.getDescription(), r2);

            String r3 = (String) results.getResource(2).getContent();
            assertEquals("true", r3);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }

    @Test
    public void catchDefinedNamespace() {

        String query1 = "xquery version '3.0';"
                + "declare namespace foo='http://foo.com'; "
                + "try { "
                + "     fn:error(fn:QName('http://foo.com', 'ERRORNAME'), 'ERRORTEXT') "
                + "} "
                + "catch foo:ERRORNAME  { 'good' } "
                + "catch *  { 'bad' } ";
        try {
            ResourceSet results = executeQuery(query1);

            assertEquals(1, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("good", r1);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        // *******************************************
        
        String query2 = "xquery version '3.0';"
                + "declare namespace foo='http://foo.com'; "
                + "try { "
                + "     fn:error(fn:QName('http://foo.com', 'ERRORNAME'), 'ERRORTEXT') "
                + "} "
                + "catch foo:ERRORNAME { $err:code } "
                + "catch *  { 'bad' } ";
        try {
            ResourceSet results = executeQuery(query2);

            assertEquals(1, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("foo:ERRORNAME", r1);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    @Test
    public void catchDefinedNamespace2() {

        String query = "xquery version '3.0';"
                + "declare namespace foo='http://foo.com'; "
                + "try { "
                + "     fn:error(fn:QName('http://foo.com', 'ERRORNAME'), 'ERRORTEXT')"
                + "} "
                + "catch foo:ERRORNAME { 'good' } "
                + "catch * { 'wrong' } ";
        try {
            ResourceSet results = executeQuery(query);

            assertEquals(1, results.getSize());

            String r1 = (String) results.getResource(0).getContent();
            assertEquals("good", r1);

        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }
}
