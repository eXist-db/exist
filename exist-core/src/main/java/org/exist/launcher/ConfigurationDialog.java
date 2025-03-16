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
package org.exist.launcher;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.swing.*;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang3.SystemUtils;
import org.exist.collections.CollectionCache;
import org.exist.storage.BrokerPool;
import org.exist.storage.DefaultCacheManager;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;

import static org.exist.launcher.ConfigurationUtility.LAUNCHER_PROPERTY_MAX_MEM;
import static org.exist.launcher.ConfigurationUtility.LAUNCHER_PROPERTY_MIN_MEM;

/**
 *
 * @author wolf
 */


public class ConfigurationDialog extends JDialog {

    private final Consumer<Boolean> callback;
    private boolean changed = false;
    private boolean dataDirChanged = false;
    private boolean jettyConfigChanged = false;
    private boolean beforeStart = false;

    /**
     * Creates new form ConfigurationDialog
     *
     * @param callback a callback for after when the configuration is saved
     *     and the dialog is dismissed.
     */
    public ConfigurationDialog(Consumer<Boolean> callback) {
        setModal(true);
        setTitle("eXist-db System Configuration");

        initComponents();

        this.callback = callback;
        
        final Properties launcherProperties = ConfigurationUtility.loadProperties();
        final int maxMemProp = Integer.parseInt(launcherProperties.getProperty(LAUNCHER_PROPERTY_MAX_MEM, "2048"));
        maxMemory.setValue(maxMemProp);
        final int minMemProp = Integer.parseInt(launcherProperties.getProperty(LAUNCHER_PROPERTY_MIN_MEM, "64"));
        minMemory.setValue(minMemProp);
        
        try {
            Configuration existConfig = new Configuration();
            final int cacheSizeProp = existConfig.getInteger(DefaultCacheManager.PROPERTY_CACHE_SIZE);
            cacheSize.setValue(cacheSizeProp);
            
            final int collectionCacheProp = existConfig.getInteger(CollectionCache.PROPERTY_CACHE_SIZE_BYTES);
            collectionCache.setValue(collectionCacheProp / 1024 / 1024); // show in MB

            final Path dir = (Path)existConfig.getProperty(BrokerPool.PROPERTY_DATA_DIR);
            dataDir.setText(dir.toAbsolutePath().toString());

            final Map<String, Integer> ports = ConfigurationUtility.getJettyPorts();
            if (ports.containsKey("jetty.port")) {
                httpPort.setValue(ports.get("jetty.port"));
            }
            if (ports.containsKey("jetty.http.port")) {
                httpPort.setValue(ports.get("jetty.http.port"));
            }
            if (ports.containsKey("ssl.port")) {
                sslPort.setValue(ports.get("ssl.port"));
            }
            if (ports.containsKey("jetty.ssl.port")) {
                sslPort.setValue(ports.get("jetty.ssl.port"));
            }
        } catch (DatabaseConfigurationException ex) {
            Logger.getLogger(ConfigurationDialog.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        checkCacheBoundaries();

        changed = false;

        final Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(d.width - this.getWidth() - 40, 60);
        //setLocationRelativeTo(null);

        setAlwaysOnTop(true);
    }

    public void open(boolean firstStart) {
        if (firstStart) {
            beforeStart = true;
            // always check data dir on first start
            dataDirChanged = true;
            btnCancel.setVisible(false);
            lbStartupMsg.setVisible(true);
            lbStartupWarn.setVisible(true);

            if (SystemUtils.IS_OS_MAC_OSX) {
                Path dir = Paths.get(System.getProperty("user.home")).resolve("Library").resolve("Application Support").resolve("org.exist");
                dataDir.setText(dir.toAbsolutePath().toString());
            }
        } else {
            lbStartupMsg.setVisible(false);
            lbStartupWarn.setVisible(false);
        }
        setVisible(true);
        requestFocus();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        lbExistLogo = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        minMemory = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        maxMemory = new javax.swing.JSpinner();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        cacheSize = new javax.swing.JSpinner();
        jLabel7 = new javax.swing.JLabel();
        collectionCache = new javax.swing.JSpinner();
        jLabel8 = new javax.swing.JLabel();
        lbCurrentUsage = new javax.swing.JLabel();
        lbStartupMsg = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        dataDir = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        btnPanel = new javax.swing.JPanel();
        btnCancel = new javax.swing.JButton();
        btnSave = new javax.swing.JButton();
        btnSelectDir = new javax.swing.JButton();
        lbStartupWarn = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        httpPort = new javax.swing.JSpinner();
        sslPort = new javax.swing.JSpinner();

        setTitle("eXist-db Configuration");
        getContentPane().setLayout(new java.awt.GridBagLayout());

        lbExistLogo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/exist/client/icons/x.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 6;
        gridBagConstraints.insets = new java.awt.Insets(0, 16, 0, 6);
        getContentPane().add(lbExistLogo, gridBagConstraints);

        jLabel1.setText("Min Memory");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        getContentPane().add(jLabel1, gridBagConstraints);

        minMemory.setModel(new javax.swing.SpinnerNumberModel(64, 64, 256, 64));
        minMemory.addChangeListener(this::minMemoryStateChanged);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        getContentPane().add(minMemory, gridBagConstraints);

        jLabel2.setText("Max Memory");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        getContentPane().add(jLabel2, gridBagConstraints);

        maxMemory.setModel(new javax.swing.SpinnerNumberModel(1024, 512, null, 64));
        maxMemory.addChangeListener(this::maxMemoryChanged);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        getContentPane().add(maxMemory, gridBagConstraints);

        jLabel3.setFont(jLabel3.getFont().deriveFont(jLabel3.getFont().getStyle() | java.awt.Font.BOLD));
        jLabel3.setText("Java Memory");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(26, 22, 16, 0);
        getContentPane().add(jLabel3, gridBagConstraints);

        jLabel4.setFont(jLabel4.getFont().deriveFont(jLabel4.getFont().getStyle() | java.awt.Font.BOLD));
        jLabel4.setText("Caches");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(26, 22, 16, 0);
        getContentPane().add(jLabel4, gridBagConstraints);

        jLabel5.setText("General Cache");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        getContentPane().add(jLabel5, gridBagConstraints);

        cacheSize.setModel(new javax.swing.SpinnerNumberModel(128, 48, 256, 16));
        cacheSize.addChangeListener(this::cacheSizeStateChanged);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        getContentPane().add(cacheSize, gridBagConstraints);

        jLabel7.setText("Collection Cache");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        getContentPane().add(jLabel7, gridBagConstraints);

        collectionCache.setModel(new javax.swing.SpinnerNumberModel(48, 48, 256, 16));
        collectionCache.addChangeListener(this::collectionCacheStateChanged);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        getContentPane().add(collectionCache, gridBagConstraints);

        jLabel8.setText("<html>Memory settings only become effective after restart and only apply when eXist-db is started via the system tray launcher.</html>");
        jLabel8.setPreferredSize(new java.awt.Dimension(280, 48));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 13, 0, 22);
        getContentPane().add(jLabel8, gridBagConstraints);

        lbCurrentUsage.setText("Memory usage (in MB):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 22, 12, 0);
        getContentPane().add(lbCurrentUsage, gridBagConstraints);

        lbStartupMsg.setFont(lbStartupMsg.getFont().deriveFont(lbStartupMsg.getFont().getStyle() & ~java.awt.Font.BOLD));
        lbStartupMsg.setText("<html>It seems you are starting eXist-db for the first time. Please configure your memory settings below.</html>");
        lbStartupMsg.setMinimumSize(new java.awt.Dimension(60, 64));
        lbStartupMsg.setPreferredSize(new java.awt.Dimension(300, 32));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(27, 22, 0, 22);
        getContentPane().add(lbStartupMsg, gridBagConstraints);

        jLabel9.setText("<html>Changing the data directory will create an empty database in the new location (unless there's already data in it).</html>");
        jLabel9.setPreferredSize(new java.awt.Dimension(280, 48));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 13, 0, 22);
        getContentPane().add(jLabel9, gridBagConstraints);

        jLabel10.setFont(jLabel10.getFont().deriveFont(jLabel10.getFont().getStyle() | java.awt.Font.BOLD));
        jLabel10.setText("Data Directory");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(26, 22, 16, 0);
        getContentPane().add(jLabel10, gridBagConstraints);

        dataDir.setMinimumSize(new java.awt.Dimension(180, 28));
        dataDir.setPreferredSize(new java.awt.Dimension(180, 28));
        dataDir.addActionListener(this::dataDirActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        getContentPane().add(dataDir, gridBagConstraints);

        jLabel11.setText("<html>Total cache size should not exceed 1/3 of max memory unless you have more than 2GB available. These sizes are in megabytes.</html>");
        jLabel11.setPreferredSize(new java.awt.Dimension(280, 48));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 13, 0, 22);
        getContentPane().add(jLabel11, gridBagConstraints);

        btnPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        btnCancel.setText("Cancel");
        btnCancel.addActionListener(this::btnCancelActionPerformed);
        btnPanel.add(btnCancel);

        btnSave.setText("Save");
        btnSave.addActionListener(this::saveConfig);
        btnPanel.add(btnSave);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(36, 13, 8, 0);
        getContentPane().add(btnPanel, gridBagConstraints);

        btnSelectDir.setText("Select");
        btnSelectDir.addActionListener(this::btnSelectDirActionPerformed);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 10;
        getContentPane().add(btnSelectDir, gridBagConstraints);

        lbStartupWarn.setFont(new java.awt.Font("Lucida Grande", 1, 13)); // NOI18N
        lbStartupWarn.setForeground(new java.awt.Color(255, 0, 0));
        lbStartupWarn.setText("<html>After startup, use dashboard or Java client to set a password for admin (empty by default).</html>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 22, 12, 22);
        getContentPane().add(lbStartupWarn, gridBagConstraints);

        jLabel12.setText("Jetty Ports");
        jLabel12.setFont(jLabel12.getFont().deriveFont(jLabel12.getFont().getStyle() | java.awt.Font.BOLD));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(26, 22, 16, 0);
        getContentPane().add(jLabel12, gridBagConstraints);

        jLabel13.setText("HTTP Port");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        getContentPane().add(jLabel13, gridBagConstraints);

        httpPort.setModel(new javax.swing.SpinnerNumberModel(8080, 80, 100000, 1));
        httpPort.setEditor(new JSpinner.NumberEditor(httpPort, "#"));
        httpPort.addChangeListener(this::jettyConfigChanged);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        getContentPane().add(httpPort, gridBagConstraints);

        jLabel14.setText("SSL Port");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        getContentPane().add(jLabel14, gridBagConstraints);

        sslPort.setModel(new javax.swing.SpinnerNumberModel(8443, 80, 100000, 1));
        sslPort.setEditor(new JSpinner.NumberEditor(sslPort, "#"));
        sslPort.addChangeListener(this::jettyConfigChanged);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        getContentPane().add(sslPort, gridBagConstraints);

        jLabel15.setText("<html>Set the ports used by the integrated web server. Please make sure " +
                "those ports are not used by other processes.</html>");
        jLabel15.setPreferredSize(new java.awt.Dimension(280, 48));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 13, 0, 22);
        getContentPane().add(jLabel15, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        setVisible(false);
    }//GEN-LAST:event_btnCancelActionPerformed

    private void maxMemoryChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_maxMemoryChanged
        checkCacheBoundaries();
        changed = true;
    }//GEN-LAST:event_maxMemoryChanged

    private boolean checkDataDir() {
        if (!dataDirChanged)
            return true;

        Path dir = Paths.get(dataDir.getText());
        if (Files.exists(dir)) {

            try (final Stream<Path> fileStream = Files.list(dir).filter(p -> FileUtils.fileName(p).endsWith(".dbx"))) {
                final boolean dbExists = fileStream.findFirst().isPresent();
                if (dbExists) {
                    final int r = JOptionPane.showConfirmDialog(this, "The specified data directory already contains data. " +
                            "Do you want to use this? Data will not be removed.", "Confirm Data Directory", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (r == JOptionPane.OK_OPTION) {
                        return true;
                    }
                    return false;
                }
            } catch (final IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to enumerate data files from directory: " + dir.toAbsolutePath(),
                    "Failed to enumerate data files", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } else {
            final int r = JOptionPane.showConfirmDialog(this, "The specified data directory does not exist. Do you want to create it?",
                "Create data directory?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r == JOptionPane.YES_OPTION) {
                try {
                    Files.createDirectories(dir);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Failed to create data directory: " + dir.toAbsolutePath(),
                            "Failed to create directory", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                return true;
            }
            return false;
        }
        if (!Files.isWritable(dir)) {
            JOptionPane.showMessageDialog(this, "The specified data directory is not writable. " +
                    "Please choose a different one.", "Data Directory Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private void saveConfig(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveConfig
        if (!beforeStart && !changed && !dataDirChanged && !jettyConfigChanged) {
            setVisible(false);
            return;
        }
        if (!checkDataDir())
            return;
        try {
            final Properties properties = new Properties();
            properties.setProperty("memory.max", maxMemory.getValue().toString());
            properties.setProperty("memory.min", minMemory.getValue().toString());

            // save the launcher properties
            ConfigurationUtility.saveProperties(properties);

            properties.clear();

            // update conf.xml
            properties.setProperty("cacheSize", cacheSize.getValue().toString());
            properties.setProperty("collectionCache", collectionCache.getValue().toString());
            properties.setProperty("dataDir", dataDir.getText());
            ConfigurationUtility.saveConfiguration("conf.xml", "conf.xsl", properties);

            properties.clear();

            if (jettyConfigChanged) {
                // update Jetty confs
                properties.setProperty("port", httpPort.getValue().toString());
                properties.setProperty("port.ssl", sslPort.getValue().toString());
                ConfigurationUtility.saveConfiguration("jetty/jetty-ssl.xml", "jetty.xsl", properties);
                ConfigurationUtility.saveConfiguration("jetty/jetty-http.xml", "jetty.xsl", properties);
            }

            if (beforeStart) {
                beforeStart = false;
                btnCancel.setVisible(true);
                setVisible(false);
                callback.accept(true);
            } else if (changed || dataDirChanged || jettyConfigChanged) {
                int r = JOptionPane.showConfirmDialog(this, "Database needs to be restarted to apply the " +
                            "new settings.", "Confirm restart", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r == JOptionPane.YES_OPTION) {
                    changed = false;
                    dataDirChanged = false;
                    setVisible(false);
                    callback.accept(true);
                }
            }
        } catch (final IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save Java settings: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        } catch (TransformerException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save configuration: " + e.getMessage() +
                    " at " + e.getLocationAsString(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_saveConfig

    private void cacheSizeStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_cacheSizeStateChanged
        changed = true;
        checkCacheBoundaries();
    }//GEN-LAST:event_cacheSizeStateChanged

    private void collectionCacheStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_collectionCacheStateChanged
        changed = true;
    }//GEN-LAST:event_collectionCacheStateChanged

    private void minMemoryStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_minMemoryStateChanged
        changed = true;
    }//GEN-LAST:event_minMemoryStateChanged

    private void dataDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataDirActionPerformed
        dataDirChanged = true;
    }//GEN-LAST:event_dataDirActionPerformed

    private void jettyConfigChanged(javax.swing.event.ChangeEvent evt) {
        jettyConfigChanged = true;
    }

    private void btnSelectDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelectDirActionPerformed
        final Optional<Path> currentDir = Optional.ofNullable(dataDir.getText())
                .map(d -> Optional.of(Paths.get(d)))
                .filter(md -> md.map(Files::exists).orElse(false))
                .orElse(ConfigurationHelper.getExistHome());

        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        currentDir.map(Path::toFile).ifPresent(chooser::setCurrentDirectory);

        if(chooser.showDialog(this, "Choose Data Directory") == JFileChooser.APPROVE_OPTION) {
            dataDir.setText(chooser.getSelectedFile().getAbsolutePath());
            dataDirChanged = true;
        }
    }//GEN-LAST:event_btnSelectDirActionPerformed

    private void checkCacheBoundaries() {
        showCurrentMem();
        final int max = (Integer)maxMemory.getValue();
        final SpinnerNumberModel cacheModel = (SpinnerNumberModel) cacheSize.getModel();
        final SpinnerNumberModel collectionCacheModel = (SpinnerNumberModel) collectionCache.getModel();
        int maxCache;
        if (max <= 2048) {
            maxCache = (max / 3);
        } else {
            maxCache = (max / 2);
        }
        cacheModel.setMaximum(maxCache - 48);
        if (((Integer)cacheModel.getMaximum()).compareTo((Integer)cacheModel.getValue()) < 0) {
            cacheModel.setValue(cacheModel.getMaximum());
        }
        collectionCacheModel.setMaximum(maxCache - (Integer)cacheModel.getValue());
        if (((Integer)collectionCacheModel.getMaximum()).compareTo((Integer)collectionCacheModel.getValue()) < 0) {
            collectionCacheModel.setValue(collectionCacheModel.getMaximum());
        }
    }

    private void showCurrentMem() {
        lbCurrentUsage.setText("Memory usage: " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) +
            " free/" + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " max mb");
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JPanel btnPanel;
    private javax.swing.JButton btnSave;
    private javax.swing.JButton btnSelectDir;
    private javax.swing.JSpinner cacheSize;
    private javax.swing.JSpinner collectionCache;
    private javax.swing.JTextField dataDir;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel lbCurrentUsage;
    private javax.swing.JLabel lbExistLogo;
    private javax.swing.JLabel lbStartupMsg;
    private javax.swing.JLabel lbStartupWarn;
    private javax.swing.JSpinner maxMemory;
    private javax.swing.JSpinner minMemory;
    private javax.swing.JSpinner httpPort;
    private javax.swing.JSpinner sslPort;
    // End of variables declaration//GEN-END:variables

}
