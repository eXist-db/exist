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
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.net.URISyntaxException;


/**
 * An ant task to create a empty collection.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBCreateTask extends AbstractXMLDBTask
{
    private String collection = null;

    /**
     * Executes the task of creating or managing an XMLDB collection
     */
    @Override
    public void execute() throws BuildException
    {
        // Check if the URI for the XMLDB collection is specified
        if (uri == null) {
            throw (new BuildException("You have to specify an XMLDB collection URI"));
        }

        // Register the database before proceeding
        registerDatabase();

        try {
            // Log the attempt to fetch the base collection
            log("Get base collection: " + uri, Project.MSG_DEBUG);
            
            // Try to get the base collection using the provided URI, user, and password
            final Collection base = DatabaseManager.getCollection(uri, user, password);

            // Check if the base collection was successfully retrieved
            if (base == null) {
                // If not, log an error or throw an exception based on failonerror flag
                final String msg = "Collection " + uri + " could not be found.";
                if (failonerror) {
                    throw (new BuildException(msg));
                } else {
                    log(msg, Project.MSG_ERR);
                }

            } else {
                Collection root = null;

                // If a specific collection is provided, attempt to create it in the base collection
                if (collection != null) {
                    log("Creating collection " + collection + " in base collection " + uri, Project.MSG_DEBUG);
                    root = mkcol(base, uri, collection);
                } else {
                    // If no specific collection is provided, use the base collection
                    root = base;
                }

                // If permissions are specified, apply them to the created collection
                if (permissions != null) {
                    setPermissions(root);
                }

                // Log the creation of the collection
                log("Created collection " + root.getName(), Project.MSG_INFO);
            }

        } catch (final XMLDBException e) {
            // Handle any XMLDB exceptions that occur during execution
            final String msg = "XMLDB exception caught: " + e.getMessage();
            if (failonerror) {
                throw (new BuildException(msg, e));
            } else {
                log(msg, e, Project.MSG_ERR);
            }

        } catch (final URISyntaxException e) {
            // Handle any URI syntax exceptions that occur
            final String msg = "URISyntaxException: " + e.getMessage();
            if (failonerror) {
                throw (new BuildException(msg, e));
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }



    /**
     * Set the Collection.
     *
     * @param collection the collection.
     */
    public void setCollection(final String collection )
    {
        this.collection = collection;
    }


    /**
     * Creates or retrieves a collection by traversing through the path segments.
     * It checks if each segment exists and creates it if necessary.
     *
     * @param root The root collection from where the traversal starts.
     * @param base The base URI of the collection where the new collection or path starts.
     * @param relPath The relative path of the collection to be created or retrieved.
     * @return The final collection object after traversing all the segments.
     * @throws XMLDBException If there is an error in the XMLDB operations.
     * @throws URISyntaxException If there is an error with the URI syntax.
     */
    private Collection mkcol(final Collection root, final String base, /*String path,*/ final String relPath ) throws XMLDBException, URISyntaxException
    {
        // Initialize the collection management service and set up the current collection as the root
        CollectionManagementService mgtService;
        Collection current = root;
        Collection c;

        // Convert the base and relative paths to XmldbURI for collection traversal
        XmldbURI baseUri = XmldbURI.xmldbUriFor(base);
        final XmldbURI collPath = XmldbURI.xmldbUriFor(relPath);

        // Log the base URI and the relative path for debugging purposes
        log("BASEURI=" + baseUri, Project.MSG_DEBUG);
        log("RELPATH=" + relPath, Project.MSG_DEBUG);

        // Convert the relative path into path segments for traversal
        final XmldbURI[] segments = collPath.getPathSegments();

        // Iterate over the segments of the path to either create or get existing collections
        for (final XmldbURI segment : segments) {
            // Append each segment to the base URI to form the complete path for the current collection
            baseUri = baseUri.append(segment);

            // Log the attempt to get the collection at the current URI
            log("Get collection " + baseUri, Project.MSG_DEBUG);
            c = DatabaseManager.getCollection(baseUri.toString(), user, password);

            // If the collection does not exist, create it
            if (c == null) {
                // Log and create the collection management service for the current collection
                log("Create collection management service for collection " + current.getName(), Project.MSG_DEBUG);
                mgtService = current.getService(CollectionManagementService.class);
                log("Create child collection " + segment);

                // Create the child collection and set it as the current collection
                current = mgtService.createCollection(segment.toString());
                log("Created collection " + current.getName() + '.');
            } else {
                // If the collection exists, set it as the current collection
                current = c;
            }
        }

        // Return the final collection after traversing through all the path segments
        return (current);
    }
}