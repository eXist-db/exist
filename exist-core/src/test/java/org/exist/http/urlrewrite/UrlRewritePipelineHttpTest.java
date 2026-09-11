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
package org.exist.http.urlrewrite;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.TestUtils;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.http.urlrewrite.XQueryURLRewrite.LEGACY_XQUERY_CONTROLLER_FILENAME;
import static org.exist.http.urlrewrite.XQueryURLRewrite.XQUERY_CONTROLLER_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Consolidated HTTP-level tests for the controller.xql URL-rewriting pipeline, formerly nine
 * separate classes each paying their own {@code ExistWebServer} startup/shutdown cost (~10-15s)
 * for the exact same standalone-mode config. Sharing one server here amortizes that cost across
 * every scenario below instead of paying it once per class -- {@code CachingResponseWrapperTest}
 * and {@code ControllerRequestWrapperTest} already cover what these scenarios' underlying classes
 * do in isolation at unit speed; what only a real HTTP round trip through a live Jetty +
 * XQueryURLRewrite + broker pool can prove is the actual wiring between them, which is what every
 * method below is for.
 * <p>
 * {@link URLRewriteContentLengthViewPipelineTest} is the one exception left as its own class: it
 * needs a distribution-mode {@code ExistWebServer} (real filesystem static-resource serving via
 * Jetty's default servlet) rather than this standalone-mode config, so it cannot share this server.
 * <p>
 * Organized by originating scenario (see section banners) rather than merged into one
 * undifferentiated method list, since each proves a distinct fact using its own fixtures.
 */
public class UrlRewritePipelineHttpTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    // ================================================================================
    // ControllerTest: controller.xq / controller.xql discovery and precedence
    // ================================================================================

    private static final String CONTROLLER_XQUERY = "<controller>xq</controller>";
    private static final String LEGACY_CONTROLLER_XQUERY = "<controller>xql</controller>";
    private static final String TEST_DOCUMENT_NAME = "test.xml";

    @Test
    public void findsLegacyController() throws IOException {
        final String testCollectionName = "test-finds-legacy-controller";
        storeAppsDoc(testCollectionName, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", LEGACY_CONTROLLER_XQUERY);

        final Tuple2<Integer, String> responseCodeAndBody = getAppsDoc(testCollectionName, TEST_DOCUMENT_NAME);
        assertEquals(HttpURLConnection.HTTP_OK, (int) responseCodeAndBody._1);
        assertEquals(LEGACY_CONTROLLER_XQUERY, responseCodeAndBody._2);
    }

    @Test
    public void findsController() throws IOException {
        final String testCollectionName = "test-finds-controller";
        storeAppsDoc(testCollectionName, XQUERY_CONTROLLER_FILENAME, "application/xquery", CONTROLLER_XQUERY);

        final Tuple2<Integer, String> responseCodeAndBody = getAppsDoc(testCollectionName, TEST_DOCUMENT_NAME);
        assertEquals(HttpURLConnection.HTTP_OK, (int) responseCodeAndBody._1);
        assertEquals(CONTROLLER_XQUERY, responseCodeAndBody._2);
    }

    @Test
    public void prefersNonLegacyController() throws IOException {
        final String testCollectionName = "test-prefers-non-legacy-controller";
        storeAppsDoc(testCollectionName, XQUERY_CONTROLLER_FILENAME, "application/xquery", CONTROLLER_XQUERY);
        storeAppsDoc(testCollectionName, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", LEGACY_CONTROLLER_XQUERY);

        final Tuple2<Integer, String> responseCodeAndBody = getAppsDoc(testCollectionName, TEST_DOCUMENT_NAME);
        assertEquals(HttpURLConnection.HTTP_OK, (int) responseCodeAndBody._1);
        assertEquals(CONTROLLER_XQUERY, responseCodeAndBody._2);
    }

    // ================================================================================
    // IfModifiedSinceHandoverControllerTest: #6603 -- the client's If-Modified-Since
    // header must survive a controller.xql -> view.xql handover.
    // ================================================================================

    private static final String IF_MODIFIED_SINCE = "Wed, 21 Oct 2015 07:28:00 GMT";

    /** Echoes what the request: module can see of the If-Modified-Since header. */
    private static final String IMS_ECHO =
            """
            xquery version "3.1";
            <result get-header="{request:get-header('If-Modified-Since')}"
                    name-present="{'If-Modified-Since' = request:get-header-names()}"/>
            """;

    private static final String IMS_MODEL =
            """
            xquery version "3.1";
            <model/>
            """;

    /** Dispatch WITH a view: the model output is passed to the view (echo.xql). */
    private static final String IMS_CONTROLLER_WITH_VIEW =
            """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";
            declare variable $exist:controller external;
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="{$exist:controller}/model.xql"/>
                <view>
                    <forward url="{$exist:controller}/echo.xql"/>
                </view>
            </dispatch>
            """;

    /** Dispatch WITHOUT a view: echo.xql is the model, so caching is left enabled. */
    private static final String IMS_CONTROLLER_NO_VIEW =
            """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";
            declare variable $exist:controller external;
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="{$exist:controller}/echo.xql"/>
            </dispatch>
            """;

    @Test
    public void ifModifiedSinceValueSurvivesViewHandover() throws IOException {
        final String coll = "ims-with-view";
        storeAppsDoc(coll, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", IMS_CONTROLLER_WITH_VIEW);
        storeAppsDoc(coll, "model.xql", "application/xquery", IMS_MODEL);
        storeAppsDoc(coll, "echo.xql", "application/xquery", IMS_ECHO);

        final String body = sendWithIfModifiedSince(coll);

        // The header name is visible to the handler...
        assertTrue("If-Modified-Since should be listed by get-header-names(): " + body,
                body.contains("name-present=\"true\""));
        // ...and, after the #6603 fix, so is its value: the view handover no longer blanks
        // getHeader(), so request:get-header() returns what the client sent.
        assertTrue("If-Modified-Since value must survive the view handover (#6603): " + body,
                body.contains("get-header=\"" + IF_MODIFIED_SINCE + "\""));
    }

    @Test
    public void ifModifiedSinceValueIsVisibleWithoutView() throws IOException {
        final String coll = "ims-no-view";
        storeAppsDoc(coll, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", IMS_CONTROLLER_NO_VIEW);
        storeAppsDoc(coll, "echo.xql", "application/xquery", IMS_ECHO);

        final String body = sendWithIfModifiedSince(coll);

        // Control: the identical request through a view-less dispatch exposes the real value,
        // confirming the header is genuinely sent and that the loss is specific to the view path.
        assertTrue("If-Modified-Since value should be visible without a view: " + body,
                body.contains("get-header=\"" + IF_MODIFIED_SINCE + "\""));
    }

    // ================================================================================
    // MultipartMethodControllerTest: #6580, #6578 -- multipart/form-data parsing on
    // methods other than POST.
    // ================================================================================

    private static final String MULTIPART_BOUNDARY = "wdbBoundary";

    private static final String MULTIPART_BODY =
            "--" + MULTIPART_BOUNDARY + "\r\n"
          + "Content-Disposition: form-data; name=\"path\"\r\n"
          + "\r\n"
          + "edition/01/17410105.xml\r\n"
          + "--" + MULTIPART_BOUNDARY + "\r\n"
          + "Content-Disposition: form-data; name=\"file\"; filename=\"17410105.xml\"\r\n"
          + "Content-Type: application/xml\r\n"
          + "\r\n"
          + "<example>hello</example>\r\n"
          + "--" + MULTIPART_BOUNDARY + "--\r\n";

    private static final String MULTIPART_CONTROLLER =
            """
            xquery version "3.1";
            <result method="{request:get-method()}"
                    is-multipart="{request:is-multipart-content()}"
                    param-names="{string-join(request:get-parameter-names(), ',')}"
                    path="{request:get-parameter('path', ())}"
                    file-param="{request:get-parameter('file', ())}"
                    uploaded-files="{string-join(request:get-uploaded-file-name('file'), ',')}"/>
            """;

    @Test
    public void multipartFormDataIsParsedForBodyMethods() throws IOException {
        final String coll = "multipart-method-controller";
        storeAppsDoc(coll, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", MULTIPART_CONTROLLER);

        // A multipart/form-data body must be parsed identically for every body-carrying method:
        // both the form field ("path") and the uploaded file ("file") must be visible, and
        // request:is-multipart-content() must report true. Prior to the fix, PUT/PATCH reported
        // is-multipart-content()=false and exposed neither the file nor its part (#6580),
        // and the controller/RESTXQ path never exposed the uploaded file at all (#6578).
        for (final String method : new String[]{"POST", "PUT", "PATCH"}) {
            final String body = sendMultipart(coll, method);
            assertTrue(method + ": is-multipart-content() should be true: " + body,
                    body.contains("is-multipart=\"true\""));
            assertTrue(method + ": form field 'path' should be visible: " + body,
                    body.contains("path=\"edition/01/17410105.xml\""));
            assertTrue(method + ": uploaded file 'file' should be visible: " + body,
                    body.contains("uploaded-files=\"17410105.xml\""));
        }
    }

    @Test
    public void multipartFormDataIsNotParsedForGet() throws IOException {
        final String coll = "multipart-method-controller-get";
        storeAppsDoc(coll, LEGACY_XQUERY_CONTROLLER_FILENAME, "application/xquery", MULTIPART_CONTROLLER);

        // GET is a safe method with no defined semantics for a request body (RFC 9110 §9.3.1),
        // so a multipart/form-data body on GET must not be parsed: is-multipart-content() is false
        // and no uploaded file is exposed to the handler. (Non-file form fields may still leak via
        // the servlet container's parameter map -- a pre-existing quirk this fix does not change.)
        final String body = sendMultipart(coll, "GET");
        assertTrue("GET: is-multipart-content() must be false: " + body,
                body.contains("is-multipart=\"false\""));
        assertTrue("GET: uploaded file must not be exposed: " + body,
                body.contains("uploaded-files=\"\""));
    }

    // ================================================================================
    // URLRewritingTest: finds the nearest parent collection's controller.xq
    // ================================================================================

    private static final XmldbURI URT_TEST_COLLECTION_NAME = XmldbURI.create("controller-test");
    private static final XmldbURI URT_TEST_COLLECTION = XmldbURI.create("/db/apps").append(URT_TEST_COLLECTION_NAME);
    private static final String URT_TEST_CONTROLLER = "xquery version \"3.1\";\n<controller>{fn:current-dateTime()}</controller>";

    @BeforeClass
    public static void setupUrlRewritingTest() throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getRestUri(existWebServer) + URT_TEST_COLLECTION + "/" + XQUERY_CONTROLLER_FILENAME),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("Content-Type", "application/xquery")
                .PUT(HttpRequest.BodyPublishers.ofString(URT_TEST_CONTROLLER, StandardCharsets.UTF_8))
                .build();
        final int statusCode = withHttpClient(client -> executeForStatus(client, request));
        assertEquals(HttpURLConnection.HTTP_CREATED, statusCode);
    }

    @AfterClass
    public static void teardownUrlRewritingTest() throws IOException {
        final HttpRequest request = authenticatedRequest(URI.create(getRestUri(existWebServer) + URT_TEST_COLLECTION),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .DELETE()
                .build();
        final int statusCode = withHttpClient(client -> executeForStatus(client, request));
        assertEquals(HttpURLConnection.HTTP_OK, statusCode);
    }

    @Test
    public void findsParentController() throws IOException {
        final XmldbURI nestedCollectionName = XmldbURI.create("nested");
        final XmldbURI docName = XmldbURI.create("test.xml");
        final String testDocument = "<hello>world</hello>";

        final String storeDocUri = getRestUri(existWebServer) + URT_TEST_COLLECTION.append(nestedCollectionName).append(docName);
        final HttpRequest storeRequest = authenticatedRequest(URI.create(storeDocUri),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("Content-Type", "application/xml")
                .PUT(HttpRequest.BodyPublishers.ofString(testDocument, StandardCharsets.UTF_8))
                .build();
        final int storeResponseStatusCode = withHttpClient(client -> executeForStatus(client, storeRequest));
        assertEquals(HttpURLConnection.HTTP_CREATED, storeResponseStatusCode);

        final String retrieveDocUri = getAppsUri(existWebServer) + "/" + URT_TEST_COLLECTION_NAME.append(nestedCollectionName).append(docName);
        final HttpRequest retrieveRequest = authenticatedRequest(URI.create(retrieveDocUri),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .GET()
                .build();
        final Tuple2<Integer, String> retrieveResponseStatusCodeAndBody = withHttpClient(client -> {
            final HttpResponseResult r = executeForStatusAndBody(client, retrieveRequest);
            return Tuple(r.statusCode(), r.body());
        });
        assertEquals(HttpURLConnection.HTTP_OK, retrieveResponseStatusCodeAndBody._1.intValue());
        assertTrue(retrieveResponseStatusCodeAndBody._2.matches("<controller>.+</controller>"));
    }

    // ================================================================================
    // URLRewriteViewPipelineTest: a stored HTML document is forwarded through a view.xq
    // that processes it via request:get-data() -- catches XHTML serialization/parsing
    // regressions in that round trip.
    // ================================================================================

    private static final String VP_TEST_COLLECTION = "/db/apps/test-url-rewrite";

    private static final String VP_CONTROLLER_XQ = """
            xquery version "3.1";
            declare variable $exist:path external;
            declare variable $exist:resource external;
            declare variable $exist:controller external;
            declare variable $exist:prefix external;

            if (ends-with($exist:resource, '.html')) then
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                    <view>
                        <forward url="view.xq"/>
                    </view>
                </dispatch>
            else
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                    <cache-control cache="yes"/>
                </dispatch>""";

    private static final String VP_VIEW_XQ = """
            xquery version "3.1";
            declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";
            declare option output:method "html";
            declare option output:media-type "text/html";

            let $html := request:get-data()
            return
                <html>
                    <head>
                        <title>View Pipeline Test</title>
                        { $html/html/head/* }
                    </head>
                    { $html/html/body }
                </html>""";

    private static final String VP_HTML_WITH_HEAD = """
            <html>
                <head>
                    <title>Test Page</title>
                    <meta charset="utf-8"/>
                </head>
                <body>
                    <h1>Hello World</h1>
                </body>
            </html>""";

    private static final String VP_HTML_WITHOUT_HEAD = """
            <html>
                <body>
                    <h1>Hello World</h1>
                </body>
            </html>""";

    @BeforeClass
    public static void setupViewPipelineTest() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + VP_TEST_COLLECTION;

        storeViaRest(restUrl + "/controller.xq", VP_CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/view.xq", VP_VIEW_XQ, "application/xquery");
        storeViaRest(restUrl + "/with-head.html", VP_HTML_WITH_HEAD, "text/html");
        storeViaRest(restUrl + "/no-head.html", VP_HTML_WITHOUT_HEAD, "text/html");

        chmodRwxrxrx(VP_TEST_COLLECTION, "controller.xq", "view.xq");
    }

    @AfterClass
    public static void teardownViewPipelineTest() throws Exception {
        deleteViaRest("http://localhost:" + existWebServer.getPort() + "/exist/rest" + VP_TEST_COLLECTION);
    }

    /**
     * Tests that an HTML document WITH a head element can be served through
     * the URL rewrite view pipeline. This is the regression case -- the view
     * must receive the document as XML nodes, not as a string.
     */
    @Test
    public void htmlWithHeadThroughViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-url-rewrite/with-head.html";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);
        assertOk(result);
        final String body = result.body();

        // The response should contain the original title from the source HTML
        assertTrue("Response should contain the source page's title",
                body.contains("Test Page"));

        // The response should contain the view's wrapper title
        assertTrue("Response should contain the view's title",
                body.contains("View Pipeline Test"));

        // The response should contain the body content
        assertTrue("Response should contain body content",
                body.contains("Hello World"));

        // The response should NOT contain raw XML entities (indicating string was returned)
        assertTrue("Response should not contain escaped XML (string instead of nodes)",
                !body.contains("&lt;html"));
    }

    /**
     * Tests that an HTML document WITHOUT a head element works (baseline).
     */
    @Test
    public void htmlWithoutHeadThroughViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-url-rewrite/no-head.html";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);
        final int status = result.statusCode();

        assertEquals(HTTP_OK, status);

        final String body = result.body();
        assertTrue("Response should contain body content",
                body.contains("Hello World"));
    }

    // ================================================================================
    // URLRewriteXSLTViewPipelineTest: #6667 -- a forward step followed by a view that
    // forwards to XSLTServlet must not throw "getOutputStream cannnot be called after
    // getWriter".
    // ================================================================================

    private static final String XSLT_TEST_COLLECTION = "/db/apps/test-xslt-view-pipeline";

    private static final String XSLT_CONTROLLER_XQ = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";

            <exist:dispatch>
              <exist:forward url="A.xql">
                <exist:set-header name="Cache-Control" value="no-cache"/>
                <exist:set-header name="Pragma" value="no-cache"/>
              </exist:forward>
              <exist:view>
                <exist:forward servlet="XSLTServlet">
                  <exist:set-attribute name="xslt.stylesheet" value="xmldb:exist://%s/B.xsl"/>
                </exist:forward>
              </exist:view>
              <exist:cache-control cache="false"/>
            </exist:dispatch>""".formatted(XSLT_TEST_COLLECTION);

    private static final String XSLT_A_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            <record>
              <name>Bob</name>
            </record>""";

    private static final String XSLT_B_XSL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml">

              <xsl:output method="xml" media-type="text/html" omit-xml-declaration="yes" indent="no"/>

              <xsl:template match="/record">
                <p>Hello <xsl:value-of select="name"/></p>
              </xsl:template>

            </xsl:stylesheet>""";

    @BeforeClass
    public static void setupXsltViewPipelineTest() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + XSLT_TEST_COLLECTION;

        storeViaRest(restUrl + "/controller.xql", XSLT_CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/A.xql", XSLT_A_XQL, "application/xquery");
        storeViaRest(restUrl + "/B.xsl", XSLT_B_XSL, "application/xslt+xml");

        chmodRwxrxrx(XSLT_TEST_COLLECTION, "controller.xql", "A.xql");
    }

    @AfterClass
    public static void teardownXsltViewPipelineTest() throws Exception {
        deleteViaRest("http://localhost:" + existWebServer.getPort() + "/exist/rest" + XSLT_TEST_COLLECTION);
    }

    @Test
    public void forwardThenXsltViewPipeline() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-xslt-view-pipeline/test";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);

        assertOk(result);
        assertTrue("Response should contain the XSLT-transformed output",
                result.body().contains("Hello Bob"));
    }

    // ================================================================================
    // Header buffer+replay redesign safety nets (see CachingResponseWrapper): each of
    // the next three proves a fact only a real forward-then-view pipeline through a live
    // container can -- CachingResponseWrapperTest covers the wrapper's own buffering
    // contract in isolation, but not whether the pipeline actually wires it correctly.
    // ================================================================================

    // -- URLRewriteSetHeaderSurvivesViewPipelineTest: an <exist:set-header> directive on
    // a forward step must survive to the final response even though applyViews() discards
    // that step's response wrapper once the view runs. --

    private static final String SH_TEST_COLLECTION = "/db/apps/test-set-header-view-pipeline";

    private static final String SH_CONTROLLER_XQ = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";

            <exist:dispatch>
              <exist:forward url="step1.xql">
                <exist:set-header name="Cache-Control" value="no-cache"/>
                <exist:set-header name="Pragma" value="no-cache"/>
              </exist:forward>
              <exist:view>
                <exist:forward url="view.xql"/>
              </exist:view>
              <exist:cache-control cache="false"/>
            </exist:dispatch>""";

    private static final String SH_STEP1_XQL = """
            xquery version "3.1";

            <step1>Hello</step1>""";

    private static final String SH_VIEW_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            let $data := request:get-data()
            return
                <p>View saw: { $data//step1/text() }</p>""";

    @BeforeClass
    public static void setupSetHeaderSurvivesTest() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + SH_TEST_COLLECTION;

        storeViaRest(restUrl + "/controller.xql", SH_CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/step1.xql", SH_STEP1_XQL, "application/xquery");
        storeViaRest(restUrl + "/view.xql", SH_VIEW_XQL, "application/xquery");

        chmodRwxrxrx(SH_TEST_COLLECTION, "controller.xql", "step1.xql", "view.xql");
    }

    @AfterClass
    public static void teardownSetHeaderSurvivesTest() throws Exception {
        deleteViaRest("http://localhost:" + existWebServer.getPort() + "/exist/rest" + SH_TEST_COLLECTION);
    }

    @Test
    public void setHeaderOnForwardStepSurvivesToFinalViewResponse() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-set-header-view-pipeline/test";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);
        assertOk(result);

        // Proves the request actually went through the full forward-then-view pipeline, not just
        // step1's raw output.
        assertTrue("Response should contain the view's output, not step1's raw output",
                result.body().contains("View saw: Hello"));

        // The actual behavior under test: the forward step's <exist:set-header> directives must
        // have survived past the view step that replaced its response wrapper.
        assertEquals("Cache-Control set on the forward step must survive to the final response",
                "no-cache", result.headers().firstValue("Cache-Control").orElse(null));
        assertEquals("Pragma set on the forward step must survive to the final response",
                "no-cache", result.headers().firstValue("Pragma").orElse(null));
    }

    // -- URLRewriteResponseSetHeaderViewPipelineTest: a header an intermediate step sets
    // programmatically via response:set-header() -- no static config representation at
    // all -- must NOT leak past a view step that discards that step's output. --

    private static final String IH_TEST_COLLECTION = "/db/apps/test-intermediate-set-header";

    private static final String IH_CONTROLLER_XQ = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";

            <exist:dispatch>
              <exist:forward url="step1.xql"/>
              <exist:view>
                <exist:forward url="view.xql"/>
              </exist:view>
              <exist:cache-control cache="false"/>
            </exist:dispatch>""";

    private static final String IH_STEP1_XQL = """
            xquery version "3.1";

            (: Set programmatically, not via <exist:set-header> config -- this step's own header,
               which must not survive once the view step below replaces its output. :)
            response:set-header("X-Step1-Debug", "step1-was-here"),
            <step1>Hello</step1>""";

    private static final String IH_VIEW_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            let $data := request:get-data()
            return
                <p>View saw: { $data//step1/text() }</p>""";

    @BeforeClass
    public static void setupIntermediateSetHeaderTest() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + IH_TEST_COLLECTION;

        storeViaRest(restUrl + "/controller.xql", IH_CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/step1.xql", IH_STEP1_XQL, "application/xquery");
        storeViaRest(restUrl + "/view.xql", IH_VIEW_XQL, "application/xquery");

        chmodRwxrxrx(IH_TEST_COLLECTION, "controller.xql", "step1.xql", "view.xql");
    }

    @AfterClass
    public static void teardownIntermediateSetHeaderTest() throws Exception {
        deleteViaRest("http://localhost:" + existWebServer.getPort() + "/exist/rest" + IH_TEST_COLLECTION);
    }

    @Test
    public void intermediateStepHeaderDoesNotSurviveToFinalViewResponse() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-intermediate-set-header/test";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);
        assertOk(result);

        // Proves the request actually went through the full forward-then-view pipeline.
        assertTrue("Response should contain the view's output",
                result.body().contains("View saw: Hello"));

        // The actual behavior under test: step1's own header must not leak past the view that
        // replaced its output -- step1's response was never the one actually sent to the client.
        assertTrue("X-Step1-Debug from the discarded intermediate step must not reach the client",
                result.headers().firstValue("X-Step1-Debug").isEmpty());
    }

    // -- URLRewriteFinalStepResponseSetHeaderSurvivesTest: the opposite, equally
    // necessary case -- a header the FINAL, actually-flushed step sets the same way
    // must still reach the client. --

    private static final String FH_TEST_COLLECTION = "/db/apps/test-final-step-set-header";

    private static final String FH_CONTROLLER_XQ = """
            xquery version "3.1";
            declare namespace exist = "http://exist.sourceforge.net/NS/exist";

            <exist:dispatch>
              <exist:forward url="step1.xql"/>
              <exist:view>
                <exist:forward url="view.xql"/>
              </exist:view>
              <exist:cache-control cache="false"/>
            </exist:dispatch>""";

    private static final String FH_STEP1_XQL = """
            xquery version "3.1";

            <step1>Hello</step1>""";

    private static final String FH_VIEW_XQL = """
            xquery version "3.1";
            declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

            let $data := request:get-data()
            return (
                (: The final step -- the one whose output actually reaches the client -- sets a
                   header programmatically, not via <exist:set-header> config. :)
                response:set-header("X-Custom-Header", "custom-value"),
                <p>View saw: { $data//step1/text() }</p>
            )""";

    @BeforeClass
    public static void setupFinalStepSetHeaderTest() throws Exception {
        final String restUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest" + FH_TEST_COLLECTION;

        storeViaRest(restUrl + "/controller.xql", FH_CONTROLLER_XQ, "application/xquery");
        storeViaRest(restUrl + "/step1.xql", FH_STEP1_XQL, "application/xquery");
        storeViaRest(restUrl + "/view.xql", FH_VIEW_XQL, "application/xquery");

        chmodRwxrxrx(FH_TEST_COLLECTION, "controller.xql", "step1.xql", "view.xql");
    }

    @AfterClass
    public static void teardownFinalStepSetHeaderTest() throws Exception {
        deleteViaRest("http://localhost:" + existWebServer.getPort() + "/exist/rest" + FH_TEST_COLLECTION);
    }

    @Test
    public void finalStepResponseSetHeaderSurvivesToClient() throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort()
                + "/exist/apps/test-final-step-set-header/test";

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final AbstractHttpTest.HttpResponseResult result =
                AbstractHttpTest.executeForStatusAndBody(AbstractHttpTest.newHttpClient(), request);
        assertOk(result);
        assertEquals("The final step's own response:set-header() call must reach the client",
                "custom-value", result.headers().firstValue("X-Custom-Header").orElse(null));
    }

    // ================================================================================
    // Shared helpers
    // ================================================================================

    /** Store a document at {@code /db/apps/<collection>/<name>} via REST, asserting 201. */
    private void storeAppsDoc(final String collection, final String name, final String mediaType, final String content)
            throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getRestUri(existWebServer) + "/db/apps/" + collection + "/" + name),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("Content-Type", mediaType)
                .PUT(HttpRequest.BodyPublishers.ofString(content))
                .build();
        final int status = withHttpClient(client -> executeForStatus(client, request));
        assertEquals(HttpURLConnection.HTTP_CREATED, status);
    }

    /** GET {@code /db/apps/<collection>/<name>}, returning (status, body). */
    private Tuple2<Integer, String> getAppsDoc(final String collection, final String name) throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getAppsUri(existWebServer) + "/" + collection + "/" + name),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .GET()
                .build();
        return withHttpClient(client -> {
            final HttpResponseResult r = executeForStatusAndBody(client, request);
            return Tuple(r.statusCode(), r.body());
        });
    }

    /** GET {@code /apps/<collection>/render} with an If-Modified-Since header, returning the body. */
    private String sendWithIfModifiedSince(final String coll) throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getServerUri(existWebServer) + "/apps/" + coll + "/render"),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("If-Modified-Since", IF_MODIFIED_SINCE)
                .GET()
                .build();
        return withHttpClient(client -> executeForStatusAndBody(client, request).body());
    }

    /** Send the fixed multipart/form-data body to {@code /apps/<collection>/echo} via {@code method}. */
    private String sendMultipart(final String coll, final String method) throws IOException {
        final HttpRequest request = authenticatedRequest(
                URI.create(getServerUri(existWebServer) + "/apps/" + coll + "/echo"),
                TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .header("Content-Type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY)
                .method(method, HttpRequest.BodyPublishers.ofString(MULTIPART_BODY, UTF_8))
                .build();
        return withHttpClient(client -> executeForStatusAndBody(client, request).body());
    }

    /** Store a resource at an arbitrary REST URL, asserting success. Shared by the view/XSLT/
     * set-header pipeline scenarios above, which address fixtures by full URL rather than a
     * (collection, name) pair. */
    private static void storeViaRest(final String url, final String content, final String contentType)
            throws IOException {
        final HttpRequest request = AbstractHttpTest.authenticatedRequest(URI.create(url), "admin", "")
                .header("Content-Type", contentType + "; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8))
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), request);
    }

    /** {@code sm:chmod} the given resources under {@code collection} to {@code rwxr-xr-x}, asserting
     * success. Shared by the view/XSLT/set-header pipeline scenarios above, whose controller/step
     * scripts must be executable. */
    private static void chmodRwxrxrx(final String collection, final String... resourceNames) throws IOException {
        final StringBuilder chmod = new StringBuilder();
        for (final String resourceName : resourceNames) {
            if (chmod.length() > 0) {
                chmod.append(',');
            }
            chmod.append("sm:chmod(xs:anyURI('").append(collection).append('/').append(resourceName)
                    .append("'), 'rwxr-xr-x')");
        }
        final String chmodUrl = "http://localhost:" + existWebServer.getPort() + "/exist/rest/db?_query=" +
                URLEncoder.encode(chmod.toString(), StandardCharsets.UTF_8) + "&_wrap=no";
        final HttpRequest chmodRequest = AbstractHttpTest.authenticatedRequest(URI.create(chmodUrl), "admin", "")
                .GET()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), chmodRequest);
    }

    /** Delete the collection at a full REST URL, asserting success. Shared teardown counterpart to
     * {@link #storeViaRest}. */
    private static void deleteViaRest(final String url) throws IOException {
        final HttpRequest deleteRequest = AbstractHttpTest.authenticatedRequest(URI.create(url), "admin", "")
                .DELETE()
                .build();
        AbstractHttpTest.executeForStatus(AbstractHttpTest.newHttpClient(), deleteRequest);
    }

    /** Assert a 200 OK status, including a truncated response body in the failure message. */
    private static void assertOk(final AbstractHttpTest.HttpResponseResult result) {
        assertEquals("Expected 200 OK but got " + result.statusCode() + ": "
                        + result.body().substring(0, Math.min(300, result.body().length())),
                HTTP_OK, result.statusCode());
    }
}
