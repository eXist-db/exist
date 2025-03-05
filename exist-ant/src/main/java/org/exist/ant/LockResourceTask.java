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
 * an ant task to lock a resource for a user.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class LockResourceTask extends UserTask
{
    private String name     = null;
    private String resource = null;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    
    /**
     * Executes the task to lock a resource for a specific user.
     * 
     * <p>This method verifies that both the resource name and user name 
     * are provided. It then attempts to retrieve the specified resource 
     * and user from the database. If both are found, it locks the resource 
     * for the user.</p>
     * 
     * <p>If the resource or user is not found, an error is either logged 
     * or thrown as an exception based on the {@code failonerror} setting.</p>
     * 
     * @throws BuildException if required parameters are missing, the resource or user is not found, or an XMLDB-related error occurs.
     */
    public void execute() throws BuildException
    {
        // Call the parent class's execute method to ensure proper setup.
        super.execute();

        // Ensure that both a resource and a user name are specified.
        if ((resource == null) || (name == null)) {
            throw new BuildException("Must specify user and resource name");
        }

        try {
            // Attempt to retrieve the specified resource from the database.
            final Resource res = base.getResource(resource);

            // If the resource does not exist, handle the error accordingly.
            if (res == null) {
                final String msg = "Resource " + resource + " not found";

                if (failonerror) {
                    throw new BuildException(msg);
                } else {
                    log(msg, Project.MSG_ERR);
                }

            } else {
                // Retrieve the user account associated with the provided name.
                final Account usr = service.getAccount(name);

                // If the user is not found, handle the error accordingly.
                if (usr == null) {
                    final String msg = "User " + name + " not found";

                    if (failonerror) {
                        throw new BuildException(msg);
                    } else {
                        log(msg, Project.MSG_ERR);
                    }
                } else {
                    // If both the resource and user exist, lock the resource for the user.
                    service.lockResource(res, usr);
                }
            }

        } catch (final XMLDBException e) {
            // Handle any XMLDB-related exceptions.
            final String msg = "XMLDB exception caught: " + e.getMessage();

            // If failonerror is true, throw an exception; otherwise, log the error.
            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }

    /**
     * @param user set the name for this object
     */
    public void setName(final String user )
    {
        this.name = user;
    }


    /**
     * @param resource set the resource for this object
     */
    public void setResource(final String resource )
    {
        this.resource = resource;
    }
}
