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
package org.exist.backup;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.start.StartException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.OSUtil;
import org.exist.util.SystemExitCodes;
import org.exist.xquery.TerminatedException;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.CommandLineParser;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.ThreadUtils.newGlobalThread;
import static org.exist.util.ThreadUtils.newInstanceThread;
import static se.softhouse.jargo.Arguments.helpArgument;


/**
 * DOCUMENT ME!
 *
 * @author wolf
 */
public class ExportGUI extends javax.swing.JFrame {
    private static final long serialVersionUID = -8104424554660744639L;

    /* general arguments */
    private static final Argument<?> helpArg = helpArgument("-h", "--help");

    private BrokerPool pool = null;
    private int documentCount = 0;
    private PrintWriter logWriter = null;

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnChangeDir;
    private javax.swing.JButton btnConfSelect;
    private javax.swing.JLabel currentTask;
    private javax.swing.JTextField dbConfig;
    private javax.swing.JButton exportBtn;
    private javax.swing.JCheckBox zipBtn;
    private javax.swing.JCheckBox incrementalBtn;
    private JCheckBox directAccessBtn;
    private javax.swing.JCheckBox scanBtn;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JMenuItem menuQuit;
    private javax.swing.JTextArea messages;
    private javax.swing.JTextField outputDir;
    private javax.swing.JProgressBar progress;
    private javax.swing.JButton startBtn;
    // End of variables declaration//GEN-END:variables

    /**
     * Creates new form CheckerGUI.
     */
    public ExportGUI() {
        super("Consistency Check and Repair");
        initComponents();
        final String existHome = System.getProperty("exist.home", "./");
        final Path home = Paths.get(existHome).normalize();
        dbConfig.setText(
                Optional.ofNullable(System.getProperty("exist.configurationFile")).map(Paths::get)
                        .orElse(home.resolve("etc").resolve("conf.xml"))
                        .toAbsolutePath().toString());
        outputDir.setText(home.resolve("export").toAbsolutePath().toString());
    }

