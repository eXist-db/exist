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

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;

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
    public void checkContent() throws Exception {

        // Get content
        String jmxXML = Request.Get(getServerUri()).execute().returnContent().asString();

        // Prepare XPath validation
        HashMap<String, String> prefix2Uri = new HashMap<String, String>();
        prefix2Uri.put("jmx", "http://exist-db.org/jmx");

        // Java GC
        if(SystemUtils.IS_JAVA_1_8) {
            assertThat(jmxXML, hasXPath("//jmx:GarbageCollectorImpl").withNamespaceContext(prefix2Uri));
        } else {
            assertThat(jmxXML, hasXPath("//jmx:GarbageCollectorExtImpl").withNamespaceContext(prefix2Uri));
        }

        // Jetty
        assertThat(jmxXML, hasXPath("//jmx:WebAppContext").withNamespaceContext(prefix2Uri));

        // eXist-db
        assertThat(jmxXML, hasXPath("//jmx:ProcessReport").withNamespaceContext(prefix2Uri));
        assertThat(jmxXML, hasXPath("//jmx:Cache").withNamespaceContext(prefix2Uri));
        assertThat(jmxXML, hasXPath("//jmx:SystemInfo").withNamespaceContext(prefix2Uri));
        assertThat(jmxXML, hasXPath("//jmx:CacheManager").withNamespaceContext(prefix2Uri));
        assertThat(jmxXML, hasXPath("//jmx:LockManager").withNamespaceContext(prefix2Uri));
        assertThat(jmxXML, hasXPath("//jmx:SanityReport").withNamespaceContext(prefix2Uri));
        assertThat(jmxXML, hasXPath("//jmx:Database").withNamespaceContext(prefix2Uri));
    }

    @Test
    public void checkBasicRequest() throws Exception {

        HttpResponse response = Request.Get(getServerUri())
                .addHeader(new BasicHeader("Accept", ContentType.APPLICATION_XML.toString()))
                .execute().returnResponse();

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("application/xml", response.getEntity().getContentType().getValue());
    }

}
