/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xmlrpc;

import java.io.IOException;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import org.exist.jetty.JettyStart;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.security.MessageDigester;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.transform.OutputKeys;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.exist.security.Permission;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.*;

import org.junit.After;
import org.xml.sax.SAXException;

/**
 * JUnit test for XMLRPC interface methods.
 *
 * @author wolf
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 * @author ljo
 */
public class XmlRpcTest {

    private static JettyStart server = null;
    // jetty.port.standalone
    private final static String URI = "http://localhost:" + System.getProperty("jetty.port", "8088") + "/xmlrpc";

    private final static XmldbURI TARGET_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xmlrpc");

    private final static XmldbURI TARGET_RESOURCE = TARGET_COLLECTION.append(TestConstants.TEST_XML_URI);

    public final static XmldbURI MODULE_RESOURCE = TARGET_COLLECTION.append(TestConstants.TEST_MODULE_URI);

    private final static XmldbURI SPECIAL_COLLECTION = TARGET_COLLECTION.append(TestConstants.SPECIAL_NAME);

    private final static XmldbURI SPECIAL_RESOURCE = SPECIAL_COLLECTION.append(TestConstants.SPECIAL_XML_URI);

    private final static String XML_DATA
            = "<test>"
            + "<para>\u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF</para>"
            + "<para>\uC5F4\uB2E8\uACC4</para>"
            + "</test>";

    private final static String XSL_DATA
            = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
            + "version=\"1.0\">"
            + "<xsl:param name=\"testparam\"/>"
            + "<xsl:template match=\"test\"><test><xsl:apply-templates/></test></xsl:template>"
            + "<xsl:template match=\"para\">"
            + "<p><xsl:value-of select=\"$testparam\"/>: <xsl:apply-templates/></p></xsl:template>"
            + "</xsl:stylesheet>";

    public final static String MODULE_DATA
            = "module namespace tm = \"http://exist-db.org/test/module\"; "
            + "declare variable $tm:imported-external-string as xs:string external;";

    public final static String QUERY_MODULE_DATA
            = "xquery version \"1.0\";"
            + "declare namespace tm-query = \"http://exist-db.org/test/module/query\";"
            + "import module namespace tm = \"http://exist-db.org/test/module\" "
            + "at \"xmldb:exist://" + MODULE_RESOURCE.toString() + "\"; "
            + "declare variable $tm-query:local-external-string as xs:string external;"
            + "($tm:imported-external-string, $tm-query:local-external-string)";

    public XmlRpcTest() {
    }

    @BeforeClass
    public static void setUp() {
        //Don't worry about closing the server : the shutdownDB hook will do the job
        initServer();
    }

    @AfterClass
    public static void stopServer() {
        server.shutdown();
        server = null;
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @After
    public void tearDown() throws XmlRpcException, MalformedURLException {
        XmlRpcClient xmlrpc = getClient();
        List<String> params = new ArrayList<>(1);
        params.add(TARGET_COLLECTION.toString());
        @SuppressWarnings("unused")
        Boolean b = (Boolean) xmlrpc.execute("removeCollection", params);
    }

    private static void initServer() {
        if (server == null) {
            server = new JettyStart();
            server.run();
        }
    }

    @Test
    public void testStoreAndRetrieve() throws XmlRpcException, IOException {
        XmlRpcClient xmlrpc = getClient();
        List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        Boolean result = (Boolean) xmlrpc.execute("createCollection", params);
        assertTrue(result);

        params.clear();
        params.add(XML_DATA);
        params.add(TARGET_RESOURCE.toString());
        params.add(1);

        result = (Boolean) xmlrpc.execute("parse", params);
        assertTrue(result);

        Map<String, String> options = new HashMap<>();
        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(options);

        byte[] data = (byte[]) xmlrpc.execute("getDocument", params);
        assertNotNull(data);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(StandardCharsets.UTF_8.name());
        params.add(0);

        data = (byte[]) xmlrpc.execute("getDocument", params);
        assertNotNull(data);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(0);
        String sdata = (String) xmlrpc.execute("getDocumentAsString", params);
        assertNotNull(data);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(options);
        Map table = (Map) xmlrpc.execute("getDocumentData", params);

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            int offset = (int) table.get("offset");
            data = (byte[]) table.get("data");
            os.write(data);
            while (offset > 0) {
                params.clear();
                params.add(table.get("handle"));
                params.add(offset);
                table = (Map<?, ?>) xmlrpc.execute("getNextChunk", params);
                offset = (int) table.get("offset");
                data = (byte[]) table.get("data");
                os.write(data);
            }
            data = os.toByteArray();
            assertTrue(data.length > 0);
        }

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        Boolean b = (Boolean) xmlrpc.execute("hasDocument", params);
        assertTrue(b);
    }

