/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2010 The eXist Project
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;


/**
 * Implementation of BackupWriter that writes to the file system.
 */
public class FileSystemWriter implements BackupWriter
{
	private File		 rootDir;
    private File         currentDir;
    private File         currentContents;
    private Writer       currentContentsOut;
    private OutputStream currentOut;
    private boolean      dataWritten = false;

    public FileSystemWriter( String path )
    {
        this( new File( path ) );
    }


    public FileSystemWriter( File file )
    {
        if( file.exists() ) {

            //removing "path"
            file.delete();
        }
        file.mkdirs();
        currentDir = file;
        rootDir = file;
    }

    public void newCollection( String name )
    {
    	File file;
        if (XmldbURI.createInternal(name).isAbsolute()) {
            file = new File( rootDir, name );
    	} else {
            file = new File( currentDir, name );
    	}

        if( file.exists() ) {
            file.delete();
        }
        file.mkdirs();
        dataWritten = true;
        currentDir  = file;
    }


    public void closeCollection()
    {
        currentDir = currentDir.getParentFile();
    }


    public void close() throws IOException
    {
    }


    public Writer newContents() throws IOException
    {
        currentContents    = new File( currentDir, "__contents__.xml" );
        currentContentsOut = new BufferedWriter( new OutputStreamWriter( new FileOutputStream( currentContents ), "UTF-8" ) );
        dataWritten        = true;
        return( currentContentsOut );
    }


    public void closeContents() throws IOException
    {
        currentContentsOut.close();
    }


    public OutputStream newEntry( String name ) throws IOException
    {
        currentOut  = new FileOutputStream( new File( currentDir, name ) );
        dataWritten = true;
        return( currentOut );
    }


    public void closeEntry() throws IOException
    {
        currentOut.close();
    }


    public void setProperties( Properties properties ) throws IOException
    {
        if( dataWritten ) {
            throw( new IOException( "Backup properties need to be set before any backup data is written" ) );
        }
        final File         propFile = new File( rootDir, "backup.properties" );
        final OutputStream os       = new FileOutputStream( propFile );
        properties.store( os, "Backup properties" );
        os.close();
    }

    @Override
    public void addToRoot(String name, File file) throws IOException {
        FileUtils.copyFile(file, new File(rootDir, name));
    }
}
