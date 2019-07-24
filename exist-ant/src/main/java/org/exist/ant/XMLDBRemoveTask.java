/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;


/**
 * an ant task to remove a collection or resource.
 *
 * @author  wolf
 *
 *          modified by
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBRemoveTask extends AbstractXMLDBTask
{
    private String resource   = null;
    private String collection = null;

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
                throw( new BuildException( "Collection " + uri + " could not be found." ) );
            }

            if( resource != null ) {
                log( "Removing resource: " + resource, Project.MSG_INFO );
                final Resource res = base.getResource( resource );

                if( res == null ) {
                    final String msg = "Resource " + resource + " not found.";

                    if( failonerror ) {
                        throw( new BuildException( msg ) );
                    } else {
                        log( msg, Project.MSG_ERR );
                    }
                } else {
                    base.removeResource( res );
                }

            } else {
                log( "Removing collection: " + collection, Project.MSG_INFO );
                final CollectionManagementService service = (CollectionManagementService)base.getService( "CollectionManagementService", "1.0" );
                service.removeCollection( collection );
            }

        }
        catch( final XMLDBException e ) {
            final String msg = "XMLDB exception during remove: " + e.getMessage();

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
     * @param collection the collection
     */
    public void setCollection( String collection )
    {
        this.collection = collection;
    }


    /**
     * Set the resource.
     *
     * @param resource the resource.
     */
    public void setResource( String resource )
    {
        this.resource = resource;
    }
}
