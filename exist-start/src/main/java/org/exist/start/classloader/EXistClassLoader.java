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
package org.exist.start.classloader;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Root class loader when eXist is started via the bootstrap loader. Extends
 * URLClassLoader to allow dynamic addition of URLs at runtime.
 *
 * @author Wolfgang Meier
 */
public class EXistClassLoader extends URLClassLoader {

    public EXistClassLoader(final URL[] urls, final ClassLoader classLoader) {
        super(urls, classLoader);
    }

    public void addURLs(final Classpath cp) {
        for (final Path path : cp) {
            try {
                addURL(path.toUri().toURL());
            } catch (final MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }
}
