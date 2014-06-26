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

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xquery.TerminatedException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import org.exist.security.PermissionDeniedException;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * DOCUMENT ME!
 *
 * @author  wolf
 */
public class ExportGUI extends javax.swing.JFrame
{
    private static final long        serialVersionUID = -8104424554660744639L;

    private BrokerPool               pool             = null;
    private int                      documentCount    = 0;
    private PrintWriter              logWriter        = null;

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton      btnChangeDir;
    private javax.swing.JButton      btnConfSelect;
    private javax.swing.JLabel       currentTask;
    private javax.swing.JTextField   dbConfig;
    private javax.swing.JButton      exportBtn;
    private javax.swing.JCheckBox    zipBtn;
    private javax.swing.JCheckBox    incrementalBtn;
    private JCheckBox                directAccessBtn;
    private javax.swing.JCheckBox    scanBtn;
    private javax.swing.JLabel       jLabel1;
    private javax.swing.JLabel       jLabel2;
    private javax.swing.JMenu        jMenu1;
    private javax.swing.JMenuBar     jMenuBar1;
    private javax.swing.JScrollPane  jScrollPane2;
    private javax.swing.JToolBar     jToolBar1;
    private javax.swing.JMenuItem    menuQuit;
    private javax.swing.JTextArea    messages;
    private javax.swing.JTextField   outputDir;
    private javax.swing.JProgressBar progress;
    private javax.swing.JButton      startBtn;
    // End of variables declaration//GEN-END:variables

    /**
     * Creates new form CheckerGUI.
     */
    public ExportGUI()
    {
        super( "Consistency Check and Repair" );
        initComponents();
        final String existHome = System.getProperty( "exist.home", "./" );
        final File   home      = new File( existHome );
        dbConfig.setText( new File( home, "conf.xml" ).getAbsolutePath() );
        outputDir.setText( new File( home, "export" ).getAbsolutePath() );
    }

    protected boolean checkOutputDir()
    {
        final File dir = new File( outputDir.getText() );

        if( !dir.exists() ) {

            if( JOptionPane.showConfirmDialog( this, "The output directory " + dir.getAbsolutePath() + " does not exist. Create it?", "Confirm", JOptionPane.YES_NO_OPTION ) == JOptionPane.YES_OPTION ) {
                dir.mkdirs();
            } else {
                return( false );
            }
        }
        return( true );
    }


    protected boolean startDB()
    {
        if( pool != null ) {
            return( true );
        }
        final File confFile = new File( dbConfig.getText() );

        if( !( confFile.exists() && confFile.canRead() ) ) {
            JOptionPane.showMessageDialog( this, "The selected database configuration file " + confFile.getAbsolutePath() + " does not exist or is not readable.", "Configuration Error", JOptionPane.ERROR_MESSAGE );
            return( false );
        }

        try {
            final Configuration config = new Configuration( confFile.getAbsolutePath(), null );
            BrokerPool.configure( 1, 5, config );
            pool = BrokerPool.getInstance();
            return( true );
        }
        catch( final Exception e ) {
            JOptionPane.showMessageDialog( this, "Could not start the database instance. Please remember\n" + "that this tool tries to launch an embedded db instance. No other db instance should\n" + "be running on the same data.", "DB Error", JOptionPane.ERROR_MESSAGE );
            e.printStackTrace();
            System.err.println( "ERROR: Failed to open database: " + e.getMessage() );
        }
        return( false );
    }


    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated
    // Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        currentTask     = new javax.swing.JLabel();
        progress        = new javax.swing.JProgressBar();
        jScrollPane2    = new javax.swing.JScrollPane();
        messages        = new javax.swing.JTextArea();
        jToolBar1       = new javax.swing.JToolBar();
        startBtn        = new javax.swing.JButton();
        exportBtn       = new javax.swing.JButton();
        incrementalBtn  = new JCheckBox( "Incremental" );
        scanBtn         = new JCheckBox( "Scan docs" );
        directAccessBtn = new JCheckBox( "Direct access" );
        zipBtn			= new JCheckBox("Create ZIP");
        outputDir       = new javax.swing.JTextField();
        jLabel1         = new javax.swing.JLabel();
        btnChangeDir    = new javax.swing.JButton();
        dbConfig        = new javax.swing.JTextField();
        jLabel2         = new javax.swing.JLabel();
        btnConfSelect   = new javax.swing.JButton();
        jMenuBar1       = new javax.swing.JMenuBar();
        jMenu1          = new javax.swing.JMenu();
        menuQuit        = new javax.swing.JMenuItem();

        setDefaultCloseOperation( javax.swing.WindowConstants.EXIT_ON_CLOSE );
        addWindowListener( new java.awt.event.WindowAdapter() {
                public void windowClosed( java.awt.event.WindowEvent evt )
                {
                    formWindowClosed( evt );
                }
            } );
        getContentPane().setLayout( new java.awt.GridBagLayout() );

