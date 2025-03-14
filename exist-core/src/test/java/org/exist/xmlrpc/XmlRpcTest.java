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
package org.exist.xmlrpc;

import java.io.IOException;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.Version;
import org.exist.security.MessageDigester;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.test.ExistWebServer;
import org.exist.test.TestConstants;
import org.exist.util.Compressor;
import org.exist.util.MimeType;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.exist.test.TestConstants.TEST_XML_URI;
import static org.exist.xmldb.RemoteCollection.MAX_UPLOAD_CHUNK;
import static org.exist.xmlrpc.RpcConnection.MAX_DOWNLOAD_CHUNK_SIZE;

import org.junit.ClassRule;
import org.junit.Test;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.exist.security.Permission;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

/**
 * JUnit test for XMLRPC interface methods.
 *
 * @author <a href="mailto:wolfgangmm@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author ljo
 */
public class XmlRpcTest {

    @ClassRule
    public final static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private final static XmldbURI TARGET_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xmlrpc");

    private final static XmldbURI TARGET_RESOURCE = TARGET_COLLECTION.append(TEST_XML_URI);

    public final static XmldbURI MODULE_RESOURCE = TARGET_COLLECTION.append(TestConstants.TEST_MODULE_URI);

    private final static XmldbURI SPECIAL_COLLECTION = TARGET_COLLECTION.append(TestConstants.SPECIAL_NAME);

    private final static XmldbURI SPECIAL_RESOURCE = SPECIAL_COLLECTION.append(TestConstants.SPECIAL_XML_URI);

    private final static String XML_DATA
            = "<test>"
            + "<para>\u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF</para>"
            + "<para>\uC5F4\uB2E8\uACC4</para>"
            + "</test>";

    private final static String XSL_DATA
            = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
            + "<xsl:output omit-xml-declaration=\"no\"/>"
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

    private static String getUri() {
        return "http://localhost:" + existWebServer.getPort() + "/xmlrpc";
    }

