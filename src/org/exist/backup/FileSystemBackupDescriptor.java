/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.backup;

import org.exist.repo.RepoBackup;
import org.exist.util.EXistInputSource;
import org.exist.util.FileInputSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;


public class FileSystemBackupDescriptor extends AbstractBackupDescriptor
{
    protected File descriptor;

    public FileSystemBackupDescriptor( File theDesc ) throws FileNotFoundException
    {
        if( !theDesc.getName().equals( BackupDescriptor.COLLECTION_DESCRIPTOR ) || !theDesc.isFile() || !theDesc.canRead() ) {
            throw( new FileNotFoundException( theDesc.getAbsolutePath() + " is not a valid collection descriptor" ) );
        }
        descriptor = theDesc;
    }

    public BackupDescriptor getChildBackupDescriptor( String describedItem )
    {
        File             child = new File( new File( descriptor.getParentFile(), describedItem ), BackupDescriptor.COLLECTION_DESCRIPTOR );
        BackupDescriptor bd    = null;

        try {
            bd = new FileSystemBackupDescriptor( child );
        }
        catch( FileNotFoundException fnfe ) {
            // DoNothing(R)
        }
        return( bd );
    }


    public BackupDescriptor getBackupDescriptor( String describedItem )
    {
        String           topDir = descriptor.getParentFile().getParentFile().getAbsolutePath();
        String           subDir = topDir + describedItem;
        String           desc   = subDir + '/' + BackupDescriptor.COLLECTION_DESCRIPTOR;
        BackupDescriptor bd     = null;

        try {
            bd = new FileSystemBackupDescriptor( new File( desc ) );
        }
        catch( FileNotFoundException fnfe ) {
            // DoNothing(R)
        }
        return( bd );
    }


    public EXistInputSource getInputSource()
    {
        return( new FileInputSource( descriptor ) );
    }


    public EXistInputSource getInputSource( String describedItem )
    {
        File             child = new File( descriptor.getParentFile(), describedItem );
        EXistInputSource is    = null;

        if( child.isFile() && child.canRead() ) {
            is = new FileInputSource( child );
        }

        return( is );
    }


    public String getSymbolicPath()
    {
        return( descriptor.getAbsolutePath() );
    }


    public String getSymbolicPath( String describedItem, boolean isChildDescriptor )
    {
        File resbase = new File( descriptor.getParentFile(), describedItem );

        if( isChildDescriptor ) {
            resbase = new File( resbase, BackupDescriptor.COLLECTION_DESCRIPTOR );
        }
        return( resbase.getAbsolutePath() );
    }


    public Properties getProperties() throws IOException
    {
        File dir = descriptor.getParentFile();

        if( dir != null ) {
        	dir = dir.getParentFile();
        	if (dir != null) {
	        	File propFile = new File( dir, BACKUP_PROPERTIES );
	
	        	try {
	        		InputStream is         = new BufferedInputStream( new FileInputStream( propFile ) );
	        		Properties  properties = new Properties();
	
	        		try {
	        			properties.load( is );
	        		}
	        		finally {
	        			is.close();
	        		}
	        		return( properties );
	        	}
	        	catch( FileNotFoundException e ) {
	        		// do nothing, return null
	        	}
        	}
        }
        return( null );
    }

    public File getParentDir()
    {
        return( descriptor.getParentFile().getParentFile().getParentFile() );
    }


    public String getName()
    {
        return( descriptor.getParentFile().getParentFile().getName() );
    }

    @Override
    public File getRepoBackup() throws IOException {
        File archive = new File(descriptor.getParentFile().getParentFile(), RepoBackup.REPO_ARCHIVE);
        if (archive.exists())
            return archive;
        return null;
    }
}
