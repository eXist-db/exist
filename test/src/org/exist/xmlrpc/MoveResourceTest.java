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

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.XmldbURI;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.ClassRule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for deadlocks when moving resources from one collection to another. Uses
 * two threads: one stores a document, then moves it to another collection.
 * Based on XML-RPC. The second thread tries to execute a query via REST.
 *
 * Due to the complex move task, threads will deadlock almost immediately if
 * something's wrong with collection locking.
 */
public class MoveResourceTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true);

    private static String getUri() {
        return "http://localhost:" + existWebServer.getPort() + "/xmlrpc";
    }

    private static String getRestUri() {
        return "http://localhost:" + existWebServer.getPort();
    }

    @Test
    public void testMove() throws InterruptedException, ExecutionException {

        final List<Callable<Void>> tasks = new ArrayList<>();
        tasks.add(new MoveThread());
        tasks.add(new CheckThread());
        tasks.add(new CheckThread());

        final ExecutorService service = Executors.newFixedThreadPool(tasks.size());
        final CompletionService<Void> cs = new ExecutorCompletionService<>(service);
        tasks.stream().forEach((task) -> {
            cs.submit(task);
        });

        //wait for all tasks to complete
        final int n = tasks.size();
        for (int i = 0; i < n; i++) {
            cs.take().get();
        }
    }

    private void createCollection(XmlRpcClient client, XmldbURI collection) throws IOException, XmlRpcException {
        List<Object> params = new ArrayList<>();
        params.add(collection.toString());
        Boolean result = (Boolean) client.execute("createCollection", params);
        assertTrue(result);
    }

    private String readData() throws IOException {
        return new String(TestUtils.readRomeoAndJulietSampleXml(), UTF_8);
    }

    private class MoveThread implements Callable<Void> {

        @Override
        public Void call() throws IOException, XmlRpcException, InterruptedException {
            for (int i = 0; i < 100; i++) {
                XmldbURI sourceColl = XmldbURI.ROOT_COLLECTION_URI.append("source" + i);
                XmldbURI targetColl1 = XmldbURI.ROOT_COLLECTION_URI.append("target");
                XmldbURI targetColl2 = targetColl1.append("test" + i);
                XmldbURI sourceResource = sourceColl.append("source.xml");
                XmldbURI targetResource = targetColl2.append("copied.xml");

                XmlRpcClient xmlrpc = getClient();

                createCollection(xmlrpc, sourceColl);
                createCollection(xmlrpc, targetColl1);
                createCollection(xmlrpc, targetColl2);

                List<Object> params = new ArrayList<>();
                params.add(readData());
                params.add(sourceResource.toString());
                params.add(1);

                Boolean result = (Boolean) xmlrpc.execute("parse", params);
                assertTrue(result);

                params.clear();
                params.add(sourceResource.toString());
                params.add(targetColl2.toString());
                params.add("copied.xml");

                xmlrpc.execute("moveResource", params);

                Map<String, String> options = new HashMap<>();
                options.put("indent", "yes");
                options.put("encoding", "UTF-8");
                options.put("expand-xincludes", "yes");
                options.put("process-xsl-pi", "no");

                params.clear();
                params.add(targetResource.toString());
                params.add(options);

                byte[] data = (byte[]) xmlrpc.execute("getDocument", params);
                assertTrue(data != null && data.length > 0);

                synchronized (this) {
                    wait(250);
                }

                params.clear();
                params.add(sourceColl.toString());
                xmlrpc.execute("removeCollection", params);

                params.set(0, targetColl1.toString());
                xmlrpc.execute("removeCollection", params);
            }
            return null;
        }
    }

    private class CheckThread implements Callable<Void> {

        @Override
        public Void call() throws IOException, InterruptedException {
            String reqUrl = getRestUri() + "/db?_query=" + URLEncoder.encode("collection('/db')//SPEECH[SPEAKER = 'JULIET']");
            for (int i = 0; i < 200; i++) {
                URL url = new URL(reqUrl);
                HttpURLConnection connect = (HttpURLConnection) url.openConnection();
                connect.setRequestMethod("GET");
                connect.connect();

                int r = connect.getResponseCode();
                assertEquals("Server returned response code " + r, 200, r);

                try (final InputStream is = connect.getInputStream()) {
                    readResponse(is);
                }

                synchronized (this) {
                    wait(250);
                }
            }
            return null;
        }

        private String readResponse(final InputStream is) throws IOException {
            final StringBuilder out = new StringBuilder();
            try(final BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                    out.append("\r\n");
                }
            }
            return out.toString();
        }
    }

    protected static XmlRpcClient getClient() throws MalformedURLException {
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
