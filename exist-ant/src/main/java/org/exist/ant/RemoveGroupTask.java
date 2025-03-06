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
import org.exist.security.Group;
import org.xmldb.api.base.XMLDBException;


/**
 * Created by IntelliJ IDEA. User: lcahlander Date: Aug 25, 2010 Time: 3:09:13 PM To change this template use File | Settings | File Templates.
 */
public class RemoveGroupTask extends UserTask
{
    private String name = null;
    
    /**
     * @see org.apache.tools.ant.Task#execute()
     * 
     * Executes the task to remove a group from the database.
     * 
     * <p>This method first checks if a group name is provided. If not, 
     * it throws an exception. It then attempts to retrieve the group 
     * from the database. If the group exists, it is removed; otherwise, 
     * a message is logged indicating that the group does not exist.</p>
     * 
     * <p>If an XMLDB-related error occurs, the method either logs the 
     * error or throws an exception based on the {@code failonerror} setting.</p>
     * 
     * @throws BuildException if the group name is not specified or an XMLDB-related error occurs.
     */
    public void execute() throws BuildException
    {
        // Call the parent class's execute method to ensure proper initialization.
        super.execute();

        // Ensure a group name is specified before proceeding.
        if (name == null) {
            throw new BuildException("You have to specify a name");
        }

        // Log the operation to indicate the removal attempt.
        log("Removing group " + name, Project.MSG_INFO);

        try {
            // Retrieve the group from the database.
            final Group group = service.getGroup(name);

            // If the group exists, remove it; otherwise, log that it does not exist.
            if (group != null) {
                service.removeGroup(group);
            } else {
                log("Group " + name + " does not exist.", Project.MSG_INFO);
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
