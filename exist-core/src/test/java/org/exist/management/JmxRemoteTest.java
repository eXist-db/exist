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

import com.evolvedbinary.j8fu.function.FunctionE;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.management.client.JMXtoXML.JMX_NAMESPACE;
import static org.exist.management.client.JMXtoXML.JMX_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
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
        final Request request = Request.get(getServerUri());
        final String jmxXml = withHttpExecutor(executor -> executor.execute(request).returnContent().asString());

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
    public void checkBasicRequest() throws IOException {
        final Request request = Request.get(getServerUri())
                .addHeader(new BasicHeader("Accept", ContentType.APPLICATION_XML.toString()));

         final Tuple2<Integer, String> codeAndMediaType = withHttpExecutor(executor -> {
            final ClassicHttpResponse response = (ClassicHttpResponse) executor.execute(request).returnResponse();
            return Tuple(response.getCode(), response.getEntity().getContentType());
        });

        assertEquals(Tuple(HttpStatus.SC_OK, "application/xml"), codeAndMediaType);
    }

    private static <T> T withHttpExecutor(final FunctionE<Executor, T, IOException> fn) throws IOException {
        return withHttpClient(client -> fn.apply(Executor.newInstance(client)));
    }
}
