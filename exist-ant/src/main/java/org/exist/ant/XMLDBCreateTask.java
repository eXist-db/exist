/*
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
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.net.URISyntaxException;


/**
 * An ant task to create a empty collection.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBCreateTask extends AbstractXMLDBTask
{
    private String collection = null;

    @Override
    public void execute() throws BuildException
    {
        if( uri == null ) {
            throw( new BuildException( "you have to specify an XMLDB collection URI" ) );
        }

        registerDatabase();

        try {
            log( "Get base collection: " + uri, Project.MSG_DEBUG );
            final Collection base = DatabaseManager.getCollection( uri, user, password );

            if( base == null ) {
                final String msg = "Collection " + uri + " could not be found.";

                if( failonerror ) {
                    throw( new BuildException( msg ) );
                } else {
                    log( msg, Project.MSG_ERR );
                }

            } else {
                Collection root = null;

                if( collection != null ) {
                    log( "Creating collection " + collection + " in base collection " + uri, Project.MSG_DEBUG );
                    root = mkcol( base, uri, collection );
                } else {
                    root = base;
                }
                
                if( permissions != null ) {
                	setPermissions( root );
                }
                
                log( "Created collection " + root.getName(), Project.MSG_INFO );
            }

        }
        catch( final XMLDBException e ) {
            final String msg = "XMLDB exception caught: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }

        }
        catch( final URISyntaxException e ) {
            final String msg = "URISyntaxException: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }


    /**
     * Set the Collection.
     *
     * @param collection the collection.
     */
    public void setCollection(final String collection )
    {
        this.collection = collection;
    }


    private Collection mkcol(final Collection root, final String base, /*String path,*/  final String relPath ) throws XMLDBException, URISyntaxException
    {
        CollectionManagementService mgtService;
        Collection                  current  = root;
        Collection                  c;
        XmldbURI                    baseUri  = XmldbURI.xmldbUriFor( base );
        final XmldbURI                    collPath = XmldbURI.xmldbUriFor( relPath );
        log( "BASEURI=" + baseUri, Project.MSG_DEBUG );
        log( "RELPATH=" + relPath, Project.MSG_DEBUG );

        //log("PATH=" + path, Project.MSG_DEBUG);

        final XmldbURI[] segments = collPath.getPathSegments();

        for( final XmldbURI segment : segments ) {
            baseUri = baseUri.append( segment );

            log( "Get collection " + baseUri, Project.MSG_DEBUG );
            c = DatabaseManager.getCollection( baseUri.toString(), user, password );

            if( c == null ) {
                log( "Create collection management service for collection " + current.getName(), Project.MSG_DEBUG );
                mgtService = current.getService( CollectionManagementService.class);
                log( "Create child collection " + segment );
                current = mgtService.createCollection( segment.toString() );
                log( "Created collection " + current.getName() + '.' );

            } else {
                current = c;
            }
        }
        return( current );
    }
}
