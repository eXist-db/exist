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
 * an ant task to list users.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class ListUsersTask extends UserTask
{
    private String outputproperty = null;
    private String separator      = ",";

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    
    /**
     * Executes the task to list all user accounts in the database.
     * 
     * <p>This method retrieves a list of all existing user accounts and 
     * formats them into a single string, separating them using the specified 
     * separator. If users exist, the formatted list is stored as a project property.</p>
     * 
     * <p>If an XMLDB-related error occurs, the method will either throw an 
     * exception or log the error based on the {@code failonerror} setting.</p>
     * 
     * @throws BuildException if an XMLDB-related error occurs.
     */
    public void execute() throws BuildException
    {
        // Call the parent class's execute method to ensure proper setup.
        super.execute();

        try {
            log("Listing all users", Project.MSG_DEBUG);

            // Retrieve the list of user accounts from the service.
            final Account[] users = service.getAccounts();

            // Check if any users were returned.
            if (users != null) {

                boolean isFirst = true; // Used to control the separator placement.
                final StringBuilder buffer = new StringBuilder();

                // Iterate over the retrieved user accounts.
                for (final Account user : users) {

                    // Append a separator only after the first user.
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        buffer.append(separator);
                    }

                    // Append the user's name to the buffer.
                    buffer.append(user.getName());
                }

                // If the buffer contains any data, store it as a project property.
                if (buffer.length() > 0) {
                    log("Setting output property " + outputproperty + " to " + buffer.toString(), Project.MSG_DEBUG);
                    getProject().setNewProperty(outputproperty, buffer.toString());
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
     * @param outputproperty set for this object
     */
    public void setOutputproperty(final String outputproperty )
    {
        this.outputproperty = outputproperty;
    }


    /**
     * @param separator set for this object
     */
    public void setSeparator(final String separator )
    {
        this.separator = separator;
    }
}
