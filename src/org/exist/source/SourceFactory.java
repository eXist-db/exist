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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;

/**
 * Factory to create a {@link org.exist.source.Source} object for a given
 * URL.
 * 
 * @author wolf
 */
public class SourceFactory {

    private final static Logger LOG = LogManager.getLogger(SourceFactory.class);

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
    public static final Source getSource(DBBroker broker, String contextPath, String location, boolean checkXQEncoding) throws IOException, PermissionDeniedException {
        Source source = null;

        /* resource: */
        if(location.startsWith(ClassLoaderSource.PROTOCOL)) {
            source = new ClassLoaderSource(location);
        } else if(contextPath != null && contextPath.startsWith(ClassLoaderSource.PROTOCOL)) {
            // Pretend it is a file on the local system so we can resolve it easily with URL() class.
            final String conPathNoProtocol = contextPath.replace(ClassLoaderSource.PROTOCOL, "file://");
            String resolvedURL = new URL(new URL(conPathNoProtocol), location).toString();
            resolvedURL = resolvedURL.replaceFirst("file://", ClassLoaderSource.PROTOCOL);
            source = new ClassLoaderSource(resolvedURL);
        }
        /* file:// or location without scheme is assumed to be a file */
        else if(location.startsWith("file:") || !location.contains(":"))
        {
            location = location.replaceAll("^(file:)?/*(.*)$", "$2");

            final Path p = Paths.get(contextPath, location);
            if(Files.isReadable(p)) {
                location = p.toUri().toASCIIString();
                source = new FileSource(p, checkXQEncoding);
            }
            
            final Path p2 = Paths.get(location);
            if(Files.isReadable(p2)){
                location = p2.toUri().toASCIIString();
                source = new FileSource(p2, checkXQEncoding);
            }

            final Path p3 = Paths.get(contextPath).toAbsolutePath().resolve(location);
            if(Files.isReadable(p3)){
                location = p3.toUri().toASCIIString();
                source = new FileSource(p3, checkXQEncoding);
            }

            /*
             * Try to load as an absolute path
             */
            final Path p4 = Paths.get("/" + location);
            if(Files.isReadable(p4)){
                location = p4.toUri().toASCIIString();
                source = new FileSource(p4, checkXQEncoding);
            }

            /*
             * Try to load from the folder of the contextPath
             */

            final Path p5 = Paths.get(contextPath).resolveSibling(location);
            if(Files.isReadable(p5)) {
                location = p5.toUri().toASCIIString();
                source = new FileSource(p5, checkXQEncoding);
            }

            /*
             * Try to load from the folder of the contextPath URL
             */
            final Path p6 = Paths.get(contextPath.replaceFirst("^file:/*(/.*)$", "$1")).resolveSibling(location);
            if(Files.isReadable(p6)) {
                location = p6.toUri().toASCIIString();
                //f6 = new File(contextPath.substring(0, contextPath.lastIndexOf('/')) + location);
                source = new FileSource(p6, checkXQEncoding);
            }

            /*
             * Lastly we try to load it using EXIST_HOME as the reference point
             */
            Path p7 = null;
            try {
				p7 = FileUtils.resolve(BrokerPool.getInstance().getConfiguration().getExistHome(), location);
				if(Files.isReadable(p7)){
				    location = p7.toUri().toASCIIString();
				    source = new FileSource(p7, checkXQEncoding);
				}
			} catch (final EXistException e) {
				LOG.warn(e);
			}
            
            if(source == null) {
                	throw new FileNotFoundException(
                            "cannot read module source from file at " + location 
                            + ". \nTried " + p.toAbsolutePath() + "\n"
                            + " and " + p2.toAbsolutePath() + "\n"
                            + " and " + p3.toAbsolutePath() + "\n"
                            + " and " + p4.toAbsolutePath() + "\n"
                            + " and " + p5.toAbsolutePath() + "\n"
                            + " and " + p6.toAbsolutePath() + "\n"
                            + " and " + p7.toAbsolutePath());
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