    private void storeData() throws XmlRpcException, MalformedURLException {
        XmlRpcClient xmlrpc = getClient();
        List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        Boolean result = (Boolean) xmlrpc.execute("createCollection", params);
        assertTrue(result);

        params.clear();
        params.add(XML_DATA);
        params.add(TARGET_RESOURCE.toString());
        params.add(1);

        result = (Boolean) xmlrpc.execute("parse", params);
        assertTrue(result);

        params.set(0, XSL_DATA);
        params.set(1, TARGET_COLLECTION.append("test.xsl").toString());
        result = (Boolean) xmlrpc.execute("parse", params);
        assertTrue(result);

        params.set(0, MODULE_DATA.getBytes(UTF_8));
        params.set(1, MODULE_RESOURCE.toString());
        params.set(2, MimeType.XQUERY_TYPE.getName());
        params.add(Boolean.TRUE);
        result = (Boolean) xmlrpc.execute("storeBinary", params);
        assertTrue(result);
    }

    @Test
    public void testRemoveCollection() throws XmlRpcException, MalformedURLException {
        storeData();
        XmlRpcClient xmlrpc = getClient();
        List params = new ArrayList(1);
        params.add(TARGET_COLLECTION.toString());
        Boolean b = (Boolean) xmlrpc.execute("hasCollection", params);
        assertTrue(b);

        b = (Boolean) xmlrpc.execute("removeCollection", params);
        assertTrue(b);

        b = (Boolean) xmlrpc.execute("hasCollection", params);
        assertFalse(b);
    }

    @Test
    public void testRemoveDoc() throws XmlRpcException, MalformedURLException {
        storeData();
        XmlRpcClient xmlrpc = getClient();
        List params = new ArrayList(1);
        params.add(TARGET_RESOURCE.toString());
        Boolean b = (Boolean) xmlrpc.execute("hasDocument", params);

        assertTrue(b);

        b = (Boolean) xmlrpc.execute("remove", params);
        assertTrue(b);

        b = (Boolean) xmlrpc.execute("hasDocument", params);
        assertFalse(b);
    }

    @Test
    public void testRetrieveDoc() throws XmlRpcException, MalformedURLException {
        storeData();
        Map<String, String> options = new HashMap<>();
        options.put("indent", "yes");
        options.put("encoding", StandardCharsets.UTF_8.name());
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");

        List<Object> params = new ArrayList<>();
        params.add(TARGET_RESOURCE.toString());
        params.add(options);

        // execute the call
        XmlRpcClient xmlrpc = getClient();
        byte[] data = (byte[]) xmlrpc.execute("getDocument", params);

        options.put("stylesheet", "test.xsl");
        data = (byte[]) xmlrpc.execute("getDocument", params);
    }

    @Test
    public void testCharEncoding() throws XmlRpcException, MalformedURLException {
        storeData();
        List<Object> params = new ArrayList<>();
        String query = "distinct-values(//para)";
        params.add(query.getBytes(UTF_8));
        params.add(new HashMap<>());
        XmlRpcClient xmlrpc = getClient();
        HashMap<?, ?> result = (HashMap<?, ?>) xmlrpc.execute("queryP", params);
        Object[] resources = (Object[]) result.get("results");
        //TODO : check the number of resources before !
        assertEquals(2, resources.length);
        String value = (String) resources[0];
        assertEquals("\u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF", value);
        value = (String) resources[1];
        assertEquals("\uC5F4\uB2E8\uACC4", value);
    }

