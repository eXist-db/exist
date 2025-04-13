/*
 * NOTE: This file is in part based on code from Mort Bay Consulting.
 * The original license statement is also included below.
 *
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
 *
 * ---------------------------------------------------------------------
 *
 * Copyright 2002-2005 Mort Bay Consulting Pty. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exist.start;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;

/**
 * Class to handle CLASSPATH construction
 *
 * @author Jan Hlavaty
 */
public class Classpath implements Iterable<Path> {

    final List<Path> _elements = new ArrayList<>();

    public Classpath() {
    }

    public Classpath(final String initial) {
        addClasspath(initial);
    }

    /**
     * Copied from {@link com.evolvedbinary.j8fu.OptionalUtil#or(Optional, Supplier)}
     * as org.exist.start is compiled into a separate Jar and doesn't have
     * the rest of eXist available on the classpath
     */
    private static <T> Optional<T> or(final Optional<T> left, final Supplier<Optional<T>> right) {
        if (left.isPresent()) {
            return left;
        } else {
            return right.get();
        }
    }

    public boolean addComponent(final String component) {
        if (component != null && !component.isEmpty()) {
            try {
                final Path p = Paths.get(component);
                if (Files.exists(p)) {
                    final Path key = p.toAbsolutePath();
                    if (!_elements.contains(key)) {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (final InvalidPathException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean addComponent(final Path component) {
        if (component != null) {
            try {
                if (Files.exists(component)) {
                    final Path key = component.toAbsolutePath();
                    if (!_elements.contains(key)) {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (final InvalidPathException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void addClasspath(final String s) {
        if (s != null) {
            final StringTokenizer t = new StringTokenizer(s, File.pathSeparator);
            while (t.hasMoreTokens()) {
                addComponent(t.nextToken());
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder cp = new StringBuilder(1024);
        final int cnt = _elements.size();
        if (cnt >= 1) {
            cp.append(_elements.getFirst());
        }
        for (int i = 1; i < cnt; i++) {
            cp.append(File.pathSeparatorChar);
            cp.append(_elements.get(i));
        }
        return cp.toString();
    }

    public EXistClassLoader getClassLoader(ClassLoader parent) {
        final URL[] urls = _elements.stream().map(Path::toUri).map(u -> {
            try {
                return Optional.of(u.toURL());
            } catch (final MalformedURLException e) {
                return Optional.<URL>empty();
            }
        }).filter(Optional::isPresent).map(Optional::get).toArray(URL[]::new);

        // try and ensure we have a classloader
        parent = or(
                    or(
                            or(
                                    Optional.ofNullable(parent), () -> Optional.ofNullable(Thread.currentThread().getContextClassLoader())
                            ),
                            () -> Optional.ofNullable(Classpath.class.getClassLoader())
                    ),
                    () -> Optional.ofNullable(ClassLoader.getSystemClassLoader())
                ).orElse(null);

        return new EXistClassLoader(urls, parent);
    }

    @Override
    public Iterator<Path> iterator() {
        return _elements.iterator();
    }
}
