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
import org.xmldb.api.base.XMLDBException;


/**
 * an ant task to remove a name.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class RemoveUserTask extends UserTask
{
    private String name = null;
    
    /**
     * @see org.apache.tools.ant.Task#execute()
     * 
     * Executes the task to remove a user account from the database.
     * 
     * <p>This method first checks if a username is provided; if not, 
     * it throws an exception. It then attempts to retrieve the user account 
     * from the database. If the account exists, it is removed.</p>
     * 
     * <p>If an XMLDB-related error occurs, the method either logs the 
     * error or throws an exception based on the {@code failonerror} setting.</p>
     * 
     * @throws BuildException if the username is not specified or if an XMLDB-related error occurs.
     */
    public void execute() throws BuildException
    {
        // Call the superclass execute method to ensure proper initialization.
        super.execute();

        // Ensure a username is specified before proceeding.
        if (name == null) {
            throw new BuildException("You have to specify a name");
        }

        try {
            // Retrieve the user account from the database.
            final Account u = service.getAccount(name);

            // If the user exists, remove the account.
            if (u != null) {
                service.removeAccount(u);
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
     * @param name set for this object
     */
    public void setName(final String name )
    {
        this.name = name;
    }
}
