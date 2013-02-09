/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
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

    public final static String PROTOCOL = "resource:";
    
    /**
     * @param source The resource name (e.g. url).
     *
     * <p> The name of a resource is a '<tt>/</tt>'-separated path name that
     * identifies the resource. Preceding "/" and "resource:"" are removed.
     */
    public ClassLoaderSource(String source) throws IOException {
        if(source.startsWith(PROTOCOL))
            {source = source.substring(PROTOCOL.length());}
        if(source.startsWith("/"))
            {source = source.substring(1);}
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final URL url = cl.getResource(source);
		if(url == null)
            {throw new IOException("Source not found: " + source);}
        setURL(url);
    }
}