        currentTask.setText( " " );
        currentTask.setMinimumSize( new java.awt.Dimension( 0, 25 ) );
        gridBagConstraints           = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx     = 0;
        gridBagConstraints.gridy     = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill      = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor    = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx   = 1.0;
        gridBagConstraints.insets    = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( currentTask, gridBagConstraints );
        gridBagConstraints           = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx     = 0;
        gridBagConstraints.gridy     = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill      = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor    = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx   = 1.0;
        gridBagConstraints.insets    = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( progress, gridBagConstraints );

        jScrollPane2.setBorder( javax.swing.BorderFactory.createTitledBorder( "Messages" ) );
        jScrollPane2.setPreferredSize( new java.awt.Dimension( 400, 200 ) );

        messages.setColumns( 20 );
        messages.setLineWrap( true );
        messages.setRows( 5 );
        messages.setBorder( javax.swing.BorderFactory.createEmptyBorder( 1, 1, 1, 1 ) );
        jScrollPane2.setViewportView( messages );

        gridBagConstraints           = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx     = 0;
        gridBagConstraints.gridy     = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill      = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor    = java.awt.GridBagConstraints.SOUTHWEST;
        gridBagConstraints.weightx   = 1.0;
        gridBagConstraints.weighty   = 1.0;
        gridBagConstraints.insets    = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( jScrollPane2, gridBagConstraints );

        jToolBar1.setRollover( true );

