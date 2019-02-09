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

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Implementation of BackupWriter that writes to a zip file.
 */
public class ZipWriter implements BackupWriter
{
    private String currentPath;
    private final ZipOutputStream out;
    private StringWriter contents;
    private boolean dataWritten = false;

    public ZipWriter(final String zipFile, final String collection) throws IOException
    {
        this(Paths.get(zipFile), collection);
    }


    public ZipWriter(final Path zipFile, final String collection) throws IOException
    {
        out         = new ZipOutputStream(Files.newOutputStream(zipFile));
        currentPath = collection;
    }

    public Writer newContents() throws IOException
    {
        contents = new StringWriter();
        return( contents );
    }


    public void closeContents() throws IOException
    {
        final ZipEntry entry = new ZipEntry( mkRelative( currentPath ) + "/__contents__.xml" );
        out.putNextEntry( entry );
        try {
            out.write(contents.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            out.closeEntry();
        }
        dataWritten = true;
    }


    public OutputStream newEntry(final String name ) throws IOException
    {
        final ZipEntry entry = new ZipEntry( mkRelative( currentPath ) + '/' + name );
        out.putNextEntry( entry );
        dataWritten = true;
        return( out );
    }

    public OutputStream newBlobEntry(final String blobId) throws IOException
    {
        final ZipEntry entry = new ZipEntry("blob/" + blobId);
        out.putNextEntry(entry);
        dataWritten = true;
        return out;
    }

    public void closeEntry() throws IOException
    {
        out.closeEntry();
    }


    public void newCollection(final String name )
    {
        if( name.startsWith( "/" ) ) {
            currentPath = name;
        } else {
            currentPath = currentPath + '/' + name;
        }
    }


    public void closeCollection()
    {
        final int p = currentPath.lastIndexOf( '/' );

        if( p > 0 ) {
            currentPath = currentPath.substring( 0, p );
        }
    }


    public void close() throws IOException
    {
        out.close();
    }


    public void setProperties(final Properties properties ) throws IOException
    {
        if( dataWritten ) {
            throw( new IOException( "Backup properties need to be set before any backup data is written" ) );
        }
        final ZipEntry entry = new ZipEntry( "backup.properties" );
        out.putNextEntry( entry );
        try {
            properties.store(out, "Backup properties");
        } finally {
            out.closeEntry();
        }
    }

    private String mkRelative(final String path )
    {
        if( (!path.isEmpty()) && ( path.charAt( 0 ) == '/' ) ) {
            return( path.substring( 1 ) );
        }
        return( path );
    }
}
