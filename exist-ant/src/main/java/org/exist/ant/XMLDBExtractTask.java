/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.ExtendedResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.util.Properties;

import javax.xml.transform.OutputKeys;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * an ant task to extract the content of a collection or resource.
 *
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 * @author jim.fuller at webcomposite.com to handle binary file extraction
 */
public class XMLDBExtractTask extends AbstractXMLDBTask {
    private String resource = null;
    private File destFile = null;
    private File destDir = null;
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
                    log("Extracting resource: " + resource + " to " + destFile.getAbsolutePath(), Project.MSG_INFO);
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
        final String[] resources = base.listResources();
        if (resources != null) {
            File dir = destDir;

            log("Extracting to directory " + destDir.getAbsolutePath(), Project.MSG_DEBUG);

            if (path != null) {
                dir = new File(destDir, path);
            }

            for (final String resource : resources) {
                final Resource res = base.getResource(resource);
                log("Extracting resource: " + res.getId(), Project.MSG_DEBUG);

                if (!dir.exists() && createdirectories) {
                    dir.mkdirs();
                }

                if (dir.exists()) {
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
        final String[] childCols = base.listChildCollections();

        if (childCols != null) {
            for (final String childCol : childCols) {
                final Collection col = base.getChildCollection(childCol);

                if (col != null) {
                    log("Extracting collection: " + col.getName(), Project.MSG_DEBUG);
                    File dir = destDir;
                    String subdir;

                    if (path != null) {
                        dir = new File(destDir, path + File.separator + childCol);
                        subdir = path + File.separator + childCol;
                    } else {
                        subdir = childCol;
                    }

                    if (!dir.exists() && createdirectories) {
                        dir.mkdirs();
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
    private void writeResource(final Resource res, final File dest) throws XMLDBException, IOException {
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
    private void writeXMLResource(final XMLResource res, final File dest) throws IOException, XMLDBException {
        if (createdirectories) {
            final File parentDir = new File(dest.getParent());
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
        }

        if (dest != null || overwrite) {
            final Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.INDENT, "yes");
            final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            final Writer writer;

            if (dest.isDirectory()) {
                String fname = res.getId();
                final File file = new File(dest, fname);
                writer = new OutputStreamWriter(new FileOutputStream(file), UTF_8);
            } else {
                writer = new OutputStreamWriter(new FileOutputStream(dest), UTF_8);
            }

            log("Writing resource " + res.getId() + " to destination " + dest.getAbsolutePath(), Project.MSG_DEBUG);
            serializer.setOutput(writer, outputProperties);
            res.getContentAsSAX(serializer);
            SerializerPool.getInstance().returnObject(serializer);
            writer.close();

        } else {
            final String msg = "Destination xml file " + ((dest != null) ? (dest.getAbsolutePath() + " ") : "") + "exists. Use " + "overwrite property to overwrite this file.";

            if (failonerror) {
                throw (new BuildException(msg));
            } else {
                log(msg, Project.MSG_ERR);
            }
        }
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
    private void writeBinaryResource(final Resource res, File dest) throws XMLDBException, IOException {
        if (createdirectories == true) {
            final File parentDir = new File(dest.getParent());
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
        }

        //dest != null && ( !dest.exists() ||
        if (dest != null || overwrite == true) {
            if (dest.isDirectory()) {
                final String fname = res.getId();
                dest = new File(dest, fname);
            }
            FileOutputStream os;
            os = new FileOutputStream(dest);

            ((ExtendedResource) res).getContentIntoAStream(os);


        } else {
            final String msg = "Dest binary file " + ((dest != null) ? (dest.getAbsolutePath() + " ") : "") + "exists. Use " + "overwrite property to overwrite file.";

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
        this.destFile = destFile;
    }

    public void setDestDir(final File destDir) {
        this.destDir = destDir;
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
