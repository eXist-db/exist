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
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import java.net.URISyntaxException;


/**
 * an ant task to copy a collection or resource.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBCopyTask extends AbstractXMLDBTask
{
    private String resource    = null;
    private String collection  = null;
    private String destination = null;
    private String name        = null;

    @Override
    public void execute() throws BuildException
    {
        if( uri == null ) {
            throw( new BuildException( "You have to specify an XMLDB collection URI" ) );
        }

        if( ( resource == null ) && ( collection == null ) ) {
            throw( new BuildException( "Missing parameter: either resource or collection should be specified" ) );
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
                log( "Create collection management service for collection " + base.getName(), Project.MSG_DEBUG );
                final EXistCollectionManagementService service = base.getService(EXistCollectionManagementService.class);

                if( resource != null ) {
                    log( "Copying resource: " + resource, Project.MSG_INFO );
                    final Resource res = base.getResource( resource );

                    if( res == null ) {
                        final String msg = "Resource " + resource + " not found.";

                        if( failonerror ) {
                            throw( new BuildException( msg ) );
                        } else {
                            log( msg, Project.MSG_ERR );
                        }
                    } else {

                        //XmldbURI resource = XmldbURI.create(resource);
                        service.copyResource( XmldbURI.xmldbUriFor( resource ), XmldbURI.xmldbUriFor( destination ), XmldbURI.xmldbUriFor( name ) );
                    }

                } else {
                    log( "Copying collection: " + collection, Project.MSG_INFO );
                    service.copy( XmldbURI.xmldbUriFor( collection ), XmldbURI.xmldbUriFor( destination ), XmldbURI.xmldbUriFor( name ) );
                }
            }
        }
        catch( final XMLDBException e ) {
            final String msg = "XMLDB exception during copy: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }

        }
        catch( final URISyntaxException e ) {
            final String msg = "URI syntax exception: " + e.getMessage();

            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }


    /**
     * Set the collection.
     *
     * @param collection the collection.
     */
    public void setCollection(final String collection )
    {
        this.collection = collection;
    }


    /**
     * Set the resource.
     *
     * @param resource the resource
     */
    public void setResource(final String resource )
    {
        this.resource = resource;
    }


    public void setDestination(final String destination )
    {
        this.destination = destination;
    }


    public void setName(final String name )
    {
        this.name = name;
    }
}
