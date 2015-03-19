/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BackupDirectory
{
    public final static Logger     LOG                     = LogManager.getLogger( BackupDirectory.class );


    public final static String     PREFIX_FULL_BACKUP_FILE = "full";
    public final static String     PREFIX_INC_BACKUP_FILE  = "inc";

    public final static String     FILE_REGEX              = "(" + PREFIX_FULL_BACKUP_FILE + "|" + PREFIX_INC_BACKUP_FILE + ")(\\d{8}-\\d{4}).*";

    public final static DateFormat DATE_FORMAT             = new SimpleDateFormat( "yyyyMMdd-HHmm" );


    private File                   dir;

    private Matcher                matcher;

    public BackupDirectory( String dirPath )
    {
        this( new File( dirPath ) );
    }


    public BackupDirectory( File directory )
    {
        this.dir = directory;
        final Pattern pattern = Pattern.compile( FILE_REGEX );
        matcher = pattern.matcher( "" );
    }

    public File createBackup( boolean incremental, boolean zip )
    {
        int  counter = 0;
        File file;

        do {
            final StringBuilder buf = new StringBuilder();
            buf.append( incremental ? PREFIX_INC_BACKUP_FILE : PREFIX_FULL_BACKUP_FILE );
            buf.append( DATE_FORMAT.format( new Date() ) );

            if( counter++ > 0 ) {
                buf.append( '_' ).append( counter );
            }

            if( zip ) {
                buf.append( ".zip" );
            }
            file = new File( dir, buf.toString() );
        } while( file.exists() );
        return( file );
    }


    public BackupDescriptor lastBackupFile()
    {
        final File[] files      = dir.listFiles();

        File newest       = null;
        Date newestDate   = null;

        for( int i = 0; i < files.length; i++ ) {
            matcher.reset( files[i].getName() );

            if( matcher.matches() ) {
                final String dateTime = matcher.group( 2 );

                try {
                    Date date = DATE_FORMAT.parse( dateTime );

                    if( ( newestDate == null ) || date.after( newestDate ) ) {
                        newestDate = date;
                        newest     = files[i];
                    }
                }
                catch( final ParseException e ) {
                }
            }
        }
        BackupDescriptor descriptor = null;

        if( newest != null ) {

            try {

                if( newest.getName().endsWith( ".zip" ) || newest.getName().endsWith( ".ZIP" ) ) {
                    descriptor = new ZipArchiveBackupDescriptor( newest );
                } else {
                    descriptor = new FileSystemBackupDescriptor( new File( newest + "/db", BackupDescriptor.COLLECTION_DESCRIPTOR ) );
                }
            }
            catch( final IOException e ) {
                e.printStackTrace();
            }
        }
        return( descriptor );
    }

}
