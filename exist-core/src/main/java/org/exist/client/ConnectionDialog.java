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
package org.exist.client;

import java.awt.Graphics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ConnectionDialog extends javax.swing.JDialog implements DialogWithResponse<Connection> {

    private static final String PROVIDED_PASSWORD_PLACEHOLDER = "__PROVIDED__PASSWORD__";

    private ComboBoxModel connectionTypeModel = null;
    private DefaultListModel favouritesModel = null;
    private final DefaultConnectionSettings defaultConnectionSettings;
    private final boolean disableEmbeddedConnectionType;
    private Path config;

    private final List<DialogCompleteWithResponse<Connection>> dialogCompleteWithResponseCallbacks = new ArrayList<>();

    private enum ConnectionType {
        Remote,
        Embedded
    }

    /**
     *  Creates new form ConnectionForm
     * @param parent Parent window.
     * @param modal modality flag.
     * @param defaultConnectionSettings Default connection settings.
     * @param embeddedByDefault Set TRUE to have embedded mode selected by default.
     * @param disableEmbeddedConnectionType Set to TRUE to force remote connections only.
     */
    public ConnectionDialog(final java.awt.Frame parent, final boolean modal, final DefaultConnectionSettings defaultConnectionSettings, final boolean embeddedByDefault, final boolean disableEmbeddedConnectionType) {
        super(parent, modal);
        this.defaultConnectionSettings = defaultConnectionSettings;
        this.config = Paths.get(defaultConnectionSettings.getConfiguration());
        this.disableEmbeddedConnectionType = disableEmbeddedConnectionType;
        this.setIconImage(InteractiveClient.getExistIcon(getClass()).getImage());
        initComponents();

        if (disableEmbeddedConnectionType) {
            cmbConnectionType.removeItem(ConnectionType.Embedded);
        } else if (embeddedByDefault) {
            cmbConnectionType.setSelectedItem(ConnectionType.Embedded);
            toggleRemoteEmbeddedDisplayTab(false);
        }
        txtPassword.addKeyListener(new EnterKeyAdapter(btnConnect));
        txtPassword.requestFocusInWindow(); //set focus to password field
    }

    private ComboBoxModel getConnectionTypeModel() {
        if (connectionTypeModel == null) {
            connectionTypeModel = new DefaultComboBoxModel(ConnectionType.values());
        }
        return connectionTypeModel;
    }

    private DefaultListModel getFavouritesModel() {
        if (favouritesModel == null) {
            favouritesModel = new DefaultListModel();
            for (final FavouriteConnection favourite : FavouriteConnections.load()) {
                favouritesModel.addElement(favourite);
            }
        }
        return favouritesModel;
    }

    private void storeFavourites(final ListModel model) {

        final List<FavouriteConnection> favourites = new ArrayList<>();

        // Write a node for each item in model.
        for (int i = 0; i < model.getSize(); i++) {
            favourites.add((FavouriteConnection) model.getElementAt(i));
        }

        FavouriteConnections.store(favourites);
    }

    @Override
    public void addDialogCompleteWithResponseCallback(final DialogCompleteWithResponse<Connection> dialogCompleteWithResponseCallback) {
        getDialogCompleteWithResponseCallbacks().add(dialogCompleteWithResponseCallback);
    }

    private List<DialogCompleteWithResponse<Connection>> getDialogCompleteWithResponseCallbacks() {
        return dialogCompleteWithResponseCallbacks;
    }

    private String getLabelText(final String resourceId) {
        return Messages.getString(resourceId) + ":";
    }

    private String getLabel(final String resourceId) {
        return Messages.getString(resourceId);
    }

    public DefaultConnectionSettings getDefaultConnectionSettings() {
        return defaultConnectionSettings;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pmFavourites = new javax.swing.JPopupMenu();
        miRemoveFavourite = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        miImportFavourites = new javax.swing.JMenuItem();
        miExportFavourites = new javax.swing.JMenuItem();
        lblExistLogo = new javax.swing.JLabel();
        lblUsername = new javax.swing.JLabel();
        lblPassword = new javax.swing.JLabel();
        lblConnectionType = new javax.swing.JLabel();
        cmbConnectionType = new javax.swing.JComboBox();
        txtUsername = new javax.swing.JTextField();
        txtPassword = new javax.swing.JPasswordField();
        tpConnectionType = new javax.swing.JTabbedPane();
        tpConnectionType.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {

            @Override
            protected final void paintContentBorder(final java.awt.Graphics g, final int tabPlacement, final int selectedIndex) {
                //dont paint tabs!
            }

            @Override
            protected final void paintContentBorderBottomEdge(final java.awt.Graphics g, final int tabPlacement, final int selectedIndex, final int x, final int y, int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected final void paintContentBorderLeftEdge(final java.awt.Graphics g, final int tabPlacement, final int selectedIndex, final int x, final int y, int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected final void paintContentBorderRightEdge(final java.awt.Graphics g, final int tabPlacement, final int selectedIndex, final int x, final int y, int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected final void paintContentBorderTopEdge(final java.awt.Graphics g, final int tabPlacement, final int selectedIndex, final int x, int y, final int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected final void paintFocusIndicator(final java.awt.Graphics g, final int tabPlacement, final java.awt.Rectangle[] rects, final int tabIndex, final java.awt.Rectangle iconRect, final java.awt.Rectangle textRect, final boolean isSelected) {
                //dont paint tabs!
            }

            @Override
            protected final void paintTab(final java.awt.Graphics g, final int tabPlacement, final java.awt.Rectangle[] rects, final int tabIndex, final java.awt.Rectangle iconRect, final java.awt.Rectangle textRect) {
                //dont paint tabs!
            }

            @Override
            protected final void paintTabArea(final java.awt.Graphics g, final int tabPlacement, final int selectedIndex) {
                //dont paint tabs!
            }

            @Override
            protected final void paintTabBackground(final Graphics g, final int tabPlacement, final int tabIndex, final int x, final int y, final int w, final int h, final boolean isSelected) {
            }

            @Override
            protected final void paintTabBorder(final java.awt.Graphics g, final int tabPlacement, final int tabIndex, final int x, final int y, final int w, final int h, final boolean isSelected) {
                //dont paint tabs!
            }
        });
        panRemote = new javax.swing.JPanel();
        lblServerUri = new javax.swing.JLabel();
        txtServerUri = new javax.swing.JTextField();
        chkSsl = new javax.swing.JCheckBox();
        panEmbedded = new javax.swing.JPanel();
        lblConfiguration = new javax.swing.JLabel();
        txtConfiguration = new javax.swing.JTextField();
        btnSelectConfiguration = new javax.swing.JButton();
        panFavourites = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstFavourites = new javax.swing.JList();
        btnSaveToFavourites = new javax.swing.JButton();
        btnClose = new javax.swing.JButton();
        btnConnect = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();

        miRemoveFavourite.setText("Remove");
        miRemoveFavourite.addActionListener(this::miRemoveFavouriteActionPerformed);
        pmFavourites.add(miRemoveFavourite);
        pmFavourites.add(jSeparator2);

        miImportFavourites.setText("Import Favourites...");
        miImportFavourites.addActionListener(this::miImportFavouritesActionPerformed);
        pmFavourites.add(miImportFavourites);

        miExportFavourites.setText("Export Favourites...");
        miExportFavourites.addActionListener(this::miExportFavouritesActionPerformed);
        pmFavourites.add(miExportFavourites);

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Database Connection");

        lblExistLogo.setIcon(InteractiveClient.getExistIcon(getClass()));

        lblUsername.setText(getLabelText("LoginPanel.2"));

        lblPassword.setText(getLabelText("LoginPanel.3"));

        lblConnectionType.setText(getLabelText("LoginPanel.4"));

        cmbConnectionType.setModel(getConnectionTypeModel());
        cmbConnectionType.addActionListener(this::cmbConnectionTypeActionPerformed);

        txtUsername.setText(getDefaultConnectionSettings().getUsername());
        if (getDefaultConnectionSettings().getPassword() != null
                && !getDefaultConnectionSettings().getPassword().isEmpty()) {
            txtPassword.setText(PROVIDED_PASSWORD_PLACEHOLDER);
        }

        tpConnectionType.setTabPlacement(javax.swing.JTabbedPane.RIGHT);

        lblServerUri.setText(getLabelText("LoginPanel.12"));

        txtServerUri.setText(getDefaultConnectionSettings().getUri());

        chkSsl.setSelected(getDefaultConnectionSettings().isSsl());
        chkSsl.setText(getLabel("LoginPanel.47"));

        javax.swing.GroupLayout panRemoteLayout = new javax.swing.GroupLayout(panRemote);
        panRemote.setLayout(panRemoteLayout);
        panRemoteLayout.setHorizontalGroup(
                panRemoteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panRemoteLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(lblServerUri)
                                .addGap(63, 63, 63)
                                .addGroup(panRemoteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(chkSsl)
                                        .addComponent(txtServerUri, javax.swing.GroupLayout.PREFERRED_SIZE, 336, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panRemoteLayout.setVerticalGroup(
                panRemoteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panRemoteLayout.createSequentialGroup()
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(panRemoteLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(lblServerUri)
                                        .addComponent(txtServerUri, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkSsl)
                                .addGap(25, 25, 25))
        );

        tpConnectionType.addTab("tab3", panRemote);

        lblConfiguration.setText(getLabelText("LoginPanel.8"));

        txtConfiguration.setEditable(false);
        txtConfiguration.setText(config.toAbsolutePath().toString());
        txtConfiguration.setToolTipText(getLabel("LoginPanel.9"));

        btnSelectConfiguration.setText("...");
        btnSelectConfiguration.setToolTipText(getLabel("LoginPanel.11"));
        btnSelectConfiguration.addActionListener(this::btnSelectConfigurationActionPerformed);

        javax.swing.GroupLayout panEmbeddedLayout = new javax.swing.GroupLayout(panEmbedded);
        panEmbedded.setLayout(panEmbeddedLayout);
        panEmbeddedLayout.setHorizontalGroup(
                panEmbeddedLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panEmbeddedLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(lblConfiguration)
                                .addGap(33, 33, 33)
                                .addComponent(txtConfiguration, javax.swing.GroupLayout.DEFAULT_SIZE, 279, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnSelectConfiguration, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
        );
        panEmbeddedLayout.setVerticalGroup(
                panEmbeddedLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panEmbeddedLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(panEmbeddedLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(lblConfiguration)
                                        .addComponent(txtConfiguration, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(btnSelectConfiguration, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(38, Short.MAX_VALUE))
        );

        tpConnectionType.addTab("tab2", panEmbedded);

        panFavourites.setBorder(javax.swing.BorderFactory.createTitledBorder(getLabel("LoginPanel.14")));

        lstFavourites.setModel(getFavouritesModel());
        lstFavourites.setComponentPopupMenu(pmFavourites);
        lstFavourites.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lstFavouritesMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(lstFavourites);

        btnSaveToFavourites.setText(getLabel("LoginPanel.17"));
        btnSaveToFavourites.addActionListener(this::btnSaveToFavouritesActionPerformed);

        javax.swing.GroupLayout panFavouritesLayout = new javax.swing.GroupLayout(panFavourites);
        panFavourites.setLayout(panFavouritesLayout);
        panFavouritesLayout.setHorizontalGroup(
                panFavouritesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panFavouritesLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(panFavouritesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 463, Short.MAX_VALUE)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panFavouritesLayout.createSequentialGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                                .addComponent(btnSaveToFavourites)))
                                .addContainerGap())
        );
        panFavouritesLayout.setVerticalGroup(
                panFavouritesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(panFavouritesLayout.createSequentialGroup()
                                .addComponent(btnSaveToFavourites)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 143, Short.MAX_VALUE)
                                .addContainerGap())
        );

        btnClose.setText(getLabel("LoginPanel.51"));
        btnClose.addActionListener(this::btnCloseActionPerformed);

        btnConnect.setText(getLabel("LoginPanel.50"));
        btnConnect.addKeyListener(new EnterKeyAdapter());
        btnConnect.addActionListener(this::btnConnectActionPerformed);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                                .addComponent(btnClose)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(btnConnect))
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addGap(8, 8, 8)
                                                                .addComponent(lblExistLogo, javax.swing.GroupLayout.PREFERRED_SIZE, 113, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addGap(18, 18, 18)
                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(panFavourites, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addComponent(tpConnectionType, javax.swing.GroupLayout.PREFERRED_SIZE, 527, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addGroup(layout.createSequentialGroup()
                                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                                        .addComponent(lblPassword, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
                                                                                        .addComponent(lblConnectionType, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                                                                .addGap(6, 6, 6)
                                                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                                        .addComponent(txtPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                        .addComponent(txtUsername, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                        .addComponent(cmbConnectionType, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                                                        .addComponent(lblUsername, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 666, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addGap(0, 0, Short.MAX_VALUE)))
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(14, 14, 14)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(lblUsername)
                                                        .addComponent(txtUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addGap(18, 18, 18)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(lblPassword)
                                                        .addComponent(txtPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addGap(26, 26, 26)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(lblConnectionType)
                                                        .addComponent(cmbConnectionType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(22, 22, 22)
                                                .addComponent(lblExistLogo, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tpConnectionType, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(panFavourites, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(btnClose)
                                        .addComponent(btnConnect))
                                .addContainerGap(35, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConnectActionPerformed

        final String password = Arrays.equals(txtPassword.getPassword(), PROVIDED_PASSWORD_PLACEHOLDER.toCharArray()) ? getDefaultConnectionSettings().getPassword() : new String(txtPassword.getPassword());
        final Connection connection;
        if (cmbConnectionType.getSelectedItem() == ConnectionType.Remote) {
            connection = new Connection(txtUsername.getText(), password, txtServerUri.getText(), chkSsl.isSelected());
        } else {
            connection = new Connection(txtUsername.getText(), password, txtConfiguration.getText());
        }

        for (final DialogCompleteWithResponse<Connection> callback : getDialogCompleteWithResponseCallbacks()) {
            callback.complete(connection);
        }

        setVisible(false);
        dispose();
    }//GEN-LAST:event_btnConnectActionPerformed

    private void cmbConnectionTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbConnectionTypeActionPerformed
        final boolean remote = (((ConnectionType) cmbConnectionType.getSelectedItem()) == ConnectionType.Remote);

        toggleRemoteEmbeddedDisplayTab(remote);
    }//GEN-LAST:event_cmbConnectionTypeActionPerformed

    private void toggleRemoteEmbeddedDisplayTab(final boolean remote) {
        //remote controls
        lblServerUri.setEnabled(remote);
        lblServerUri.setVisible(remote);
        txtServerUri.setEnabled(remote);
        txtServerUri.setVisible(remote);
        chkSsl.setEnabled(remote);
        chkSsl.setVisible(remote);

        //embedded controls
        lblConfiguration.setEnabled(!remote);
        lblConfiguration.setVisible(!remote);
        txtConfiguration.setEnabled(!remote);
        txtConfiguration.setVisible(!remote);
        btnSelectConfiguration.setEnabled(!remote);
        btnSelectConfiguration.setVisible(!remote);

        if (remote) {
            tpConnectionType.setSelectedIndex(0);
            if (txtServerUri.getText().isEmpty()) {
                txtServerUri.setText(defaultConnectionSettings.getUri());
                chkSsl.setSelected(defaultConnectionSettings.isSsl());
            }
        } else {
            tpConnectionType.setSelectedIndex(1);
            if (txtConfiguration.getText().isEmpty()) {
                txtConfiguration.setText(defaultConnectionSettings.getConfiguration());
            }
        }
    }

    private void btnSelectConfigurationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelectConfigurationActionPerformed
        final JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (config != null && config.getParent() != null) {
            chooser.setCurrentDirectory(config.getParent().toFile());
        }
        if (chooser.showDialog(this, Messages.getString("LoginPanel.37")) == JFileChooser.APPROVE_OPTION) {
            config = chooser.getSelectedFile().toPath();
            txtConfiguration.setText(config.toAbsolutePath().toString());
        }
    }//GEN-LAST:event_btnSelectConfigurationActionPerformed

    private void btnSaveToFavouritesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveToFavouritesActionPerformed
        final String favouriteName = JOptionPane.showInputDialog(this, "Please enter a name for your favourite:", "Save Favourite", JOptionPane.QUESTION_MESSAGE);

        if (favouriteName != null && !favouriteName.isEmpty()) {
            for (int i = 0; i < getFavouritesModel().getSize(); i++) {
                if (getFavouritesModel().elementAt(i).equals(favouriteName)) {
                    final int result = JOptionPane.showConfirmDialog(this, Messages.getString("LoginPanel.19"), Messages.getString("LoginPanel.20"), JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.NO_OPTION) {
                        return;
                    }
                    getFavouritesModel().remove(i);
                    break;
                }
            }

            final FavouriteConnection favourite;
            if (cmbConnectionType.getSelectedItem() == ConnectionType.Remote) {
                favourite = new FavouriteConnection(
                        favouriteName,
                        txtUsername.getText(),
                        new String(txtPassword.getPassword()),
                        txtServerUri.getText(),
                        chkSsl.isSelected()
                );
            } else {
                favourite = new FavouriteConnection(
                        favouriteName,
                        txtUsername.getText(),
                        new String(txtPassword.getPassword()),
                        config.toAbsolutePath().toString()
                );
            }
            getFavouritesModel().addElement(favourite);
            storeFavourites(getFavouritesModel());
        }
    }//GEN-LAST:event_btnSaveToFavouritesActionPerformed

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_btnCloseActionPerformed

    private void lstFavouritesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lstFavouritesMouseClicked

        if (SwingUtilities.isRightMouseButton(evt)) {
            miRemoveFavourite.setEnabled(!lstFavourites.isSelectionEmpty());
            miExportFavourites.setEnabled(!getFavouritesModel().isEmpty());
        }

        if (evt.getClickCount() == 2 && lstFavourites.getSelectedIndex() >= 0) {
            final FavouriteConnection favourite = (FavouriteConnection) lstFavourites.getSelectedValue();

            final boolean favouriteHasEmbeddedMode = "".equals(favourite.getUri());

            if (disableEmbeddedConnectionType && favouriteHasEmbeddedMode) {
                JOptionPane.showMessageDialog(this, "The favourite connection '" + favourite.getName() + "' uses an Embedded Connection Type, but Embedded Connections have been disabled at client startup.", "Favourite Selection Error", JOptionPane.ERROR_MESSAGE);
                lstFavourites.clearSelection();
            } else {

                txtUsername.setText(favourite.getUsername());
                txtPassword.setText(favourite.getPassword());

                cmbConnectionType.setSelectedItem(favouriteHasEmbeddedMode ? ConnectionType.Embedded : ConnectionType.Remote);
                tpConnectionType.setSelectedIndex(cmbConnectionType.getSelectedIndex());

                txtServerUri.setText(favourite.getUri());
                chkSsl.setSelected(favourite.isSsl());

                txtConfiguration.setText(favourite.getConfiguration());

                txtPassword.requestFocusInWindow(); //set focus to password field
            }
        }
    }//GEN-LAST:event_lstFavouritesMouseClicked

    private void miImportFavouritesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miImportFavouritesActionPerformed
        final Path file = Paths.get("favourites.xml"); //$NON-NLS-1$
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(file.toFile());
        chooser.showOpenDialog(this);
        final Path selectedImportFile = chooser.getSelectedFile().toPath();

        if (selectedImportFile == null) {
            JOptionPane.showMessageDialog(this, Messages.getString("LoginPanel.33"), Messages.getString("LoginPanel.34"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!Files.isReadable(selectedImportFile)) {
            JOptionPane.showMessageDialog(this, Messages.getString("LoginPanel.35"), Messages.getString("LoginPanel.36"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            FavouriteConnections.importFromFile(selectedImportFile);

            //reload the favourites model
            getFavouritesModel().removeAllElements();
            for (final FavouriteConnection favourite : FavouriteConnections.load()) {
                getFavouritesModel().addElement(favourite);
            }
        } catch (final IOException ioe) {
            JOptionPane.showMessageDialog(this, "Unable to read preferences file: " + selectedImportFile.toAbsolutePath() + ": " + ioe.getMessage(), "Error Importing Preferences", JOptionPane.ERROR_MESSAGE);
        } catch (final InvalidPreferencesFormatException ipfe) {
            JOptionPane.showMessageDialog(this, "Invalid format for preferences file: " + selectedImportFile.toAbsolutePath() + ": " + ipfe.getMessage(), "Error Importing Preferences", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_miImportFavouritesActionPerformed

    private void miExportFavouritesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miExportFavouritesActionPerformed
        final Path file = Paths.get(Messages.getString("LoginPanel.25")); //$NON-NLS-1$
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(file.toFile());
        chooser.showSaveDialog(this);

        final Path selectedExportFile = chooser.getSelectedFile().toPath();

        if (selectedExportFile == null) {
            JOptionPane.showMessageDialog(this, Messages.getString("LoginPanel.26"), Messages.getString("LoginPanel.27"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }

        if (Files.exists(selectedExportFile) && !Files.isReadable(selectedExportFile)) {
            JOptionPane.showMessageDialog(this, Messages.getString("LoginPanel.28"), Messages.getString("LoginPanel.29"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }

        try {
            FavouriteConnections.exportToFile(selectedExportFile);
        } catch (final IOException ioe) {
            JOptionPane.showMessageDialog(this, "Unable to write preferences file: " + selectedExportFile.toAbsolutePath() + ": " + ioe.getMessage(), "Error Importing Preferences", JOptionPane.ERROR_MESSAGE);
        } catch (final BackingStoreException bse) {
            JOptionPane.showMessageDialog(this, "Backing store error for export to file: " + selectedExportFile.toAbsolutePath() + ": " + bse.getMessage(), "Error Importing Preferences", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_miExportFavouritesActionPerformed

    private void miRemoveFavouriteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miRemoveFavouriteActionPerformed
        if (!lstFavourites.isSelectionEmpty()) {
            getFavouritesModel().remove(lstFavourites.getSelectedIndex());
            storeFavourites(getFavouritesModel());
        }
    }//GEN-LAST:event_miRemoveFavouriteActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnClose;
    private javax.swing.JButton btnConnect;
    private javax.swing.JButton btnSaveToFavourites;
    private javax.swing.JButton btnSelectConfiguration;
    private javax.swing.JCheckBox chkSsl;
    private javax.swing.JComboBox cmbConnectionType;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JLabel lblConfiguration;
    private javax.swing.JLabel lblConnectionType;
    private javax.swing.JLabel lblExistLogo;
    private javax.swing.JLabel lblPassword;
    private javax.swing.JLabel lblServerUri;
    private javax.swing.JLabel lblUsername;
    private javax.swing.JList lstFavourites;
    private javax.swing.JMenuItem miExportFavourites;
    private javax.swing.JMenuItem miImportFavourites;
    private javax.swing.JMenuItem miRemoveFavourite;
    private javax.swing.JPanel panEmbedded;
    private javax.swing.JPanel panFavourites;
    private javax.swing.JPanel panRemote;
    private javax.swing.JPopupMenu pmFavourites;
    private javax.swing.JTabbedPane tpConnectionType;
    private javax.swing.JTextField txtConfiguration;
    private javax.swing.JPasswordField txtPassword;
    private javax.swing.JTextField txtServerUri;
    private javax.swing.JTextField txtUsername;
    // End of variables declaration//GEN-END:variables
}
