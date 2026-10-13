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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retrieving a stored document with {@code getDocument} is not XDM serialization, so a CDATA
 * section is normally returned as one. The html output method has no CDATA sections for HTML
 * elements though, so there the content of a script element must come back as character data
 * (see <a href="https://github.com/eXist-db/exist/issues/6810">issue #6810</a>).
 */
public class GetDocumentHtmlCdataTest {

    @RegisterExtension
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String DOC_PATH = "/db/html-cdata-test.xml";

    private static final String DOC = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body><script><![CDATA[if (a < b) f()]]></script></body></html>""";

    private static XmlRpcClient xmlrpc;

    @BeforeAll
    public static void storeDocument() throws MalformedURLException, XmlRpcException {
        xmlrpc = getXmlRpcClient();
        final List<Object> params = new ArrayList<>();
        params.add(DOC);
        params.add(DOC_PATH);
        params.add(1);
        assertTrue((Boolean) xmlrpc.execute("parse", params));
    }

    @AfterAll
    public static void removeDocument() throws XmlRpcException {
        final List<Object> params = new ArrayList<>();
        params.add(DOC_PATH);
        xmlrpc.execute("remove", params);
    }

    private static String getDocument(final String method) throws XmlRpcException {
        final Map<String, String> options = new HashMap<>();
        options.put("indent", "no");
        if (method != null) {
            options.put("method", method);
        }
        if ("html".equals(method)) {
            options.put("html-version", "5.0");
        }
        final List<Object> params = new ArrayList<>();
        params.add(DOC_PATH);
        params.add(options);
        return new String((byte[]) xmlrpc.execute("getDocument", params), UTF_8);
    }

    @Test
    public void htmlMethodReturnsScriptContentWithoutCdata() throws XmlRpcException {
        assertEquals("<!DOCTYPE html><html><body><script>if (a < b) f()</script></body></html>", getDocument("html"));
    }

    @Test
    public void xmlSyntaxMethodsKeepCdata() throws XmlRpcException {
        final String cdata = "<script><![CDATA[if (a < b) f()]]></script>";
        assertTrue(getDocument(null).contains(cdata));
        assertTrue(getDocument("xml").contains(cdata));
        assertTrue(getDocument("xhtml").contains(cdata));
    }

    private static XmlRpcClient getXmlRpcClient() throws MalformedURLException {
        final XmlRpcClient client = new XmlRpcClient();
        final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setServerURL(new URL("http://localhost:" + existWebServer.getPort() + "/xmlrpc"));
        config.setBasicUserName(TestUtils.ADMIN_DB_USER);
        config.setBasicPassword(TestUtils.ADMIN_DB_PWD);
        client.setConfig(config);
        return client;
    }
}
