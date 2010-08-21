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

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.TerminatedException;

import java.io.File;

import java.util.List;


public class ExportMain
{
    // command-line options
    private final static int                  HELP_OPT          = 'h';
    private final static int                  EXPORT_OPT        = 'x';
    private final static int                  OUTPUT_DIR_OPT    = 'd';
    private final static int                  CONFIG_OPT        = 'c';
    private final static int                  INCREMENTAL_OPT   = 'i';
    private final static int                  DIRECT_ACCESS_OPT = 'D';

    private final static CLOptionDescriptor[] OPTIONS           = new CLOptionDescriptor[] {
        new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED, HELP_OPT, "print help on command line options and exit." ),
        new CLOptionDescriptor( "dir", CLOptionDescriptor.ARGUMENT_REQUIRED, OUTPUT_DIR_OPT, "the directory to which all output will be written." ),
        new CLOptionDescriptor( "config", CLOptionDescriptor.ARGUMENT_REQUIRED, CONFIG_OPT, "the database configuration (conf.xml) file to use " + "for launching the db." ),
        new CLOptionDescriptor( "direct", CLOptionDescriptor.ARGUMENT_DISALLOWED, DIRECT_ACCESS_OPT, "use an (even more) direct access to the db, bypassing some " + "index structures" ),
        new CLOptionDescriptor( "export", CLOptionDescriptor.ARGUMENT_DISALLOWED, EXPORT_OPT, "export database contents while preserving as much data as possible" ),
        new CLOptionDescriptor( "incremental", CLOptionDescriptor.ARGUMENT_DISALLOWED, INCREMENTAL_OPT, "create incremental backup (use with --export|-x)" )
    };

    protected static BrokerPool startDB( String configFile )
    {
        try {
            Configuration config;

            if( configFile == null ) {
                config = new Configuration();
            } else {
                config = new Configuration( configFile, null );
            }
            BrokerPool.configure( 1, 5, config );
            return( BrokerPool.getInstance() );
        }
        catch( DatabaseConfigurationException e ) {
            System.err.println( "ERROR: Failed to open database: " + e.getMessage() );
        }
        catch( EXistException e ) {
            System.err.println( "ERROR: Failed to open database: " + e.getMessage() );
        }
        return( null );
    }


    @SuppressWarnings( "unchecked" )
    public static void main( String[] args )
    {
        CLArgsParser optParser = new CLArgsParser( args, OPTIONS );

        if( optParser.getErrorString() != null ) {
            System.err.println( "ERROR: " + optParser.getErrorString() );
            return;
        }
        boolean        export       = false;
        boolean        incremental  = false;
        boolean        direct       = false;
        String         exportTarget = "export/";
        String         dbConfig     = null;

        List<CLOption> opts         = optParser.getArguments();

        for( CLOption option : opts ) {

            switch( option.getId() ) {

                case HELP_OPT: {
                    System.out.println( "Usage: java " + ExportMain.class.getName() + " [options]" );
                    System.out.println( CLUtil.describeOptions( OPTIONS ).toString() );
                    System.exit( 0 );
                    break;
                }

                case OUTPUT_DIR_OPT: {
                    exportTarget = option.getArgument();
                    break;
                }

                case DIRECT_ACCESS_OPT: {
                    direct = true;
                    break;
                }

                case CONFIG_OPT: {
                    dbConfig = option.getArgument();
                    break;
                }

                case EXPORT_OPT: {
                    export = true;
                    break;
                }

                case INCREMENTAL_OPT: {
                    incremental = true;
                    break;
                }
            }
        }

        BrokerPool pool = startDB( dbConfig );

        if( pool == null ) {
            System.exit( 1 );
        }
        int      retval = 0; // return value
        DBBroker broker = null;

        try {
            broker = pool.get( pool.getSecurityManager().getSystemSubject() );
            ConsistencyCheck  checker = new ConsistencyCheck( broker, direct );
            List<ErrorReport> errors  = checker.checkAll( new CheckCallback() );

            if( errors.size() > 0 ) {
                System.err.println( "ERRORS FOUND." );
                retval = 1;
            } else {
                System.out.println( "No errors." );
            }

            if( export ) {
                File dir = new File( exportTarget );

                if( !dir.exists() ) {
                    dir.mkdirs();
                }
                SystemExport sysexport = new SystemExport( broker, new Callback(), null, direct );
                sysexport.export( exportTarget, incremental, true, errors );
            }
        }
        catch( EXistException e ) {
            System.err.println( "ERROR: Failed to retrieve database broker: " + e.getMessage() );
            retval = 2;
        }
        catch( TerminatedException e ) {
            System.err.println( "WARN: Export was terminated by db." );
            retval = 3;
        }
        finally {
            pool.release( broker );
            BrokerPool.stopAll( false );
        }
        System.exit( retval );
    }

    private static class Callback implements SystemExport.StatusCallback
    {
        public void startCollection( String path )
        {
            System.out.println( "Entering collection " + path + " ..." );
        }


        public void startDocument( String name, int count, int docsCount )
        {
            System.out.println( "Writing document " + name + " [" + count + " of " + docsCount + ']' );
        }


        public void error( String message, Throwable exception )
        {
            System.err.println( message );

            if( exception != null ) {
                exception.printStackTrace();
            }
        }
    }


    private static class CheckCallback implements org.exist.backup.ConsistencyCheck.ProgressCallback
    {
        public void startDocument( String name, int current, int count )
        {
        }


        public void startCollection( String path )
        {
        }


        public void error( ErrorReport error )
        {
            System.out.println( error.toString() );
        }
    }
}
