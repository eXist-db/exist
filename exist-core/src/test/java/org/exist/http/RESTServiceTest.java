/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
package org.exist.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import javax.xml.parsers.ParserConfigurationException;

import com.googlecode.junittoolbox.ParallelRunner;
import org.eclipse.jetty.http.HttpStatus;
import org.exist.Namespaces;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.test.ExistWebServer;
import org.exist.util.Base64Encoder;
import org.exist.util.ExistSAXParserFactory;
import org.exist.xmldb.XmldbURI;
import org.junit.runner.RunWith;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

/**
 * A test case for accessing a remote server via REST-Style Web API.
 * @author <a href="mailto:pierrick.brihaye@free.fr">wolf
 * @author Pierrick Brihaye</a>
 */
//@RunWith(ParallelRunner.class)    // TODO(AR) when running in parallel a deadlock is encountered in eXist-db... this needs to be resolved!
public class RESTServiceTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String XML_DATA = "<test>"
            + "<para>\u00E4\u00E4\u00FC\u00FC\u00F6\u00F6\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC</para>"
            + "</test>";

    private static final String XUPDATE = "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">"
            + "<xu:append select=\"/test\" child=\"1\">"
            + "<para>Inserted paragraph.</para>"
            + "</xu:append>" + "</xu:modifications>";

    private static final String QUERY_REQUEST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<query xmlns=\"" + Namespaces.EXIST_NS + "\">"
            + "<properties>"
            + "<property name=\"indent\" value=\"yes\"/>"
            + "<property name=\"encoding\" value=\"UTF-8\"/>"
            + "</properties>"
            + "<text>"
            + "xquery version \"1.0\";"
            + "(::pragma exist:serialize indent=no ::)"
            + "//para"
            + "</text>" + "</query>";

    private static final String QUERY_REQUEST_ERROR = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<query xmlns=\"" + Namespaces.EXIST_NS + "\">"
            + "<properties>"
            + "<property name=\"indent\" value=\"yes\"/>"
            + "<property name=\"encoding\" value=\"UTF-8\"/>"
            + "</properties>"
            + "<text>"
            + "xquery version \"1.0\";"
            + "//undeclared:para"
            + "</text>" + "</query>";

    private static final String TEST_MODULE =
            "module namespace t=\"http://test.foo\";\n" +
                    "declare variable $t:VAR { 'World!' };";

    private static final String TEST_XQUERY =
            "xquery version \"1.0\";\n" +
                    "declare option exist:serialize \"method=text media-type=text/text\";\n" +
                    "import module namespace request=\"http://exist-db.org/xquery/request\";\n" +
                    "import module namespace t=\"http://test.foo\" at \"module.xq\";\n" +
                    "let $param := request:get-parameter('p', ())\n" +
                    "return\n" +
                    "	($param, ' ', $t:VAR)";

    private static final String TEST_XQUERY_PARAMETER =
            "xquery version \"1.0\";\n" +
                    "declare namespace request=\"http://exist-db.org/xquery/request\";\n" +
                    "import module namespace requestparametermod=\"http://exist-db.org/xquery/requestparametermod\" at \"requestparametermod.xqm\";\n" +
                    "concat(\"xql=\", request:get-parameter(\"doc\",())),\n" +
                    "concat(\"xqm=\", $requestparametermod:request)";

    private static final String TEST_XQUERY_PARAMETER_MODULE =
            "module namespace requestparametermod = \"http://exist-db.org/xquery/requestparametermod\";\n" +
                    "declare namespace request=\"http://exist-db.org/xquery/request\";\n" +
                    "declare variable $requestparametermod:request { request:get-parameter(\"doc\",())};\n";

    private static final String TEST_XQUERY_WITH_PATH_PARAMETER =
            "xquery version \"1.0\";\n" +
                    "declare namespace request=\"http://exist-db.org/xquery/request\";\n" +
                    "declare option exist:serialize \"method=text media-type=text/text\";\n" +
                    "(\"pathInfo=\", request:get-path-info(), \"\n\"," +
                    "\"servletPath=\", request:get-servlet-path(), \"\n\")";

    private static final String TEST_XQUERY_WITH_PATH_AND_CONTENT =
            "xquery version \"3.0\";\n" +
                    "declare namespace request=\"http://exist-db.org/xquery/request\";\n" +
                    "declare option exist:serialize \"method=text media-type=text/text\";\n" +
                    "request:get-data()//data/text() || ' ' || request:get-path-info()";

    private static String credentials;
    private static String badCredentials;

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort() + "/rest";
    }

    private static String getServerUriRedirected() {
        return "http://localhost:" + existWebServer.getPort();
    }

    private static String getCollectionUri() {
        return getServerUri() + XmldbURI.ROOT_COLLECTION + "/test";
    }

    private static String getCollectionUriRedirected() {
        return getServerUriRedirected() + XmldbURI.ROOT_COLLECTION + "/test";
    }

    private static String getResourceUri() {
        return getServerUri() + XmldbURI.ROOT_COLLECTION + "/test/test.xml";
    }

    /* About path components of URIs:

     ** reserved characters # http://tools.ietf.org/html/rfc3986#section-2.2
     *
     *  gen-delims  = ":" / "/" / "?" / "#" / "[" / "]" / "@"
     *  sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
     *              / "*" / "+" / "," / ";" / "="
     *  reserved    = gen-delims / sub-delims
        RCHAR=": / ? # [ ] @ ! $ & ' ( ) *  + , ; ="

     ** path-segment # http://tools.ietf.org/html/rfc3986#section-3.3
     *
     *  unreserved  = ALPHA / DIGIT / "-" / "." / "_" / "~"
     *  pct-encoded = "%" HEXDIG HEXDIG
     *  sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
     *              / "*" / "+" / "," / ";" / "="
     *  pchar       = unreserved / pct-encoded / sub-delims / ":" / "@"

     ** So, characters literally allowed in a path-segment are:
        PCHAR="A-Z a-z 0-9 - . _ ~ ! $ & ' ( ) *  + , ; = : @"

     ** All the rest has to be percent-encoded
     *  the percent sign itself MUST start a code
     *  reserved+ chars in need of encoding - in a path-segment - are:
     *       /   ?   #   [   ]   %
     *  %20 %2F %3F %23 %5B %5D %25

     ** Interoperability /rest/ space:
     *  most webbrowsers act mostly correct
     *  curl does _no_ encoding on its own
     *  all browsers send a bare / as is (user error? will separate path-segments)
     *  all browsers send a bare ? as is (user error? will start the query-string)
     *  no browser sends a bare # at all (user error? will start the fragment-identifier)
     *  chrome and msie send [] verbatim (wrong? apache can accomodate…)
     *  all browsers send a bare % as is (user error? will start an escape, apache returns Bad Request)

     ** Interoperability /webdav/ space:
     *  the GET and PUT methods mirror /rest/ space
     *  These characters are not allowed in an NFTS filename
        INTFS='/ \ : *   ? " < > |'
     *  of those, Macintosh HFS only prohibits the colon
     *  most other UN*X FSs only prohibit the slash
     *  Quick test with bash on Linux extfs:
        TWDAV="$PCHAR $RCHAR $INTFS %"
     *  set -f; for fn in $TWDAV; do echo T__${fn}__ > /tmp/T__${fn}__; done
     *  only the slash will error out (twice)
     *  anything in this set can be thrown at webdav!

     ** Beware, some chars valid in a path-segment must not be in a filename (mostly NTFS)
     */
    // Below String mostly contains the PCHAR set literally; the colon fails though, so its omitted…
    // Also in the mix: some (mandatory except %27) escapes, some multibyte UTF-8 characters
    // and a superficial directory traversal and a superficial double slash too
    private static String getResourceUriPlus() {
        return getServerUri() + XmldbURI.ROOT_COLLECTION + "/test//../test/A-Za-z0-9_~!$&'()*+,;=@%20%23%25%27%2F%3F%5B%5Däöü.xml";
    }


    @BeforeClass
    public static void createCredentials() {
        final Base64Encoder enc = new Base64Encoder();
        enc.translate("admin:".getBytes());
        credentials = new String(enc.getCharArray());

        enc.reset();
        enc.translate("johndoe:this pw should fail".getBytes());
        badCredentials = new String(enc.getCharArray());
    }

    @Test
    public void getFailNoSuchDocument() throws IOException {
        final String uri = getCollectionUri() + "/nosuchdocument.xml";
        final HttpURLConnection connect = getConnection(uri);
        try {
            connect.setRequestMethod("GET");
            connect.connect();

            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.NOT_FOUND_404, r);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void xqueryGetWithEmptyPath() throws IOException {
        /* store the documents that we need for this test */
        doPut(TEST_XQUERY_WITH_PATH_PARAMETER, "requestwithpath.xq", HttpStatus.CREATED_201);

        final String path = getCollectionUri() + "/requestwithpath.xq";
        final HttpURLConnection connect = getConnection(path);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("GET");
            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);
            final String response = readResponse(connect.getInputStream());
            final String pathInfo = response.substring("pathInfo=".length(), response.indexOf("servletPath=") - 2);
            final String servletPath = response.substring(response.indexOf("servletPath=") + "servletPath=".length(), response.lastIndexOf("\r\n"));

            //check the responses
            assertEquals("XQuery servletPath is: \"" + servletPath + "\" expected: \"/db/test/requestwithpath.xq\"", "/db/test/requestwithpath.xq", servletPath);
            assertEquals("XQuery pathInfo is: \"" + pathInfo + "\" expected: \"\"", "", pathInfo);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void xqueryPOSTWithEmptyPath() throws IOException {
        /* store the documents that we need for this test */
        doPut(TEST_XQUERY_WITH_PATH_PARAMETER, "requestwithpath.xq", HttpStatus.CREATED_201);

        String path = getCollectionUri() + "/requestwithpath.xq";
        final HttpURLConnection connect = preparePost("boo", path);
try {
        connect.connect();
        final int r = connect.getResponseCode();
        assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);
        final String response = readResponse(connect.getInputStream());
        final String pathInfo = response.substring("pathInfo=".length(), response.indexOf("servletPath=")-2);
        final String servletPath = response.substring(response.indexOf("servletPath=") + "servletPath=".length(), response.lastIndexOf("\r\n"));

        //check the responses
        assertEquals("XQuery servletPath is: \"" + servletPath + "\" expected: \"/db/test/requestwithpath.xq\"", "/db/test/requestwithpath.xq", servletPath);
        assertEquals("XQuery pathInfo is: \"" + pathInfo + "\" expected: \"\"", "", pathInfo);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void xqueryGetWithNonEmptyPath() throws IOException {
        /* store the documents that we need for this test */
        doPut(TEST_XQUERY_WITH_PATH_PARAMETER, "requestwithpath.xq", HttpStatus.CREATED_201);

        final String path = getCollectionUri() + "/requestwithpath.xq/some/path";
        final HttpURLConnection connect = getConnection(path);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("GET");
            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);
            final String response = readResponse(connect.getInputStream());
            final String pathInfo = response.substring("pathInfo=".length(), response.indexOf("servletPath=") - 2);
            final String servletPath = response.substring(response.indexOf("servletPath=") + "servletPath=".length(), response.lastIndexOf("\r\n"));

            //check the responses
            assertEquals("XQuery servletPath is: \"" + servletPath + "\" expected: \"/db/test/requestwithpath.xq\"", "/db/test/requestwithpath.xq", servletPath);
            assertEquals("XQuery pathInfo is: \"" + pathInfo + "\" expected: \"/some/path\"", "/some/path", pathInfo);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void xqueryPOSTWithNonEmptyPath() throws IOException {
        /* store the documents that we need for this test */
        doPut(TEST_XQUERY_WITH_PATH_PARAMETER, "requestwithpath.xq", HttpStatus.CREATED_201);

        final String path = getCollectionUri() + "/requestwithpath.xq/some/path";
        final HttpURLConnection connect = preparePost("boo", path);
        try {
            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);
            final String response = readResponse(connect.getInputStream());
            final String pathInfo = response.substring("pathInfo=".length(), response.indexOf("servletPath=") - 2);
            final String servletPath = response.substring(response.indexOf("servletPath=") + "servletPath=".length(), response.lastIndexOf("\r\n"));

            //check the responses
            assertEquals("XQuery servletPath is: \"" + servletPath + "\" expected: \"/db/test/requestwithpath.xq\"", "/db/test/requestwithpath.xq", servletPath);
            assertEquals("XQuery pathInfo is: \"" + pathInfo + "\" expected: \"/some/path\"", "/some/path", pathInfo);
        } finally {
            connect.disconnect();
        }
    }


    @Test
    public void xqueryGetFailWithNonEmptyPath() throws IOException {
        /* store the documents that we need for this test */
        final HttpURLConnection sconnect = getConnection(getResourceUri());
        try {
            sconnect.setRequestProperty("Authorization", "Basic " + credentials);
            sconnect.setRequestMethod("PUT");
            sconnect.setDoOutput(true);
            sconnect.setRequestProperty("ContentType", "application/xml");
            try (final Writer writer = new OutputStreamWriter(sconnect.getOutputStream(), UTF_8)) {
                writer.write(XML_DATA);
            }

            final String path = getResourceUri() + "/some/path";    // should not be able to get this path
            final HttpURLConnection connect = getConnection(path);
            try {
                connect.setRequestMethod("GET");
                connect.connect();
                final int r = connect.getResponseCode();
                assertEquals("Server returned response code " + r, HttpStatus.NOT_FOUND_404, r);
            } finally {
                connect.disconnect();
            }
        } finally {
            sconnect.disconnect();
        }
    }

    @Test
    public void testPut() throws IOException {
        final int r = uploadData();
        assertEquals("Server returned response code " + r, HttpStatus.CREATED_201, r);

        doGet();
    }

    @Test
    public void testPutPlus() throws IOException {
        assumeThat("Requires non-Windows platform", System.getProperty("os.name").toLowerCase(), not(containsString("win")));

        final int r = uploadDataPlus();
        assertEquals("Server returned response code " + r, HttpStatus.CREATED_201, r);

        doGetPlus();
    }

    @Test
    public void putFailAgainstCollection() throws IOException {
        final HttpURLConnection connect = getConnection(getCollectionUri());
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "application/xml");
            try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
                writer.write(XML_DATA);
            }

            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.BAD_REQUEST_400, r);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void putWithCharset() throws IOException {
        final HttpURLConnection connect = getConnection(getResourceUri());
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "application/xml; charset=UTF-8");

            try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
                writer.write(XML_DATA);
            }

            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.CREATED_201, r);

            doGet();
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void putFailAndRechallengeAuthorization() throws IOException {
        final HttpURLConnection connect = getConnection(getResourceUri());
        try {
            connect.setRequestProperty("Authorization", "Basic " + badCredentials);
            connect.setDoOutput(true);
            connect.setRequestMethod("PUT");
            connect.setAllowUserInteraction(false);
            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.UNAUTHORIZED_401, r);
            final String auth = connect.getHeaderField("WWW-Authenticate");
            assertEquals("WWW-Authenticate = " + auth, "Basic realm=\"exist\"", auth);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void putAgainstXQuery() throws IOException {
        doPut(TEST_XQUERY_WITH_PATH_AND_CONTENT, "requestwithcontent.xq", HttpStatus.CREATED_201);

        final String path = getCollectionUriRedirected() + "/requestwithcontent.xq/a/b/c";
        final HttpURLConnection connect = getConnection(path);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "application/xml");
            try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
                writer.write("<data>test data</data>");
            }

            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("doPut: Server returned response code " + r, HttpStatus.OK_200, r);

            //get the response of the query
            final String response = readResponse(connect.getInputStream());
            assertEquals("test data /a/b/c", response.trim());
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void deleteAgainstXQuery() throws IOException {
        doPut(TEST_XQUERY_WITH_PATH_PARAMETER, "requestwithcontent.xq", HttpStatus.CREATED_201);

        final String path = getCollectionUriRedirected() + "/requestwithcontent.xq/a/b/c";
        final HttpURLConnection connect = getConnection(path);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("DELETE");

            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("doDelete: Server returned response code " + r, HttpStatus.OK_200, r);

            //get the response of the query
            final String response = readResponse(connect.getInputStream());
            final String pathInfo = response.substring("pathInfo=".length(), response.indexOf("servletPath=")-2);
            assertEquals("/a/b/c", pathInfo);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void headAgainstXQuery() throws IOException {
        doPut(TEST_XQUERY_WITH_PATH_PARAMETER, "requestwithcontent.xq", HttpStatus.CREATED_201);

        final String path = getCollectionUriRedirected() + "/requestwithcontent.xq/a/b/c";
        final HttpURLConnection connect = getConnection(path);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("HEAD");

            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("doHead: Server returned response code " + r, HttpStatus.OK_200, r);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void xUpdate() throws IOException {
        final HttpURLConnection connect = preparePost(XUPDATE, getResourceUri());
        try {
            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);

            doGet();
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void queryPost() throws IOException, SAXException, ParserConfigurationException {
        uploadData();
        
        final HttpURLConnection connect = preparePost(QUERY_REQUEST, getResourceUri());
        try {
            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);

            final String data = readResponse(connect.getInputStream());
            final int hits = parseResponse(data);
            assertEquals(1, hits);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void queryPostXQueryError() throws IOException {
        final HttpURLConnection connect = preparePost(QUERY_REQUEST_ERROR, getResourceUri());
        try {
            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.BAD_REQUEST_400, r);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void queryGet() throws IOException {
        final String uri = getCollectionUri()
                + "?_query="
                + URLEncoder
                        .encode(
                                "doc('"
                                        + XmldbURI.ROOT_COLLECTION
                                        + "/test/test.xml')//para[. = '\u00E4\u00E4\u00FC\u00FC\u00F6\u00F6\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC']/text()",
                                UTF_8.displayName());
        final HttpURLConnection connect = getConnection(uri);
        try {
            connect.setRequestMethod("GET");
            connect.connect();

            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);

            readResponse(connect.getInputStream());
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void queryGetXQueryError() throws IOException {
        String uri = getCollectionUri()
                + "?_query="
                + URLEncoder
                .encode(
                        "not-$a:-function()",
                        UTF_8.displayName());
        final HttpURLConnection connect = getConnection(uri);
        try {
            connect.setRequestMethod("GET");
            connect.connect();

            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.BAD_REQUEST_400, r);
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void requestModule() throws IOException {
        String uri = getCollectionUri() + "?_query=request:get-uri()&_wrap=no";
        HttpURLConnection connect = getConnection(uri);
        try {
            connect.setRequestMethod("GET");
            connect.connect();

            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);

            final String response = readResponse(connect.getInputStream()).trim();
            assertTrue(response.endsWith(XmldbURI.ROOT_COLLECTION + "/test"));
        } finally {
            connect.disconnect();
        }

        uri = getCollectionUri() + "?_query=request:get-url()&_wrap=no";
        try {
            connect = getConnection(uri);
            connect.setRequestMethod("GET");
            connect.connect();

            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);

            final String response = readResponse(connect.getInputStream()).trim();
            //TODO : the server name may have been renamed by the Web server
            assertTrue(response.endsWith(XmldbURI.ROOT_COLLECTION + "/test"));
        } finally {
            connect.disconnect();
        }
    }

    @Test
    public void requestGetParameterFromModule() throws IOException {
        /* store the documents that we need for this test */
        doPut(TEST_XQUERY_PARAMETER, "requestparameter.xql", HttpStatus.CREATED_201);
        doPut(TEST_XQUERY_PARAMETER_MODULE, "requestparametermod.xqm", HttpStatus.CREATED_201);

        /* execute the stored xquery a few times */
        for (int i = 0; i < 5; i++) {
            final HttpURLConnection connect = getConnection(getCollectionUri() + "/requestparameter.xql?doc=somedoc" + i);
            try {
                connect.setRequestProperty("Authorization", "Basic " + credentials);
                connect.setRequestMethod("GET");
                connect.connect();

                final int iHttpResult = connect.getResponseCode();
                assertEquals("Server returned response code " + iHttpResult, HttpStatus.OK_200, iHttpResult);
                String contentType = connect.getContentType();
                final int semicolon = contentType.indexOf(';');
                if (semicolon > 0) {
                    contentType = contentType.substring(0, semicolon).trim();
                }
                assertEquals("Server returned content type " + contentType, "application/xml", contentType);

                //get the response of the query
                final String response = readResponse(connect.getInputStream());

                final String strXQLRequestParameter = response.substring("xql=".length(), response.indexOf("xqm="));
                final String strXQMRequestParameter = response.substring(response.indexOf("xqm=") + "xqm=".length(), response.lastIndexOf("\r\n"));

                //check the responses
                assertEquals("XQuery Request Parameter is: \"" + strXQLRequestParameter + "\" expected: \"somedoc" + i + "\"", "somedoc" + i, strXQLRequestParameter);
                assertEquals("XQuery Module Request Parameter is: \"" + strXQMRequestParameter + "\" expected: \"somedoc" + i + "\"", "somedoc" + i, strXQMRequestParameter);
            } finally {
                connect.disconnect();
            }
        }
    }

    @Test
    public void storedQuery() throws IOException {
        doPut(TEST_MODULE, "module.xq", HttpStatus.CREATED_201);
        doPut(TEST_XQUERY, "test.xq", HttpStatus.CREATED_201);

        doStoredQuery(false, false);

        // cached:
        doStoredQuery(true, false);

        // cached and wrapped:
        doStoredQuery(true, true);
    }

    private void doPut(final String data, final String path, final int responseCode) throws IOException {
        final HttpURLConnection connect = getConnection(getCollectionUri() + '/' + path);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "application/xquery");
            try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
                writer.write(data);
            }

            connect.connect();
            final int r = connect.getResponseCode();
            assertEquals("doPut: Server returned response code " + r, responseCode, r);
        } finally {
            connect.disconnect();
        }
    }

    private void doStoredQuery(final boolean cacheHeader, final boolean wrap) throws IOException {

        String uri = getCollectionUri() + "/test.xq?p=Hello";
        if(wrap) {
            uri += "&_wrap=yes";
        }

        final HttpURLConnection connect = getConnection(uri);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("GET");
            connect.connect();

            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);

            final String cached = connect.getHeaderField("X-XQuery-Cached");
            assertNotNull(cached);
            assertEquals(cacheHeader, Boolean.valueOf(cached).booleanValue());

            String contentType = connect.getContentType();
            final int semicolon = contentType.indexOf(';');
            if (semicolon > 0) {
                contentType = contentType.substring(0, semicolon).trim();
            }
            if (wrap) {
                assertEquals("Server returned content type " + contentType, "application/xml", contentType);
            } else {
                assertEquals("Server returned content type " + contentType, "text/text", contentType);
            }

            final String response = readResponse(connect.getInputStream());
            if (wrap) {
                assertTrue("Server returned response: " + response,
                        response.startsWith("<exist:result "));
            } else {
                assertTrue("Server returned response: " + response,
                        response.startsWith("Hello World!"));
            }
        } finally {
            connect.disconnect();
        }
    }

    private int uploadData() throws IOException {
        final HttpURLConnection connect = getConnection(getResourceUri());
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "application/xml");
            try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
                writer.write(XML_DATA);
            }

            connect.connect();
            return connect.getResponseCode();
        } finally {
            connect.disconnect();
        }
    }

    private void doGet() throws IOException {
        final HttpURLConnection connect = getConnection(getResourceUri());
        try {
            connect.setRequestMethod("GET");
            connect.connect();

            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);
            String contentType = connect.getContentType();
            final int semicolon = contentType.indexOf(';');
            if (semicolon > 0) {
                contentType = contentType.substring(0, semicolon).trim();
            }
            assertEquals("Server returned content type " + contentType, "application/xml", contentType);

            readResponse(connect.getInputStream());
        } finally {
            connect.disconnect();
        }
    }

    private int uploadDataPlus() throws IOException {
        final HttpURLConnection connect = getConnection(getResourceUriPlus());
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "application/xml");
            try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
                writer.write(XML_DATA);
            }

            connect.connect();
            return connect.getResponseCode();
        } finally {
            connect.disconnect();
        }
    }

    private void doGetPlus() throws IOException {
        final HttpURLConnection connect = getConnection(getResourceUriPlus());
        try {
            connect.setRequestMethod("GET");
            connect.connect();

            final int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, HttpStatus.OK_200, r);
            String contentType = connect.getContentType();
            final int semicolon = contentType.indexOf(';');
            if (semicolon > 0) {
                contentType = contentType.substring(0, semicolon).trim();
            }
            assertEquals("Server returned content type " + contentType, "application/xml", contentType);

            readResponse(connect.getInputStream());
        } finally {
            connect.disconnect();
        }
    }

    private HttpURLConnection preparePost(final String content, final String path) throws IOException {
        final HttpURLConnection connect = getConnection(path);
        try {
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("POST");
            connect.setDoOutput(true);
            connect.setRequestProperty("Content-Type", "application/xml");

            try (final Writer writer = new OutputStreamWriter(connect.getOutputStream(), UTF_8)) {
                writer.write(content);
            }

            return connect;
        } finally {
            connect.disconnect();
        }
    }

    private String readResponse(final InputStream is) throws IOException {
        try(final BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8))) {
            String line;
            final StringBuilder out = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\r\n");
            }
            return out.toString();
        }
    }

    private int parseResponse(final String data) throws IOException, SAXException, ParserConfigurationException {
        final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
        factory.setNamespaceAware(true);
        final InputSource src = new InputSource(new StringReader(data));
        final SAXParser parser = factory.newSAXParser();
        final XMLReader reader = parser.getXMLReader();
        final SAXAdapter adapter = new SAXAdapter();
        reader.setContentHandler(adapter);
        reader.parse(src);

        final Document doc = adapter.getDocument();

        final Element root = doc.getDocumentElement();
        final String hits = root.getAttributeNS(Namespaces.EXIST_NS, "hits");
        return Integer.parseInt(hits);
    }

    private HttpURLConnection getConnection(final String url) throws IOException {
        final URL u = new URL(url);
        return (HttpURLConnection) u.openConnection();
    }
}
