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
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.ExtendedResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.xml.transform.OutputKeys;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * an ant task to extract the content of a collection or resource.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 * @author jim.fuller at webcomposite.com to handle binary file extraction
 */
public class XMLDBExtractTask extends AbstractXMLDBTask {
    private String resource = null;
    private Path destFile = null;
    private Path destDir = null;
    private boolean createdirectories = false;
    private boolean subcollections = false;
    private boolean overwrite = false;


    /**
     * Executes the task of extracting a resource or a collection from an XMLDB collection.
     * This method checks the provided collection URI and resource, and either extracts
     * a single resource or an entire collection based on the parameters.
     *
     * @throws BuildException If there is an error during the execution, such as a missing collection or resource.
     */
    
    @Override
    public void execute() throws BuildException {
        // Check if the URI for the XMLDB collection is specified
        if (uri == null) {
            // If the URI is missing and failonerror flag is true, throw an exception
            if (failonerror) {
                throw (new BuildException("You need to specify an XMLDB collection URI"));
            }
        } else {
            // Register the database connection before proceeding
            registerDatabase();

            try {
                // Attempt to retrieve the base collection from the database using the URI, user, and password
                final Collection base = DatabaseManager.getCollection(uri, user, password);

                // Check if the collection exists
                if (base == null) {
                    throw (new BuildException("Collection " + uri + " could not be found."));
                }

                // If a resource is specified and destination directory is not specified, extract the resource
                if ((resource != null) && (destDir == null)) {
                    log("Extracting resource: " + resource + " to " + destFile.toAbsolutePath().toString(), Project.MSG_INFO);

                    // Try to get the resource from the collection
                    final Resource res = base.getResource(resource);

                    // If the resource is not found, handle it accordingly
                    if (res == null) {
                        final String msg = "Resource " + resource + " not found.";
                        if (failonerror) {
                            throw (new BuildException(msg));
                        } else {
                            log(msg, Project.MSG_ERR);
                        }
                    } else {
                        // Write the resource to the destination file
                        writeResource(res, destFile);
                    }

                } else {
                    // Otherwise, extract the entire collection
                    extractResources(base, null);

                    // If subcollections are specified, extract them as well
                    if (subcollections) {
                        extractSubCollections(base, null);
                    }
                }

            } catch (final XMLDBException e) {
                // Handle XMLDB exceptions (e.g., errors during collection/resource retrieval)
                final String msg = "XMLDB exception caught while executing query: " + e.getMessage();
                if (failonerror) {
                    throw (new BuildException(msg, e));
                } else {
                    log(msg, e, Project.MSG_ERR);
                }

            } catch (final IOException e) {
                // Handle IO exceptions (e.g., issues writing the destination file)
                final String msg = "XMLDB exception caught while writing destination file: " + e.getMessage();
                if (failonerror) {
                    throw (new BuildException(msg, e));
                } else {
                    log(msg, e, Project.MSG_ERR);
                }
            }
        }
    }

    /**
     * Create directory from a collection.
     *
     * @param base the collection
     * @param path the path
     *
     * @throws XMLDBException if a database error occurs
     * @throws IOException if an I/O error occurs
     */
    private void extractResources(final Collection base, final String path) throws XMLDBException, IOException {
        final List<String> resources = base.listResources();
        if (!resources.isEmpty()) {
            Path dir = destDir;

            log("Extracting to directory " + destDir.toAbsolutePath().toString(), Project.MSG_DEBUG);

            if (path != null) {
                dir =  destDir.resolve(path);
            }

            for (final String resource : resources) {
                final Resource res = base.getResource(resource);
                log("Extracting resource: " + res.getId(), Project.MSG_DEBUG);

                if (Files.notExists(dir) && createdirectories) {
                    Files.createDirectories(dir);
                }

                if (Files.exists(dir)) {
                    writeResource(res, dir);
                }

            }
        }
    }

    /**
     * Extract multiple resources from a collection.
     *
     * @param base the collection
     * @param path the path
     *
     * @throws XMLDBException if a database error occurs
     * @throws IOException if an I/O error occurs
     */
    private void extractSubCollections(final Collection base, final String path) throws XMLDBException, IOException {
        final List<String> childCols = base.listChildCollections();
        if (!childCols.isEmpty()) {
            for (final String childCol : childCols) {
                final Collection col = base.getChildCollection(childCol);

                if (col != null) {
                    log("Extracting collection: " + col.getName(), Project.MSG_DEBUG);
                    Path dir = destDir;
                    final String subdir;

                    if (path != null) {
                        dir = destDir.resolve(path).resolve(childCol);
                        subdir = path + File.separator + childCol;
                    } else {
                        subdir = childCol;
                    }

                    if (Files.notExists(dir) && createdirectories) {
                        Files.createDirectories(dir);
                    }

                    extractResources(col, subdir);

                    if (subcollections) {
                        extractSubCollections(col, subdir);
                    }
                }
            }
        }
    }