    protected boolean checkOutputDir() {
        final Path dir = Paths.get(outputDir.getText()).normalize();

        if (!Files.exists(dir)) {

            if (JOptionPane.showConfirmDialog(this, "The output directory " + dir.toAbsolutePath().toString() + " does not exist. Create it?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try {
                    Files.createDirectories(dir);
                } catch (final IOException e) {
                    JOptionPane.showMessageDialog(this, "Could not create output dir: " + dir.toAbsolutePath().toString(), "Configuration Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                    System.err.println("ERROR: Failed to create output dir: " + e.getMessage());
                }
            } else {
                return false;
            }
        }
        return true;
    }


    protected boolean startDB() {
        if (pool != null) {
            return true;
        }
        final Path confFile = Paths.get(dbConfig.getText()).normalize();

        if (!(Files.exists(confFile) && Files.isReadable(confFile))) {
            JOptionPane.showMessageDialog(this, "The selected database configuration file " + confFile.toAbsolutePath().toString() + " does not exist or is not readable.", "Configuration Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            final Configuration config = new Configuration(confFile.toAbsolutePath().toString(), Optional.empty());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
            return true;
        } catch (final Exception e) {
            JOptionPane.showMessageDialog(this, "Could not start the database instance. Please remember\n" + "that this tool tries to launch an embedded db instance. No other db instance should\n" + "be running on the same data.", "DB Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.err.println("ERROR: Failed to open database: " + e.getMessage());
        }
        return false;
    }


    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated
    // Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        currentTask = new javax.swing.JLabel();
        progress = new javax.swing.JProgressBar();
        jScrollPane2 = new javax.swing.JScrollPane();
        messages = new javax.swing.JTextArea();
        jToolBar1 = new javax.swing.JToolBar();
        startBtn = new javax.swing.JButton();
        exportBtn = new javax.swing.JButton();
        incrementalBtn = new JCheckBox("Incremental");
        scanBtn = new JCheckBox("Scan docs");
        directAccessBtn = new JCheckBox("Direct access");
        zipBtn = new JCheckBox("Create ZIP");
        zipBtn.setSelected(true);
        outputDir = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        btnChangeDir = new javax.swing.JButton();
        dbConfig = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        btnConfSelect = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        menuQuit = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(final java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        currentTask.setText(" ");
        currentTask.setMinimumSize(new java.awt.Dimension(0, 25));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(currentTask, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(progress, gridBagConstraints);

        jScrollPane2.setBorder(javax.swing.BorderFactory.createTitledBorder("Messages"));
        jScrollPane2.setPreferredSize(new java.awt.Dimension(400, 200));

        messages.setColumns(20);
        messages.setLineWrap(true);
        messages.setRows(5);
        messages.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        jScrollPane2.setViewportView(messages);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(jScrollPane2, gridBagConstraints);

        jToolBar1.setRollover(true);

        startBtn.setText("Check");
        startBtn.setFocusable(false);
        startBtn.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        startBtn.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        startBtn.addActionListener(this::startBtncheck);
        jToolBar1.add(startBtn);

        exportBtn.setText("Check & Export");
        exportBtn.setFocusable(false);
        exportBtn.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        exportBtn.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        exportBtn.addActionListener(this::exportBtnActionPerformed);
        jToolBar1.add(exportBtn);

        jToolBar1.add(incrementalBtn);
        scanBtn.setSelected(true);
        scanBtn.setToolTipText("Perform additional checks; scans every XML document");
        jToolBar1.add(scanBtn);
        directAccessBtn.setToolTipText("Bypass collection index by scanning collection store");
        jToolBar1.add(directAccessBtn);
        jToolBar1.add(zipBtn);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(jToolBar1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(outputDir, gridBagConstraints);

        jLabel1.setText("Output Directory:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(jLabel1, gridBagConstraints);

        btnChangeDir.setText("Change");
        btnChangeDir.addActionListener(this::btnChangeDirActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(btnChangeDir, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(dbConfig, gridBagConstraints);

        jLabel2.setText("DB Configuration:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(jLabel2, gridBagConstraints);

        btnConfSelect.setText("Select");
        btnConfSelect.setMaximumSize(new java.awt.Dimension(75, 24));
        btnConfSelect.setMinimumSize(new java.awt.Dimension(75, 24));
        btnConfSelect.setPreferredSize(new java.awt.Dimension(75, 24));
        btnConfSelect.addActionListener(this::btnConfSelectActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        getContentPane().add(btnConfSelect, gridBagConstraints);

        jMenu1.setText("File");

        menuQuit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_MASK));
        menuQuit.setText("Quit");
        menuQuit.addActionListener(this::menuQuitActionPerformed);
        jMenu1.add(menuQuit);

        jMenuBar1.add(jMenu1);

        setJMenuBar(jMenuBar1);

        pack();
    } // </editor-fold>//GEN-END:initComponents


    private void formWindowClosed(final java.awt.event.WindowEvent evt) { // GEN-FIRST:event_formWindowClosed
        BrokerPool.stopAll(false);
    } // GEN-LAST:event_formWindowClosed


    private void startBtncheck(final java.awt.event.ActionEvent evt) { // GEN-FIRST:event_startBtncheck

        if (!checkOutputDir()) {
            return;
        }
        final Runnable checkRun = () -> {
            openLog(outputDir.getText());

            try {
                checkDB();
            } finally {
                closeLog();
            }
        };
        if (pool != null) {
            newInstanceThread(pool, "export-gui.check-run", checkRun).start();
        } else {
            newGlobalThread("export-gui.check-run", checkRun).start();
        }
    } // GEN-LAST:event_startBtncheck


    private void exportBtnActionPerformed(final java.awt.event.ActionEvent evt) { // GEN-FIRST:event_exportBtnActionPerformed

        if (!checkOutputDir()) {
            return;
        }
        final Runnable th = () -> {
            openLog(outputDir.getText());

            try {
                currentTask.setText("Checking database consistency ...");
                final List<ErrorReport> errors = checkDB();
                currentTask.setText("Exporting data ...");
                exportDB(outputDir.getText(), errors);
            } finally {
                closeLog();
            }
        };
        if (pool != null) {
            newInstanceThread(pool, "export-gui.export", th).start();
        } else {
            newGlobalThread("export-gui.export", th).start();
        }
    } // GEN-LAST:event_exportBtnActionPerformed


    private void btnChangeDirActionPerformed(final java.awt.event.ActionEvent evt) { // GEN-FIRST:event_btnChangeDirActionPerformed
        final Path dir = Paths.get(outputDir.getText());
        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setSelectedFile(dir.resolve("export").toFile());
        chooser.setCurrentDirectory(dir.toFile());

        if (chooser.showDialog(this, "Export") == JFileChooser.APPROVE_OPTION) {
            outputDir.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    } // GEN-LAST:event_btnChangeDirActionPerformed


    private void menuQuitActionPerformed(final java.awt.event.ActionEvent evt) { // GEN-FIRST:event_menuQuitActionPerformed
        BrokerPool.stopAll(false);
        System.exit(SystemExitCodes.OK_EXIT_CODE);
    } // GEN-LAST:event_menuQuitActionPerformed


    private void btnConfSelectActionPerformed(final java.awt.event.ActionEvent evt) { // GEN-FIRST:event_btnConfSelectActionPerformed
        final Path dir = Paths.get(dbConfig.getText()).normalize().getParent();
        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(Optional.ofNullable(System.getProperty("exist.configurationFile"))
                .map(Paths::get)
                .orElse(dir.resolve("etc").resolve("conf.xml"))
                .toFile());
        chooser.setCurrentDirectory(dir.resolve("etc").toFile());
        chooser.setFileFilter(new FileFilter() {
            public boolean accept(final File f) {
                if (f.isDirectory()) {
                    return (true);
                }
                final MimeType mime = MimeTable.getInstance().getContentTypeFor(f.getName());

                if (mime == null) {
                    return false;
                }
                return mime.isXMLType();
            }


            public String getDescription() {
                return ("Database XML configuration file");
            }

        });

        if (chooser.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION) {
            dbConfig.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    } // GEN-LAST:event_btnConfSelectActionPerformed


    private void exportDB(final String exportTarget, final List<ErrorReport> errorList) {
        if (!startDB()) {
            return;
        }

        try {
            final SystemExport.StatusCallback callback = new SystemExport.StatusCallback() {
                public void startCollection(final String path) {
                    progress.setString(path);
                }


                public void startDocument(final String name, final int current, final int count) {
                    progress.setString(name);
                    progress.setValue(progress.getValue() + 1);
                }


                public void error(final String message, final Throwable exception) {
                    displayMessage(message);

                    if (exception != null) {
                        displayMessage(exception.toString());
                    }
                    displayMessage("---------------------------------------------------");
                }
            };
            progress.setIndeterminate(false);
            progress.setValue(0);
            progress.setStringPainted(true);
            progress.setMinimum(0);
            progress.setMaximum(documentCount);

            Object[] selected = directAccessBtn.getSelectedObjects();
            final boolean directAccess = (selected != null) && (selected[0] != null);

            selected = incrementalBtn.getSelectedObjects();
            final boolean incremental = (selected != null) && (selected[0] != null);

            selected = zipBtn.getSelectedObjects();
            final boolean zip = (selected != null) && (selected[0] != null);

            displayMessage("Starting export ...");
            final long start = System.currentTimeMillis();

            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                 final Txn transaction = pool.getTransactionManager().beginTransaction()) {

                final SystemExport sysexport = new SystemExport(broker, transaction, callback, null, directAccess);
                final Path file = sysexport.export(exportTarget, incremental, zip, errorList);

                transaction.commit();


                final long end = System.currentTimeMillis();

                displayMessage("Export to " + file.toAbsolutePath().toString() + " completed successfully.");
                displayMessage("Export took " + (end - start) + "ms.");

            } catch (final EXistException e) {
                System.err.println("ERROR: Failed to retrieve database broker: " + e.getMessage());
            }

        } finally {
            progress.setString("");
            progress.setValue(0);
            currentTask.setText(" ");
        }
    }


    private List<ErrorReport> checkDB() {
        if (!startDB()) {
            return (null);
        }

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            Object[] selected = directAccessBtn.getSelectedObjects();
            final boolean directAccess = (selected != null) && (selected[0] != null);
            selected = scanBtn.getSelectedObjects();
            final boolean scan = (selected != null) && (selected[0] != null);

            final ConsistencyCheck checker = new ConsistencyCheck(broker, transaction, directAccess, scan);
            final org.exist.backup.ConsistencyCheck.ProgressCallback cb = new ConsistencyCheck.ProgressCallback() {
                public void startDocument(final String path, final int current, final int count) {
                    progress.setString(path);
                    progress.setValue(progress.getValue() + 1);
                }


                public void error(final ErrorReport error) {
                    displayMessage(error.toString());
                    displayMessage("---------------------------------------------------");
                }


                public void startCollection(final String path) {
                    progress.setString(path);
                }
            };

            progress.setIndeterminate(true);
            messages.setText("");
            displayMessage("Checking collections ...");
            final List<ErrorReport> errors = checker.checkCollectionTree(cb);

            if (errors.isEmpty()) {
                displayMessage("No errors found.");
            } else {
                displayMessage("Errors found.");
            }

            progress.setStringPainted(true);
            progress.setString("Counting documents ...");
            documentCount = checker.getDocumentCount();
            progress.setIndeterminate(false);

            progress.setValue(0);
            progress.setMinimum(0);
            progress.setMaximum(documentCount);

            displayMessage("Checking documents ...");
            checker.checkDocuments(cb, errors);

            if (errors.isEmpty()) {
                displayMessage("No errors found.");
            } else {
                displayMessage("Errors found.");
            }
            progress.setString("");

            transaction.commit();

            return errors;
        } catch (final EXistException | PermissionDeniedException e) {
            System.err.println("ERROR: Failed to retrieve database broker: " + e.getMessage());
        } catch (final TerminatedException e) {
            System.err.println("WARN: Check terminated by db.");
        } finally {
            progress.setValue(0);
            currentTask.setText(" ");
        }
        return (null);
    }


    public void displayMessage(final String message) {
        messages.append(message + '\n');
        messages.setCaretPosition(messages.getDocument().getLength());

        if (logWriter != null) {
            logWriter.println(message);
        }
    }


    private void openLog(final String dir) {
        final Path file = SystemExport.getUniqueFile("report", ".log", dir);
        try {
            logWriter = new PrintWriter(Files.newBufferedWriter(file, UTF_8));
        } catch(final IOException e) {
            System.err.println("ERROR: failed to create log file");
        }
    }


    private void closeLog() {
        if (logWriter != null) {
            logWriter.close();
        }
    }


    /**
     * Main entry point.
     *
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        try {
            // parse command-line options
            CommandLineParser
                    .withArguments(helpArg)
                    .programName("export-gui" + (OSUtil.isWindows() ? ".bat" : ".sh"))
                    .parse(args);

        } catch (final ArgumentException e) {
            consoleOut(e.getMessageAndUsage().toString());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);
        }

        java.awt.EventQueue.invokeLater(() -> new ExportGUI().setVisible(true));
    }

    private static void consoleOut(final String msg) {
        System.out.println(msg); //NOSONAR this has to go to the console
    }
}
