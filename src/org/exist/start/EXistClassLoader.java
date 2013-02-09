package org.exist.start;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Root class loader when eXist is started via the bootstrap loader. Extends
 * URLClassLoader to allow dynamic addition of URLs at runtime.
 *
 * @author Wolfgang Meier
 */
public class EXistClassLoader extends URLClassLoader {

    public EXistClassLoader(URL[] urls, ClassLoader classLoader) {
        super(urls, classLoader);
    }

    public void addURLs(Classpath cp) {
        for (final File file : cp) {
            try {
                addURL(file.toURI().toURL());
            } catch (final MalformedURLException e) {
            }
        }
    }
}