    /**
     * Extract single resource.
     *
     * @param res the resource
     * @param dest the destination file.
     *
     * @throws XMLDBException if a database error occurs
     * @throws IOException if an I/O error occurs
     */
    private void writeResource(final Resource res, final Path dest) throws XMLDBException, IOException {
        if (res instanceof XMLResource) {
            writeXMLResource((XMLResource) res, dest);
        } else if (res instanceof ExtendedResource) {
            writeBinaryResource(res, dest);
        }
    }

    /**
     * Extract XML resource.
     *
     * @param res the resource
     * @param dest the destination file
     *
     * @throws XMLDBException if a database error occurs
     * @throws IOException if an I/O error occurs
     */
    private void writeXMLResource(final XMLResource res, final Path dest) throws IOException, XMLDBException {
        if (createdirectories) {
            final Path parentDir = dest.getParent();
            if (Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }
        }

        if (dest != null || overwrite) {
            final Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.INDENT, "yes");
            final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            try (final Writer writer = getWriter(res, dest)) {
                log("Writing resource " + res.getId() + " to destination " + dest.toAbsolutePath().toString(), Project.MSG_DEBUG);
                serializer.setOutput(writer, outputProperties);
                res.getContentAsSAX(serializer);
            } finally {
                SerializerPool.getInstance().returnObject(serializer);
            }

        } else {
            final String msg = "Destination xml file " + ((dest != null) ? (dest.toAbsolutePath().toString() + " ") : "") + "exists. Use " + "overwrite property to overwrite this file.";

            if (failonerror) {
                throw (new BuildException(msg));
            } else {
                log(msg, Project.MSG_ERR);
            }
        }
    }

    /**
     * Retrieves a Writer to write the content of an XMLResource to a destination.
     * If the destination is a directory, the content will be written to a file
     * named after the resource's ID. Otherwise, the content will be written
     * directly to the specified destination file.
     *
     * @param res The XMLResource to write to the destination.
     * @param dest The destination path (either a file or a directory).
     * @return A Writer that can be used to write the resource content.
     * @throws XMLDBException If there is an error with the XMLDB resource.
     * @throws IOException If there is an error while creating or writing to the file.
     */
    private Writer getWriter(XMLResource res, Path dest) throws XMLDBException, IOException {
        final Writer writer;

        // Check if the destination is a directory
        if (Files.isDirectory(dest)) {
            // If it is a directory, create a new file inside the directory using the resource's ID as the file name
            final Path file = dest.resolve(res.getId());
            // Create a BufferedWriter for the new file using UTF-8 encoding
            writer = Files.newBufferedWriter(file, UTF_8);

        } else {
            // If it's not a directory, use the destination file directly
            // Create a BufferedWriter for the destination file
            writer = Files.newBufferedWriter(destFile, UTF_8);
        }
        
        // Return the writer that can be used to write the resource's content
        return writer;
    }


    /**
     * Extract single binary resource.
     *
     * @param res the resource
     * @param dest the destination file
     *
     * @throws XMLDBException if a database error occurs
     * @throws IOException if an I/O error occurs
     */
    private void writeBinaryResource(final Resource res, Path dest) throws XMLDBException, IOException {
        if (createdirectories) {
            final Path parentDir = dest.getParent();
            if (Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }
        }

        //dest != null && ( !dest.exists() ||
        if (dest != null || overwrite) {
            if (Files.isDirectory(dest)) {
                final String fname = res.getId();
                dest = dest.resolve(fname);
            }

            try(final OutputStream os = new BufferedOutputStream(Files.newOutputStream(dest))) {
                ((ExtendedResource) res).getContentIntoAStream(os);
            }

        } else {
            final String msg = "Dest binary file " + ((dest != null) ? (dest.toAbsolutePath().toString() + " ") : "") + "exists. Use " + "overwrite property to overwrite file.";

            if (failonerror) {
                throw (new BuildException(msg));
            } else {
                log(msg, Project.MSG_ERR);
            }
        }
    }

    /**
     * Sets the resource for this object.
     * 
     * @param resource The resource to set for this object
     */
    public void setResource(final String resource) {
        this.resource = resource;
    }


    /**
     * Set the path for the destination file
     * 
     * @param destFile set the destination file
     */
    public void setDestFile(final File destFile) {
        this.destFile = destFile.toPath();
    }

    /**
     * Set the path for the destination directory
     * 
     * @param destDir set the destination directory
     */
    public void setDestDir(final File destDir) {
        this.destDir = destDir.toPath();
    }


    /**
     * Set the type.
     *
     * @param type the type
     *
     * @deprecated Not used anymore
     */
    @Deprecated
    public void setType(final String type) {
    }

    /**
     * Set if there will be created directories or not
     * 
     * @param createdirectories True to create it, false otherwise
     */
    public void setCreatedirectories(final boolean createdirectories) {
        this.createdirectories = createdirectories;
    }

    /**
     * Set if there will be created subcollections or not
     * 
     * @param subcollections True to create it, false otherwise
     */
    public void setSubcollections(final boolean subcollections) {
        this.subcollections = subcollections;
    }
    
    /**
     * Set if there will be overwtited files or not
     * 
     * @param overwrite True to create it, false otherwise
     */
    public void setOverwrite(final boolean overwrite) {
        this.overwrite = overwrite;
    }
}