    @Test
    public void testQuery() throws XmlRpcException, MalformedURLException {
        storeData();
        List<Object> params = new ArrayList<>();
        String query
                = "(::pragma exist:serialize indent=no::) //para";
        params.add(query.getBytes(UTF_8));
        params.add(10);
        params.add(1);
        params.add(new HashMap());
        XmlRpcClient xmlrpc = getClient();
        byte[] result = (byte[]) xmlrpc.execute("query", params);
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    public void testQueryWithStylesheet() throws XmlRpcException, MalformedURLException, SAXException, IOException {
        storeData();
        Map<String, String> options = new HashMap<>();
        options.put(EXistOutputKeys.STYLESHEET, "test.xsl");
        options.put(EXistOutputKeys.STYLESHEET_PARAM + ".testparam", "Test");
        options.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
        //TODO : check the number of resources before !
        List<Object> params = new ArrayList<>();
        String query = "//para[1]";
        params.add(query.getBytes(UTF_8));
        params.add(options);
        XmlRpcClient xmlrpc = getClient();
        Integer handle = (Integer) xmlrpc.execute("executeQuery", params);
        assertNotNull(handle);

        params.clear();
        params.add(handle);
        params.add(0);
        params.add(options);
        byte[] item = (byte[]) xmlrpc.execute("retrieve", params);
        assertNotNull(item);
        assertTrue(item.length > 0);
        String out = new String(item, UTF_8);
        assertXMLEqual("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p>Test: \u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF</p>", out);
    }

    @Test
    public void testCompile() throws XmlRpcException, MalformedURLException {
        storeData();
        List<Object> params = new ArrayList<>();
        String query = "<a>Invalid<a>";
        params.add(query.getBytes(UTF_8));
        params.add(new HashMap<>());
        XmlRpcClient xmlrpc = getClient();
        Map stats = (Map) xmlrpc.execute("compile", params);
        assertNotNull(stats);
        assertNotNull(stats.get("error"));
    }

    @Test
    public void testAddAccount() throws MalformedURLException, XmlRpcException {
        String user = "rudi";
        String passwd = "pass";
        String simpleMd5 = MessageDigester.md5(passwd, true);
        String digest = MessageDigester.md5(user + ":exist:" + passwd, false);
        List<Object> params = new ArrayList<>(12);
        params.add("rudi");
        params.add(simpleMd5);
        params.add(digest);
        params.add(new String[]{"guest"});
        params.add(true);
        params.add(Permission.DEFAULT_UMASK);
        params.add(new HashMap<>());

        XmlRpcClient xmlrpc = getClient();
        xmlrpc.execute("addAccount", params);

        XmlRpcClientConfigImpl config = (XmlRpcClientConfigImpl) xmlrpc.getClientConfig();
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        xmlrpc.execute("sync", Collections.EMPTY_LIST);
    }

    @Test
    public void testExecuteQuery() throws XmlRpcException, MalformedURLException {
        storeData();
        List<Object> params = new ArrayList<>();
        String query = "distinct-values(//para)";
        params.add(query.getBytes(UTF_8));
        params.add(new HashMap<>());
        XmlRpcClient xmlrpc = getClient();
        Integer handle = (Integer) xmlrpc.execute("executeQuery", params);
        assertNotNull(handle);

        params.clear();
        params.add(handle);
        Integer hits = (Integer) xmlrpc.execute("getHits", params);
        assertNotNull(hits);

        assertEquals(2, hits.intValue());

        params.add(0);
        params.add(new HashMap());
        byte[] item = (byte[]) xmlrpc.execute("retrieve", params);

        params.clear();
        params.add(handle);
        params.add(1);
        params.add(new HashMap());
        item = (byte[]) xmlrpc.execute("retrieve", params);
    }

    @Test
    public void testQueryModuleExternalVar() throws XmlRpcException, MalformedURLException {
        storeData();
        List<Object> params = new ArrayList<>();
        params.add(QUERY_MODULE_DATA.getBytes(UTF_8));

        Map<String, Object> qp = new HashMap<>();

        Map<String, Object> namespaceDecls = new HashMap<>();
        namespaceDecls.put("tm", "http://exist-db.org/test/module");
        namespaceDecls.put("tm-query", "http://exist-db.org/test/module/query");
        qp.put(RpcAPI.NAMESPACES, namespaceDecls);

        Map<String, Object> variableDecls = new HashMap<>();
        variableDecls.put("tm:imported-external-string", "imported-string-value");
        variableDecls.put("tm-query:local-external-string", "local-string-value");
        qp.put(RpcAPI.VARIABLES, variableDecls);

        params.add(qp);

        XmlRpcClient xmlrpc = getClient();
        Map<String, Object[]> result = (Map<String, Object[]>) xmlrpc.execute("queryP", params);
        Object[] resources = (Object[]) result.get("results");
        assertEquals(2, resources.length);
        String value = (String) resources[0];
        assertEquals("imported-string-value", value);
        value = (String) resources[1];
        assertEquals("local-string-value", value);
    }

    @Test
    public void testCollectionWithAccentsAndSpaces() throws XmlRpcException, MalformedURLException {
        storeData();
        List<Object> params = new ArrayList<>();
        params.add(SPECIAL_COLLECTION.toString());
        XmlRpcClient xmlrpc = getClient();
        xmlrpc.execute("createCollection", params);

        params.clear();
        params.add(XML_DATA);
        params.add(SPECIAL_RESOURCE.toString());
        params.add(1);

        Boolean result = (Boolean) xmlrpc.execute("parse", params);
        assertTrue(result);

        params.clear();
        params.add(SPECIAL_COLLECTION.removeLastSegment().toString());

        HashMap collection = (HashMap) xmlrpc.execute("describeCollection", params);
        Object[] collections = (Object[]) collection.get("collections");
        boolean foundMatch = false;
        String targetCollectionName = SPECIAL_COLLECTION.lastSegment().toString();
        for (int i = 0; i < collections.length; i++) {
            String childName = (String) collections[i];
            if (childName.equals(targetCollectionName)) {
                foundMatch = true;
                break;
            }
        }
        assertTrue("added collection not found", foundMatch);

        Map<String, String> options = new HashMap<>();
        options.put("indent", "yes");
        options.put("encoding", StandardCharsets.UTF_8.name());
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");

        params.clear();
        params.add(SPECIAL_RESOURCE.toString());
        params.add(options);

        // execute the call
        byte[] data = (byte[]) xmlrpc.execute("getDocument", params);
    }

    protected XmlRpcClient getClient() throws MalformedURLException {
        XmlRpcClient client = new XmlRpcClient();
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setServerURL(new URL(URI));
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        client.setConfig(config);
        return client;
    }

    public static void main(String[] args) {
        org.junit.runner.JUnitCore.main(XmlRpcTest.class.getName());
        //Explicit shutdownDB for the shutdownDB hook
        System.exit(0);
    }
}