    @After
    public void tearDown() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = getClient();
        assertThat(xmlrpc.execute("removeCollection", List.of(TARGET_COLLECTION.toString()))).isInstanceOf(Boolean.class);
    }

    @Test
    public void testStoreAndRetrieve() throws XmlRpcException, IOException {
        final XmlRpcClient xmlrpc = createCollection(TARGET_COLLECTION);
        final List<Object> params = new ArrayList<>();
        params.add(XML_DATA);
        params.add(TARGET_RESOURCE.toString());
        params.add(0);

        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.clear();
        params.add(XML_DATA.getBytes());
        params.add(TARGET_RESOURCE.toString());
        params.add("application/xml");
        params.add(1);

        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.clear();
        params.add(XML_DATA.getBytes());
        params.add(TARGET_RESOURCE.toString());
        params.add(1);
        params.add(new Date(10_0000));
        params.add(new Date(20_0000));

        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(Map.of());

        assertThat((byte[])xmlrpc.execute("getDocument", params)).isNotEmpty();

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(StandardCharsets.UTF_8.name());
        params.add(0);

        assertThat((byte[])xmlrpc.execute("getDocument", params)).isNotEmpty();

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(Map.of(EXistOutputKeys.COMPRESS_OUTPUT, "yes"));

        assertThat((byte[])xmlrpc.execute("getDocument", params)).isNotEmpty();
        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(0);

        assertThat(xmlrpc.execute("getDocumentAsString", params)).isInstanceOf(String.class);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(Map.of());
        Map table = (Map) xmlrpc.execute("getDocumentData", params);

        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            int offset = (int) table.get("offset");
            byte[] data = (byte[]) table.get("data");
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
            assertThat(data).isNotEmpty();
        }

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        assertThat(xmlrpc.execute("hasDocument", params)).isEqualTo(TRUE);
    }

    private XmlRpcClient createCollection(XmldbURI collection) throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = getClient();
        assertThat(xmlrpc.execute("createCollection", List.of(collection.toString()))).isEqualTo(TRUE);
        return xmlrpc;
    }

    private XmlRpcClient storeData() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = createCollection(TARGET_COLLECTION);
        final List<Object> params = new ArrayList<>();
        params.add(XML_DATA);
        params.add(TARGET_RESOURCE.toString());
        params.add(1);

        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.set(0, XSL_DATA);
        params.set(1, TARGET_COLLECTION.append("test.xsl").toString());
        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.set(0, MODULE_DATA.getBytes(UTF_8));
        params.set(1, MODULE_RESOURCE.toString());
        params.set(2, MimeType.XQUERY_TYPE.getName());
        params.add(TRUE);
        assertThat(xmlrpc.execute("storeBinary", params)).isEqualTo(TRUE);

        return xmlrpc;
    }

    @Test
    public void getDocumentDataChunked_nextChunk() throws IOException, XmlRpcException {
        final XmlRpcClient xmlrpc = createCollection(TARGET_COLLECTION);
        final List<Object> params = new ArrayList<>();
        final String generatedXml = generateXml((int) (MAX_DOWNLOAD_CHUNK_SIZE * 1.5));
        params.add(generatedXml);
        params.add(TARGET_RESOURCE.toString());
        params.add(1);
        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.clear();
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
        parameters.put(OutputKeys.INDENT, "no");
        params.add(TARGET_RESOURCE.toString());
        params.add(parameters);
        Map table = (Map) xmlrpc.execute("getDocumentData", params);

        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            int offset = (int) table.get("offset");
            byte[] data = (byte[]) table.get("data");
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
            assertThat(generatedXml).isEqualTo(new String(data));
        }
    }

    @Test
    public void getDocumentDataChunked_nextExtendedChunk() throws IOException, XmlRpcException {
        final XmlRpcClient xmlrpc = createCollection(TARGET_COLLECTION);
        final List<Object> params = new ArrayList<>();
        final String generatedXml = generateXml((int) (MAX_DOWNLOAD_CHUNK_SIZE * 1.75));
        params.add(generatedXml);
        params.add(TARGET_RESOURCE.toString());
        params.add(1);
        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.clear();
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
        parameters.put(OutputKeys.INDENT, "no");
        params.add(TARGET_RESOURCE.toString());
        params.add(parameters);
        Map table = (Map) xmlrpc.execute("getDocumentData", params);

        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            long offset = (int) table.get("offset");
            byte[] data = (byte[]) table.get("data");
            os.write(data);
            while (offset > 0) {
                params.clear();
                params.add(table.get("handle"));
                params.add(String.valueOf(offset));
                table = (Map<?, ?>) xmlrpc.execute("getNextExtendedChunk", params);
                offset = Long.valueOf((String) table.get("offset"));
                data = (byte[]) table.get("data");
                os.write(data);
            }
            data = os.toByteArray();
            assertThat(generatedXml).isEqualTo(new String(data));
        }
    }

    @Test
    public void uploadCompressedAndDownload() throws IOException, XmlRpcException {
        final XmlRpcClient xmlrpc = getClient();
        final String resURI = XmldbURI.ROOT_COLLECTION_URI.append("test.bin").toString();
        final Date now = new Date(System.currentTimeMillis());
        final byte[] binary = generateBinary((int) (MAX_UPLOAD_CHUNK * 1.5));

        // 1) upload
        String uploadedFileName = null;
        try (final InputStream is = new UnsynchronizedByteArrayInputStream(binary)) {
            final byte[] chunk = new byte[MAX_UPLOAD_CHUNK];
            int len;
            while ((len = is.read(chunk)) > -1) {
                final byte[] compressed = Compressor.compress(chunk, len);
                final List<Object> params = new ArrayList<>();
                if (uploadedFileName != null) {
                    params.add(uploadedFileName);
                }
                params.add(compressed);
                params.add(len);
                uploadedFileName = (String) xmlrpc.execute("uploadCompressed", params);
            }
        }

        // set the properties of the uploaded file
        final List<Object> paramsEx = new ArrayList<>();
        paramsEx.add(uploadedFileName);
        paramsEx.add(resURI);
        paramsEx.add(TRUE);
        paramsEx.add("application/octet-stream");
        paramsEx.add(FALSE);
        paramsEx.add(now);
        paramsEx.add(now);
        xmlrpc.execute("parseLocalExt", paramsEx);


        // 2) download
        final List<Object> params = new ArrayList<>();
        params.add(resURI);
        params.add(Map.of());
        Map table = (Map) xmlrpc.execute("getDocumentData", params);
        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            long offset = (int) table.get("offset");
            byte[] data = (byte[]) table.get("data");
            os.write(data);
            while (offset > 0) {
                params.clear();
                params.add(table.get("handle"));
                params.add(String.valueOf(offset));
                table = (Map<?, ?>) xmlrpc.execute("getNextExtendedChunk", params);
                offset = Long.valueOf((String) table.get("offset"));
                data = (byte[]) table.get("data");
                os.write(data);
            }

            data = os.toByteArray();
            assertThat(binary).isEqualTo(data);
        }
    }

    @Test
    public void testRemoveCollection() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List params = new ArrayList(1);
        params.add(TARGET_COLLECTION.toString());
        assertThat(xmlrpc.execute("hasCollection", params)).isEqualTo(TRUE);
        assertThat(xmlrpc.execute("removeCollection", params)).isEqualTo(TRUE);
        assertThat(xmlrpc.execute("hasCollection", params)).isEqualTo(FALSE);
    }

    @Test
    public void testRemoveDoc() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List params = new ArrayList(1);
        params.add(TARGET_RESOURCE.toString());
        assertThat(xmlrpc.execute("hasDocument", params)).isEqualTo(TRUE);
        assertThat(xmlrpc.execute("remove", params)).isEqualTo(TRUE);
        assertThat(xmlrpc.execute("hasDocument", params)).isEqualTo(FALSE);
    }

    @Test
    public void testRetrieveDoc() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final Map<String, String> options = new HashMap<>();
        options.put("indent", "yes");
        options.put("encoding", StandardCharsets.UTF_8.name());
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");

        final List<Object> params = new ArrayList<>();
        params.add(TARGET_RESOURCE.toString());
        params.add(options);

        // execute the call
        assertThat(xmlrpc.execute("getDocument", params)).isInstanceOf(byte[].class);

        options.put("stylesheet", "test.xsl");
        assertThat(xmlrpc.execute("getDocument", params)).isInstanceOf(byte[].class);
    }

    @Test
    public void testCharEncoding() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        final String query = "distinct-values(//para)";
        params.add(query.getBytes(UTF_8));
        params.add(Map.of());

        HashMap<?, ?> result = (HashMap<?, ?>) xmlrpc.execute("queryP", params);
        Object[] resources = (Object[]) result.get("results");
        //TODO : check the number of resources before !
        assertThat(resources).containsExactly(
                "\u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF",
                "\uC5F4\uB2E8\uACC4");
    }

    @Test
    public void testQuery() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        final String query = "(::pragma exist:serialize indent=no::) //para";
        params.add(query.getBytes(UTF_8));
        params.add(10);
        params.add(1);
        params.add(Map.of(RpcAPI.PROTECTED_MODE, "/db"));

        assertThat((byte[]) xmlrpc.execute("query", params)).isNotNull().isNotEmpty();
    }

    @Test
    public void testQuerySummary() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add("//para");

        assertThat((Map<String, Object>) xmlrpc.execute("querySummary", params)).isNotEmpty();
    }

    @Test
    public void testQueryWithStylesheet() throws XmlRpcException, MalformedURLException, SAXException, IOException {
        final XmlRpcClient xmlrpc = storeData();
        final Map<String, String> options = new HashMap<>();
        options.put(EXistOutputKeys.STYLESHEET, "test.xsl");
        options.put(EXistOutputKeys.STYLESHEET_PARAM + ".testparam", "Test");
        options.put(OutputKeys.OMIT_XML_DECLARATION, "no");
        //TODO : check the number of resources before !
        final List<Object> params = new ArrayList<>();
        String query = "//para[1]";
        params.add(query.getBytes(UTF_8));
        params.add(options);

        Integer handle = (Integer) xmlrpc.execute("executeQuery", params);
        assertThat(handle).isNotNull();

        params.clear();
        params.add(handle);
        params.add(0);
        params.add(options);
        byte[] item = (byte[]) xmlrpc.execute("retrieve", params);
        assertThat(item).isNotNull().isNotEmpty();
        String out = new String(item, UTF_8);

        final Source expected = Input.fromString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<p>Test: \u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF</p>").build();
        final Source actual = Input.fromString(out).build();

        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertThat(diff.hasDifferences()).withFailMessage(diff.toString()).isFalse();
    }

    @Test
    public void testCompile() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        final String query = "<a>Invalid<a>";
        params.add(query.getBytes(UTF_8));
        params.add(Map.of());

        Map stats = (Map) xmlrpc.execute("compile", params);
        assertThat(stats).isNotNull();
        assertThat(stats.get("error")).isNotNull();
    }

    @Test
    public void testAccount() throws MalformedURLException, XmlRpcException {
        final String user = "rudi";
        final String passwd = "pass";
        final String simpleMd5 = MessageDigester.md5(passwd, true);
        final String digest = MessageDigester.md5(user + ":exist:" + passwd, false);
        final List<Object> params = new ArrayList<>(12);
        params.add(user);
        params.add(simpleMd5);
        params.add(digest);
        params.add(new String[]{"guest"});
        params.add(true);
        params.add(Permission.DEFAULT_UMASK);
        params.add(Map.of());

        final XmlRpcClient xmlrpc = getClient();
        assertThat(xmlrpc.execute("addAccount", params)).isEqualTo(TRUE);

        params.clear();
        params.add(user);
        params.add(simpleMd5);
        params.add(digest);
        params.add(new String[]{"guest"});
        assertThat(xmlrpc.execute("updateAccount", params)).isEqualTo(TRUE);

        assertThat(xmlrpc.execute("setUserPrimaryGroup", List.of(user, "guest"))).isEqualTo(TRUE);

        XmlRpcClientConfigImpl config = (XmlRpcClientConfigImpl) xmlrpc.getClientConfig();
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        assertThat(xmlrpc.execute("sync", Collections.emptyList())).isEqualTo(TRUE);

        Object[] accounts = (Object[]) xmlrpc.execute("getAccounts", Collections.emptyList());
        assertThat(accounts).hasSize(5);

        for (Object account : accounts) {
            Map<String, Object> accountInfo = (Map<String, Object>) account;
            assertThat(accountInfo).hasSize(9);
            String name = (String) accountInfo.get("name");
            assertThat((Map<String, Object>) xmlrpc.execute("getAccount", List.of(name)))
                    .containsExactlyEntriesOf(accountInfo);
        }

        assertThat((Object[]) xmlrpc.execute("getGroupMembers", List.of("dba")))
                .containsExactlyInAnyOrder("SYSTEM", "admin");
        xmlrpc.execute("addAccountToGroup", List.of(user, "dba"));
        assertThat((Object[]) xmlrpc.execute("getGroupMembers", List.of("dba")))
                .containsExactlyInAnyOrder("SYSTEM", "admin", user);

        assertThat((Object[]) xmlrpc.execute("getGroupMembers", List.of("nogroup")))
                .containsExactlyInAnyOrder("nobody");

        assertThat(xmlrpc.execute("updateAccount", List.of(user, List.of("nogroup")))).isEqualTo(TRUE);
        assertThat((Object[]) xmlrpc.execute("getGroupMembers", List.of("nogroup")))
                .containsExactlyInAnyOrder("nobody", user);

        xmlrpc.execute("removeGroupMember", List.of("nogroup", user));
        assertThat((Object[]) xmlrpc.execute("getGroupMembers", List.of("nogroup")))
                .containsExactlyInAnyOrder("nobody");

        assertThat(xmlrpc.execute("updateAccount", List.of(user, List.of("guest", "dba"), "dba"))).isEqualTo(TRUE);
        assertThat((Object[]) xmlrpc.execute("getGroupMembers", List.of("dba")))
                .containsExactlyInAnyOrder("SYSTEM", "admin");

        assertThat(xmlrpc.execute("removeAccount", List.of(user))).isEqualTo(TRUE);
    }

    @Test
    public void testGroups() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = getClient();

        assertThat((Object[]) xmlrpc.execute("getGroups", Collections.emptyList()))
                .containsExactlyInAnyOrder("dba", "guest", "nogroup")
                .allSatisfy(groupName -> {
                    assertThat((Map<String, Object>) xmlrpc.execute("getGroup", List.of(groupName)))
                            .hasSize(5).containsEntry("name", groupName);
                });

        assertThat(xmlrpc.execute("addGroup", List.of("testGroup", Map.of()))).isEqualTo(TRUE);
        assertThat((Object[]) xmlrpc.execute("getGroups", Collections.emptyList()))
                .containsExactlyInAnyOrder("dba", "guest", "nogroup", "testGroup");

        assertThat(xmlrpc.execute("updateGroup", List.of("testGroup", List.of("admin"), Map.of()))).isEqualTo(TRUE);

        xmlrpc.execute("removeGroupManager", List.of("testGroup", "admin"));
        xmlrpc.execute("addGroupManager", List.of("admin", "testGroup"));
        xmlrpc.execute("removeGroup", List.of("testGroup"));
    }

    @Test
    public void testExecuteQuery() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        String query = "distinct-values(//para)";
        params.add(query.getBytes(UTF_8));
        params.add(Map.of());

        Integer handle = (Integer) xmlrpc.execute("executeQuery", params);
        assertThat(handle).isNotNull();

        params.clear();
        params.add(handle);
        Integer hits = (Integer) xmlrpc.execute("getHits", params);
        assertThat(hits).isNotNull().isEqualTo(2);

        params.add(0);
        params.add(new HashMap());
        assertThat(xmlrpc.execute("retrieve", params)).isInstanceOf(byte[].class);

        params.clear();
        params.add(handle);
        params.add(1);
        params.add(new HashMap());
        assertThat(xmlrpc.execute("retrieve", params)).isInstanceOf(byte[].class);
    }

    @Test
    public void testQueryModuleExternalVar() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(QUERY_MODULE_DATA.getBytes(UTF_8));

        final Map<String, Object> qp = new HashMap<>();

        final Map<String, Object> namespaceDecls = new HashMap<>();
        namespaceDecls.put("tm", "http://exist-db.org/test/module");
        namespaceDecls.put("tm-query", "http://exist-db.org/test/module/query");
        qp.put(RpcAPI.NAMESPACES, namespaceDecls);

        final Map<String, Object> variableDecls = new HashMap<>();
        variableDecls.put("tm:imported-external-string", "imported-string-value");
        variableDecls.put("tm-query:local-external-string", "local-string-value");
        qp.put(RpcAPI.VARIABLES, variableDecls);

        params.add(qp);

        Map<String, Object[]> result = (Map<String, Object[]>) xmlrpc.execute("queryP", params);
        Object[] resources = (Object[]) result.get("results");
        assertThat(resources).containsExactly("imported-string-value", "local-string-value");
    }

    @Test
    public void testCollectionWithAccentsAndSpaces() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = createCollection(SPECIAL_COLLECTION);
        final List<Object> params = new ArrayList<>();
        params.add(XML_DATA);
        params.add(SPECIAL_RESOURCE.toString());
        params.add(1);

        assertThat(xmlrpc.execute("parse", params)).isEqualTo(TRUE);

        params.clear();
        params.add(SPECIAL_COLLECTION.removeLastSegment().toString());

        HashMap collection = (HashMap) xmlrpc.execute("describeCollection", params);
        Object[] collections = (Object[]) collection.get("collections");
        boolean foundMatch = false;
        String targetCollectionName = SPECIAL_COLLECTION.lastSegment().toString();
        for (Object o : collections) {
            String childName = (String) o;
            if (childName.equals(targetCollectionName)) {
                foundMatch = true;
                break;
            }
        }
        assertThat(foundMatch).withFailMessage("added collection not found").isTrue();

        final Map<String, String> options = new HashMap<>();
        options.put("indent", "yes");
        options.put("encoding", StandardCharsets.UTF_8.name());
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");

        params.clear();
        params.add(SPECIAL_RESOURCE.toString());
        params.add(options);

        // execute the call
        assertThat(xmlrpc.execute("getDocument", params)).isInstanceOf(byte[].class);
    }

    @Test
    public void testGetVersion() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = getClient();
        assertThat(xmlrpc.execute("getVersion", Collections.emptyList())).isEqualTo(Version.getVersion());
    }

    @Test
    public void testConfigureCollection() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = createCollection(TARGET_COLLECTION);
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        params.add("<configuration/>");
        assertThat(xmlrpc.execute("configureCollection", params)).isEqualTo(TRUE);
    }

    @Test
    public void testCreateId() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = createCollection(TARGET_COLLECTION);
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat((String) xmlrpc.execute("createId", params)).endsWith(".xml");
    }

    @Test
    public void testCreateResourceId() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = createCollection(TARGET_COLLECTION);
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat((String) xmlrpc.execute("createResourceId", params)).endsWith(".xml");
    }

    @Test
    public void testGetCollectionDesc() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        Map<String, Object> response = (Map<String, Object>) xmlrpc.execute("getCollectionDesc", params);
        assertThat(response).isNotEmpty();
    }

    @Test
    public void testExistsAndCanOpenCollection() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat(xmlrpc.execute("existsAndCanOpenCollection", params)).isEqualTo(TRUE);
    }

    @Test
    public void testDescribeResource() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_RESOURCE.toString());
        Map<String, Object> result = (Map<String, Object>) xmlrpc.execute("describeResource", params);
        assertThat(result).isNotEmpty();
    }

    @Test
    public void testGetContentDigest() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(MODULE_RESOURCE.toString());
        params.add("SHA-256");
        Map<String, Object> result = (Map<String, Object>) xmlrpc.execute("getContentDigest", params);
        assertThat(result).isNotEmpty();
    }

    @Test
    public void testDocumentListing() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();

        assertThat((Object[]) xmlrpc.execute("getDocumentListing", Collections.emptyList())).isNotEmpty();

        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat((Object[]) xmlrpc.execute("getDocumentListing", params)).isNotEmpty();
    }

    @Test
    public void testGetCollectionListing() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add("/db");
        Object[] result = (Object[]) xmlrpc.execute("getCollectionListing", params);
        assertThat(result).isNotEmpty();
    }

    @Test
    public void testGetResourceCount() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat((Integer) xmlrpc.execute("getResourceCount", params)).isEqualTo(3);
    }

    @Test
    public void testPermissions() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat((Map<String, Object>) xmlrpc.execute("getPermissions", params)).isNotEmpty();

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        assertThat((Map<String, Object>) xmlrpc.execute("getPermissions", params)).isNotEmpty();

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add(755);
        assertThat(xmlrpc.execute("setPermissions", params)).isEqualTo(TRUE);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add("rw-rw-rw-");
        assertThat(xmlrpc.execute("setPermissions", params)).isEqualTo(TRUE);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add("admin");
        params.add("dba");
        params.add(755);
        assertThat(xmlrpc.execute("setPermissions", params)).isEqualTo(TRUE);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add("admin");
        params.add("dba");
        params.add("rw-rw-rw-");
        assertThat(xmlrpc.execute("setPermissions", params)).isEqualTo(TRUE);

        params.clear();
        params.add(TARGET_RESOURCE.toString());
        params.add("admin");
        params.add("dba");
        params.add(755);
        params.add(Collections.emptyList());
        assertThat(xmlrpc.execute("setPermissions", params)).isEqualTo(TRUE);
    }

    @Test
    public void testChgrp() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(MODULE_RESOURCE.toString());
        params.add("guest");
        assertThat(xmlrpc.execute("chgrp", params)).isEqualTo(TRUE);
    }

    @Test
    public void testChown() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(MODULE_RESOURCE.toString());
        params.add("anoymous");
        assertThat(xmlrpc.execute("chown", params)).isEqualTo(TRUE);

        params.add("guest");
        assertThat(xmlrpc.execute("chown", params)).isEqualTo(TRUE);
    }

    @Test
    public void testGetBinaryResource() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(MODULE_RESOURCE.toString());
        assertThat((byte[]) xmlrpc.execute("getBinaryResource", params)).isNotEmpty();
    }

    @Test
    public void testListDocumentPermissions() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat((Map<String, List>) xmlrpc.execute("listDocumentPermissions", params)).isNotEmpty();
    }

    @Test
    public void testListCollectionPermissions() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add("/db");
        assertThat((Map<String, List>) xmlrpc.execute("listCollectionPermissions", params)).isNotEmpty();
    }

    @Test
    public void testGetSubCollectionPermissions() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add("/db");
        params.add("xmlrpc");
        assertThat((Map<String, List>) xmlrpc.execute("getSubCollectionPermissions", params)).isNotEmpty();
    }

    @Test
    public void testGetSubCollectionCreationTime() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add("/db");
        params.add("xmlrpc");
        assertThat((Long)xmlrpc.execute("getSubCollectionCreationTime", params)).isPositive();
    }

    @Test
    public void testGetSubResourcePermissions() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        params.add(TEST_XML_URI.toString());
        assertThat((Map<String, List>) xmlrpc.execute("getSubResourcePermissions", params)).isNotEmpty();
    }

    @Test
    public void testGetCreationDate() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_COLLECTION.toString());
        assertThat((Date) xmlrpc.execute("getCreationDate", params)).isNotNull();
    }

    @Test
    public void testGetTimestamps() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(TARGET_RESOURCE.toString());
        assertThat((Object[]) xmlrpc.execute("getTimestamps", params)).isNotEmpty();
    }

    @Test
    public void testPrintDiagnostics() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add(QUERY_MODULE_DATA);
        params.add(Map.of());
        assertThat((String) xmlrpc.execute("printDiagnostics", params))
                .contains("$tm:imported-external-string, $tm-query:local-external-string");
    }

    @Test
    public void testQueryP() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add("//test");
        params.add(TARGET_RESOURCE.toString());
        params.add("1");
        params.add(Map.of());
        assertThat((Map<String, Object>) xmlrpc.execute("queryP", params)).isNotEmpty();

        params.set(0, "//test".getBytes());
        assertThat((Map<String, Object>) xmlrpc.execute("queryP", params)).isNotEmpty();
    }

    @Test
    public void testQueryPT() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();
        final List<Object> params = new ArrayList<>();
        params.add("//test".getBytes());
        params.add(Map.of());
        assertThat((Map<String, Object>) xmlrpc.execute("queryPT", params)).isNotEmpty();

        params.clear();
        params.add("//test".getBytes());
        params.add(TARGET_RESOURCE.toString());
        params.add("1");
        params.add(Map.of());
        assertThat((Map<String, Object>) xmlrpc.execute("queryPT", params)).isNotEmpty();
    }

    @Test
    public void testLockUnlockResources() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();

        assertThat(xmlrpc.execute("hasUserLock", List.of(TARGET_RESOURCE.toString()))).isEqualTo("");
        assertThat(xmlrpc.execute("lockResource", List.of(TARGET_RESOURCE.toString(), "admin"))).isEqualTo(TRUE);
        assertThat(xmlrpc.execute("hasUserLock", List.of(TARGET_RESOURCE.toString()))).isEqualTo("admin");
        assertThat(xmlrpc.execute("unlockResource", List.of(TARGET_RESOURCE.toString()))).isEqualTo(TRUE);
        assertThat(xmlrpc.execute("hasUserLock", List.of(TARGET_RESOURCE.toString()))).isEqualTo("");
    }

    @Test
    public void testIndexedElements() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();

        assertThat((Object[])xmlrpc.execute("getIndexedElements", List.of(TARGET_COLLECTION.toString(), true))).isEmpty();
        assertThat(xmlrpc.execute("reindexCollection", List.of(TARGET_COLLECTION.toString()))).isEqualTo(TRUE);
        assertThat(xmlrpc.execute("reindexDocument", List.of(TARGET_RESOURCE.toString()))).isEqualTo(TRUE);
    }

    @Test
    public void testLastModified() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();

        assertThat(xmlrpc.execute("setLastModified", List.of(TARGET_RESOURCE.toString(), 5000L))).isEqualTo(TRUE);
    }

    @Test
    public void testGetDocType() throws XmlRpcException, MalformedURLException {
        final XmlRpcClient xmlrpc = storeData();

        assertThat((Object[])xmlrpc.execute("getDocType", List.of(TARGET_RESOURCE.toString()))).isNotEmpty();
    }

    private String generateXml(final int minBytes) {
        int bytes = 0;
        final StringBuilder builder = new StringBuilder("<container>");
        bytes += 11;

        int i = 0;
        while (bytes < minBytes) {
            builder.append("<num>");
            bytes += 5;

            final String n = String.valueOf(++i);
            builder.append(n);
            bytes += n.length();

            builder.append("</num>");
            bytes += 6;
        }

        builder.append("</container>");

        return builder.toString();
    }

    private byte[] generateBinary(final int minBytes) {
        final byte[] buf = new byte[minBytes];
        new Random().nextBytes(buf);
        return buf;
    }

    protected XmlRpcClient getClient() throws MalformedURLException {
        XmlRpcClient client = new XmlRpcClient();
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setServerURL(new URL(getUri()));
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        client.setConfig(config);
        return client;
    }
}
