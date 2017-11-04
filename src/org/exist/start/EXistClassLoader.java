package org.exist.start;

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
