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

import org.exist.EXistException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
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

            final File f = new File(contextPath + File.separatorChar + location);
            if(f.canRead())
            {

                location = f.toURI().toASCIIString();
                source = new FileSource(f, "UTF-8", checkXQEncoding);
            }
            
            final File f2 = new File(location);
            if(f2.canRead()){
                location = f2.toURI().toASCIIString();
                source = new FileSource(f2, "UTF-8", checkXQEncoding);
            }

            final File f3 = new File(new File(contextPath).getAbsolutePath(), location);
            if(f3.canRead()){
                location = f3.toURI().toASCIIString();
                source = new FileSource(f3, "UTF-8", checkXQEncoding);
            }

            /*
             * Try to load as an absolute path
             */
            final File f4 = new File("/" + location);
            if(f4.canRead()){
                location = f4.toURI().toASCIIString();
                source = new FileSource(f4, "UTF-8", checkXQEncoding);
            }

            /*
             * Lastly we try to load it using EXIST_HOME as the reference point
             */
            File f5 = null;
            try {
				f5 = new File(new File(BrokerPool.getInstance().getConfiguration().getExistHome().getCanonicalPath()), location);
				if(f5.canRead()){
				    location = f5.toURI().toASCIIString();
				    source = new FileSource(f5, "UTF-8", checkXQEncoding);
				}
			} catch (final EXistException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            if(source == null){
            	
                	throw new FileNotFoundException(
                            "cannot read module source from file at " + location 
                            + ". \nTried " + f.getAbsolutePath() + "\n"
                            + " and " + f2.getAbsolutePath() + "\n" 
                            + " and " + f3.getAbsolutePath() + "\n" 
                            + " and " + f4.getAbsolutePath() + "\n" 
                            + " and " + f5.getAbsolutePath() 
                            
                			);
            }
        }
        
        /* xmldb: */
        else if(location.startsWith(XmldbURI.XMLDB_URI_PREFIX))
        {
        	DocumentImpl resource = null;
        	try
        	{
				final XmldbURI pathUri = XmldbURI.create(location);
				resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
				if (resource != null)
					{source = new DBSource(broker, (BinaryDocument)resource, true);}
        	}
			finally
			{
				//TODO: this is nasty!!! as we are unlocking the resource whilst there
				//is still a source
				if(resource != null)
					{resource.getUpdateLock().release(Lock.READ_LOCK);}
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
            final URL url = new URL(location);
            source = new URLSource(url);
        }

        return source;
    }
}
