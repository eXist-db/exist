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

import java.io.IOException;
import java.net.URL;

/**
 * A source loaded through the current context class loader.
 *
 * @author wolf
 */
public class ClassLoaderSource extends URLSource {

    public static final String PROTOCOL = "resource:";

    private final String source;

    /**
     * @param source The resource name (e.g. url).
     *               The name of a resource is a '<code>/</code>'-separated path name that
     *               identifies the resource. Preceding "/" and "resource:"" are removed.
     * @throws IOException in case of an I/O error
     */
    public ClassLoaderSource(final String source) throws IOException {
        super(sourceToUrl(source));
        this.source = source;
    }

    private static URL sourceToUrl(String source) throws IOException {
        if (source.startsWith(PROTOCOL)) {
            source = source.substring(PROTOCOL.length());
        }
        if (source.startsWith("/")) {
            source = source.substring(1);
        }
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final URL url = cl.getResource(source);
        if (url == null) {
            throw new IOException("Source not found: " + source);
        }
        return url;
    }

    @Override
    public String type() {
        final String protocol = url.getProtocol();
        final String host = url.getHost();
        if ("file".equals(protocol) && (host == null || host.length() == 0 || "localhost".equals(host) || "127.0.0.1".equals(host))) {
            return "File";
        }
        return "Classloader";
    }

    public String getSource() {
        return source;
    }
}