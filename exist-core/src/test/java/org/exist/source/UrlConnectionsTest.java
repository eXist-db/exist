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
package org.exist.source;

import org.exist.test.SilentServer;
import org.exist.util.Configuration;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * See <a href="https://github.com/eXist-db/exist/issues/6902">#6902</a>.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class UrlConnectionsTest {

    @RegisterExtension
    final SilentServer silentServer = new SilentServer();

    @Test
    void openAppliesTimeouts() throws Exception {
        final URLConnection connection = UrlConnections.open(
                URI.create(silentServer.url("/x")).toURL(), new UrlConnections.Timeouts(1234, 567));
        assertEquals(1234, connection.getConnectTimeout());
        assertEquals(567, connection.getReadTimeout());
    }

    @Test
    void openStreamFromSilentHostTimesOut() {
        assertThrows(SocketTimeoutException.class, () -> {
            try (final InputStream is = UrlConnections.openStream(
                    URI.create(silentServer.url("/x")).toURL(), new UrlConnections.Timeouts(1000, 300))) {
                is.read();
            }
        });
    }

    @Test
    void timeoutsFromConfiguration() throws Exception {
        assertEquals(UrlConnections.Timeouts.DEFAULT, UrlConnections.Timeouts.from(null));

        final Configuration configuration = new Configuration();
        configuration.setProperty(XQueryContext.PROPERTY_URL_CONNECT_TIMEOUT, 1500);
        configuration.setProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, 2500);
        assertEquals(new UrlConnections.Timeouts(1500, 2500), UrlConnections.Timeouts.from(configuration));
    }
}
