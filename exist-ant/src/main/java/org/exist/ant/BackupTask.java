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
import org.exist.backup.Backup;
import org.exist.xmldb.XmldbURI;

import java.nio.file.Paths;


/**
 * DOCUMENT ME!
 *
 * @author  wolf
 * @author carvazpal
 */
public class BackupTask extends AbstractXMLDBTask
{
    private String dir = null;
    private boolean deduplicateBlobs = false;

    /**
     * Executes the task of creating a backup for the specified XMLDB collection.
     * 
     * <p>This method performs the backup by first ensuring that the necessary parameters are provided 
     * (the collection URI and backup directory). It then proceeds to register the database, 
     * log the backup operation details, and finally calls the {@link Backup#backup} method to 
     * perform the backup operation.</p>
     * 
     * <p>If any required parameters are missing or an exception occurs during the backup process, 
     * an error message is logged or thrown, depending on the {@code failonerror} setting.</p>
     *
     * @throws BuildException If there is an error during the backup process or if required parameters are missing.
     */
    @Override
    public void execute() throws BuildException
    {
        // Check if the URI for the collection is provided, otherwise throw an exception
        if( uri == null ) {
            throw( new BuildException( "You have to specify an XMLDB collection URI" ) );
        }

        // Check if the backup directory is provided, otherwise throw an exception
        if( dir == null ) {
            throw( new BuildException( "Missing required parameter: dir" ) );
        }

        // Register the database to make sure it is available for the backup operation
        registerDatabase();
        
        // Log the details about the collection and backup directory
        log( "Creating backup of collection: " + uri );
        log( "Backup directory: " + dir );

        try {
            // Initialize the Backup object with user credentials, directory, and URI
            final Backup backup = new Backup(user, password, Paths.get(dir), XmldbURI.create(uri), null, deduplicateBlobs);
            
            // Perform the backup operation (passing false for incremental backup)
            backup.backup(false, null);
        }
        catch( final Exception e ) {
            // Print the stack trace for debugging purposes
            e.printStackTrace();
            
            // Prepare the error message for the exception that occurred during the backup
            final String msg = "Exception during backup: " + e.getMessage();

            // If failonerror is true, throw an exception; otherwise, log the error
            if( failonerror ) {
                throw( new BuildException( msg, e ) );
            } else {
                log( msg, e, Project.MSG_ERR );
            }
        }
    }


    /**
     * Set the directory.
     *
     * @param dir the directory.
     */
    public void setDir(final String dir )
    {
        this.dir = dir;
    }

    /**
     * @param deduplicateBlobs to be set to this object.
     */
    public void setDeduplicateBlobs(final boolean deduplicateBlobs) {
        this.deduplicateBlobs = deduplicateBlobs;
    }
}
