/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2011 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.backup;

import java.util.concurrent.ExecutorService;
import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;

import org.xml.sax.SAXException;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;

import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import javax.xml.parsers.ParserConfigurationException;
import org.exist.backup.restore.listener.ConsoleRestoreListener;
import org.exist.backup.restore.listener.GuiRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.client.ClientFrame;

/**
 * Main.java
 * 
 * @author Wolfgang Meier
 */
public class Main
{
    private final static int                  HELP_OPT       = 'h';
    private final static int                  USER_OPT       = 'u';
    private final static int                  PASS_OPT       = 'p';
    private final static int                  DBA_PASS_OPT   = 'P';
    private final static int                  BACKUP_OPT     = 'b';
    private final static int                  BACKUP_DIR_OPT = 'd';
    private final static int                  RESTORE_OPT    = 'r';
    private final static int                  OPTION_OPT     = 'o';
    private final static int                  GUI_OPT        = 'U';
    private final static int                  QUIET_OPT      = 'q';
    private final static int                  REBUILD_OPT    = 'R';

    private final static CLOptionDescriptor[] OPTIONS        = new CLOptionDescriptor[] {
        new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED, HELP_OPT, "print help on command line options and exit." ),
        new CLOptionDescriptor( "gui", CLOptionDescriptor.ARGUMENT_DISALLOWED, GUI_OPT, "start in GUI mode" ),
        new CLOptionDescriptor( "user", CLOptionDescriptor.ARGUMENT_REQUIRED, USER_OPT, "set user." ),
        new CLOptionDescriptor( "password", CLOptionDescriptor.ARGUMENT_REQUIRED, PASS_OPT, "set the password for connecting to the database." ),
        new CLOptionDescriptor( "dba-password", CLOptionDescriptor.ARGUMENT_REQUIRED, DBA_PASS_OPT, "if the backup specifies a different password for the admin/dba user, use this option " + "to specify the new password. Otherwise you will get a permission denied" ),
        new CLOptionDescriptor( "backup", CLOptionDescriptor.ARGUMENT_REQUIRED, BACKUP_OPT, "backup the specified collection." ),
        new CLOptionDescriptor( "dir", CLOptionDescriptor.ARGUMENT_REQUIRED, BACKUP_DIR_OPT, "specify the directory to use for backups." ),
        new CLOptionDescriptor( "restore", CLOptionDescriptor.ARGUMENT_REQUIRED, RESTORE_OPT, "read the specified restore file and restore the " + "resources described there." ),
        new CLOptionDescriptor( "option", CLOptionDescriptor.ARGUMENTS_REQUIRED_2 | CLOptionDescriptor.DUPLICATES_ALLOWED, OPTION_OPT, "specify extra options: property=value. For available properties see " + "client.properties." ),
        new CLOptionDescriptor( "quiet", CLOptionDescriptor.ARGUMENT_DISALLOWED, QUIET_OPT, "be quiet. Just print errors." ),
        new CLOptionDescriptor( "rebuild", CLOptionDescriptor.ARGUMENT_DISALLOWED, REBUILD_OPT, "rebuild the app repository after restore.")
    };

    /**
     * Constructor for Main.
     *
     * @param  args  DOCUMENT ME!
     */
    @SuppressWarnings( "unchecked" )
    public static void process( String[] args )
    {
        // read properties
        final Properties properties = new Properties();

        try {
            final File        propFile = ConfigurationHelper.lookup( "backup.properties" );
            InputStream pin;

            if( propFile.canRead() ) {
                pin = new FileInputStream( propFile );
            } else {
                pin = Main.class.getResourceAsStream( "backup.properties" );
            }

            if( pin != null ) {

                try {
                    properties.load( pin );
                }
                finally {
                    pin.close();
                }
            }

        }
        catch( final IOException ioe ) {
        }

        final Preferences        preferences = Preferences.userNodeForPackage( Main.class );

        // parse command-line options
        final CLArgsParser optParser   = new CLArgsParser( args, OPTIONS );

        if( optParser.getErrorString() != null ) {
            System.err.println( "ERROR: " + optParser.getErrorString() );
            return;
        }
        final List<CLOption> opts          = optParser.getArguments();
        String               optionBackup  = null;
        String               optionRestore = null;
        String               optionPass    = null;
        String               optionDbaPass = null;
        boolean              doBackup      = false;
        boolean              doRestore     = false;
        boolean              guiMode       = false;
        boolean              quiet         = false;
        boolean              rebuildRepo   = false;

        for( final CLOption option : opts ) {

            switch( option.getId() ) {

                case HELP_OPT: {
                    printUsage();
                    return;
                }

                case GUI_OPT: {
                    guiMode = true;
                    break;
                }

                case QUIET_OPT: {
                    quiet = true;
                    break;
                }
                case OPTION_OPT: {
                    properties.setProperty( option.getArgument( 0 ), option.getArgument( 1 ) );
                    break;
                }

                case USER_OPT: {
                    properties.setProperty( "user", option.getArgument() );
                    break;
                }

                case PASS_OPT: {
                    properties.setProperty( "password", option.getArgument() );
                    optionPass = option.getArgument(); //remove after change inside restore
                    break;
                }

                case DBA_PASS_OPT: {
                    optionDbaPass = option.getArgument();
                    break;
                }

                case BACKUP_OPT: {
                    if( option.getArgumentCount() == 1 ) {
                        optionBackup = option.getArgument();
                    } else {
                        optionBackup = null;
                    }
                    doBackup = true;
                    break;
                }

                case RESTORE_OPT: {
                    if( option.getArgumentCount() == 1 ) {
                        optionRestore = option.getArgument();
                    }
                    doRestore = true;
                    break;
                }

                case BACKUP_DIR_OPT: {
                    properties.setProperty( "backup-dir", option.getArgument() );
                    break;
                }

                case REBUILD_OPT: {
                    rebuildRepo = true;
                    break;
                }
            }
        }

        // initialize driver
        Database database;

        try {
            final Class<?> cl = Class.forName( properties.getProperty( "driver", "org.exist.xmldb.DatabaseImpl" ) );
            database = (Database)cl.newInstance();
            database.setProperty( "create-database", "true" );

            if( properties.containsKey( "configuration" ) ) {
                database.setProperty( "configuration", properties.getProperty( "configuration" ) );
            }
            DatabaseManager.registerDatabase( database );
        }
        catch( final ClassNotFoundException e ) {
            reportError( e );
            return;
        }
        catch( final InstantiationException e ) {
            reportError( e );
            return;
        }
        catch( final IllegalAccessException e ) {
            reportError( e );
            return;
        }
        catch( final XMLDBException e ) {
            reportError( e );
            return;
        }

        // process
        if( doBackup ) {

            if( optionBackup == null ) {

                if( guiMode ) {
                    final CreateBackupDialog dialog = new CreateBackupDialog( properties.getProperty( "uri", "xmldb:exist://" ), properties.getProperty( "user", "admin" ), properties.getProperty( "password", "" ), new File( preferences.get( "directory.backup", System.getProperty( "user.dir" ) ) ) );

                    if( JOptionPane.showOptionDialog( null, dialog, "Create Backup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null ) == JOptionPane.YES_OPTION ) {
                        optionBackup = dialog.getCollection();
                        properties.setProperty( "backup-dir", dialog.getBackupTarget() );
                    }
                } else {
                    optionBackup = XmldbURI.ROOT_COLLECTION;
                }
            }

            if( optionBackup != null ) {

                try {
                    final Backup backup = new Backup( properties.getProperty( "user", "admin" ), properties.getProperty( "password", "" ), properties.getProperty( "backup-dir", "backup" ), XmldbURI.xmldbUriFor( properties.getProperty( "uri", "xmldb:exist://" ) + optionBackup ), properties );
                    backup.backup( guiMode, null );
                }
                catch( final Exception e ) {
                    reportError( e );
                }
            }
        }

        if( doRestore ) {

            if( ( optionRestore == null ) && guiMode ) {
                final JFileChooser chooser = new JFileChooser();
                chooser.setMultiSelectionEnabled( false );
                chooser.setFileSelectionMode( JFileChooser.FILES_ONLY );

                if( chooser.showDialog( null, "Select backup file for restore" ) == JFileChooser.APPROVE_OPTION ) {
                    final File f = chooser.getSelectedFile();
                    optionRestore = f.getAbsolutePath();
                }
            }

            if(optionRestore != null) {

                final String username = properties.getProperty( "user", "admin" );
                final File f = new File(optionRestore);
                final String uri = properties.getProperty( "uri", "xmldb:exist://");
                
                try {
                    if(guiMode) {
                        restoreWithGui(username, optionPass, optionDbaPass, f, uri);
                    } else {
                        restoreWithoutGui(username, optionPass, optionDbaPass, f, uri, rebuildRepo, quiet);
                    }
                }
                catch( final Exception e ) {
                    reportError( e );
                }
            }
        }

        try {
            String uri = properties.getProperty( "uri", XmldbURI.EMBEDDED_SERVER_URI_PREFIX );
            if(!(uri.contains(XmldbURI.ROOT_COLLECTION) || uri.endsWith( XmldbURI.ROOT_COLLECTION))) {
                uri += XmldbURI.ROOT_COLLECTION;
            }

            final Collection root = DatabaseManager.getCollection(uri, properties.getProperty( "user", "admin" ), ( optionDbaPass == null ) ? optionPass : optionDbaPass );
            shutdown( root );
        }
        catch( final Exception e ) {
            reportError( e );
        }
        System.exit( 0 );
    }

    private static void restoreWithoutGui(final String username, final String password, final String dbaPassword, final File f,
                                          final String uri, final boolean rebuildRepo, boolean quiet) {
        
        final RestoreListener listener = new ConsoleRestoreListener(quiet);
        final Restore restore = new Restore();

        try {
            restore.restore(listener, username, password, dbaPassword, f, uri);
        } catch(final FileNotFoundException fnfe) {
            listener.error(fnfe.getMessage());
        } catch(final IOException ioe) {
            listener.error(ioe.getMessage());
        } catch(final SAXException saxe) {
            listener.error(saxe.getMessage());
        } catch(final XMLDBException xmldbe) {
            listener.error(xmldbe.getMessage());
        } catch(final ParserConfigurationException pce) {
            listener.error(pce.getMessage());
        } catch(final URISyntaxException use) {
            listener.error(use.getMessage());
        }
        
        if(listener.hasProblems()) {
            System.err.println(listener.warningsAndErrorsAsString());
        }
        if (rebuildRepo) {
            System.out.println("Rebuilding application repository ...");
            System.out.println("URI: " + uri);
            try {
                String rootURI = uri;
                if(!(rootURI.contains(XmldbURI.ROOT_COLLECTION) || rootURI.endsWith( XmldbURI.ROOT_COLLECTION))) {
                    rootURI += XmldbURI.ROOT_COLLECTION;
                }
                final Collection root = DatabaseManager.getCollection(rootURI, username, dbaPassword);
                if (root != null) {
                    ClientFrame.repairRepository(root);
                    System.out.println("Application repository rebuilt successfully.");
                } else {
                    System.err.println("Failed to retrieve root collection: " + uri);
                }
            } catch (XMLDBException e) {
                reportError(e);
                System.err.println("Rebuilding application repository failed!");
            }
        } else {
            System.out.println("\nIf you restored collections inside /db/apps, you may want\n" +
                    "to rebuild the application repository. To do so, run the following query\n" +
                    "as admin:\n\n" +
                    "import module namespace repair=\"http://exist-db.org/xquery/repo/repair\"\n" +
                    "at \"resource:org/exist/xquery/modules/expathrepo/repair.xql\";\n" +
                    "repair:clean-all(),\n" +
                    "repair:repair()\n");
        }
    }
    
    private static void restoreWithGui(final String username, final String password, final String dbaPassword, final File f, final String uri) {
        
        final GuiRestoreListener listener = new GuiRestoreListener();
        
        final Callable<Void> callable = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                
                final Restore restore = new Restore();
                
                try {
                    restore.restore(listener, username, password, dbaPassword, f, uri);

                    listener.hideDialog();

                    if (JOptionPane.showConfirmDialog(null, "Would you like to rebuild the application repository?\nThis is only necessary if application packages were restored.", "Rebuild App Repository?",
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        System.out.println("Rebuilding application repository ...");
                        try {
                            String rootURI = uri;
                            if(!(rootURI.contains(XmldbURI.ROOT_COLLECTION) || rootURI.endsWith( XmldbURI.ROOT_COLLECTION))) {
                                rootURI += XmldbURI.ROOT_COLLECTION;
                            }
                            final Collection root = DatabaseManager.getCollection(rootURI, username, dbaPassword);
                            ClientFrame.repairRepository(root);
                            System.out.println("Application repository rebuilt successfully.");
                        } catch (XMLDBException e) {
                            reportError(e);
                            System.err.println("Rebuilding application repository failed!");
                        }
                    }
                } catch (final Exception e) {
                    ClientFrame.showErrorMessage(e.getMessage(), null); //$NON-NLS-1$
                } finally {
                    if(listener.hasProblems()) {
                        ClientFrame.showErrorMessage(listener.warningsAndErrorsAsString(), null);
                    }
                }
                
                return null;
            }
        };
       
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Void> future = executor.submit(callable);

        while(!future.isDone() && !future.isCancelled()) {
            try {
                future.get(100, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException ie) {

            } catch (final ExecutionException ee) {
                break;
            } catch (final TimeoutException te) {

            }
        }
    }
    
    
    private static void reportError( Throwable e )
    {
        e.printStackTrace();

        if( e.getCause() != null ) {
            System.err.println( "caused by " );
            e.getCause().printStackTrace();
        }
        
        System.exit(1);
    }


    private static void printUsage()
    {
        System.out.println( "Usage: java " + Main.class.getName() + " [options]" );
        System.out.println( CLUtil.describeOptions( OPTIONS ).toString() );
    }


    private static void shutdown( Collection root )
    {
        try {
            final DatabaseInstanceManager mgr = (DatabaseInstanceManager)root.getService( "DatabaseInstanceManager", "1.0" );

            if( mgr == null ) {
                System.err.println( "service is not available" );
            } else if( mgr.isLocalInstance() ) {
                System.out.println( "shutting down database..." );
                mgr.shutdown();
            }
        }
        catch( final XMLDBException e ) {
            System.err.println( "database shutdown failed: " );
            e.printStackTrace();
        }
    }


    public static void main( String[] args )
    {
        try {
            process( args );
        }
        catch( final Throwable e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
