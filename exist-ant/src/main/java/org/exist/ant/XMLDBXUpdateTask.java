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
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;


/**
 * An ant task to update a collection or resource using XUpdate.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBXUpdateTask extends AbstractXMLDBTask
{
    private String resource = null;
    private String commands = null;

    /**
     * Executes the XUpdate command to update an XML resource or an entire collection in the XMLDB.
     * <p>
     * This method checks for a valid collection URI and performs the update on a specified resource
     * or the entire collection, depending on the input parameters. It logs messages at different levels 
     * depending on the success or failure of the operation.
     *
     * @throws BuildException If there is an error during execution, such as invalid URI, resource not found,
     *                        or XMLDB exceptions.
     */
    @Override
    public void execute() throws BuildException {
        // Check if URI is provided for the XMLDB collection
        if( uri == null ) {
            // Throw an exception if the URI is not specified
            throw( new BuildException( "You have to specify an XMLDB collection URI" ) );
        }

        // Log the XUpdate commands being executed at the DEBUG level
        log( "XUpdate command is: " + commands, Project.MSG_DEBUG );

        // Register the database (authentication, setup, etc.)
        registerDatabase();

        try {
            // Log the URI of the collection being accessed at the DEBUG level
            log( "Get base collection: " + uri, Project.MSG_DEBUG );

            // Get the base collection from the database using the provided URI, user, and password
            final Collection base = DatabaseManager.getCollection( uri, user, password );

            // Check if the collection could not be found
            if( base == null ) {
                // If failonerror flag is set, throw an exception
                final String msg = "Collection " + uri + " could not be found.";
                if( failonerror ) {
                    throw( new BuildException( msg ) );
                } else {
                    // Otherwise, log an error message
                    log( msg, Project.MSG_ERR );
                }

            } else {
                // Get the XUpdate query service for the collection
                final XUpdateQueryService service = base.getService( XUpdateQueryService.class);

                // Check if a specific resource is provided for updating
                if( resource != null ) {
                    log( "Updating resource: " + resource, Project.MSG_INFO );

                    // Get the resource from the collection
                    final Resource res = base.getResource( resource );

                    // Check if the resource could not be found
                    if( res == null ) {
                        // If failonerror flag is set, throw an exception
                        final String msg = "Resource " + resource + " not found.";
                        if( failonerror ) {
                            throw( new BuildException( msg ) );
                        } else {
                            // Otherwise, log an error message
                            log( msg, Project.MSG_ERR );
                        }
                    } else {
                        // Perform the XUpdate operation on the resource
                        service.updateResource( resource, commands );
                    }

                } else {
                    // If no specific resource is provided, update the entire collection
                    log( "Updating collection: " + base.getName(), Project.MSG_INFO );
                    service.update( commands );
                }
            }

        // Handle XMLDB exceptions that might occur during the XUpdate process
        } catch( final XMLDBException e ) {
            // Log the exception message at the error level
            final String msg = "XMLDB exception during XUpdate: " + e.getMessage();
            
            // If failonerror flag is set, throw an exception
            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                // Otherwise, log the error message and exception
                log( msg, e, Project.MSG_ERR );
            }
        }
    }


    /**
     * Set the resource.
     *
     * @param resource the resource.
     */
    public void setResource(final String resource )
    {
        this.resource = resource;
    }

    /**
     * Set the commands.
     *
     * @param commands the commands to be set.
     */
    public void setCommands(final String commands )
    {
        this.commands = commands;
    }
}
