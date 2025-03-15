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
import org.apache.tools.ant.taskdefs.condition.Condition;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;


/**
 * An ant task to check for the existence of a collection or resource to be used as a ant condition.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBExistTask extends AbstractXMLDBTask implements Condition
{
    private String resource = null;

    /**
     * Evaluates whether a specified XMLDB collection and optional resource exist.
     * This method checks if the collection URI is valid and if a specific resource
     * exists within the collection.
     *
     * @return true if the collection and resource (if specified) exist, false otherwise.
     * @throws BuildException If there is an error during execution.
     */
    @Override
    public boolean eval() throws BuildException
    {
        // Initialize the variable to track if the collection/resource exists
        boolean exist = false;

        // Check if the URI for the XMLDB collection is specified
        if (uri == null) {
            throw (new BuildException("You have to specify an XMLDB collection URI"));
        }

        // Register the database connection before attempting to retrieve the collection
        registerDatabase();

        try {
            // Log the process of checking the collection at the specified URI
            log("Checking collection: " + uri, Project.MSG_INFO);

            // Try to get the base collection from the database using the URI, user, and password
            final Collection base = DatabaseManager.getCollection(uri, user, password);

            // Check if the base collection exists
            if (base != null) {
                log("Base collection found", Project.MSG_DEBUG);
                exist = true; // Set exist to true if the collection is found
            }

            // If the collection exists and a resource is specified, check if the resource exists
            if ((base != null) && (resource != null)) {
                log("Checking resource: " + resource, Project.MSG_INFO);
                final Resource res = base.getResource(resource);

                // If the resource is not found, set exist to false
                if (res == null) {
                    log("Resource not found", Project.MSG_DEBUG);
                    exist = false;
                }
            }

        }
        catch (final XMLDBException e) {
            // If an XMLDB exception occurs, log the issue and set exist to false
            log("Resource or collection cannot be retrieved", Project.MSG_DEBUG);
            exist = false;
        }

        // Return true if both the collection and resource (if specified) exist, otherwise false
        return (exist);
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
}
