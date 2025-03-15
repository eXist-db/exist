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
import org.apache.tools.ant.types.DirSet;
import org.exist.xmldb.ConsoleRestoreServiceTaskListener;
import org.exist.xmldb.EXistRestoreService;
import org.exist.xmldb.RestoreServiceTaskListener;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Restore Backup Task.
 *
 * @author  wolf
 */
public class RestoreTask extends AbstractXMLDBTask
{
    private Path zipFile;
    private Path dir;
    private DirSet dirSet;
    private String restorePassword;
    private boolean overwriteApps;

    /**
     * Executes the task to restore an XMLDB collection from a directory or ZIP file.
     * 
     * <p>This method checks if the required parameters are provided, such as the collection URI 
     * and at least one restore source (directory, directory set, or ZIP file). 
     * It then attempts to restore the collection using the specified source.</p>
     * 
     * <p>If an error occurs, it either throws an exception or logs the error based 
     * on the {@code failonerror} setting.</p>
     * 
     * @throws BuildException if the required arguments are missing or if an error occurs during restore.
     */
    @Override
    public void execute() throws BuildException
    {
        // Ensure the collection URI is provided.
        if (uri == null) {
            throw new BuildException("You have to specify an XMLDB collection URI");
        }

        // Check if at least one restore source (directory, directory set, or file) is provided.
        if ((dir == null) && (dirSet == null) && (zipFile == null)) {
            throw new BuildException("Missing required argument: either dir, dirset, or file required");
        }

        // If restoring from a directory, ensure it is readable.
        if ((dir != null) && !Files.isReadable(dir)) {
            final String msg = "Cannot read restore file: " + dir.toAbsolutePath();

            if (failonerror) {
                throw new BuildException(msg);
            } else {
                log(msg, Project.MSG_ERR);
            }

        } else {
            // Register the database connection.
            registerDatabase();

            try {
                if (dir != null) {
                    // Restore from a single directory.
                    log("Restoring from " + dir.toAbsolutePath(), Project.MSG_INFO);
                    final Path file = dir.resolve("__contents__.xml");

                    if (!Files.exists(file)) {
                        final String msg = "Could not find file " + file.toAbsolutePath();

                        if (failonerror) {
                            throw new BuildException(msg);
                        } else {
                            log(msg, Project.MSG_ERR);
                        }
                    } else {
                        restoreCollection(file);
                    }

                } else if (dirSet != null) {
                    // Restore from a directory set.
                    final DirectoryScanner scanner = dirSet.getDirectoryScanner(getProject());
                    scanner.scan();
                    final String[] includedFiles = scanner.getIncludedFiles();
                    log("Found " + includedFiles.length + " files.\n");

                    for (final String included : includedFiles) {
                        dir = scanner.getBasedir().toPath().resolve(included);
                        final Path contentsFile = dir.resolve("__contents__.xml");

                        if (!Files.exists(contentsFile)) {
                            final String msg = "Did not find file " + contentsFile.toAbsolutePath();

                            if (failonerror) {
                                throw new BuildException(msg);
                            } else {
                                log(msg, Project.MSG_ERR);
                            }
                        } else {
                            log("Restoring from " + contentsFile.toAbsolutePath() + " ...\n");
                            restoreCollection(contentsFile);
                        }
                    }

                } else if (zipFile != null) {
                    // Restore from a ZIP file.
                    log("Restoring from " + zipFile.toAbsolutePath(), Project.MSG_INFO);

                    if (!Files.exists(zipFile)) {
                        final String msg = "File not found: " + zipFile.toAbsolutePath();

                        if (failonerror) {
                            throw new BuildException(msg);
                        } else {
                            log(msg, Project.MSG_ERR);
                        }
                    } else {
                        restoreCollection(zipFile);
                    }
                }

            } catch (final Exception e) {
                e.printStackTrace();
                final String msg = "Exception during restore: " + e.getMessage();

                if (failonerror) {
                    throw new BuildException(msg, e);
                } else {
                    log(msg, e, Project.MSG_ERR);
                }
            }
        }
    }

    /**
     * Restores the collection from the given file.
     *
     * @param file the path to the restore file.
     * @throws XMLDBException if an error occurs during the restore process.
     */
    private void restoreCollection(Path file) throws XMLDBException {
        final RestoreServiceTaskListener listener = new ConsoleRestoreServiceTaskListener();
        final Collection collection = DatabaseManager.getCollection(uri, user, password);
        final EXistRestoreService service = collection.getService(EXistRestoreService.class);
        service.restore(file.normalize().toAbsolutePath().toString(), restorePassword, listener, overwriteApps);
    }


    /**
     * @return a new DirSet object
     */
    public DirSet createDirSet()
    {
        this.dirSet = new DirSet();
        return( dirSet );
    }


    /**
     * Set the directory.
     *
     * @param dir the directory
     */
    public void setDir(final File dir )
    {
        this.dir = dir.toPath();
    }


    /**
     * Set the file
     * 
     * @param file the file
     */
    public void setFile(final File file )
    {
        this.zipFile = file.toPath();
    }


    /**
     * Set the password
     * 
     * @param pass the password
     */
    public void setRestorePassword(final String pass )
    {
        this.restorePassword = pass;
    }

    /**
     * True to set the OverwriteApps, false otherwise
     * 
     * @param overwriteApps 
     */
    public void setOverwriteApps(final boolean overwriteApps)
    {
        this.overwriteApps = overwriteApps;
    }
}
