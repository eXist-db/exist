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
package org.exist.storage;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.custommonkey.xmlunit.Diff;
import org.exist.Namespaces;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jim fuller at webcomposite.com
 *
 * Test XInclude Serialiser via REST/XMLRPC/WEBDAV/SOAP interfaces
 */
public class XIncludeSerializerTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, true, true, true);

    private final static XmldbURI XINCLUDE_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xinclude_test");
    private final static XmldbURI XINCLUDE_NESTED_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xinclude_test/data");

    private final static String getXmlRpcApi() {
        return "http://127.0.0.1:" + existWebServer.getPort() + "/xmlrpc";
    }

    private final static String getRestUri()  {
        return "http://admin:admin@127.0.0.1:" + existWebServer.getPort() + "/db/xinclude_test";
    }

    private final static String XML_DATA1
            = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<xi:include href='metatags.xml'/>"
            + "</root>"
            + "</test>";

    private final static String XML_DATA2
            = "<html>"
            + "<head>"
            + "<metatag xml:id='metatag' name='test' description='test'/>"
            + "</head>"
            + "</html>";

    private final static String XML_DATA3
            = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<xi:include href='../xinclude_test/data/metatags.xml'/>"
            + "</root>"
            + "</test>";

    private final static String XML_DATA4
            = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<xi:include href='data/metatags.xml'/>"
            + "</root>"
            + "</test>";

    private final static String XML_DATA5
            = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<xi:include href='data/metatags.xml' xpointer='xpointer(//metatag)'/>"
            + "</root>"
            + "</test>";

    private final static String XML_DATA6
            = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<xi:include href='data/metatags.xml' xpointer='metatag'/>"
            + "</root>"
            + "</test>";

    private final static String XML_DATA7
            = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<xi:include href='data/unknown.xml'>"
            + "<xi:fallback><warning>Not found</warning></xi:fallback>"
            + "</xi:include>"
            + "</root>"
            + "</test>";

    private final static String XML_DATA8
            = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<xi:include href='data/unknown.xml'/>"
            + "</root>"
            + "</test>";

    private final static String XML_RESULT = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<html>"
            + "<head>"
            + "<metatag xml:id='metatag' name='test' description='test'/>"
            + "</head>"
            + "</html>"
            + "</root>"
            + "</test>";

    private final static String XML_RESULT_XPOINTER = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<metatag xml:id='metatag' name='test' description='test'/>"
            + "</root>"
            + "</test>";

    private final static String XML_RESULT_FALLBACK1 = "<test xmlns:xi='" + Namespaces.XINCLUDE_NS + "'>"
            + "<root>"
            + "<warning>Not found</warning>"
            + "</root>"
            + "</test>";

    @Test
    public void absSimpleREST() throws IOException, SAXException {
        // path needs to indicate indent and wrap is off
        final String uri = getRestUri() + "/test_simple.xml?_indent=no&_wrap=no";

        // we use honest http
        final HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        final StringBuilder out = new StringBuilder();
        try(final BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\r\n");
            }
        }

        final String responseXML = out.toString();

        final Diff myDiff = new Diff(XML_RESULT, responseXML);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
    }

    @Test
    public void relSimpleREST1() throws IOException, SAXException {
        final String uri = getRestUri() + "/test_relative1.xml?_indent=no&_wrap=no";

        final HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        final BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
        String line;
        final StringBuilder out = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append("\r\n");
        }
        final String responseXML = out.toString();

        final Diff myDiff = new Diff(XML_RESULT, responseXML);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
    }

    @Test
    public void relSimpleREST2() throws IOException, SAXException {
        // path needs to indicate indent and wrap is off
        final String uri = getRestUri() + "/test_relative2.xml?_indent=no&_wrap=no";

        final HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        final BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
        String line;
        final StringBuilder out = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append("\r\n");
        }
        final String responseXML = out.toString();

        final Diff myDiff = new Diff(XML_RESULT, responseXML);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
    }

    @Test
    public void xpointerREST3() throws IOException, SAXException {
        final String uri = getRestUri() + "/test_xpointer1.xml?_indent=no&_wrap=no";

        final HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        final BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
        String line;
        StringBuilder out = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append("\r\n");
        }
        final String responseXML = out.toString();

        final Diff myDiff = new Diff(XML_RESULT_XPOINTER, responseXML);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
    }

    @Test
    public void xpointerREST4() throws IOException, SAXException {
        final String uri = getRestUri() + "/test_xpointer2.xml?_indent=no&_wrap=no";

        final HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        final BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
        String line;
        final StringBuilder out = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append("\r\n");
        }
        final String responseXML = out.toString();

        final Diff myDiff = new Diff(XML_RESULT_XPOINTER, responseXML);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
    }

    @Test
    public void fallback1() throws IOException, SAXException {
        final String uri = getRestUri() + "/test_fallback1.xml?_indent=no&_wrap=no";

        final HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        final BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
        String line;
        final StringBuilder out = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append("\r\n");
        }
        String responseXML = out.toString();

        final Diff myDiff = new Diff(XML_RESULT_FALLBACK1, responseXML);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
    }

    @Test(expected = IOException.class)
    public void fallback2() throws IOException {
        final String uri = getRestUri() + "/test_fallback2.xml?_indent=no&_wrap=no";

        final HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        final BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), StandardCharsets.UTF_8));
        String line;
        final StringBuilder out = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append("\r\n");
        }
        final String responseXML = out.toString();
    }

    //TODO add full url test e.g. http://www.example.org/test.xml for xinclude
    //TODO add simple and relative url with xpointer
    //ex. <xi:include href="../javascript.xml#xpointer(html/head)"/>
    //TODO add simple and relative url with xpointer and namespaces
    // ex. <xi:include href="../javascript.xml#xmlns(x=http://www.w3.org/1999/xhtml)xpointer(/x:html/x:head)"/>
    /*
     * XML-RPC tests
     *
     */
    //TODO check serialisation via this interface, simple and relative

    /*
     * WebDAV tests
     *
     */
    //TODO check serialisation via this interface, simple and relative???
    // probably overkill
    /*
     * SOAP tests
     *
     */
    // probably overkill
    //TODO check serialisation via this interface, simple and relative???
    // probably overkill
    /*
     * helper functions
     *
     */
    protected HttpURLConnection getConnection(final String url) throws IOException {
        final URL u = new URL(url);
        return (HttpURLConnection) u.openConnection();
    }

    private static XmlRpcClient getClient() throws MalformedURLException {
        final XmlRpcClient client = new XmlRpcClient();
        final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setServerURL(new URL(getXmlRpcApi()));
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        client.setConfig(config);
        return client;
    }

   //TODO create reader for xml
    /*
     * SetUp / TearDown functions
     *
     */
    @BeforeClass
    public static void startDB() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = getClient();
        final List<Object> params = new ArrayList<>();
        params.add(XINCLUDE_COLLECTION.toString());
        xmlrpc.execute("createCollection", params);

        params.clear();
        params.add(XINCLUDE_NESTED_COLLECTION.toString());
        xmlrpc.execute("createCollection", params);

        params.clear();
        params.add(XML_DATA1);
        params.add("/db/xinclude_test/test_simple.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA2);
        params.add("/db/xinclude_test/metatags.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA2);
        params.add("/db/xinclude_test/data/metatags.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA3);
        params.add("/db/xinclude_test/test_relative1.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA4);
        params.add("/db/xinclude_test/test_relative2.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA5);
        params.add("/db/xinclude_test/test_xpointer1.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA6);
        params.add("/db/xinclude_test/test_xpointer2.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA7);
        params.add("/db/xinclude_test/test_fallback1.xml");
        params.add(1);
        xmlrpc.execute("parse", params);

        params.clear();
        params.add(XML_DATA8);
        params.add("/db/xinclude_test/test_fallback2.xml");
        params.add(1);
        xmlrpc.execute("parse", params);
    }
}
