/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.TestUtils;
import org.exist.collections.CollectionConfiguration;
import org.exist.test.ExistWebServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BaseURITest {

    private static final String COLLECTION_CONFIG = """
        <?xml version="1.0" encoding="UTF-8"?>
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <triggers>
                <trigger class="org.exist.extensions.exquery.restxq.impl.RestXqTrigger"/>
            </triggers>
        </collection>
        """;

    private static String TEST_COLLECTION = "/db/restxq/integration-test";

    private static ContentType XQUERY_CONTENT_TYPE = ContentType.create("application/xquery", "UTF-8");

    private static String XQUERY_MEDIA_FILENAME = "restxq-tests-media.xqm";

    private static final String XQUERY_MEDIA_BODY =
        """
            xquery version "3.0";
                    
            module namespace mod1 = "http://mod1";
                    
            declare namespace output = "https://www.w3.org/2010/xslt-xquery-serialization";
                    
            declare
                %rest:GET
                %rest:path("/media-type-json1")
                %rest:produces("application/json")
            function mod1:media-type-json1() {
                <success/>
            };
            """;

    private static String XQUERY_BASE_URI_FILENAME = "restxq-tests-base-uri.xqm";

    private static final String XQUERY_BASE_URI_BODY =
        """
            xquery version "3.1";
                    
            module namespace ex = "http://example/restxq/1";
            import module namespace rest = "http://exquery.org/ns/restxq";
                    
            declare
                %rest:GET
                %rest:path("/base-uri")
            function ex:base-uri-using-restxq() {
                <result>
                  <static>{static-base-uri()}</static>
                  <exists>{ exists(static-base-uri()) }</exists>
                  <resolve-explicit>{ resolve-uri('#foobaz', static-base-uri() ) }</resolve-explicit>
                  <resolve-implicit>{ resolve-uri('#foobar') }</resolve-implicit>
                  <resolve-path>{ resolve-uri('path/to/file#foobar') }</resolve-path>
                </result>
            };
            """;

    private static final String XMLRPC_BASE_URI_BODY =
        """
            <result>
                <static>{static-base-uri()}</static>
                <exists>{ exists(static-base-uri()) }</exists>
                <resolve-implicit>{ resolve-uri('#foobar') }</resolve-implicit>
                <resolve-path>{ resolve-uri('path/to/file#foobar') }</resolve-path>
            </result>
        """;

    private static Executor executor = null;

    @ClassRule
    public static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort();
    }

    private static String getRestUri() {
        return getServerUri() + "/rest";
    }

    private static String getRestXqUri() {
        return getServerUri() + "/restxq";
    }

    private static String getRestServerUri() {
        return getServerUri() + "/rest";
    }

    private static String getXmlRpcUri() {
        return getServerUri() + "/xmlrpc";
    }

    /**
     * Upload RESTXQ resource functions prior to tests using them
     *
     * @throws IOException
     */
    @BeforeClass
    public static void storeResourceFunctions() throws IOException {
        executor = Executor.newInstance()
            .auth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
            .authPreemptive(new HttpHost("localhost", existWebServer.getPort()));

        HttpResponse response = null;

        response = executor.execute(Request
            .Put(getRestUri() + "/db/system/config" + TEST_COLLECTION + "/" + CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE)
            .bodyString(COLLECTION_CONFIG, ContentType.APPLICATION_XML)
        ).returnResponse();
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

        response = executor.execute(Request
            .Get(getRestUri() + "/db/?_query=rest:resource-functions()")
        ).returnResponse();
        assertEquals(SC_OK, response.getStatusLine().getStatusCode());
        assertNotNull(response.getEntity().getContent());

        response = executor.execute(Request
            .Put(getRestUri() + TEST_COLLECTION + "/" + XQUERY_BASE_URI_FILENAME)
            .bodyString(XQUERY_BASE_URI_BODY, XQUERY_CONTENT_TYPE)
        ).returnResponse();
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);

        response = executor.execute(Request
            .Put(getRestUri() + TEST_COLLECTION + "/" + XQUERY_MEDIA_FILENAME)
            .bodyString(XQUERY_MEDIA_BODY, XQUERY_CONTENT_TYPE)
        ).returnResponse();
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

        response = executor.execute(Request
            .Get(getRestUri() + "/db/?_query=rest:resource-functions()")
        ).returnResponse();
        assertEquals(SC_OK, response.getStatusLine().getStatusCode());
        assertNotNull(response.getEntity().getContent());
        var result = readEntityElement(response.getEntity());
        assertThat(result).contains("<rest:resource-function xquery-uri=\"/db/restxq/integration-test/restxq-tests-base-uri.xqm\">");
        assertThat(result).contains("<rest:resource-function xquery-uri=\"/db/restxq/integration-test/restxq-tests-media.xqm\">");
    }

    @Ignore("Test the return of non XML media types - TBD")
    @Test
    public void mediaTypeJson1() throws IOException, ParserConfigurationException, SAXException {
        final HttpResponse response = executor.execute(Request
            .Get(getRestXqUri() + "/media-type-json1")
            .addHeader(new BasicHeader("Accept", "application/json"))
        ).returnResponse();

        assertEquals(SC_OK, response.getStatusLine().getStatusCode());

        final var entity = response.getEntity();
        assertThat(entity.getContentType().getValue()).isEqualTo("application/json; charset=UTF-8");
        var result = readEntityElement(entity);
        assertThat(result).contains("<success");
    }

    /**
     * XQUERY_BASE_URI_BODY function/script has been stored at XQUERY_BASE_URI_FILENAME in @Before
     * check that the installed function using base-uri is called, and check the result.
     *
     * @throws IOException
     */
    @Test
    public void baseURIRestXQ() throws IOException {

        var response = executor.execute(Request
            .Get(getRestXqUri() + "/base-uri")
            .addHeader(new BasicHeader("Accept", "application/xml"))
        ).returnResponse();

        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(SC_OK);
        final var entity = response.getEntity();
        assertThat(entity.getContentType().getValue()).isEqualTo("application/xml; charset=UTF-8");
        var result = readEntityElement(entity);
        assertThat(result).contains("<static>xmldb:exist://" +TEST_COLLECTION + "/" + XQUERY_BASE_URI_FILENAME +
            "</static>");
        assertThat(result).contains("<exists>true</exists>");
        assertThat(result).contains("<resolve-explicit>xmldb:exist:" + TEST_COLLECTION + "/" + XQUERY_BASE_URI_FILENAME + "#foobaz</resolve-explicit>");
        assertThat(result).contains("<resolve-implicit>xmldb:exist:" + TEST_COLLECTION + "/" + XQUERY_BASE_URI_FILENAME + "#foobar</resolve-implicit>");
        assertThat(result).contains("<resolve-path>xmldb:exist:" + TEST_COLLECTION + "/path/to/file#foobar</resolve-path>");
    }

    /**
     * Execute XQuery as the _query parameter of a request
     * @throws IOException
     */
    @Test public void baseURIRestServerQuery() throws IOException {

        var query = URLEncoder.encode("static-base-uri()", StandardCharsets.UTF_8);
        var response = executor.execute(Request
            .Get(getRestServerUri() + "/db/restserver/baseuri?_query=" + query)
            .addHeader(new BasicHeader("Accept", "application/xml"))
        ).returnResponse();

        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(SC_OK);
        final var entity = response.getEntity();
        assertThat(entity.getContentType().getValue()).isEqualTo("application/xml; charset=UTF-8");

        var result = readEntityElement(entity);
        assertThat(result).contains("<exist:value exist:type=\"xs:anyURI\">xmldb:exist:///db/restserver/baseuri</exist:value>");
    }

    private static final String XML_QUERY_BASE_URI = """
      xquery version "3.1";
      <tests>
          <static-base-uri>{ static-base-uri() }</static-base-uri>
          <exists>{ exists(static-base-uri()) }</exists>
          <resolve-explicit>{ resolve-uri('#foobaz', static-base-uri() ) }</resolve-explicit>
          <resolve-implicit>{ resolve-uri('#foobar') }</resolve-implicit>
      </tests>
      """;

    /**
     * Execute XML_QUERY_BASE_URI supplied as the body of a REST server put request
     *
     * 1. PUT the script
     * 2. GET the result of executing the script
     *
     * @throws IOException
     */
    @Test public void baseURIRestServerScript() throws IOException {

        final var credentials = Base64.encodeBase64String("admin:".getBytes(UTF_8));

        var response = executor.execute(Request
            .Put(getRestServerUri() + "/db/test/test.xq")
            .addHeader(new BasicHeader("Authorization", "Basic " + credentials))
            .addHeader(new BasicHeader("Accept", "*/*"))
            .addHeader(new BasicHeader("Content-Type", "application/xquery; charset=UTF-8"))
            .bodyString(XML_QUERY_BASE_URI, ContentType.TEXT_XML)
        ).returnResponse();
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(SC_CREATED);

        response = executor.execute(Request
            .Get(getRestServerUri() + "/db/test/test.xq")
            .addHeader(new BasicHeader("Accept", "application/xml"))
            .addHeader(new BasicHeader("Authorization", "Basic " + credentials))
        ).returnResponse();
        final var entity = response.getEntity();
        final var content = readEntityElement(entity);
        assertThat(response.getStatusLine().getStatusCode()).as("Message was: %s", content).isEqualTo(SC_OK);
        assertThat(content).contains("<static-base-uri>xmldb:exist:///db/test/test.xq</static-base-uri>");
        assertThat(content).contains("<exists>true</exists>");
        assertThat(content).contains("<resolve-implicit>xmldb:exist:/db/test/test.xq#foobar</resolve-implicit>");
        assertThat(content).contains("<resolve-explicit>xmldb:exist:/db/test/test.xq#foobaz</resolve-explicit>");
    }


    private static final String XML_QUERY_EXTENDED_BASE_URI = """
      <query xmlns="http://exist.sourceforge.net/NS/exist" start="1" max="10">
      <text><![CDATA[%s]]></text>
      </query>
      """.formatted(XML_QUERY_BASE_URI);

    @Test public void baseURIExtendedQuery() throws IOException {

        final var credentials = Base64.encodeBase64String("admin:".getBytes(UTF_8));

        var response = executor.execute(Request
            .Post(getRestServerUri() + "/db/test-rest-static-base-uri")
            .addHeader(new BasicHeader("Authorization", "Basic " + credentials))
            .addHeader(new BasicHeader("Accept", "*/*"))
            .addHeader(new BasicHeader("Content-Type", "application/xml; charset=UTF-8"))
            .bodyString(XML_QUERY_EXTENDED_BASE_URI, ContentType.TEXT_XML)
        ).returnResponse();

        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(SC_OK);
        final var entity = response.getEntity();
        assertThat(readEntityElement(entity)).contains("<static-base-uri>xmldb:exist:///db/test-rest-static-base-uri</static-base-uri>");
    }

    @Test public void baseURIXQueryServlet() throws IOException {

        final var credentials = Base64.encodeBase64String("admin:".getBytes(UTF_8));

        var response = executor.execute(Request
            .Get(getServerUri() + "/dir1/base-uri-xqueryservlet.xqy")
            .addHeader(new BasicHeader("Authorization", "Basic " + credentials))
            .addHeader(new BasicHeader("Accept", "*/*"))
        ).returnResponse();

        final var entity = response.getEntity();
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(SC_OK);
        final var content = readEntityElement(entity);
        // The static-base-uri is the DB root URI, not a subpath - as we are executing an external file, this seems reasonable
        //assertThat(content).contains("<static-base-uri>xmldb:exist:///db</static-base-uri>");
        assertThat(content).contains("<static-base-uri>file:/");
        assertThat(content).contains("/dir1/base-uri-xqueryservlet.xqy</static-base-uri>");
        assertThat(content).contains("<does-sbu-xqs-exist>true</does-sbu-xqs-exist>");
        assertThat(content).contains("<resolve-implicit>file:/");
        assertThat(content).contains("/dir1/base-uri-xqueryservlet.xqy#foobar</resolve-implicit>");
        assertThat(content).contains("<resolve-explicit>file:/");
        assertThat(content).contains("/dir1/base-uri-xqueryservlet.xqy#foobaz</resolve-explicit>");
        assertThat(content).contains("<resolve-path>file:/");
        assertThat(content).contains("/dir1/path/to/file#foobar</resolve-path>");
    }

    @Test public void baseURIXmlRpc() throws MalformedURLException, XmlRpcException {
        final XmlRpcClient xmlrpc = getXmlRpcClient();

        //sanity check
        var result = xmlrpc.execute("hasCollection", List.of("/db"));
        assertThat(result instanceof Boolean b).isTrue();

        List<Object> params = new ArrayList<>();
        params.add(XMLRPC_BASE_URI_BODY.getBytes(UTF_8));
        params.add(new HashMap<>());
        var handle = xmlrpc.execute("executeQuery", params);
        assertThat(handle).isInstanceOf(Integer.class);

        params.clear();
        params.add(handle);
        params.add(0);
        params.add(new HashMap());
        byte[] item = (byte[]) xmlrpc.execute("retrieve", params);
        assertThat(item).isNotNull();
        var s = new String(item, UTF_8);
        assertThat(s).isNotEmpty();
    }

    static private String readEntityElement(final HttpEntity entity) throws IOException {
        final var inputStream = entity.getContent();
        final var textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader
            (inputStream, StandardCharsets.UTF_8))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }

    static private XmlRpcClient getXmlRpcClient() throws MalformedURLException {
        XmlRpcClient client = new XmlRpcClient();
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setServerURL(new URL(getXmlRpcUri()));
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        client.setConfig(config);
        return client;
    }

}
