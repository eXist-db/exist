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

import org.exist.backup.Backup;
import org.exist.xmldb.XmldbURI;

import java.nio.file.Paths;


/**
 * DOCUMENT ME!
 *
 * @author  wolf
 */
public class BackupTask extends AbstractXMLDBTask
{
    private String dir = null;
    private boolean deduplicateBlobs = false;

    @Override
    public void execute() throws BuildException
    {
        if( uri == null ) {
            throw( new BuildException( "you have to specify an XMLDB collection URI" ) );
        }

        if( dir == null ) {
            throw( new BuildException( "missing required parameter: dir" ) );
        }

        registerDatabase();
        log( "Creating backup of collection: " + uri );
        log( "Backup directory: " + dir );

        try {
            final Backup backup = new Backup(user, password, Paths.get(dir), XmldbURI.create( uri ), null, deduplicateBlobs);
            backup.backup( false, null );

        }
        catch( final Exception e ) {
            e.printStackTrace();
            final String msg = "Exception during backup: " + e.getMessage();

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
    public void setDir( String dir )
    {
        this.dir = dir;
    }

    public void setDeduplicateBlobs(final boolean deduplicateBlobs) {
        this.deduplicateBlobs = deduplicateBlobs;
    }
}
