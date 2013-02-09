// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.exist.start;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * Class to handle CLASSPATH construction
 * @author Jan Hlavat�
 */
public class Classpath implements Iterable<File> {

    Vector<File> _elements = new Vector<File>();    

    public Classpath()
    {}    

    public Classpath(String initial)
    {
        addClasspath(initial);
    }
        
    public boolean addComponent(String component)
    {
        if ((component != null)&&(component.length()>0)) {
            try {
                final File f = new File(component);
                if (f.exists())
                {
                    final File key = f.getCanonicalFile();
                    if (!_elements.contains(key))
                    {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (final IOException e) {}
        }
        return false;
    }
    
    public boolean addComponent(File component)
    {
        if (component != null) {
            try {
                if (component.exists()) {
                    final File key = component.getCanonicalFile();
                    if (!_elements.contains(key)) {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (final IOException e) {}
        }
        return false;
    }

    public void addClasspath(String s)
    {
        if (s != null)
        {
            final StringTokenizer t = new StringTokenizer(s, File.pathSeparator);
            while (t.hasMoreTokens())
            {
                addComponent(t.nextToken());
            }
        }
    }    
    
    public String toString()
    {
        final StringBuilder cp = new StringBuilder(1024);
        final int cnt = _elements.size();
        if (cnt >= 1) {
            cp.append( ((File)(_elements.elementAt(0))).getPath() );
        }
        for (int i=1; i < cnt; i++) {
            cp.append(File.pathSeparatorChar);
            cp.append( ((File)(_elements.elementAt(i))).getPath() );
        }
        return cp.toString();
    }

    public EXistClassLoader getClassLoader(ClassLoader parent) {
        final int cnt = _elements.size();
        final URL[] urls = new URL[cnt];
        for (int i=0; i < cnt; i++) {
            try {
                urls[i] = _elements.elementAt(i).toURI().toURL();
            } catch (final MalformedURLException e) {}
        }
        
        if (parent == null)
        	{parent = Thread.currentThread().getContextClassLoader();}
        if (parent == null) {
            parent = Classpath.class.getClassLoader();
        }
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }
        return new EXistClassLoader(urls, parent);
    }

    @Override
    public Iterator<File> iterator() {
        return _elements.iterator();
    }
}
