// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

//Modified for eXist-db

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
 * @author Jan Hlavat�
 */
public class Classpath implements Iterable<Path> {

    final List<Path> _elements = new ArrayList<>();

    public Classpath() {}

    public Classpath(final String initial)
    {
        addClasspath(initial);
    }
        
    public boolean addComponent(final String component) {
        if (component != null && component.length() > 0) {
            try {
                final Path p = Paths.get(component);
                if (Files.exists(p))
                {
                    final Path key = p.toAbsolutePath();
                    if (!_elements.contains(key))
                    {
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
            while (t.hasMoreTokens())
            {
                addComponent(t.nextToken());
            }
        }
    }    

    @Override
    public String toString() {
        final StringBuilder cp = new StringBuilder(1024);
        final int cnt = _elements.size();
        if (cnt >= 1) {
            cp.append(_elements.get(0));
        }
        for (int i=1; i < cnt; i++) {
            cp.append(File.pathSeparatorChar);
            cp.append(_elements.get(i));
        }
        return cp.toString();
    }

    public EXistClassLoader getClassLoader(ClassLoader parent) {
        final URL urls[] = _elements
                .stream()
                .map(Path::toUri)
                .map(u -> {
                    try {
                        return Optional.of(u.toURL());
                    } catch(final MalformedURLException e) {
                        return Optional.<URL>empty();
                    }
                }).filter(ou -> ou.isPresent())
                .map(Optional::get)
                .toArray(sz -> new URL[sz]);

        // try and ensure we have a classloader
        parent = or(
            or(
                or(Optional.ofNullable(parent), () -> Optional.ofNullable(Thread.currentThread().getContextClassLoader())),
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

    /**
     * Copied from {@link com.evolvedbinary.j8fu.OptionalUtil#or(Optional, Supplier)}
     * as org.exist.start is compiled into a separate Jar and doesn't have
     * the rest of eXist available on the classpath
     */
    private static <T> Optional<T> or(final Optional<T> left, final Supplier<Optional<T>> right) {
        if(left.isPresent()) {
            return left;
        } else {
            return right.get();
        }
    }
}
