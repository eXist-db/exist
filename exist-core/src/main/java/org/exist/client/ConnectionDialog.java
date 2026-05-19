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
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ListModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ConnectionDialog extends JDialog implements DialogWithResponse<Connection> {

    private static final String PROVIDED_PASSWORD_PLACEHOLDER = "__PROVIDED__PASSWORD__";

    private final boolean disableEmbeddedConnectionType;
    private final DefaultConnectionSettings defaultConnectionSettings;
    private final List<DialogCompleteWithResponse<Connection>> dialogCompleteWithResponseCallbacks = new ArrayList<>();

    private ComboBoxModel<ConnectionType> connectionTypeModel;
    private DefaultListModel<FavouriteConnection> favouritesModel;
    private Path config;
    private JButton btnConnect;
    private JButton btnSelectConfiguration;
    private JCheckBox chkSsl;
    private JComboBox<ConnectionType> cmbConnectionType;
    private JLabel lblConfiguration;
    private JLabel lblServerUri;
    private JList<FavouriteConnection> lstFavourites;
    private JMenuItem miExportFavourites;
    private JMenuItem miRemoveFavourite;
    private JTabbedPane tpConnectionType;
    private JTextField txtConfiguration;
    private JPasswordField txtPassword;
    private JTextField txtServerUri;
    private JTextField txtUsername;

    private enum ConnectionType {
        REMOTE,
        EMBEDDED
    }

    /**
     * Creates new form ConnectionForm
     *
     * @param parent                        Parent window.
     * @param modal                         modality flag.
     * @param defaultConnectionSettings     Default connection settings.
     * @param embeddedByDefault             Set TRUE to have embedded mode selected by default.
     * @param disableEmbeddedConnectionType Set to TRUE to force remote connections only.
     */
    public ConnectionDialog(final java.awt.Frame parent, final boolean modal, final DefaultConnectionSettings defaultConnectionSettings, final boolean embeddedByDefault, final boolean disableEmbeddedConnectionType) {
        super(parent, modal);
        this.defaultConnectionSettings = defaultConnectionSettings;
        this.config = Path.of(defaultConnectionSettings.getConfiguration());
        this.disableEmbeddedConnectionType = disableEmbeddedConnectionType;
        InteractiveClient.setExistImage(getClass(), this::setIconImage);
        initComponents();

        if (disableEmbeddedConnectionType) {
            cmbConnectionType.removeItem(ConnectionType.EMBEDDED);
        } else if (embeddedByDefault) {
            cmbConnectionType.setSelectedItem(ConnectionType.EMBEDDED);
            toggleRemoteEmbeddedDisplayTab(false);
        }
        txtPassword.addKeyListener(new EnterKeyAdapter(btnConnect));
        txtPassword.requestFocusInWindow(); //set focus to password field
    }

    private ComboBoxModel<ConnectionType> getConnectionTypeModel() {
        if (connectionTypeModel == null) {
            connectionTypeModel = new DefaultComboBoxModel<>(ConnectionType.values());
        }
        return connectionTypeModel;
    }

    private DefaultListModel<FavouriteConnection> getFavouritesModel() {
        if (favouritesModel == null) {
            favouritesModel = new DefaultListModel<>();
            for (final FavouriteConnection favourite : FavouriteConnections.load()) {
                favouritesModel.addElement(favourite);
            }
        }
        return favouritesModel;
    }

    private void storeFavourites(final ListModel<FavouriteConnection> model) {
        final List<FavouriteConnection> favourites = new ArrayList<>();

        // Write a node for each item in model.
        for (int i = 0; i < model.getSize(); i++) {
            favourites.add(model.getElementAt(i));
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
    private void initComponents() {
        JPopupMenu pmFavourites = new JPopupMenu();
        miRemoveFavourite = new JMenuItem();
        JPopupMenu.Separator jSeparator2 = new JPopupMenu.Separator();
        JMenuItem miImportFavourites = new JMenuItem();
        miExportFavourites = new JMenuItem();
        JLabel lblExistLogo = new JLabel();
        JLabel lblUsername = new JLabel();
        JLabel lblPassword = new JLabel();
        JLabel lblConnectionType = new JLabel();
        cmbConnectionType = new JComboBox<>();
        txtUsername = new JTextField();
        txtPassword = new JPasswordField();
        tpConnectionType = new JTabbedPane();
        tpConnectionType.setUI(new BasicTabbedPaneUI() {

            @Override
            protected void paintContentBorder(final Graphics g, final int tabPlacement, final int selectedIndex) {
                //dont paint tabs!
            }

            @Override
            protected void paintContentBorderBottomEdge(final Graphics g, final int tabPlacement, final int selectedIndex, final int x, final int y, int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected void paintContentBorderLeftEdge(final Graphics g, final int tabPlacement, final int selectedIndex, final int x, final int y, int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected void paintContentBorderRightEdge(final Graphics g, final int tabPlacement, final int selectedIndex, final int x, final int y, int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected void paintContentBorderTopEdge(final Graphics g, final int tabPlacement, final int selectedIndex, final int x, int y, final int w, final int h) {
                //dont paint tabs!
            }

            @Override
            protected void paintFocusIndicator(final Graphics g, final int tabPlacement, final java.awt.Rectangle[] rects, final int tabIndex, final java.awt.Rectangle iconRect, final java.awt.Rectangle textRect, final boolean isSelected) {
                //dont paint tabs!
            }

            @Override
            protected void paintTab(final Graphics g, final int tabPlacement, final java.awt.Rectangle[] rects, final int tabIndex, final java.awt.Rectangle iconRect, final java.awt.Rectangle textRect) {
                //dont paint tabs!
            }

            @Override
            protected void paintTabArea(final Graphics g, final int tabPlacement, final int selectedIndex) {
                //dont paint tabs!
            }

            @Override
            protected void paintTabBackground(final Graphics g, final int tabPlacement, final int tabIndex, final int x, final int y, final int w, final int h, final boolean isSelected) {
                //dont paint tabs!
            }

            @Override
            protected void paintTabBorder(final Graphics g, final int tabPlacement, final int tabIndex, final int x, final int y, final int w, final int h, final boolean isSelected) {
                //dont paint tabs!
            }
        });
        JPanel panRemote = new JPanel();
        lblServerUri = new JLabel();
        txtServerUri = new JTextField();
        chkSsl = new JCheckBox();
        JPanel panEmbedded = new JPanel();
        lblConfiguration = new JLabel();
        txtConfiguration = new JTextField();
        btnSelectConfiguration = new JButton();
        JPanel panFavourites = new JPanel();
        JScrollPane jScrollPane1 = new JScrollPane();
        lstFavourites = new JList<>();
        JButton btnSaveToFavourites = new JButton();
        JButton btnClose = new JButton();
        btnConnect = new JButton();
        JSeparator jSeparator1 = new JSeparator();

        miRemoveFavourite.setText("Remove");
        miRemoveFavourite.addActionListener(e -> miRemoveFavouriteActionPerformed());
        pmFavourites.add(miRemoveFavourite);
        pmFavourites.add(jSeparator2);

        miImportFavourites.setText("Import Favourites...");
        miImportFavourites.addActionListener(e -> miImportFavouritesActionPerformed());
        pmFavourites.add(miImportFavourites);

        miExportFavourites.setText("Export Favourites...");
        miExportFavourites.addActionListener(e -> miExportFavouritesActionPerformed());
        pmFavourites.add(miExportFavourites);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Database Connection");

        InteractiveClient.setExistImageIcon(getClass(), lblExistLogo::setIcon);
        lblUsername.setText(getLabelText("LoginPanel.2"));
        lblPassword.setText(getLabelText("LoginPanel.3"));
        lblConnectionType.setText(getLabelText("LoginPanel.4"));

        cmbConnectionType.setModel(getConnectionTypeModel());
        cmbConnectionType.addActionListener(e -> cmbConnectionTypeActionPerformed());

        txtUsername.setText(getDefaultConnectionSettings().getUsername());
        if (getDefaultConnectionSettings().getPassword() != null
                && !getDefaultConnectionSettings().getPassword().isEmpty()) {
            txtPassword.setText(PROVIDED_PASSWORD_PLACEHOLDER);
        }

        tpConnectionType.setTabPlacement(SwingConstants.RIGHT);
        lblServerUri.setText(getLabelText("LoginPanel.12"));
        txtServerUri.setText(getDefaultConnectionSettings().getUri());

        chkSsl.setSelected(getDefaultConnectionSettings().isSsl());
        chkSsl.setText(getLabel("LoginPanel.47"));

        GroupLayout panRemoteLayout = new GroupLayout(panRemote);
        panRemote.setLayout(panRemoteLayout);
        panRemoteLayout.setHorizontalGroup(
                panRemoteLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(panRemoteLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(lblServerUri)
                                .addGap(63, 63, 63)
                                .addGroup(panRemoteLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(chkSsl)
                                        .addComponent(txtServerUri, GroupLayout.PREFERRED_SIZE, 336, GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panRemoteLayout.setVerticalGroup(
                panRemoteLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING, panRemoteLayout.createSequentialGroup()
                                .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(panRemoteLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(lblServerUri)
                                        .addComponent(txtServerUri, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
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
        btnSelectConfiguration.addActionListener(e -> btnSelectConfigurationActionPerformed());

        GroupLayout panEmbeddedLayout = new GroupLayout(panEmbedded);
        panEmbedded.setLayout(panEmbeddedLayout);
        panEmbeddedLayout.setHorizontalGroup(
                panEmbeddedLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(panEmbeddedLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(lblConfiguration)
                                .addGap(33, 33, 33)
                                .addComponent(txtConfiguration, GroupLayout.DEFAULT_SIZE, 279, Short.MAX_VALUE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnSelectConfiguration, GroupLayout.PREFERRED_SIZE, 33, GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())
        );
        panEmbeddedLayout.setVerticalGroup(
                panEmbeddedLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING, panEmbeddedLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(panEmbeddedLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(lblConfiguration)
                                        .addComponent(txtConfiguration, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(btnSelectConfiguration, GroupLayout.PREFERRED_SIZE, 28, GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(38, Short.MAX_VALUE))
        );

        tpConnectionType.addTab("tab2", panEmbedded);

        panFavourites.setBorder(BorderFactory.createTitledBorder(getLabel("LoginPanel.14")));

        lstFavourites.setModel(getFavouritesModel());
        lstFavourites.setComponentPopupMenu(pmFavourites);
        lstFavourites.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                lstFavouritesMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(lstFavourites);

        btnSaveToFavourites.setText(getLabel("LoginPanel.17"));
        btnSaveToFavourites.addActionListener(e -> btnSaveToFavouritesActionPerformed());

        GroupLayout panFavouritesLayout = new GroupLayout(panFavourites);
        panFavourites.setLayout(panFavouritesLayout);
        panFavouritesLayout.setHorizontalGroup(
                panFavouritesLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(panFavouritesLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(panFavouritesLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 463, Short.MAX_VALUE)
                                        .addGroup(GroupLayout.Alignment.TRAILING, panFavouritesLayout.createSequentialGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                                .addComponent(btnSaveToFavourites)))
                                .addContainerGap())
        );
        panFavouritesLayout.setVerticalGroup(
                panFavouritesLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(panFavouritesLayout.createSequentialGroup()
                                .addComponent(btnSaveToFavourites)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 143, Short.MAX_VALUE)
                                .addContainerGap())
        );

        btnClose.setText(getLabel("LoginPanel.51"));
        btnClose.addActionListener(e -> btnCloseActionPerformed());

        btnConnect.setText(getLabel("LoginPanel.50"));
        btnConnect.addKeyListener(new EnterKeyAdapter());
        btnConnect.addActionListener(e -> btnConnectActionPerformed());

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                                .addComponent(btnClose)
                                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(btnConnect))
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addGap(8, 8, 8)
                                                                .addComponent(lblExistLogo, GroupLayout.PREFERRED_SIZE, 113, GroupLayout.PREFERRED_SIZE)
                                                                .addGap(18, 18, 18)
                                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                        .addComponent(panFavourites, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                                                        .addComponent(tpConnectionType, GroupLayout.PREFERRED_SIZE, 527, GroupLayout.PREFERRED_SIZE)
                                                                        .addGroup(layout.createSequentialGroup()
                                                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                                                                                        .addComponent(lblPassword, GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
                                                                                        .addComponent(lblConnectionType, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                                                                .addGap(6, 6, 6)
                                                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                                        .addComponent(txtPassword, GroupLayout.PREFERRED_SIZE, 270, GroupLayout.PREFERRED_SIZE)
                                                                                        .addComponent(txtUsername, GroupLayout.PREFERRED_SIZE, 270, GroupLayout.PREFERRED_SIZE)
                                                                                        .addComponent(cmbConnectionType, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)))
                                                                        .addComponent(lblUsername, GroupLayout.PREFERRED_SIZE, 115, GroupLayout.PREFERRED_SIZE)))
                                                        .addComponent(jSeparator1, GroupLayout.PREFERRED_SIZE, 666, GroupLayout.PREFERRED_SIZE))
                                                .addGap(0, 0, Short.MAX_VALUE)))
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(14, 14, 14)
                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                        .addComponent(lblUsername)
                                                        .addComponent(txtUsername, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                                .addGap(18, 18, 18)
                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                        .addComponent(lblPassword)
                                                        .addComponent(txtPassword, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                                .addGap(26, 26, 26)
                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                        .addComponent(lblConnectionType)
                                                        .addComponent(cmbConnectionType, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(22, 22, 22)
                                                .addComponent(lblExistLogo, GroupLayout.PREFERRED_SIZE, 108, GroupLayout.PREFERRED_SIZE)))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tpConnectionType, GroupLayout.PREFERRED_SIZE, 86, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(ComponentPlacement.RELATED)
                                .addComponent(panFavourites, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jSeparator1, GroupLayout.PREFERRED_SIZE, 10, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(btnClose)
                                        .addComponent(btnConnect))
                                .addContainerGap(35, Short.MAX_VALUE))
        );

        pack();
    }

    private void btnConnectActionPerformed() {
        final String password = Arrays.equals(txtPassword.getPassword(), PROVIDED_PASSWORD_PLACEHOLDER.toCharArray()) ? getDefaultConnectionSettings().getPassword() : new String(txtPassword.getPassword());
        final Connection connection;
        if (cmbConnectionType.getSelectedItem() == ConnectionType.REMOTE) {
            connection = new Connection(txtUsername.getText(), password, txtServerUri.getText(), chkSsl.isSelected());
        } else {
            connection = new Connection(txtUsername.getText(), password, txtConfiguration.getText());
        }

        for (final DialogCompleteWithResponse<Connection> callback : getDialogCompleteWithResponseCallbacks()) {
            callback.complete(connection);
        }

        setVisible(false);
        dispose();
    }

    private void cmbConnectionTypeActionPerformed() {
        final boolean remote = cmbConnectionType.getSelectedItem() == ConnectionType.REMOTE;

        toggleRemoteEmbeddedDisplayTab(remote);
    }

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

    private void btnSelectConfigurationActionPerformed() {
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
    }

    private void btnSaveToFavouritesActionPerformed() {
        final String favouriteName = JOptionPane.showInputDialog(this, "Please enter a name for your favourite:", "Save Favourite", JOptionPane.QUESTION_MESSAGE);

        if (favouriteName != null && !favouriteName.isEmpty()) {
            for (int i = 0; i < getFavouritesModel().getSize(); i++) {
                if (getFavouritesModel().elementAt(i).getName().equals(favouriteName)) {
                    final int result = JOptionPane.showConfirmDialog(this, Messages.getString("LoginPanel.19"), Messages.getString("LoginPanel.20"), JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.NO_OPTION) {
                        return;
                    }
                    getFavouritesModel().remove(i);
                    break;
                }
            }

            final FavouriteConnection favourite;
            if (cmbConnectionType.getSelectedItem() == ConnectionType.REMOTE) {
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
    }

    private void btnCloseActionPerformed() {
        setVisible(false);
        dispose();
    }

    private void lstFavouritesMouseClicked(MouseEvent evt) {

        if (SwingUtilities.isRightMouseButton(evt)) {
            miRemoveFavourite.setEnabled(!lstFavourites.isSelectionEmpty());
            miExportFavourites.setEnabled(!getFavouritesModel().isEmpty());
        }

        if (evt.getClickCount() == 2 && lstFavourites.getSelectedIndex() >= 0) {
            final FavouriteConnection favourite = lstFavourites.getSelectedValue();

            final boolean favouriteHasEmbeddedMode = "".equals(favourite.getUri());

            if (disableEmbeddedConnectionType && favouriteHasEmbeddedMode) {
                JOptionPane.showMessageDialog(this, "The favourite connection '" + favourite.getName() + "' uses an Embedded Connection Type, but Embedded Connections have been disabled at client startup.", "Favourite Selection Error", JOptionPane.ERROR_MESSAGE);
                lstFavourites.clearSelection();
            } else {

                txtUsername.setText(favourite.getUsername());
                txtPassword.setText(favourite.getPassword());

                cmbConnectionType.setSelectedItem(favouriteHasEmbeddedMode ? ConnectionType.EMBEDDED : ConnectionType.REMOTE);
                tpConnectionType.setSelectedIndex(cmbConnectionType.getSelectedIndex());

                txtServerUri.setText(favourite.getUri());
                chkSsl.setSelected(favourite.isSsl());

                txtConfiguration.setText(favourite.getConfiguration());

                txtPassword.requestFocusInWindow(); //set focus to password field
            }
        }
    }

    private void miImportFavouritesActionPerformed() {
        final Path file = Path.of("favourites.xml"); //$NON-NLS-1$
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
    }

    private void miExportFavouritesActionPerformed() {
        final Path file = Path.of(Messages.getString("LoginPanel.25")); //$NON-NLS-1$
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
    }

    private void miRemoveFavouriteActionPerformed() {
        if (!lstFavourites.isSelectionEmpty()) {
            getFavouritesModel().remove(lstFavourites.getSelectedIndex());
            storeFavourites(getFavouritesModel());
        }
    }
}
