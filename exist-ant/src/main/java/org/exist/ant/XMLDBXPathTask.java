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
import org.apache.tools.ant.PropertyHelper;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import javax.xml.transform.OutputKeys;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * an ant task to execute an query using XPath.
 *
 * The query is either passed as nested text in the element, or via an attribute "query".
 *
 *
 * @author wolf modified by:
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBXPathTask extends AbstractXMLDBTask {
    private String resource = null;
    private String namespace = null;
    private String query = null;
    private String text = null;

    // count mode
    private boolean count = false;
    private File destDir = null;
    private String outputproperty;

    /**
     * Executes the task to query an XMLDB collection using an XPath query.
     * It connects to the specified XMLDB collection, runs the query, and handles the results 
     * based on the configuration (output to directory or a property).
     * 
     * The method supports querying either a specific resource within the collection or the entire collection.
     * The results can be written to a destination directory or returned as a string property in the project.
     * 
     * @throws BuildException If an error occurs during the execution, including connection issues, query execution,
     *                        or file writing problems.
     */
    @Override
    public void execute() throws BuildException {
        // Ensure the collection URI is specified
        if (uri == null) {
            throw new BuildException("you have to specify an XMLDB collection URI");
        }

        // If the text property is specified, resolve any project properties
        if (text != null) {
            final PropertyHelper helper = PropertyHelper.getPropertyHelper(getProject());
            query = helper.replaceProperties(null, text, null);
        }

        // Ensure the query is specified
        if (query == null) {
            throw new BuildException("you have to specify a query");
        }

        log("XPath is: " + query, org.apache.tools.ant.Project.MSG_DEBUG);

        // Register database connection
        registerDatabase();

        try {
            log("Get base collection: " + uri, Project.MSG_DEBUG);
            
            // Connect to the specified XMLDB collection
            final Collection base = DatabaseManager.getCollection(uri, user, password);

            // If the collection is not found, handle the error
            if (base == null) {
                final String msg = "Collection " + uri + " could not be found.";

                if (failonerror) {
                    throw new BuildException(msg);
                } else {
                    log(msg, Project.MSG_ERR);
                }

            } else {
                // Get the XPath query service from the collection
                final XPathQueryService service = base.getService(XPathQueryService.class);

                // Set properties for pretty-printing and encoding
                service.setProperty(OutputKeys.INDENT, "yes");
                service.setProperty(OutputKeys.ENCODING, UTF_8.name());

                // If a namespace is specified, set it for the XPath service
                if (namespace != null) {
                    log("Using namespace: " + namespace, Project.MSG_DEBUG);
                    service.setNamespace("ns", namespace);
                }

                // Execute the query (either on a specific resource or the entire collection)
                final ResourceSet results;
                if (resource != null) {
                    log("Query resource: " + resource, Project.MSG_DEBUG);
                    results = service.queryResource(resource, query);
                } else {
                    log("Query collection", Project.MSG_DEBUG);
                    results = service.query(query);
                }

                // Log the number of results found
                log("Found " + results.getSize() + " results", Project.MSG_INFO);

                // If a destination directory is specified, write the results to it
                if ((destDir != null) && (results != null)) {
                    log("write results to directory " + destDir.getAbsolutePath(), Project.MSG_INFO);
                    final ResourceIterator iter = results.getIterator();

                    log("Writing results to directory " + destDir.getAbsolutePath(), Project.MSG_DEBUG);

                    // Write each resource to the specified directory
                    while (iter.hasMoreResources()) {
                        final XMLResource res = (XMLResource) iter.nextResource();
                        log("Writing resource " + res.getId(), Project.MSG_DEBUG);
                        writeResource(res, destDir);
                    }

                // If output property is specified, store results in it
                } else if (outputproperty != null) {
                    // If 'count' flag is set, store the number of results
                    if (count) {
                        getProject().setNewProperty(outputproperty, String.valueOf(results.getSize()));
                    } else {
                        final ResourceIterator iter = results.getIterator();
                        final StringBuilder result = new StringBuilder();

                        // Append the content of each resource to the result string
                        while (iter.hasMoreResources()) {
                            final XMLResource res = (XMLResource) iter.nextResource();
                            result.append(res.getContent().toString());
                            result.append("\n");
                        }
                        // Set the result as the property value
                        getProject().setNewProperty(outputproperty, result.toString());
                    }
                }
            }
        } catch (final XMLDBException e) {
            // Handle errors during XMLDB operations
            final String msg = "XMLDB exception caught while executing query: " + e.getMessage();

            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }

        } catch (final IOException e) {
            // Handle errors while writing destination files
            final String msg = "XMLDB exception caught while writing destination file: " + e.getMessage();

            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }

    /**
     * Writes the content of an XMLResource to a specified destination file.
     * The resource is serialized using SAX and written to the destination file with proper formatting.
     *
     * @param resource The XMLResource to be written.
     * @param dest The destination file where the resource content will be saved.
     * @throws IOException If an error occurs while writing to the destination file.
     * @throws XMLDBException If an error occurs while handling the XMLDB resource.
     */
    private void writeResource(final XMLResource resource, final File dest) throws IOException, XMLDBException {
        // Check if the destination file is specified
        if (dest != null) {
            // Set up output properties for indentation (pretty-printing)
            final Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.INDENT, "yes");

            // Borrow a SAX serializer from the pool to serialize the content
            final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            try (final Writer writer = getWriter(resource, dest)) {
                // Set the output writer and properties on the serializer
                serializer.setOutput(writer, outputProperties);

                // Serialize the resource content as SAX events
                resource.getContentAsSAX(serializer);
            } finally {
                // Return the serializer to the pool after use
                SerializerPool.getInstance().returnObject(serializer);
            }

        } else {
            // If destination is not specified, log an error or throw an exception based on failonerror flag
            final String msg = "Destination target does not exist.";

            if (failonerror) {
                throw new BuildException(msg);
            } else {
                log(msg, Project.MSG_ERR);
            }
        }
    }


    /**
     * Retrieves a Writer to write the content of an XML resource to a specified destination file.
     * If the destination is a directory, it creates the file with the resource's ID as the filename.
     * If the destination is a file, it directly returns a Writer for that file.
     *
     * @param resource The XML resource whose content is to be written.
     * @param dest The destination file or directory where the resource content will be written.
     * @return A Writer object to write the resource content.
     * @throws XMLDBException If there is an issue accessing or handling the XMLDB resource.
     * @throws IOException If an error occurs while creating or accessing the destination file or directory.
     */
    private Writer getWriter(XMLResource resource, File dest) throws XMLDBException, IOException {
        final Writer writer;
        // Check if the destination is a directory
        if (dest.isDirectory()) {
            // If the directory doesn't exist, create it
            if (!dest.exists()) {
                dest.mkdirs();
            }

            // Generate the filename from the resource's ID
            String fname = resource.getId();
            // Ensure the filename ends with ".xml"
            if (!fname.endsWith(".xml")) {
                fname += ".xml";
            }

            // Resolve the filename to the full path in the destination directory
            final Path file = dest.toPath().resolve(fname);
            // Create a buffered writer to write to the file
            writer = Files.newBufferedWriter(file, UTF_8);

        } else {
            // If the destination is a file, directly create a buffered writer for it
            writer = Files.newBufferedWriter(dest.toPath(), UTF_8);
        }
        return writer;
    }


    /**
     * Set the query.
     *
     * @param query the query.
     */
    public void setQuery(final String query) {
        this.query = query;
    }

    /**
     * Add text.
     *
     * @param text the text to be added.
     */
    public void addText(final String text) {
        this.text = text;
    }

    /**
     * Set the resource.
     *
     * @param resource the resource to be set.
     */
    public void setResource(final String resource) {
        this.resource = resource;
    }

    /**
     * Set the namespace.
     *
     * @param namespace the namespace to be set.
     */
    public void setNamespace(final String namespace) {
        this.namespace = namespace;
    }

    /**
     * Set the destinatation directory
     *
     * @param destDir the directory to be set.
     */
    public void setDestDir(final File destDir) {
        this.destDir = destDir;
    }

    /**
     * Set the output property.
     *
     * @param outputproperty the property to be set.
     */
    public void setOutputproperty(final String outputproperty) {
        this.outputproperty = outputproperty;
    }
    
    /**
     * Set a count.
     *
     * @param count true to be set, false otherwise.
     */
    public void setCount(final boolean count) {
        this.count = count;
    }
}
