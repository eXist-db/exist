/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.management;

import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.exist.management.client.JMXtoXML.JMX_NAMESPACE;
import static org.exist.management.client.JMXtoXML.JMX_PREFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.xmlunit.matchers.HasXPathMatcher.hasXPath;

public class JmxRemoteTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, true, true, true, false);

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort() + "/exist/status";
    }

    @Test
    public void checkContent() throws IOException {
        // Get content
        final String jmxXml = Request.Get(getServerUri()).execute().returnContent().asString();

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
    }

    @Test
    public void checkBasicRequest() throws IOException {
        final HttpResponse response = Request.Get(getServerUri())
                .addHeader(new BasicHeader("Accept", ContentType.APPLICATION_XML.toString()))
                .execute().returnResponse();

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("application/xml", response.getEntity().getContentType().getValue());
    }
}
