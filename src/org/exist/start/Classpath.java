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
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * Class to handle CLASSPATH construction
 * @author Jan Hlavat�
 */
public class Classpath {

    Vector _elements = new Vector();    

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
                File f = new File(component);
                if (f.exists())
                {
                    File key = f.getCanonicalFile();
                    if (!_elements.contains(key))
                    {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (IOException e) {}
        }
        return false;
    }
    
    public boolean addComponent(File component)
    {
        if (component != null) {
            try {
                if (component.exists()) {
                    File key = component.getCanonicalFile();
                    if (!_elements.contains(key)) {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (IOException e) {}
        }
        return false;
    }

    public void addClasspath(String s)
    {
        if (s != null)
        {
            StringTokenizer t = new StringTokenizer(s, File.pathSeparator);
            while (t.hasMoreTokens())
            {
                addComponent(t.nextToken());
            }
        }
    }    
    
    public String toString()
    {
        StringBuilder cp = new StringBuilder(1024);
        int cnt = _elements.size();
        if (cnt >= 1) {
            cp.append( ((File)(_elements.elementAt(0))).getPath() );
        }
        for (int i=1; i < cnt; i++) {
            cp.append(File.pathSeparatorChar);
            cp.append( ((File)(_elements.elementAt(i))).getPath() );
        }
        return cp.toString();
    }
    
    public ClassLoader getClassLoader(ClassLoader parent) {
        int cnt = _elements.size();
        URL[] urls = new URL[cnt];
        for (int i=0; i < cnt; i++) {
            try {
                urls[i] = ((File)(_elements.elementAt(i))).toURI().toURL();
            } catch (MalformedURLException e) {}
        }
        
        if (parent == null)
        	parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) {
            parent = Classpath.class.getClassLoader();
        }
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }
        return new URLClassLoader(urls, parent);
    }
}
