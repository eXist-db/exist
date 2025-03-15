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
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;


/**
 * abstract base class for all user-related tasks.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 * @author  andrzej@chaeron.com
 */
public abstract class UserTask extends AbstractXMLDBTask
{
    protected UserManagementService service = null;
    protected Collection            base    = null;

    /**
     * @see org.apache.tools.ant.Task#execute()
     * 
     * Executes the task to retrieve and initialize the base collection from the XMLDB system.
     * 
     * <p>This method checks if the URI for the XMLDB collection is provided. It then attempts 
     * to fetch the base collection from the database and initialize the {@code UserManagementService}.</p>
     * 
     * <p>If an error occurs, it either logs the error or throws an exception based on the 
     * {@code failonerror} flag.</p>
     * 
     * @throws BuildException if the URI is missing or any error occurs during execution.
     */
    @Override
    public void execute() throws BuildException
    {
        // Ensure that the URI is provided for the XMLDB collection
        if (uri == null) {
            throw new BuildException("You have to specify an XMLDB collection URI");
        }

        // Register the database (perform necessary setup)
        registerDatabase();

        try {
            log("Get base collection: " + uri, Project.MSG_DEBUG);
            
            // Attempt to retrieve the base collection
            base = DatabaseManager.getCollection(uri, user, password);

            if (base == null) {
                // If the collection could not be found, log an error or throw an exception
                final String msg = "Collection " + uri + " could not be found.";

                if (failonerror) {
                    throw new BuildException(msg);
                } else {
                    log(msg, Project.MSG_ERR);
                }
            } else {
                // Initialize the UserManagementService
                service = base.getService(UserManagementService.class);
            }

        } catch (final XMLDBException e) {
            // Handle any XMLDB exceptions
            final String msg = "XMLDB exception caught: " + e.getMessage();

            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }
}
