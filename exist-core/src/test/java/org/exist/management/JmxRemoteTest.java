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
package org.exist.management;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.commons.lang3.SystemUtils;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.management.client.JMXtoXML.JMX_NAMESPACE;
import static org.exist.management.client.JMXtoXML.JMX_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.xmlunit.matchers.HasXPathMatcher.hasXPath;

public class JmxRemoteTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort() + "/exist/status";
    }

    @Test
    public void checkContent() throws IOException {
        // Get content
        final HttpRequest request = HttpRequest.newBuilder(URI.create(getServerUri())).GET().build();
        final String jmxXml = withHttpClient(client ->
                AbstractHttpTest.executeForStatusAndBody(client, request).body());

        // Prepare XPath validation
        final Map<String, String> prefix2Uri = new HashMap<>();
        prefix2Uri.put(JMX_PREFIX, JMX_NAMESPACE);

        // Java GC
        if (SystemUtils.IS_JAVA_1_8) {
            assertThat(jmxXml, hasXPath("//jmx:GarbageCollectorImpl").withNamespaceContext(prefix2Uri));
        } else {
            assertThat(jmxXml, hasXPath("//jmx:GarbageCollectorExtImpl").withNamespaceContext(prefix2Uri));
        }

        // Jetty
        assertThat(jmxXml, hasXPath("//jmx:WebAppContext").withNamespaceContext(prefix2Uri));

        // eXist-db
        assertThat(jmxXml, hasXPath("//jmx:ProcessReport").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:Cache").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:SystemInfo").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:CacheManager").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:CollectionCache").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:LockTable").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:SanityReport").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:Database").withNamespaceContext(prefix2Uri));

        // Regression guard for https://github.com/eXist-db/exist/issues/6379:
        // PerInstanceMBean must expose InstanceId as a JMX attribute, which depends on the
        // getInstanceId() JavaBean naming convention. Renaming the interface method (or any
        // concrete implementation) to a bare name like instanceId() silently drops the
        // attribute from MBeanInfo. Verify a representative sample of MXBeans that extend
        // PerInstanceMBean still publish InstanceId.
        assertThat(jmxXml, hasXPath("//jmx:Database/jmx:InstanceId").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:ProcessReport/jmx:InstanceId").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:CollectionCache/jmx:InstanceId").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:LockTable/jmx:InstanceId").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:SanityReport/jmx:InstanceId").withNamespaceContext(prefix2Uri));
    }

    @Test
    public void vectorCategoryIncludesVectorStore() throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(getServerUri() + "?c=vector")).GET().build();
        final String jmxXml = withHttpClient(client ->
                AbstractHttpTest.executeForStatusAndBody(client, request).body());

        final Map<String, String> prefix2Uri = new HashMap<>();
        prefix2Uri.put(JMX_PREFIX, JMX_NAMESPACE);

        assertThat(jmxXml, hasXPath("//jmx:VectorStore").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:VectorStore/jmx:Available").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:VectorStore/jmx:FileName").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:VectorStore/jmx:EntryCount").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:VectorStore/jmx:EntryCountKnown").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:VectorStore/jmx:StorageBackend").withNamespaceContext(prefix2Uri));
    }

    @Test
    public void vectorCategoryIncludesVectorEmbeddingWhenExtensionPresent() throws IOException {
        assumeTrue("Vector extension not on classpath", isVectorExtensionPresent());

        final HttpRequest request = HttpRequest.newBuilder(URI.create(getServerUri() + "?c=vector")).GET().build();
        final String jmxXml = withHttpClient(client ->
                AbstractHttpTest.executeForStatusAndBody(client, request).body());

        final Map<String, String> prefix2Uri = new HashMap<>();
        prefix2Uri.put(JMX_PREFIX, JMX_NAMESPACE);

        assertThat(jmxXml, hasXPath("//jmx:VectorEmbedding").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:VectorEmbedding/jmx:ModelCount").withNamespaceContext(prefix2Uri));
        assertThat(jmxXml, hasXPath("//jmx:VectorEmbedding/jmx:KnnBackend").withNamespaceContext(prefix2Uri));
    }

    @Test
    public void checkBasicRequest() throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(getServerUri()))
                .header("Accept", "application/xml")
                .GET()
                .build();

        final Tuple2<Integer, String> codeAndMediaType = withHttpClient(client -> {
            final HttpResponse<Void> response = send(client, request, HttpResponse.BodyHandlers.discarding());
            return Tuple(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null));
        });

        assertEquals(Tuple(HttpURLConnection.HTTP_OK, "application/xml"), codeAndMediaType);
    }

    private static <T> HttpResponse<T> send(final HttpClient client, final HttpRequest request,
            final HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
        try {
            return client.send(request, bodyHandler);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting HTTP response", e);
        }
    }

    private static boolean isVectorExtensionPresent() {
        try {
            Class.forName("org.exist.vector.VectorExtensionLifecycle");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }
}
