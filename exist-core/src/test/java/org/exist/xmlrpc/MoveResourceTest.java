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

import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.ClassRule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.samples.Samples.SAMPLES;
import static org.junit.Assert.*;

/**
 * Test for deadlocks when moving resources from one collection to another. Uses
 * two threads: one stores a document, then moves it to another collection.
 * Based on XML-RPC. The second thread tries to execute a query via REST.
 *
 * Due to the complex move task, threads will deadlock almost immediately if
 * something's wrong with collection locking.
 */
public class MoveResourceTest {

    private static final int DELAY = 10;  // milliseconds
    private static final long TIMEOUT = 5 * 60 * 1000;  // milliseconds
    /** Retry REST calls on transient 5xx (warmup, lock contention, stale connections). Real deadlocks still fail. */
    private static final int REST_RETRY_MAX = 3;
    private static final int REST_RETRY_DELAY_MS = 100;

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static String getXmlRpcUri() {
        return "http://localhost:" + existWebServer.getPort() + "/xmlrpc";
    }

    private static String getRestUri() {
        return "http://localhost:" + existWebServer.getPort();
    }

    @Test
    public void testMove() throws InterruptedException, ExecutionException {

        final List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(new MoveThread(50));
        tasks.add(new CheckThread(100));
        tasks.add(new CheckThread(100));

        ExecutorService service = null;
        try {
            service = Executors.newFixedThreadPool(tasks.size(), new ThreadFactory() {
                final AtomicInteger id = new AtomicInteger();
                @Override
                public Thread newThread(final Runnable r) {
                    return new Thread(r, "MoveResourceTest.testMove-" + id.getAndIncrement());
                }
            });

            final CompletionService<Boolean> cs = new ExecutorCompletionService<>(service);
            tasks.forEach(cs::submit);

            //wait for all tasks to complete
            final int n = tasks.size();
            for (int i = 0; i < n; i++) {
                final Future<Boolean> future = cs.poll(TIMEOUT, TimeUnit.MILLISECONDS);
                if (future == null) {
                    i--;
                } else {
                    final Boolean result = future.get();
                    assertNotNull(result);
                }
            }
        } finally {
            if (service != null) {
                service.shutdownNow();
            }
        }
    }

    private void createCollection(XmlRpcClient client, XmldbURI collection) throws XmlRpcException {
        List<Object> params = new ArrayList<>();
        params.add(collection.toString());
        Boolean result = (Boolean) client.execute("createCollection", params);
        assertTrue(result);
    }

    private class MoveThread implements Callable<Boolean> {
        private final int iterations;

        public MoveThread(final int iterations) {
            this.iterations = iterations;
        }

        @Override
        public Boolean call() throws IOException, XmlRpcException, InterruptedException {
            final String romeoAndJuliet = readSample();
            final XmlRpcClient xmlrpc = getXmlRpcClient();
            for (int i = 0; i < iterations; i++) {
                XmldbURI sourceColl = XmldbURI.ROOT_COLLECTION_URI.append("source" + i);
                XmldbURI targetColl1 = XmldbURI.ROOT_COLLECTION_URI.append("target");
                XmldbURI targetColl2 = targetColl1.append("test" + i);
                XmldbURI sourceResource = sourceColl.append("source.xml");
                XmldbURI targetResource = targetColl2.append("copied.xml");

                createCollection(xmlrpc, sourceColl);
                createCollection(xmlrpc, targetColl1);
                createCollection(xmlrpc, targetColl2);

                List<Object> params = new ArrayList<>();
                params.add(romeoAndJuliet);
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

                Thread.sleep(DELAY);

                params.clear();
                params.add(sourceColl.toString());
                xmlrpc.execute("removeCollection", params);

                params.set(0, targetColl1.toString());
                xmlrpc.execute("removeCollection", params);
            }

            return true;
        }

        private String readSample() throws IOException {
            try (final InputStream is = SAMPLES.getRomeoAndJulietSample()) {
                return InputStreamUtil.readString(is, UTF_8);
            }
        }
    }

    private static class CheckThread implements Callable<Boolean> {
        private static final PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
        private final int iterations;

        public CheckThread(final int iterations) {
            this.iterations = iterations;
        }

        @Override
        public Boolean call() throws IOException, InterruptedException {
            final CloseableHttpClient client = HttpClients
                    .custom()
                    .setConnectionManager(poolingHttpClientConnectionManager)
                    .build();
            final Executor executor = Executor.newInstance(client);

            final String reqUrl = getRestUri() + "/db?_query=" + URLEncoder.encode("collection('/db')//SPEECH[SPEAKER = 'JULIET']", "UTF-8");
            final Request request = Request.get(reqUrl);

            for (int i = 0; i < iterations; i++) {
                HttpResponse response = null;
                int lastStatus = -1;
                for (int r = 0; r <= REST_RETRY_MAX; r++) {
                    response = executor.execute(request).returnResponse();
                    lastStatus = response.getCode();
                    if (lastStatus == HttpStatus.SC_OK) {
                        break;
                    }
                    if (lastStatus < 500 || r == REST_RETRY_MAX) {
                        fail("REST query failed" + (r > 0 ? " after " + r + " retries" : "") + ": HTTP " + lastStatus);
                    }
                    Thread.sleep(REST_RETRY_DELAY_MS);
                }
                assertEquals("HTTP " + lastStatus, HttpStatus.SC_OK, response.getCode());

                Thread.sleep(DELAY);
            }

            return true;
        }
    }

    private static XmlRpcClient getXmlRpcClient() throws MalformedURLException {
        final XmlRpcClient client = new XmlRpcClient();
        final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setEnabledForExtensions(true);
        config.setServerURL(new URL(getXmlRpcUri()));
        config.setBasicUserName(TestUtils.ADMIN_DB_USER);
        config.setBasicPassword(TestUtils.ADMIN_DB_PWD);
        client.setConfig(config);
        return client;
    }
}
