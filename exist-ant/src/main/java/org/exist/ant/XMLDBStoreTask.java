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
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

import java.io.File;
import java.util.ArrayList;


/**
 * An Ant task to store a set of files into eXist.
 *
 * The task expects a nested fileset element. The files selected by the fileset will be stored into the database.
 *
 * New collections can be created as needed. It is also possible to specify that files relative to the base directory should be stored into
 * subcollections of the root collection, where the relative path of the directory corresponds to the relative path of the subcollections.
 *
 * @author wolf
 *
 *         slightly modified by:
 * @author <a href="mailto:peter.klotz@blue-elephant-systems.com">Peter Klotz</a>
 */
public class XMLDBStoreTask extends AbstractXMLDBTask {
    private File mimeTypesFile = null;
    private File srcFile = null;
    private String targetFile = null;
    private ArrayList<FileSet> fileSetList = null;
    private boolean createCollection = false;
    private boolean createSubcollections = false;
    private boolean includeEmptyDirs = true;
    private String type = null;
    private String defaultMimeType = null;
    private String forceMimeType = null;
    private MimeTable mtable = null;

    /**
     * This method is executed when the task is invoked. It interacts with an XMLDB collection,
     * stores files (either individual or from a fileset) into the collection, and handles 
     * error logging and permissions management. 
     * 
     * The method performs various checks, including ensuring a valid URI is provided, 
     * determining if files are available for storage, and processing each file or fileset 
     * accordingly.
     */
    @Override
    public void execute() throws BuildException {
        // Check if the URI for the XMLDB collection is specified, else throw an error
        if (uri == null) {
            throw new BuildException("you have to specify an XMLDB collection URI");
        }

        // Ensure either a file set or a source file is provided, else throw an error
        if ((fileSetList == null) && (srcFile == null)) {
            throw new BuildException("no file set specified");
        }

        // Register the database (e.g., set up authentication or initial setup)
        registerDatabase();

        // Get the position of the ROOT_COLLECTION in the URI string
        int p = uri.indexOf(XmldbURI.ROOT_COLLECTION);
        if (p == Constants.STRING_NOT_FOUND) {
            // If ROOT_COLLECTION is not found, throw an error
            throw new BuildException("invalid uri: '" + uri + "'");
        }

        // Extract the base URI and the remaining path for collection access
        final String baseURI = uri.substring(0, p);
        final String path;
        if (p == (uri.length() - 3)) {  // If URI ends with ROOT_COLLECTION, path is empty
            path = "";
        } else {
            path = uri.substring(p + 3);  // Extract path after ROOT_COLLECTION
        }

        // Initialize collection reference
        Collection root = null;
        try {
            // If createCollection flag is true, create the collection; otherwise, fetch it
            if (createCollection) {
                root = DatabaseManager.getCollection(baseURI + XmldbURI.ROOT_COLLECTION, user, password);
                root = mkcol(root, baseURI, XmldbURI.ROOT_COLLECTION, path);
            } else {
                root = DatabaseManager.getCollection(uri, user, password);
            }
        } catch (final XMLDBException e) {
            // Handle any XMLDB exceptions during collection retrieval
            final String msg = "XMLDB exception caught: " + e.getMessage();
            if (failonerror) {
                throw new BuildException(msg, e);  // Fail if failonerror flag is set
            } else {
                log(msg, e, Project.MSG_ERR);  // Log the error but continue if failonerror is false
                return;
            }
        }

        // If collection could not be found, log the error
        if (root == null) {
            final String msg = "Collection " + uri + " could not be found.";
            if (failonerror) {
                throw new BuildException(msg);
            } else {
                log(msg, Project.MSG_ERR);
            }
        } else {
            // Proceed with file storage logic if collection is valid
            Collection col = root;
            String relDir;
            String prevDir = null;

            // If a source file is provided, store it in the collection
            if (srcFile != null) {
                log("Storing " + srcFile.getName());

                // Determine the MIME type of the file, either from the file or a forced MIME type
                final String baseMimeType;
                if (forceMimeType != null) {
                    baseMimeType = forceMimeType;
                } else {
                    final MimeType fileMime = getMimeTable().getContentTypeFor(srcFile.getName());
                    if (fileMime != null) {
                        baseMimeType = fileMime.getName();
                    } else {
                        baseMimeType = defaultMimeType;
                    }
                }

                // Determine MIME type based on the provided type (xml or binary)
                final MimeType mime;
                if ("xml".equals(type)) {
                    mime = (baseMimeType != null) ? new MimeType(baseMimeType, MimeType.XML) : MimeType.XML_TYPE;
                } else if ("binary".equals(type)) {
                    mime = (baseMimeType != null) ? new MimeType(baseMimeType, MimeType.BINARY) : MimeType.BINARY_TYPE;
                } else {
                    final String msg = "Cannot guess mime-type kind for " + srcFile.getName() + ". Treating it as a binary.";
                    log(msg, Project.MSG_ERR);
                    mime = (baseMimeType != null) ? new MimeType(baseMimeType, MimeType.BINARY) : MimeType.BINARY_TYPE;
                }

                // Set the target file name if not specified
                if (targetFile == null) {
                    targetFile = srcFile.getName();
                }

                try {
                    // Determine the resource type (XML or Binary) and store the file
                    final Class<? extends Resource> resourceType = mime.isXMLType() ? XMLResource.class : BinaryResource.class;
                    log("Creating resource " + targetFile + " in collection " + col.getName() + " of type " + resourceType.getName() + " with mime-type: " + mime.getName(), Project.MSG_DEBUG);
                    try (Resource res = col.createResource(targetFile, resourceType)) {
                        if (srcFile.length() == 0) {
                            // Handle empty file edge case (skip processing)
                        } else {
                            // Set the content and MIME type for the resource
                            res.setContent(srcFile);
                            ((EXistResource) res).setMimeType(mime.getName());
                            col.storeResource(res);  // Store the resource in the collection
                        }

                        // Set permissions for the resource if specified
                        if (permissions != null) {
                            setPermissions(res);
                        }
                    }
                } catch (final XMLDBException e) {
                    // Handle XMLDB exceptions during file storage
                    final String msg = "XMLDB exception caught: " + e.getMessage();
                    if (failonerror) {
                        throw new BuildException(msg, e);
                    } else {
                        log(msg, e, Project.MSG_ERR);
                    }
                }
            } else {
                // If fileset is provided, handle storage of each file in the set
                for (final FileSet fileSet : fileSetList) {
                    log("Storing fileset", Project.MSG_DEBUG);

                    // Use DirectoryScanner to scan the files in the fileset
                    final DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
                    scanner.scan();
                    final String[] includedFiles = scanner.getIncludedFiles();
                    final String[] includedDirs = scanner.getIncludedDirectories();
                    log("Found " + includedDirs.length + " directories and " + includedFiles.length + " files.\n");

                    final File baseDir = scanner.getBasedir();

                    // If creating subcollections and empty directories are included, create subcollections for directories
                    if (includeEmptyDirs && createSubcollections) {
                        for (final String included : includedDirs) {
                            try {
                                log("Creating " + included + " ...\n");

                                // Handle directory path and create subcollections if needed
                                p = included.lastIndexOf(File.separatorChar);
                                if (p != Constants.STRING_NOT_FOUND) {
                                    relDir = included.substring(0, p);
                                    relDir = relDir.replace(File.separatorChar, '/');  // Ensure cross-platform compatibility

                                    // Create subcollections if the relative directory is different from the previous one
                                    if (createSubcollections && ((prevDir == null) || (!relDir.equals(prevDir)))) {
                                        col = mkcol(root, baseURI, XmldbURI.ROOT_COLLECTION + path, relDir);
                                        prevDir = relDir;
                                    }
                                } else {
                                    col = mkcol(root, baseURI, XmldbURI.ROOT_COLLECTION + path, included);
                                }
                            } catch (final XMLDBException e) {
                                final String msg = "XMLDB exception caught: " + e.getMessage();
                                if (failonerror) {
                                    throw new BuildException(msg, e);
                                } else {
                                    log(msg, e, Project.MSG_ERR);
                                }
                            }
                        }
                    }

                    // Store each included file from the fileset
                    for (final String included : includedFiles) {
                        try {
                            final File file = new File(baseDir, included);
                            log("Storing " + included + " ...\n");

                            // Handle file path and create subcollections if necessary
                            p = included.lastIndexOf(File.separatorChar);
                            if (p != Constants.STRING_NOT_FOUND) {
                                relDir = included.substring(0, p);
                                relDir = relDir.replace(File.separatorChar, '/');  // Ensure cross-platform compatibility

                                // Create subcollections if required
                                if (createSubcollections && ((prevDir == null) || (!relDir.equals(prevDir)))) {
                                    col = mkcol(root, baseURI, XmldbURI.ROOT_COLLECTION + path, relDir);
                                    prevDir = relDir;
                                }
                            } else {
                                col = root;  // No directory part, store at the root collection
                            }

                            // Determine the MIME type for the current file
                            MimeType currentMime = getMimeTable().getContentTypeFor(file.getName());
                            final String currentBaseMimeType = (forceMimeType != null) ? forceMimeType : (currentMime != null ? currentMime.getName() : defaultMimeType);

                            if (type != null) {
                                // Adjust MIME type based on file type (XML or Binary)
                                if ("xml".equals(type)) {
                                    currentMime = new MimeType(currentBaseMimeType, MimeType.XML);
                                } else if ("binary".equals(type)) {
                                    currentMime = new MimeType(currentBaseMimeType, MimeType.BINARY);
                                }
                            }

                            // Default MIME type to binary if not found
                            if (currentMime == null) {
                                final String msg = "Cannot find mime-type kind for " + file.getName() + ". Treating it as a binary.";
                                log(msg, Project.MSG_ERR);
                                currentMime = new MimeType(currentBaseMimeType, MimeType.BINARY);
                            }

                            // Determine the resource type (XML or Binary) and store the file
                            final Class<? extends Resource> resourceType = currentMime.isXMLType() ? XMLResource.class : BinaryResource.class;
                            log("Creating resource " + file.getName() + " in collection " + col.getName() + " of type " + resourceType.getName() + " with mime-type: " + currentMime.getName(), Project.MSG_DEBUG);
                            try (Resource res = col.createResource(file.getName(), resourceType)) {
                                res.setContent(file);
                                ((EXistResource) res).setMimeType(currentMime.getName());
                                col.storeResource(res);  // Store the resource in the collection

                                // Set permissions for the resource if specified
                                if (permissions != null) {
                                    setPermissions(res);
                                }
                            }
                        } catch (final XMLDBException e) {
                            final String msg = "XMLDB exception caught: " + e.getMessage();
                            if (failonerror) {
                                throw new BuildException(msg, e);
                            } else {
                                log(msg, e, Project.MSG_ERR);
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * This method allows more than one Fileset per store task
     *
     * @param set The FileSet to be added to the list.
     */
    public void addFileset(final FileSet set) {
        // Initialize the list of FileSets if it's not already created
        if (fileSetList == null) {
            fileSetList = new ArrayList<>();
        }

        // Add the provided FileSet to the list
        fileSetList.add(set);
    }


    /**
     * Set the src file
     * 
     * @param file to be set
     */
    public void setSrcFile(final File file) {
        this.srcFile = file;
    }
    
    /**
     * Set the target file
     * 
     * @param name of the file
     */
    public void setTargetFile(final String name) {
        this.targetFile = name;
    }

    /**
     * Set if it will create a collection or not
     * 
     * @param create  True to create a collection, false if not
     */
    public void setCreatecollection(final boolean create) {
        this.createCollection = create;
    }
    
    /**
     * Set if it will create a subcollection or not
     * 
     * @param create  True to create a subcollection, false if not
     */
    public void setCreatesubcollections(final boolean create) {
        this.createSubcollections = create;
    }
    
    /**
     * Set if it will include empty directories or not
     * 
     * @param create True to include, false if not
     */
    public void setIncludeEmptyDirs(final boolean create) {
        this.includeEmptyDirs = create;
    }

    /**
     * Set if it will create a collection or not
     * 
     * @param create  True to create a collection, false if not
     */
    public void setMimeTypesFile(final File file) {
        this.mimeTypesFile = file;
    }
    
    /**
     * Set a type
     * 
     * @param type to set the type
     */
    public void setType(final String type) {
        this.type = type;
    }

    /**
     * Set the default type
     * 
     * @param mimeType to set the type
     */
    public void setDefaultMimeType(final String mimeType) {
        this.defaultMimeType = mimeType;
    }

    /**
     * Forcw the type
     * 
     * @param mimeType to set the type
     */
    public void setForceMimeType(final String mimeType) {
        this.forceMimeType = mimeType;
    }

    /**
     * Retrieves the MimeTable instance. If the table has not been initialized yet, it checks if a custom 
     * MIME types file is provided. If the file exists, it loads the MIME types from that file. Otherwise, 
     * it falls back to the default MIME types resources.
     * 
     * The MimeTable is used to map file extensions to MIME types, ensuring correct handling of file types 
     * when storing or processing files in the task.
     * 
     * @return The MimeTable instance, either loaded from a file or from the default resources.
     * @throws BuildException If an error occurs while loading the MIME types file.
     */
    private MimeTable getMimeTable() throws BuildException {
        // Check if the MimeTable has already been initialized
        if (mtable == null) {
            // If a custom MIME types file is provided and exists, load it
            if (mimeTypesFile != null && mimeTypesFile.exists()) {
                log("Trying to use MIME Types file " + mimeTypesFile.getAbsolutePath(), Project.MSG_DEBUG);
                mtable = MimeTable.getInstance(mimeTypesFile.toPath());
            } else {
                // Otherwise, use the default MIME types resources
                log("Using default MIME Types resources", Project.MSG_DEBUG);
                mtable = MimeTable.getInstance();
            }
        }
        return mtable;
    }

}