        startBtn.setText( "Check" );
        startBtn.setFocusable( false );
        startBtn.setHorizontalTextPosition( javax.swing.SwingConstants.CENTER );
        startBtn.setVerticalTextPosition( javax.swing.SwingConstants.BOTTOM );
        startBtn.addActionListener( new java.awt.event.ActionListener() {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    startBtncheck( evt );
                }
            } );
        jToolBar1.add( startBtn );

        exportBtn.setText( "Check & Export" );
        exportBtn.setFocusable( false );
        exportBtn.setHorizontalTextPosition( javax.swing.SwingConstants.CENTER );
        exportBtn.setVerticalTextPosition( javax.swing.SwingConstants.BOTTOM );
        exportBtn.addActionListener( new java.awt.event.ActionListener() {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    exportBtnActionPerformed( evt );
                }
            } );
        jToolBar1.add( exportBtn );

        jToolBar1.add( incrementalBtn );
        scanBtn.setSelected(true);
        scanBtn.setToolTipText( "Perform additional checks; scans every XML document" );
        jToolBar1.add( scanBtn );
        directAccessBtn.setToolTipText( "Bypass collection index by scanning collection store" );
        jToolBar1.add( directAccessBtn );
        jToolBar1.add( zipBtn );

        gridBagConstraints           = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill      = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx   = 1.0;
        getContentPane().add( jToolBar1, gridBagConstraints );
        gridBagConstraints         = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx   = 1;
        gridBagConstraints.gridy   = 2;
        gridBagConstraints.fill    = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets  = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( outputDir, gridBagConstraints );

        jLabel1.setText( "Output Directory:" );
        gridBagConstraints        = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx  = 0;
        gridBagConstraints.gridy  = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( jLabel1, gridBagConstraints );

        btnChangeDir.setText( "Change" );
        btnChangeDir.addActionListener( new java.awt.event.ActionListener() {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    btnChangeDirActionPerformed( evt );
                }
            } );
        gridBagConstraints        = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx  = 2;
        gridBagConstraints.gridy  = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( btnChangeDir, gridBagConstraints );
        gridBagConstraints         = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx   = 1;
        gridBagConstraints.gridy   = 1;
        gridBagConstraints.fill    = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets  = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( dbConfig, gridBagConstraints );

        jLabel2.setText( "DB Configuration:" );
        gridBagConstraints        = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx  = 0;
        gridBagConstraints.gridy  = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( jLabel2, gridBagConstraints );

        btnConfSelect.setText( "Select" );
        btnConfSelect.setMaximumSize( new java.awt.Dimension( 75, 24 ) );
        btnConfSelect.setMinimumSize( new java.awt.Dimension( 75, 24 ) );
        btnConfSelect.setPreferredSize( new java.awt.Dimension( 75, 24 ) );
        btnConfSelect.addActionListener( new java.awt.event.ActionListener() {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    btnConfSelectActionPerformed( evt );
                }
            } );
        gridBagConstraints        = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx  = 2;
        gridBagConstraints.gridy  = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets( 5, 5, 5, 5 );
        getContentPane().add( btnConfSelect, gridBagConstraints );

        jMenu1.setText( "File" );

        menuQuit.setAccelerator( javax.swing.KeyStroke.getKeyStroke( java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_MASK ) );
        menuQuit.setText( "Quit" );
        menuQuit.addActionListener( new java.awt.event.ActionListener() {
                public void actionPerformed( java.awt.event.ActionEvent evt )
                {
                    menuQuitActionPerformed( evt );
                }
            } );
        jMenu1.add( menuQuit );

        jMenuBar1.add( jMenu1 );

        setJMenuBar( jMenuBar1 );

        pack();
    } // </editor-fold>//GEN-END:initComponents


    private void formWindowClosed( java.awt.event.WindowEvent evt )
    { // GEN-FIRST:event_formWindowClosed
        BrokerPool.stopAll( false );
    } // GEN-LAST:event_formWindowClosed


    private void startBtncheck( java.awt.event.ActionEvent evt )
    { // GEN-FIRST:event_startBtncheck

        if( !checkOutputDir() ) {
            return;
        }
        final Runnable checkRun = new Runnable() {
            public void run()
            {
                openLog( outputDir.getText() );

                try {
                    checkDB();
                }
                finally {
                    closeLog();
                }
            }
        };
        new Thread( checkRun ).start();
    } // GEN-LAST:event_startBtncheck


    private void exportBtnActionPerformed( java.awt.event.ActionEvent evt )
    { // GEN-FIRST:event_exportBtnActionPerformed

        if( !checkOutputDir() ) {
            return;
        }
        final Runnable th = new Runnable() {
            public void run()
            {
                openLog( outputDir.getText() );

                try {
                    currentTask.setText( "Checking database consistency ..." );
                    List<ErrorReport> errors = checkDB();
                    currentTask.setText( "Exporting data ..." );
                    exportDB( outputDir.getText(), errors );
                }
                finally {
                    closeLog();
                }
            }
        };
        new Thread( th ).start();
    } // GEN-LAST:event_exportBtnActionPerformed


    private void btnChangeDirActionPerformed( java.awt.event.ActionEvent evt )
    { // GEN-FIRST:event_btnChangeDirActionPerformed
        final File               dir     = new File( outputDir.getText() );
        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled( false );
        chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
        chooser.setSelectedFile( new File( dir, "export" ) );
        chooser.setCurrentDirectory( dir );

        if( chooser.showDialog( this, "Export" ) == JFileChooser.APPROVE_OPTION ) {
            outputDir.setText( chooser.getSelectedFile().getAbsolutePath() );
        }
    } // GEN-LAST:event_btnChangeDirActionPerformed


    private void menuQuitActionPerformed( java.awt.event.ActionEvent evt )
    { // GEN-FIRST:event_menuQuitActionPerformed
        BrokerPool.stopAll( false );
        System.exit( 0 );
    } // GEN-LAST:event_menuQuitActionPerformed


    private void btnConfSelectActionPerformed( java.awt.event.ActionEvent evt )
    { // GEN-FIRST:event_btnConfSelectActionPerformed
        final File               dir     = new File( dbConfig.getText() ).getParentFile();
        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled( false );
        chooser.setFileSelectionMode( JFileChooser.FILES_ONLY );
        chooser.setSelectedFile( new File( dir, "conf.xml" ) );
        chooser.setCurrentDirectory( dir );
        chooser.setFileFilter( new FileFilter() {
                public boolean accept( File f )
                {
                    if( f.isDirectory() ) {
                        return( true );
                    }
                    final MimeType mime = MimeTable.getInstance().getContentTypeFor( f.getName() );

                    if( mime == null ) {
                        return( false );
                    }
                    return( mime.isXMLType() );
                }


                public String getDescription()
                {
                    return( "Database XML configuration file" );
                }

            } );

        if( chooser.showDialog( this, "Select" ) == JFileChooser.APPROVE_OPTION ) {
            dbConfig.setText( chooser.getSelectedFile().getAbsolutePath() );
        }
    } // GEN-LAST:event_btnConfSelectActionPerformed


    private void exportDB( String exportTarget, List<ErrorReport> errorList )
    {
        if( !startDB() ) {
            return;
        }
        DBBroker broker = null;

        try {
            broker = pool.get( pool.getSecurityManager().getSystemSubject() );
            final SystemExport.StatusCallback callback = new SystemExport.StatusCallback() {
                public void startCollection( String path )
                {
                    progress.setString( path );
                }


                public void startDocument( String name, int current, int count )
                {
                    progress.setString( name );
                    progress.setValue( progress.getValue() + 1 );
                }


                public void error( String message, Throwable exception )
                {
                    displayMessage( message );

                    if( exception != null ) {
                        displayMessage( exception.toString() );
                    }
                    displayMessage( "---------------------------------------------------" );
                }
            };
            progress.setIndeterminate( false );
            progress.setValue( 0 );
            progress.setStringPainted( true );
            progress.setMinimum( 0 );
            progress.setMaximum( documentCount );

            Object[] selected     = directAccessBtn.getSelectedObjects();
            final boolean  directAccess = ( selected != null ) && ( selected[0] != null );

            selected = incrementalBtn.getSelectedObjects();
            final boolean      incremental = ( selected != null ) && ( selected[0] != null );
            
            selected = zipBtn.getSelectedObjects();
            final boolean zip = ( selected != null ) && ( selected[0] != null );

            displayMessage( "Starting export ..." );
            final long start = System.currentTimeMillis();
            final SystemExport sysexport   = new SystemExport( broker, callback, null, directAccess );
            final File         file        = sysexport.export( exportTarget, incremental, zip, errorList );

            displayMessage( "Export to " + file.getAbsolutePath() + " completed successfully." );
            displayMessage( "Export took " + (System.currentTimeMillis() - start) + "ms.");
            progress.setString( "" );
        }
        catch( final EXistException e ) {
            System.err.println( "ERROR: Failed to retrieve database broker: " + e.getMessage() );
        }
        finally {
            pool.release( broker );
            progress.setValue( 0 );
            currentTask.setText( " " );
        }
    }


    private List<ErrorReport> checkDB()
    {
        if( !startDB() ) {
            return( null );
        }
        DBBroker broker = null;

        try {
            broker = pool.get( pool.getSecurityManager().getSystemSubject() );
            Object[] selected     = directAccessBtn.getSelectedObjects();
            final boolean directAccess = ( selected != null ) && ( selected[0] != null );
            selected = scanBtn.getSelectedObjects();
            final boolean scan = ( selected != null ) && ( selected[0] != null );

            final ConsistencyCheck                                   checker      = new ConsistencyCheck( broker, directAccess, scan );
            final org.exist.backup.ConsistencyCheck.ProgressCallback cb           = new ConsistencyCheck.ProgressCallback() {
                public void startDocument( String path, int current, int count )
                {
                    progress.setString( path );
                    progress.setValue( progress.getValue() + 1 );
                }


                public void error( ErrorReport error )
                {
                    displayMessage( error.toString() );
                    displayMessage( "---------------------------------------------------" );
                }


                public void startCollection( String path )
                {
                    progress.setString( path );
                }
            };

            progress.setIndeterminate( true );
            messages.setText( "" );
            displayMessage( "Checking collections ..." );
            final List<ErrorReport> errors = checker.checkCollectionTree( cb );

            if( errors.size() == 0 ) {
                displayMessage( "No errors found." );
            } else {
                displayMessage( "Errors found." );
            }

            progress.setStringPainted( true );
            progress.setString( "Counting documents ..." );
            documentCount = checker.getDocumentCount();
            progress.setIndeterminate( false );

            progress.setValue( 0 );
            progress.setMinimum( 0 );
            progress.setMaximum( documentCount );

            displayMessage( "Checking documents ..." );
            checker.checkDocuments( cb, errors );

            if( errors.size() == 0 ) {
                displayMessage( "No errors found." );
            } else {
                displayMessage( "Errors found." );
            }
            progress.setString( "" );
            return( errors );
        }
        catch( final EXistException e ) {
            System.err.println( "ERROR: Failed to retrieve database broker: " + e.getMessage() );
        }
        catch (final PermissionDeniedException pde) {
            System.err.println( "ERROR: Failed to retrieve database broker: " + pde.getMessage() );
        }
        catch( final TerminatedException e ) {
            System.err.println( "WARN: Check terminated by db." );
        }
        finally {
            pool.release( broker );
            progress.setValue( 0 );
            currentTask.setText( " " );
        }
        return( null );
    }


    public void displayMessage( String message )
    {
        messages.append( message + '\n' );
        messages.setCaretPosition( messages.getDocument().getLength() );

        if( logWriter != null ) {
            logWriter.println( message );
        }
    }


    private void openLog( String dir )
    {
        try {
            final File         file = SystemExport.getUniqueFile( "report", ".log", dir );
            final OutputStream os   = new BufferedOutputStream( new FileOutputStream( file ) );
            logWriter = new PrintWriter( new OutputStreamWriter( os, UTF_8 ) );
        }
        catch( final FileNotFoundException e ) {
            System.err.println( "ERROR: failed to create log file" );
        }
    }


    private void closeLog()
    {
        if( logWriter != null ) {
            logWriter.close();
        }
    }


    /**
     * DOCUMENT ME!
     *
     * @param  args  the command line arguments
     */
    public static void main( String[] args )
    {
        java.awt.EventQueue.invokeLater( new Runnable() {
                public void run()
                {
                    new ExportGUI().setVisible( true );
                }
            } );
    }
}
