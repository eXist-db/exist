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
import org.xmldb.api.base.XMLDBException;

import java.util.List;


/**
 * an ant task to list the sub-collections or resources in a collection.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBListTask extends AbstractXMLDBTask
{
    private boolean hasCollections = false;
    private boolean hasResources   = false;
    private String  separator      = ",";
    private String  outputproperty;

    /* (non-Javadoc)
     * @see org.apache.tools.ant.Task#execute()
     */
    
    /**
     * Executes the task of listing collections and/or resources in the specified XMLDB collection.
     * 
     * This method first checks if the required collection URI is provided. It also checks if 
     * there are collections or resources to be listed. Then it attempts to retrieve the 
     * specified XMLDB collection and list either the child collections, resources, or both, 
     * depending on the flags set. The results are concatenated into a single string, which 
     * is stored in a project property.
     * 
     * @throws BuildException if any error occurs during execution, including issues with
     *         the URI, database connection, or XMLDB operations.
     */
    public void execute() throws BuildException {
        // Check if the URI for the XMLDB collection is specified
        if (uri == null) {
            throw new BuildException("You have to specify an XMLDB collection URI");
        }

        // Ensure that at least one of the flags (collections or resources) is set to true
        if (!hasCollections && !hasResources) {
            throw new BuildException("You have at least one of collections or resources or both");
        }

        // Register the database before proceeding
        registerDatabase();

        try {
            // Log the attempt to fetch the base collection
            log("Get base collection: " + uri, Project.MSG_DEBUG);
            
            // Try to get the base collection using the provided URI, user, and password
            final Collection base = DatabaseManager.getCollection(uri, user, password);

            // If the collection is not found, log an error or throw an exception
            if (base == null) {
                final String msg = "Collection " + uri + " could not be found.";

                if (failonerror) {
                    throw new BuildException(msg);
                } else {
                    log(msg, Project.MSG_ERR);
                    return;
                }
            }

            // Initialize a StringBuilder to store the results
            final StringBuilder buffer = new StringBuilder();

            // If the hasCollections flag is true, list child collections
            if (hasCollections) {
                final List<String> childCollections = base.listChildCollections();
                if (!childCollections.isEmpty()) {
                    log("Listing child collections", Project.MSG_DEBUG);
                    boolean isFirst = true;

                    // Iterate through child collections and append them to the buffer
                    for (final String col : childCollections) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            buffer.append(separator); // Add separator for subsequent items
                        }
                        buffer.append(col);
                    }
                }
            }

            // If the hasResources flag is true, list resources in the collection
            if (hasResources) {
                log("Listing resources", Project.MSG_DEBUG);
                final List<String> resources = base.listResources();
                if (!resources.isEmpty()) {
                    if (buffer.length() > 0) {
                        buffer.append(separator); // Add separator if collections were listed
                    }

                    boolean isFirst = true;

                    // Iterate through resources and append them to the buffer
                    for (final String resource : resources) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            buffer.append(separator); // Add separator for subsequent items
                        }
                        buffer.append(resource);
                    }
                }
            }

            // If any collections or resources were found, set the property with the result
            if (buffer.length() > 0) {
                log("Set property " + outputproperty, Project.MSG_INFO);
                getProject().setNewProperty(outputproperty, buffer.toString());
            }

        } catch (final XMLDBException e) {
            // Handle XMLDB exceptions that occur during collection or resource listing
            final String msg = "XMLDB exception during list: " + e.getMessage();

            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }



    /**
     * Sets whether to list child collections in the specified XMLDB collection.
     *
     * @param collections true to list collections, false otherwise.
     */
    public void setCollections(final boolean collections) {
        this.hasCollections = collections;
    }

    /**
     * Sets the resources
     * 
     * @param resources true to set resources, false overwise
     */
    public void setResources(final boolean resources )
    {
        this.hasResources = resources;
    }

    /**
     * Sets the separator
     * 
     * @param separator to be set
     */
    public void setSeparator(final String separator )
    {
        this.separator = separator;
    }

    /**
     * Set the output property
     * 
     * @param outputproperty to be set
     */
    public void setOutputproperty(final String outputproperty )
    {
        this.outputproperty = outputproperty;
    }
}
