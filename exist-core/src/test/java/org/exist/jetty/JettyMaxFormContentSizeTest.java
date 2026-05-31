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
package org.exist.jetty;

import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for issue #4087 — the eXist Jetty 12 context XML must
 * expose a tunable {@code maxFormContentSize}. Without it, the Jetty 12
 * default (200,000 bytes) silently caps form uploads at ~200KB with no
 * configuration knob, since the per-Request attribute mechanism used in
 * the original Jetty 9 jetty.xml workaround no longer exists.
 *
 * <p>The test parses each shipped context XML through Jetty's
 * {@link XmlConfiguration} — the same path Jetty itself uses at startup —
 * and verifies the {@code maxFormContentSize} setter is honored, both at
 * the default value and when overridden via the documented Jetty
 * property.</p>
 */
public class JettyMaxFormContentSizeTest {

    private static final String CTX = "/org/exist/jetty/etc/webapps/exist-webapp-context.xml";
    private static final String STANDALONE_CTX = "/org/exist/jetty/etc/standalone-webapps/exist-webapp-context.xml";

    @Test
    public void contextHonorsMaxFormContentSizeDefault() throws Exception {
        final WebAppContext ctx = configureContext(CTX, Map.of());
        assertEquals(200_000, ctx.getMaxFormContentSize());
    }

    @Test
    public void contextHonorsMaxFormContentSizeOverride() throws Exception {
        final WebAppContext ctx = configureContext(CTX,
                Map.of("jetty.http.maxFormContentSize", "2000000"));
        assertEquals(2_000_000, ctx.getMaxFormContentSize());
    }

    @Test
    public void standaloneContextHonorsMaxFormContentSizeDefault() throws Exception {
        final WebAppContext ctx = configureContext(STANDALONE_CTX, Map.of());
        assertEquals(200_000, ctx.getMaxFormContentSize());
    }

    @Test
    public void standaloneContextHonorsMaxFormContentSizeOverride() throws Exception {
        final WebAppContext ctx = configureContext(STANDALONE_CTX,
                Map.of("jetty.http.maxFormContentSize", "5000000"));
        assertEquals(5_000_000, ctx.getMaxFormContentSize());
    }

    private static WebAppContext configureContext(final String resource,
                                                  final Map<String, String> properties) throws Exception {
        final java.net.URL url = JettyMaxFormContentSizeTest.class.getResource(resource);
        assertNotNull("missing classpath resource " + resource, url);
        final XmlConfiguration xml = new XmlConfiguration(
                org.eclipse.jetty.util.resource.ResourceFactory.root().newResource(url));
        xml.getProperties().putAll(properties);
        return (WebAppContext) xml.configure();
    }
}
