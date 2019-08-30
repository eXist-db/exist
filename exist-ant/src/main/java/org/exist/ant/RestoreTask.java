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
    private Path zipFile         = null;
    private Path   dir             = null;
    private DirSet dirSet          = null;
    private String restorePassword = null;
    private boolean overwriteApps = false;

    @Override
    public void execute() throws BuildException
    {
        if( uri == null ) {
            throw( new BuildException( "You have to specify an XMLDB collection URI" ) );
        }

        if( ( dir == null ) && ( dirSet == null ) && ( zipFile == null ) ) {
            throw( new BuildException( "Missing required argument: either dir, dirset or file required" ) );
        }

        if( ( dir != null ) && !Files.isReadable(dir)) {
            final String msg = "Cannot read restore file: " + dir.toAbsolutePath().toString();

            if( failonerror ) {
                throw( new BuildException( msg ) );
            } else {
                log( msg, Project.MSG_ERR );
            }

        } else {
            registerDatabase();

            try {

                if( dir != null ) {
                    log( "Restoring from " + dir.toAbsolutePath().toString(), Project.MSG_INFO );
                    final Path file = dir.resolve("__contents__.xml" );

                    if( !Files.exists(file)) {
                        final String msg = "Could not find file " + file.toAbsolutePath().toString();

                        if( failonerror ) {
                            throw( new BuildException( msg ) );
                        } else {
                            log( msg, Project.MSG_ERR );
                        }
                    } else {
                        final RestoreServiceTaskListener listener = new ConsoleRestoreServiceTaskListener();
                        final Collection collection = DatabaseManager.getCollection(uri, user, password);
                        final EXistRestoreService service = (EXistRestoreService) collection.getService("RestoreService", "1.0");
                        service.restore(file.normalize().toAbsolutePath().toString(), restorePassword, listener, overwriteApps);
                    }

                } else if( dirSet != null ) {
                    final DirectoryScanner scanner = dirSet.getDirectoryScanner( getProject() );
                    scanner.scan();
                    final String[] includedFiles = scanner.getIncludedFiles();
                    log( "Found " + includedFiles.length + " files.\n" );

                    for( final String included : includedFiles ) {
                        dir = scanner.getBasedir().toPath().resolve(included);
                        final Path contentsFile = dir.resolve("__contents__.xml");

                        if( !Files.exists(contentsFile)) {
                            final String msg = "Did not found file " + contentsFile.toAbsolutePath().toString();

                            if( failonerror ) {
                                throw( new BuildException( msg ) );
                            } else {
                                log( msg, Project.MSG_ERR );
                            }
                        } else {
                            log( "Restoring from " + contentsFile.toAbsolutePath().toString() + " ...\n" );

                            // TODO subdirectories as sub-collections?
                            final RestoreServiceTaskListener listener = new ConsoleRestoreServiceTaskListener();
                            final Collection collection = DatabaseManager.getCollection(uri, user, password);
                            final EXistRestoreService service = (EXistRestoreService) collection.getService("RestoreService", "1.0");
                            service.restore(contentsFile.normalize().toAbsolutePath().toString(), restorePassword, listener, overwriteApps);
                        }
                    }

                } else if( zipFile != null ) {
                    log( "Restoring from " + zipFile.toAbsolutePath().toString(), Project.MSG_INFO );

                    if( !Files.exists(zipFile)) {
                        final String msg = "File not found: " + zipFile.toAbsolutePath().toString();

                        if( failonerror ) {
                            throw( new BuildException( msg ) );
                        } else {
                            log( msg, Project.MSG_ERR );
                        }
                    } else {
                        final RestoreServiceTaskListener listener = new ConsoleRestoreServiceTaskListener();
                        final Collection collection = DatabaseManager.getCollection(uri, user, password);
                        final EXistRestoreService service = (EXistRestoreService) collection.getService("RestoreService", "1.0");
                        service.restore(zipFile.normalize().toAbsolutePath().toString(), restorePassword, listener, overwriteApps);
                    }
                }

            }
            catch( final Exception e ) {
                e.printStackTrace();
                final String msg = "Exception during restore: " + e.getMessage();

                if( failonerror ) {
                    throw new BuildException( msg, e );
                } else {
                    log( msg, e, Project.MSG_ERR );
                }
            }
        }
    }


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
    public void setDir( File dir )
    {
        this.dir = dir.toPath();
    }


    public void setFile( File file )
    {
        this.zipFile = file.toPath();
    }


    public void setRestorePassword( String pass )
    {
        this.restorePassword = pass;
    }
}
