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
import org.exist.source.*;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.XMLResource;

import javax.xml.transform.OutputKeys;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Ant task to execute an XQuery.
 *
 * The query is either passed as nested text in the element, or via an attribute "query" or via a URL or via a query file. External variables
 * declared in the XQuery can be set via one or more nested &lt;variable&gt; elements.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBXQueryTask extends AbstractXMLDBTask {
    private String text = null;
    private String queryUri = null;
    private String query = null;
    private File queryFile = null;
    private File destDir = null;
    private String outputproperty;
    private List<Variable> variables = new ArrayList<>();

    /**
     * Executes an XML query against an XMLDB collection and handles the results.
     * The query can be specified in one of several ways: as an attribute, text, URI, or file.
     * Results are either written to a specified directory or stored as a project property.
     * @throws BuildException If there is a failure in retrieving the collection, executing the query, or writing the results, and {@code failonerror} is set to {@code true}.
     * @throws XMLDBException If an error occurs while interacting with the XMLDB collection or executing the query.
     * @throws IOException If an error occurs while writing the results to the destination file or directory.
     */
    @Override
    public void execute() throws BuildException {
        // Check if URI is provided
        if (uri == null) {
            throw new BuildException("you have to specify an XMLDB collection URI");
        }

        // If 'text' is provided, process it and replace any project properties
        if (text != null) {
            final PropertyHelper helper = PropertyHelper.getPropertyHelper(getProject());
            query = helper.replaceProperties(null, text, null);
        }

        // Ensure that a valid query is provided
        if (queryFile == null && query == null && queryUri == null) {
            throw new BuildException("you have to specify a query either as attribute, text, URI or in a file");
        }

        // Register the database (authentication and other steps)
        registerDatabase();

        try {
            // Attempt to get the base collection from the database
            log("Get base collection: " + uri, Project.MSG_DEBUG);
            final Collection base = DatabaseManager.getCollection(uri, user, password);

            // If the collection is not found, log or throw an error
            if (base == null) {
                final String msg = "Collection " + uri + " could not be found.";
                if (failonerror) {
                    throw new BuildException(msg);
                } else {
                    log(msg, Project.MSG_ERR);
                }
            } else {
                // Get the EXistXQueryService for querying
                final EXistXQueryService service = base.getService(EXistXQueryService.class);
                service.setProperty(OutputKeys.INDENT, "yes");
                service.setProperty(OutputKeys.ENCODING, UTF_8.name());

                // Declare any variables for the XQuery
                for (final Variable var : variables) {
                    service.declareVariable(var.name, var.value);
                }

                // Determine the source of the query (from URI, file, or string)
                final Source source;
                if (queryUri != null) {
                    log("XQuery url " + queryUri, Project.MSG_DEBUG);
                    if (queryUri.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
                        final Resource resource = base.getResource(queryUri);
                        source = new BinarySource((byte[]) resource.getContent(), true);
                    } else {
                        source = new URLSource(new URL(queryUri));
                    }
                } else if (queryFile != null) {
                    log("XQuery file " + queryFile.getAbsolutePath(), Project.MSG_DEBUG);
                    source = new FileSource(queryFile.toPath(), true);
                } else {
                    log("XQuery string: " + query, Project.MSG_DEBUG);
                    source = new StringSource(query);
                }

                // Execute the query and get the results
                final ResourceSet results = service.execute(source);
                log("Found " + results.getSize() + " results", Project.MSG_INFO);

                // If a destination directory is specified, write the results there
                if ((destDir != null) && (results != null)) {
                    log("write results to directory " + destDir.getAbsolutePath(), Project.MSG_INFO);
                    final ResourceIterator iter = results.getIterator();
                    while (iter.hasMoreResources()) {
                        final XMLResource res = (XMLResource) iter.nextResource();
                        log("Writing resource " + res.getId(), Project.MSG_DEBUG);
                        writeResource(res, destDir);
                    }

                // Otherwise, if an output property is specified, store the results as a property
                } else if (outputproperty != null) {
                    final ResourceIterator iter = results.getIterator();
                    String result = null;
                    while (iter.hasMoreResources()) {
                        final XMLResource res = (XMLResource) iter.nextResource();
                        result = res.getContent().toString();
                    }
                    getProject().setNewProperty(outputproperty, result);
                }
            }

        } catch (final XMLDBException e) {
            final String msg = "XMLDB exception caught while executing query: " + e.getMessage();
            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        } catch (final IOException e) {
            final String msg = "XMLDB exception caught while writing destination file: " + e.getMessage();
            if (failonerror) {
                throw new BuildException(msg, e);
            } else {
                log(msg, e, Project.MSG_ERR);
            }
        }
    }

    /**
     * Writes the content of an XML resource to a specified destination file or directory.
     * The resource is serialized using the {@link SAXSerializer} and written to the destination.
     * <p>
     * The method handles the serialization of the XML resource and writes it to the destination,
     * creating a file in the destination directory if needed. If the destination does not exist, 
     * it logs an error or throws a {@link BuildException}, depending on the {@code failonerror} flag.
     *
     * @param resource The XML resource whose content is to be written.
     * @param dest The destination file or directory where the resource content will be saved.
     * @throws IOException If an error occurs while writing the resource content to the destination.
     * @throws XMLDBException If an error occurs while accessing the XML resource content.
     */
    private void writeResource(final XMLResource resource, final File dest) throws IOException, XMLDBException {
        // Check if the destination file or directory is not null
        if (dest != null) {
            
            // Create properties to define how the content will be serialized
            final Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.INDENT, "yes");  // Enabling indentation for readability

            // Borrow a SAXSerializer from the SerializerPool to handle the XML serialization
            final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            try (final Writer writer = getWriter(resource, dest)) {
                // Set the output writer and properties for the serializer
                serializer.setOutput(writer, outputProperties);
                
                // Get the content of the XML resource and serialize it to the output writer
                resource.getContentAsSAX(serializer);
            } finally {
                // Return the serializer to the pool after the operation is complete
                SerializerPool.getInstance().returnObject(serializer);
            }

        } else {
            // If destination is null, log an error or throw an exception
            final String msg = "Destination target does not exist.";
            
            // Check if errors should fail the build or just log them
            if (failonerror) {
                throw new BuildException(msg);  // Throw an exception if failonerror is true
            } else {
                log(msg, Project.MSG_ERR);  // Log an error if failonerror is false
            }
        }
    }


    /**
     * Gets a writer to write the content of an XML resource to the specified destination file or directory.
     * <p>
     * If the destination is a directory, the method creates the directory (if it doesn't exist) and
     * generates a file name based on the resource ID. If the destination is a file, the writer is created
     * directly for that file.
     *
     * @param resource The XML resource whose content is to be written.
     * @param dest The destination file or directory where the resource content will be written.
     * @return A {@link Writer} object to write the content of the resource to the destination.
     * @throws XMLDBException If there is an issue accessing the resource content.
     * @throws IOException If an error occurs while creating the writer or writing to the destination file.
     */
    private Writer getWriter(XMLResource resource, File dest) throws XMLDBException, IOException {
        final Writer writer;

        // Check if the destination is a directory
        if (dest.isDirectory()) {
            
            // If the directory doesn't exist, create it
            if (!dest.exists()) {
                dest.mkdirs();
            }

            // Get the resource ID and append ".xml" if the file name doesn't end with it
            String fname = resource.getId();
            if (!fname.endsWith(".xml")) {
                fname += ".xml";
            }

            // Create a path to the file within the destination directory
            final Path file = dest.toPath().resolve(fname);
            
            // Create a buffered writer for the file in UTF-8 encoding
            writer = Files.newBufferedWriter(file, UTF_8);

        } else {
            // If the destination is a file, create a buffered writer directly for that file
            writer = Files.newBufferedWriter(dest.toPath(), UTF_8);
        }

        // Return the writer to be used for writing content
        return writer;
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
     * Set query.
     *
     * @param query the query to be set.
     */
    public void setQuery(final String query) {
        this.query = query;
    }

    /**
     * Set a query file.
     *
     * @param queryFile the file to be set.
     */
    public void setQueryFile(final File queryFile) {
        this.queryFile = queryFile;
    }

    /**
    * Set a query URI.
    *
    * @param queryUri the URI to be set.
    */
    public void setQueryUri(final String queryUri) {
        this.queryUri = queryUri;
    }

    /**
     * Set a destination directory.
     *
     * @param destDir the destination to be set.
     */
    public void setDestDir(final File destDir) {
        this.destDir = destDir;
    }

    /**
     * Add a variable.
     *
     * @param variable the variable to be added.
     */
    public void addVariable(final Variable variable) {
        variables.add(variable);
    }

    /**
     * Set an output property.
     *
     * @param outputproperty the property to be set.
     */
    public void setOutputproperty(final String outputproperty) {
        this.outputproperty = outputproperty;
    }

    /**
     * Defines a nested element to set an XQuery variable.
     */
    public static class Variable {
        private String name = null;
        private String value = null;

        public Variable() {
        }

        public void setName(final String name) {
            this.name = name;
        }


        public void setValue(final String value) {
            this.value = value;
        }
    }
}
