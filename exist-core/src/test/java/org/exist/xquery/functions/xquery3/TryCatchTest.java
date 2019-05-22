/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
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
 */
package org.exist.xquery.functions.xquery3;

import com.googlecode.junittoolbox.ParallelRunner;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XMLAssert;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ResourceSet;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author wessels
 */
@RunWith(ParallelRunner.class)
public class TryCatchTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void encapsulated_1() throws XMLDBException {
        final String query1 = "xquery version '3.0';"
                + "<a>{ try { 'b' + 7 } catch * { 'c' } }</a>";

        final ResourceSet results = existEmbeddedServer.executeQuery(query1);
        final String r = (String) results.getResource(0).getContent();

        assertEquals("<a>c</a>", r);
    }

       @Test
    public void encapsulated_2() throws XMLDBException {
        final String query1 = "xquery version '3.0';"
                + "for $i in (1,2,3,4) return <a>{ try { 'b' + $i } catch * { 'c' } }</a>";

        final ResourceSet results = existEmbeddedServer.executeQuery(query1);
        assertEquals(4, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        assertEquals("<a>c</a>", r);
    }

   @Test
    public void encapsulated_3() throws XMLDBException, IOException, SAXException {
        final String query1 = "xquery version '3.0';"
                + "<foo>{ for $i in (1,2,3,4) return <a>{ try { 'b' + $i } catch * { 'c' } }</a> }</foo>";

        final ResourceSet results = existEmbeddedServer.executeQuery(query1);
        assertEquals(1, results.getSize());

        final String r = (String) results.getResource(0).getContent();
        XMLUnit.setIgnoreWhitespace(true);
        XMLAssert.assertXMLEqual("<foo><a>c</a><a>c</a><a>c</a><a>c</a></foo>", r);
    }

    @Test
    public void xQuery3_1() throws XMLDBException {
        final String query1 = "xquery version '1.0';"
                + "try { a + 7 } catch * { 1 }";
        try {
            final ResourceSet results = existEmbeddedServer.executeQuery(query1);
            final String r = (String) results.getResource(0).getContent();
            assertEquals("1", r);
            fail("exception expected");
        } catch (final Throwable t) {

            final Throwable cause = t.getCause();
            if (cause instanceof XPathException) {
                final XPathException ex = (XPathException) cause;
                assertEquals("exerr:EXXQDY0003", ex.getErrorCode().getErrorQName().getStringValue());
            } else {
                throw t;
            }
        }
    }

    @Test
    public void simpleCatch() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "try { a + 7 } catch * { 1 }";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        final String r = (String) results.getResource(0).getContent();
        assertEquals("1", r);
    }

    @Test
    public void catchWithCodeAndDescription() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch * "
                + "{  $err:code, $err:description } ";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(2, results.getSize());

        final String r1 = (String) results.getResource(0).getContent();
        assertEquals(ErrorCodes.XPDY0002.getErrorQName().getStringValue(), r1);

        final String r2 = (String) results.getResource(1).getContent();
        assertEquals(ErrorCodes.XPDY0002.getDescription() + " Undefined context sequence for 'child::{}a'", r2);
    }

    @Test
    public void catchWithError3Matches() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 { 1 }"
                + "catch err:XPDY0002 { 2 }"
                + "catch err:XPDY0003 { 3 }";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        final String r = (String) results.getResource(0).getContent();
        assertEquals("2", r);
    }

    @Test(expected = XMLDBException.class)
    public void catchWithErrorNoMatches() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 { 1 }"
                + "catch err:XPDY0002 { a }"
                + "catch err:XPDY0003 { 3 }";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        final String r = (String) results.getResource(0).getContent();
        assertEquals("2", r);
    }

    @Test
    public void catchWithMultipleMatches() throws XMLDBException {
        final String query1 = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 | err:XPDY0003 { 13 }"
                + "catch err:XPDY0002 { 2 }"
                + "catch err:XPDY0004 | err:XPDY0005 { 45 }";

        final ResourceSet results = existEmbeddedServer.executeQuery(query1);
        final String r = (String) results.getResource(0).getContent();
        assertEquals("2", r);

        final String query2 = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch err:XPDY0001 | * { 13 }"
                + "catch err:XPDY0002 { 2 }"
                + "catch err:XPDY0004 | err:XPDY0005 { 45 }";

        final ResourceSet results2 = existEmbeddedServer.executeQuery(query2);
        final String r2 = (String) results2.getResource(0).getContent();
        assertEquals("13", r2);
    }


    @Test
    public void catchFnError() throws XMLDBException {
        final String query1 = "xquery version '3.0';"
                + "try {"
                + " fn:error( fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000') ) "
                + "} catch * "
                + "{ $err:code }";

        final ResourceSet results = existEmbeddedServer.executeQuery(query1);
        assertEquals(1, results.getSize());
        final String r1 = (String) results.getResource(0).getContent();
        assertEquals("err:FOER0000", r1);


        final String query2 = "xquery version '3.0';"
                + "try {"
                + " fn:error( fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000') ) "
                + "} catch * "
                + "{ $err:code }";

        final ResourceSet results2 = existEmbeddedServer.executeQuery(query2);
        assertEquals(1, results2.getSize());
        final String r2 = (String) results2.getResource(0).getContent();
        assertEquals("err:FOER0000", r2);

        
        final String query3 = "xquery version '3.0';"
                + "try {"
                + " fn:error(fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000'), 'TEST') "
                + "} catch * "
                + "{ $err:code, $err:description }";

        final ResourceSet results3 = existEmbeddedServer.executeQuery(query3);
        assertEquals(2, results3.getSize());
        final String r31 = (String) results3.getResource(0).getContent();
        assertEquals("err:FOER0000", r31);
        final String r32 = (String) results3.getResource(1).getContent();
        assertEquals("TEST", r32);


        final String query4 = "xquery version '3.0';"
                + "try {"
                + " fn:error(fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000'), 'TEST') "
                + "} catch *  "
                + "{ $err:code, $err:description }";

        final ResourceSet results4 = existEmbeddedServer.executeQuery(query4);
        assertEquals(2, results4.getSize());
        final String r41 = (String) results4.getResource(0).getContent();
        assertEquals("err:FOER0000", r41);
        final String r42 = (String) results4.getResource(1).getContent();
        assertEquals("TEST", r42);


        final String query5 = "xquery version '3.0';"
                + "try {"
                + " fn:error(fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000'), 'TEST', <ab/>) "
                + "} catch *  "
                + "{ $err:code, $err:description, $err:value }";

        final ResourceSet results5 = existEmbeddedServer.executeQuery(query5);
        assertEquals(3, results5.getSize());
        final String r51 = (String) results5.getResource(0).getContent();
        assertEquals("err:FOER0000", r51);
        final String r52 = (String) results5.getResource(1).getContent();
        assertEquals("TEST", r52);
        final String r53 = (String) results5.getResource(2).getContent();
        assertEquals("<ab/>", r53);
    }

    @Test
    public void catchFullErrorCode() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "try { a + 7 } "
                + "catch *  "
                + "{  $err:code, $err:description, empty($err:value) } ";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(3, results.getSize());

        final String r1 = (String) results.getResource(0).getContent();
        assertEquals(ErrorCodes.XPDY0002.getErrorQName().getStringValue(), r1);

        final String r2 = (String) results.getResource(1).getContent();
        assertEquals(ErrorCodes.XPDY0002.getDescription() + " Undefined context sequence for 'child::{}a'", r2);

        final String r3 = (String) results.getResource(2).getContent();
        assertEquals("true", r3);
    }

    @Test
    public void catchDefinedNamespace() throws XMLDBException {
        final String query1 = "xquery version '3.0';"
                + "declare namespace foo='http://foo.com'; "
                + "try { "
                + "     fn:error(fn:QName('http://foo.com', 'ERRORNAME'), 'ERRORTEXT') "
                + "} "
                + "catch foo:ERRORNAME  { 'good' } "
                + "catch *  { 'bad' } ";

        final ResourceSet results = existEmbeddedServer.executeQuery(query1);
        assertEquals(1, results.getSize());
        final String r1 = (String) results.getResource(0).getContent();
        assertEquals("good", r1);


        final String query2 = "xquery version '3.0';"
                + "declare namespace foo='http://foo.com'; "
                + "try { "
                + "     fn:error(fn:QName('http://foo.com', 'ERRORNAME'), 'ERRORTEXT') "
                + "} "
                + "catch foo:ERRORNAME { $err:code } "
                + "catch *  { 'bad' } ";

        final ResourceSet results2 = existEmbeddedServer.executeQuery(query2);
        assertEquals(1, results2.getSize());
        final String r2 = (String) results2.getResource(0).getContent();
        assertEquals("foo:ERRORNAME", r2);
    }

    @Test
    public void catchDefinedNamespace2() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "declare namespace foo='http://foo.com'; "
                + "try { "
                + "     fn:error(fn:QName('http://foo.com', 'ERRORNAME'), 'ERRORTEXT')"
                + "} "
                + "catch foo:ERRORNAME { 'good' } "
                + "catch * { 'wrong' } ";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        assertEquals(1, results.getSize());

        final String r1 = (String) results.getResource(0).getContent();
        assertEquals("good", r1);
    }
}
