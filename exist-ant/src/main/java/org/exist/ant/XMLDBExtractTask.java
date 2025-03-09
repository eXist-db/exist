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


    @Override
    public void execute() throws BuildException {
        if (uri == null) {

            if (failonerror) {
                throw (new BuildException("You need to specify an XMLDB collection URI"));
            }

        } else {
            registerDatabase();

            try {
                final Collection base = DatabaseManager.getCollection(uri, user, password);

                if (base == null) {
                    throw (new BuildException("Collection " + uri + " could not be found."));
                }

                if ((resource != null) && (destDir == null)) {

                    // extraction of a single resource
                    log("Extracting resource: " + resource + " to " + destFile.toAbsolutePath(), Project.MSG_INFO);
                    final Resource res = base.getResource(resource);

                    if (res == null) {
                        final String msg = "Resource " + resource + " not found.";

                        if (failonerror) {
                            throw (new BuildException(msg));
                        } else {
                            log(msg, Project.MSG_ERR);
                        }
                    } else {
                        writeResource(res, destFile);
                    }

                } else {

                    // extraction of a collection
                    extractResources(base, null);

                    if (subcollections) {
                        extractSubCollections(base, null);
                    }
                }

            } catch (final XMLDBException e) {
                final String msg = "XMLDB exception caught while executing query: " + e.getMessage();

                if (failonerror) {
                    throw (new BuildException(msg, e));
                } else {
                    log(msg, e, Project.MSG_ERR);
                }

            } catch (final IOException e) {
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

            log("Extracting to directory " + destDir.toAbsolutePath(), Project.MSG_DEBUG);

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
                log("Writing resource " + res.getId() + " to destination " + dest.toAbsolutePath(), Project.MSG_DEBUG);
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

    private Writer getWriter(XMLResource res, Path dest) throws XMLDBException, IOException {
        final Writer writer;
        if (Files.isDirectory(dest)) {
            final Path file = dest.resolve(res.getId());
            writer = Files.newBufferedWriter(file, UTF_8);

        } else {
            writer = Files.newBufferedWriter(destFile, UTF_8);
        }
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

    public void setResource(final String resource) {
        this.resource = resource;
    }

    public void setDestFile(final File destFile) {
        this.destFile = destFile.toPath();
    }

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

    public void setCreatedirectories(final boolean createdirectories) {
        this.createdirectories = createdirectories;
    }

    public void setSubcollections(final boolean subcollections) {
        this.subcollections = subcollections;
    }

    public void setOverwrite(final boolean overwrite) {
        this.overwrite = overwrite;
    }
}
