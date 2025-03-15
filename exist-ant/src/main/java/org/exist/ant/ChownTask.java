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
import org.exist.security.Account;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;


/**
 * an ant task to change permissions on a resource.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class ChownTask extends UserTask
{
    private String name     = null;
    private String group    = null;
    private String resource = null;
    
    /**
     * @see org.apache.tools.ant.Task#execute()
     * 
     * Executes the task to change the ownership of a resource or collection. 
     * 
     * <p>This method ensures that both a user and a group are specified. If not, it throws an exception. 
     * Then, it retrieves the user account from the service and applies the ownership change.</p>
     * 
     * <p>If a specific resource is provided, ownership is changed for that resource. Otherwise, 
     * ownership is changed for the entire collection.</p>
     * 
     * <p>Any XMLDB-related errors are handled based on the {@code failonerror} setting.</p>
     * 
     * @throws BuildException if the user or group is not specified, or if an XMLDB-related error occurs.
     */
    public void execute() throws BuildException
    {
        // Call the parent class's execute method to ensure proper initialization.
        super.execute();

        // Ensure that both a user and a group are specified.
        if( ( name == null ) || ( group == null ) ) {
            throw new BuildException( "Must specify user and group" );
        }

        try {
            // Retrieve the user account from the service.
            final Account usr = service.getAccount( name );

            // If a resource is specified, apply ownership change to that resource.
            if( resource != null ) {
                final Resource res = base.getResource( resource );
                service.chown( res, usr, group );
            } 
            // Otherwise, apply ownership change at the collection level.
            else {
                service.chown( usr, group );
            }

        } catch( final XMLDBException e ) {
            // Capture any XMLDB-related exceptions and format the error message.
            final String msg = "XMLDB exception caught: " + e.getMessage();

            // If failonerror is set, throw an exception. Otherwise, log the error and continue.
            if( failonerror ) {
                throw new BuildException( msg, e );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }



    /**
     * @param name sets name for this object
     */
    public void setName(final String user )
    {
        this.name = user;
    }


    /**
     * @param resource set for this object
     */
    public void setResource(final String resource )
    {
        this.resource = resource;
    }


    /**
     * @param group set for this object
     */
    public void setGroup(final String group )
    {
        this.group = group;
    }
}
