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

    /**
     * Executes the task to remove either a resource or a collection from the specified XMLDB collection.
     * It checks for necessary parameters, logs actions, and handles exceptions appropriately.
     */
    @Override
    public void execute() throws BuildException
    {
        // Ensure the URI for the XMLDB collection is specified
        if (uri == null) {
            throw new BuildException("You have to specify an XMLDB collection URI");
        }

        // Ensure either a resource or collection is specified
        if ((resource == null) && (collection == null)) {
            throw new BuildException("Missing parameter: either resource or collection should be specified");
        }

        // Register the database before proceeding
        registerDatabase();

        try {
            // Log the attempt to get the base collection
            log("Get base collection: " + uri, Project.MSG_DEBUG);
            
            // Retrieve the base collection from the database
            final Collection base = DatabaseManager.getCollection(uri, user, password);

            // Check if the base collection was successfully retrieved
            if (base == null) {
                throw new BuildException("Collection " + uri + " could not be found.");
            }

            // If a resource is specified, remove the resource
            if (resource != null) {
                log("Removing resource: " + resource, Project.MSG_INFO);
                final Resource res = base.getResource(resource);

                // Check if the resource exists
                if (res == null) {
                    final String msg = "Resource " + resource + " not found.";

                    // Handle error based on failonerror flag
                    if (failonerror) {
                        throw new BuildException(msg);
                    } else {
                        log(msg, Project.MSG_ERR);
                    }
                } else {
                    // Remove the resource
                    base.removeResource(res);
                }

            } else {
                // If a collection is specified, remove the collection
                log("Removing collection: " + collection, Project.MSG_INFO);
                final CollectionManagementService service = base.getService(CollectionManagementService.class);
                service.removeCollection(collection);
            }

        } catch (final XMLDBException e) {
            // Handle XMLDB exceptions that occur during the removal process
            final String msg = "XMLDB exception during remove: " + e.getMessage();

            // Handle error based on failonerror flag
            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }


    /**
     * Set the collection.
     *
     * @param collection the collection
     */
    public void setCollection(final String collection )
    {
        this.collection = collection;
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
