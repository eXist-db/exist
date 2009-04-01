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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;

/**
 * Factory to create a {@link org.exist.source.Source} object for a given
 * URL.
 * 
 * @author wolf
 */
public class SourceFactory {

    /**
     * Create a {@link Source} object for the given URL.
     * 
     * As a special case, if the URL starts with "resource:", the resource
     * will be read from the current context class loader.
     * 
     * @param broker broker, can be null if not asking for a database resource
     * @param contextPath
     * @param location
     * @throws MalformedURLException
     * @throws IOException
     */
    public static final Source getSource(DBBroker broker, String contextPath, String location, boolean checkXQEncoding) throws MalformedURLException, IOException, PermissionDeniedException
    {
        Source source = null;
        
        /* file:// or location without scheme is assumed to be a file */
        if(location.startsWith("file:") || location.indexOf(':') == Constants.STRING_NOT_FOUND)
        {
            location = location.replaceAll("^(file:)?/*(.*)$", "$2");

            File f = new File(contextPath + File.separatorChar + location);
            if(!f.canRead())
            {
                File f2 = new File(location);
                if(!f2.canRead()){
                    throw new FileNotFoundException(
                        "cannot read module source from file at " + location 
                        + ". Tried " + f.getAbsolutePath() 
                        + " and " + f2.getAbsolutePath() );
                }
                else {
                	f = f2;
                }
            }
            location = f.toURI().toASCIIString();
            source = new FileSource(f, "UTF-8", checkXQEncoding);
        }
        
        /* xmldb: */
        else if(location.startsWith(XmldbURI.XMLDB_URI_PREFIX))
        {
        	DocumentImpl resource = null;
        	try
        	{
				XmldbURI pathUri = XmldbURI.create(location);
				resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
				source = new DBSource(broker, (BinaryDocument)resource, true);
        	}
			finally
			{
				//TODO: this is nasty!!! as we are unlocking the resource whilst there
				//is still a source
				if(resource != null)
					resource.getUpdateLock().release(Lock.READ_LOCK);
			}
        }
        
        /* resource: */
        else if(location.startsWith(ClassLoaderSource.PROTOCOL))
        {
            source = new ClassLoaderSource(location);
        }

        /* any other URL */
        else
        {
            URL url = new URL(location);
            source = new URLSource(url);
        }

        return source;
    }
}
