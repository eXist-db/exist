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
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;


/**
 * an ant task to change permissions on a resource.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 * @author  andrzej@chaeron.com
 */
public class ChmodTask extends UserTask
{
    private String resource = null;
    private String mode     = null;

    /**
     * @see org.apache.tools.ant.Task#execute()
     * 
     * Executes the task to set permissions on a specified resource. 
     * 
     * <p>This method checks the provided permissions and mode. If permissions are not provided, 
     * it will fall back to using the mode. Then it retrieves the specified resource 
     * and applies the permissions using the {@link #setPermissions(Resource, UserManagementService)} method.</p>
     * 
     * <p>If any error occurs, such as missing permissions or XMLDB exceptions, the method 
     * will either throw an exception or log the error based on the {@code failonerror} setting.</p>
     * 
     * @throws BuildException if permissions or mode are not specified, or if an XMLDB-related error occurs.
     */
    @Override
    public void execute() throws BuildException
    {
        // Initialize the resource object to null.
        Resource res = null;
        
        // Call the superclass's execute method to ensure any base class functionality is executed first.
        super.execute();
        
        // Check if permissions are not provided.
        if( permissions == null ) {
            // If mode is also not provided, throw an exception.
            if( mode == null ) {
                throw new BuildException( "You must specify permissions" );
            } else {
                // Otherwise, use the mode as the permissions value.
                permissions = mode;
            }
        }

        try {
            // If a resource is specified, attempt to retrieve it from the base collection.
            if( resource != null ) {
                res = base.getResource( resource );
            }
            
            // Set the permissions on the retrieved resource using the service.
            setPermissions( res, service );
        } catch( final XMLDBException e ) {
            // Handle any XMLDB exceptions that may occur.
            final String msg = "XMLDB exception caught: " + e.getMessage();
            
            // If failonerror is set to true, throw an exception to indicate failure.
            if( failonerror ) {
                throw new BuildException( msg, e );
            } else {
                // Otherwise, log the error and continue.
                log( msg, e, Project.MSG_ERR );
            }
        }
    }



    /**
     * @param resource to be set to this object.
     */
    public void setResource(final String resource )
    {
        this.resource = resource;
    }
    
    
    /**
     * This method sets a mode for this object and sets a message warning
     *
     * @param mode to be set to this object.
     */
    public void setMode(final String mode )
    {
        this.mode = mode;
        
        log( "WARNING: mode attribute is deprecated, please use new permissions attribute instead!", Project.MSG_WARN );
    }

}
