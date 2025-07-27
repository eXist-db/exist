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
import java.net.URI;
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

    @Override
    public void execute() throws BuildException {
        if (uri == null) {
            throw new BuildException("you have to specify an XMLDB collection URI");
        }

        if (text != null) {
            final PropertyHelper helper = PropertyHelper.getPropertyHelper(getProject());
            query = helper.replaceProperties(null, text, null);
        }

        if (queryFile == null && query == null && queryUri == null) {
            throw new BuildException("you have to specify a query either as attribute, text, URI or in a file");
        }

        registerDatabase();

        try {
            log("Get base collection: " + uri, Project.MSG_DEBUG);
            final Collection base = DatabaseManager.getCollection(uri, user, password);

            if (base == null) {
                final String msg = "Collection " + uri + " could not be found.";

                if (failonerror) {
                    throw new BuildException(msg);
                } else {
                    log(msg, Project.MSG_ERR);
                }

            } else {
                final EXistXQueryService service = base.getService(EXistXQueryService.class);

                // set pretty-printing on
                service.setProperty(OutputKeys.INDENT, "yes");
                service.setProperty(OutputKeys.ENCODING, UTF_8.name());

                for (final Variable var : variables) {
                    System.out.println("Name: " + var.name);
                    System.out.println("Value: " + var.value);
                    service.declareVariable(var.name, var.value);
                }

                final Source source;
                if (queryUri != null) {
                    log("XQuery url " + queryUri, Project.MSG_DEBUG);

                    if (queryUri.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
                        final Resource resource = base.getResource(queryUri);
                        source = new BinarySource((byte[]) resource.getContent(), true);
                    } else {
                        source = new URLSource(URI.create(queryUri).toURL());
                    }
                } else if (queryFile != null) {
                    log("XQuery file " + queryFile.getAbsolutePath(), Project.MSG_DEBUG);
                    source = new FileSource(queryFile.toPath(), true);
                } else {
                    log("XQuery string: " + query, Project.MSG_DEBUG);
                    source = new StringSource(query);
                }

                final ResourceSet results = service.execute(source);
                log("Found " + results.getSize() + " results", Project.MSG_INFO);

                if ((destDir != null) && (results != null)) {
                    log("write results to directory " + destDir.getAbsolutePath(), Project.MSG_INFO);
                    final ResourceIterator iter = results.getIterator();

                    log("Writing results to directory " + destDir.getAbsolutePath(), Project.MSG_DEBUG);

                    while (iter.hasMoreResources()) {
                        final XMLResource res = (XMLResource) iter.nextResource();
                        log("Writing resource " + res.getId(), Project.MSG_DEBUG);
                        writeResource(res, destDir);
                    }

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

    private void writeResource(final XMLResource resource, final File dest) throws IOException, XMLDBException {
        if (dest != null) {
            final Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.INDENT, "yes");

            final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            try(final Writer writer = getWriter(resource, dest)) {
                serializer.setOutput(writer, outputProperties);
                resource.getContentAsSAX(serializer);
            } finally {
                SerializerPool.getInstance().returnObject(serializer);
            }

        } else {
            final String msg = "Destination target does not exist.";

            if (failonerror) {
                throw new BuildException(msg);
            } else {
                log(msg, Project.MSG_ERR);
            }
        }
    }

    private Writer getWriter(XMLResource resource, File dest) throws XMLDBException, IOException {
        final Writer writer;
        if (dest.isDirectory()) {

            if (!dest.exists()) {
                dest.mkdirs();
            }

            String fname = resource.getId();
            if (!fname.endsWith(".xml")) {
                fname += ".xml";
            }

            final Path file = dest.toPath().resolve(fname);
            writer = Files.newBufferedWriter(file, UTF_8);

        } else {
            writer = Files.newBufferedWriter(dest.toPath(), UTF_8);
        }
        return writer;
    }


    public void addText(final String text) {
        this.text = text;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public void setQueryFile(final File queryFile) {
        this.queryFile = queryFile;
    }

    public void setQueryUri(final String queryUri) {
        this.queryUri = queryUri;
    }

    public void setDestDir(final File destDir) {
        this.destDir = destDir;
    }

    public void addVariable(final Variable variable) {
        variables.add(variable);
    }

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
