/**
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.client;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.exist.xmldb.XmldbURI;

/**
 * This class implements the graphical login panel used to log into
 * local and remote eXist database instances.
 *
 * @author Wolfgang M. Meier <wolfgang@exist-db.org>
 * @author Tobias Wunden <tobias.wunden@o2it.ch>
 */
public class LoginPanel extends JPanel {
    
    public static final int TYPE_REMOTE = 0;
    public static final int TYPE_EMBEDDED = 1;
    
    /** Uri for local connections */
    public static final String URI_EMBEDDED = XmldbURI.EMBEDDED_SERVER_URI.toString();
    
    /** Default uri for remote connections */
    public static final String URI_REMOTE = "xmldb:exist://localhost:8080/exist/xmlrpc"; //$NON-NLS-1$
    
    /** Name of Preference node containing favourites */
    public static final String FAVOURITES_NODE = Messages.getString("LoginPanel.1"); //$NON-NLS-1$
    
    /** The properties modified by this panel */
    protected Properties properties;

    
    /** Ui components */
    JTextField username;
    JPasswordField password;
    JTextField cur_url;
    JTextField configuration;
    JButton selectConf;
    
    JComboBox type;
    JList favourites;
    DefaultListModel favouritesModel;
    JTextField title;
    JButton btnAddFavourite;
    JButton btnRemoveFavourite;
    JButton btnLoadFavourite;
    
    JButton btnExportFavourite;
    JButton btnImportFavourite;
    
    /**
     * Creates a new login panel with properties
     *
     * @param props a list of properties modified by the panel
     */
    public LoginPanel(Properties props) {
        super(false);
        this.properties=new Properties(props);
        setupComponents();
    }
    
    /**
     * Sets up the graphical components.
     */
    private void setupComponents() {
        GridBagLayout grid = new GridBagLayout();
        setLayout(grid);
        GridBagConstraints c = new GridBagConstraints();
        final int inset = 5;
        c.insets = new Insets(inset, inset, inset, inset);
        
        // y pos as a counter
        int gridy=0;
        
        JLabel label = new JLabel(Messages.getString("LoginPanel.2")); //$NON-NLS-1$
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        username = new JTextField(properties.getProperty(InteractiveClient.USER), 12);
        c.gridx = 1;
        c.gridy = gridy;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(username, c);
        add(username);
        
        gridy++;
        
        label = new JLabel(Messages.getString("LoginPanel.3")); //$NON-NLS-1$
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        password = new JPasswordField(12);
        c.gridx = 1;
        c.gridy = gridy;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(password, c);
        add(password);
        
        gridy++;
        
        
        label = new JLabel(Messages.getString("LoginPanel.4")); //$NON-NLS-1$
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        type = new JComboBox();
        type.addItem(Messages.getString("LoginPanel.5")); //$NON-NLS-1$
        
        // when parameter is specified, embedded mode can not be selected
        boolean showEmbeddedMode = 
                properties.getProperty("NO_EMBED_MODE", "FALSE").equalsIgnoreCase("FALSE");
        if(showEmbeddedMode){
            type.addItem(Messages.getString("LoginPanel.6")); 
        }
        
        
        final String uri=properties.getProperty("uri"); //$NON-NLS-1$
        type.setSelectedIndex(uri.equals(URI_EMBEDDED) ? TYPE_EMBEDDED : TYPE_REMOTE);
        type.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switch (type.getSelectedIndex()) {
                    // in case embedded, default URL xmldb:exist//, allow to change conf file
                    case TYPE_EMBEDDED:
                        cur_url.setText(URI_EMBEDDED);
                        cur_url.setEnabled(false);
                        configuration.setEnabled(true);
                        selectConf.setEnabled(true);
                        break;
                    case TYPE_REMOTE:
                        cur_url.setText(!uri.equals(URI_EMBEDDED) ? uri : URI_REMOTE);
                        cur_url.setEnabled(true);
                        configuration.setEnabled(false);
                        selectConf.setEnabled(false);
                        break;
                }
            }
        });
        c.gridx = 1;
        c.gridy = gridy;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        grid.setConstraints(type, c);
        add(type);

        gridy++;
        
        // File chooser for a conf file in local, active in local mode
        label = new JLabel(Messages.getString("LoginPanel.8")); //$NON-NLS-1$
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);

        configuration = new JTextField(properties.getProperty(InteractiveClient.CONFIGURATION), 40);
        // the client will run by itself the Database (needs exclusive access otherwise access is read-only)
        configuration.setToolTipText(Messages.getString("LoginPanel.9")); //$NON-NLS-1$
        // if type selected is remote, select a conf file should be disable
        if (type.getSelectedIndex() == TYPE_REMOTE) configuration.setEnabled(false);
        c.gridx = 1;
        c.gridy = gridy;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(configuration, c);
        add(configuration);

        selectConf = new JButton(Messages.getString("LoginPanel.10")); //$NON-NLS-1$
        selectConf.setToolTipText(Messages.getString("LoginPanel.11")); //$NON-NLS-1$
        if (type.getSelectedIndex() == TYPE_REMOTE) selectConf.setEnabled(false);
        selectConf.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String conf=configuration.getText();
                if (conf == null) selectConfFile(null); 
                else selectConfFile(new File(conf).getParentFile());
            }
        });
        c.gridx = 2;
        c.gridy = gridy;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(selectConf, c);
        add(selectConf);

        gridy++;
        
        // URI, active in remote mode
        label = new JLabel(Messages.getString("LoginPanel.12")); //$NON-NLS-1$
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        cur_url = new JTextField(uri, 20);
        cur_url.setEnabled(!uri.equals(URI_EMBEDDED));
        c.gridx = 1;
        c.gridy = gridy;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(cur_url, c);
        add(cur_url);
        
        gridy++;
        gridy++;
        
        label = new JLabel(Messages.getString("LoginPanel.13")); //$NON-NLS-1$
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.insets = new Insets(20, inset, inset, inset);
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        title = new JTextField();
        title.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent arg0) {
                btnAddFavourite.setEnabled(title.getText().length() > 0);
            }
            public void removeUpdate(DocumentEvent arg0) {
                btnAddFavourite.setEnabled(title.getText().length() > 0);
            }
            public void changedUpdate(DocumentEvent arg0) {
                btnAddFavourite.setEnabled(title.getText().length() > 0);
            }
        });
        c.gridx = 1;
        c.gridy = gridy;
        c.gridwidth = 2;
        c.insets = new Insets(20, inset, inset, inset);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(title, c);
        add(title);
        
        gridy++;
        
        label = new JLabel(Messages.getString("LoginPanel.14")); //$NON-NLS-1$
        c.gridx = 0;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.gridheight = 4;
        c.insets = new Insets(inset, inset, inset, inset);
        c.anchor = GridBagConstraints.NORTHEAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        favouritesModel = new DefaultListModel();
        Favourite[] f = loadFavourites();
        for (int i=0; i < f.length; favouritesModel.addElement(f[i++]));
        favourites = new JList(favouritesModel);
        favourites.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                boolean selection = favourites.getSelectedIndex() >= 0;
                btnLoadFavourite.setEnabled(selection);
                btnRemoveFavourite.setEnabled(selection);
            }
        });
        favourites.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (e.getClickCount() == 2 && favourites.getSelectedIndex() >= 0) {
                    Favourite f = (Favourite)favourites.getSelectedValue();
                    title.setText(f.getName());
                    username.setText(f.getUsername());
                    password.setText(f.getPassword());
                    configuration.setText(f.getConfiguration());
                    type.setSelectedIndex(URI_EMBEDDED.equals(f.getUrl()) ? TYPE_EMBEDDED : TYPE_REMOTE);
                    cur_url.setText(f.getUrl());
                }
            }
        });
        JScrollPane scroll = new JScrollPane(favourites);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(200, 130));
        c.gridx = 1;
        c.gridy = gridy;
        c.gridheight = 4;
        c.insets = new Insets(inset, inset, inset, inset);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(scroll, c);
        add(scroll);
        
        btnLoadFavourite = new JButton(Messages.getString("LoginPanel.15")); //$NON-NLS-1$
        btnLoadFavourite.setToolTipText(Messages.getString("LoginPanel.16")); //$NON-NLS-1$
        btnLoadFavourite.setEnabled(false);
        btnLoadFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Favourite f = (Favourite)favourites.getSelectedValue();
                title.setText(f.getName());
                username.setText(f.getUsername());
                password.setText(f.getPassword());
                configuration.setText(f.getConfiguration());
                type.setSelectedIndex(URI_EMBEDDED.equals(f.getUrl()) ? TYPE_EMBEDDED : TYPE_REMOTE);
                cur_url.setText(f.getUrl());
            }
        });
        c.gridx = 2;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(inset, inset, 15, inset);
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(btnLoadFavourite, c);
        add(btnLoadFavourite);
        
        gridy++;
        
        btnAddFavourite = new JButton(Messages.getString("LoginPanel.17")); //$NON-NLS-1$
        btnAddFavourite.setToolTipText(Messages.getString("LoginPanel.18")); //$NON-NLS-1$
        btnAddFavourite.setEnabled(false);
        btnAddFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String t = title.getText();
                for (int i=0; i < favouritesModel.getSize(); i++) {
                    if (favouritesModel.elementAt(i).equals(t)) {
                        int result = JOptionPane.showConfirmDialog(LoginPanel.this, Messages.getString("LoginPanel.19"), Messages.getString("LoginPanel.20"), JOptionPane.YES_NO_OPTION); //$NON-NLS-1$ //$NON-NLS-2$
                        if (result == JOptionPane.NO_OPTION) {
                            return;
                        }
                        favouritesModel.remove(i);
                        break;
                    }
                }
                Favourite f = new Favourite(
                          title.getText()
                        , username.getText()
                        , new String(password.getPassword())
                        , cur_url.getText()
                        , configuration.getText() 
                );
                favouritesModel.addElement(f);
                storeFavourites(favouritesModel);
            }
        });
        c.gridx = 2;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(inset, inset, inset, inset);
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(btnAddFavourite, c);
        add(btnAddFavourite);
        
        btnRemoveFavourite = new JButton(Messages.getString("LoginPanel.21")); //$NON-NLS-1$
        btnRemoveFavourite.setEnabled(false);
        btnRemoveFavourite.setToolTipText(Messages.getString("LoginPanel.22")); //$NON-NLS-1$
        btnRemoveFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                favouritesModel.remove(favourites.getSelectedIndex());
                btnRemoveFavourite.setEnabled(false);
                btnLoadFavourite.setEnabled(false);
                storeFavourites(favourites.getModel());
                repaint();
            }
        });
        
        gridy++;
        
        c.gridx = 2;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(inset, inset, 15, inset);
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(btnRemoveFavourite, c);
        add(btnRemoveFavourite);
        
        btnExportFavourite = new JButton(Messages.getString("LoginPanel.23")); //$NON-NLS-1$
        btnExportFavourite.setEnabled(true);
        btnExportFavourite.setToolTipText(Messages.getString("LoginPanel.24")); //$NON-NLS-1$
        btnExportFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                
                File file = new File( Messages.getString("LoginPanel.25") ); //$NON-NLS-1$
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(file);
                chooser.showSaveDialog(LoginPanel.this);
                
                File selectedFile = chooser.getSelectedFile();
                
                if(selectedFile==null){
                    JOptionPane.showMessageDialog(LoginPanel.this, Messages.getString("LoginPanel.26"), Messages.getString("LoginPanel.27"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
                
                if(selectedFile.exists() && !selectedFile.canWrite()){
                    JOptionPane.showMessageDialog(LoginPanel.this, Messages.getString("LoginPanel.28"), Messages.getString("LoginPanel.29"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
                exportFavourites(selectedFile);
                repaint();
            }
        });
        
        gridy++;
        
        c.gridx = 2;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(inset, inset, inset, inset);
        grid.setConstraints(btnExportFavourite, c);
        add(btnExportFavourite);
        
        btnImportFavourite = new JButton(Messages.getString("LoginPanel.30")); //$NON-NLS-1$
        btnImportFavourite.setEnabled(true);
        btnImportFavourite.setToolTipText(Messages.getString("LoginPanel.31")); //$NON-NLS-1$
        btnImportFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                
                File file = new File( "favourites.xml" ); //$NON-NLS-1$
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(file);
                
                chooser.showOpenDialog(LoginPanel.this);
                File selectedFile = chooser.getSelectedFile();
                
                if(selectedFile==null){
                    JOptionPane.showMessageDialog(LoginPanel.this, Messages.getString("LoginPanel.33"), Messages.getString("LoginPanel.34"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
                
                if(!selectedFile.canRead()){
                    JOptionPane.showMessageDialog(LoginPanel.this, Messages.getString("LoginPanel.35"), Messages.getString("LoginPanel.36"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$ //$NON-NLS-2$
                    return;
                }
                
                importFavourites(selectedFile);
                

                repaint();
            }
        });
        
        gridy++;
        
        c.gridx = 2;
        c.gridy = gridy;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(btnImportFavourite, c);
        add(btnImportFavourite);
        
        JPanel spacer = new JPanel();
        c.gridx = 2;
        c.gridy = 8;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.fill = GridBagConstraints.BOTH;
        grid.setConstraints(spacer, c);
        add(spacer);
        
    }
    
    /**
     * Set and return the modified properties
     */
    public Properties getProperties() {
        this.properties.setProperty(InteractiveClient.PASSWORD , new String(password.getPassword()));
        this.properties.setProperty(InteractiveClient.URI , cur_url.getText());
        this.properties.setProperty(InteractiveClient.USER , username.getText());
        this.properties.setProperty(InteractiveClient.CONFIGURATION , configuration.getText());
        return this.properties;
    }
    
    /**
     * File chooser for a configuration file
     */
      private void selectConfFile(File dir) {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setCurrentDirectory(dir);
        if (chooser.showDialog(this, Messages.getString("LoginPanel.37")) //$NON-NLS-1$
            == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            configuration.setText(f.getAbsolutePath());
        }
    }

    
    /**
     * Loads the connection favourites using the Preferences API.
     *
     * @return the favourites
     */
    private Favourite[] loadFavourites() {
        
        Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);
        Preferences favouritesNode = prefs.node(FAVOURITES_NODE);
        
        // Get all favourites
        String favouriteNodeNames[]=new String[0];
        try {
            favouriteNodeNames = favouritesNode.childrenNames();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
        
        // Copy for each connection data into Favourite array
        Favourite[] favourites = new Favourite[favouriteNodeNames.length];
        for(int i=0 ; i< favouriteNodeNames.length ; i++){
            Preferences node = favouritesNode.node( favouriteNodeNames[i]);
            
            Favourite favourite = new Favourite(
                    favouriteNodeNames[i]
                  , node.get(Favourite.USERNAME, "") //$NON-NLS-1$
                  , node.get(Favourite.PASSWORD, "") //$NON-NLS-1$
                  , node.get(Favourite.URL, "") //$NON-NLS-1$
                  , node.get(Favourite.CONFIGURATION, "") //$NON-NLS-1$
            );
            
            favourites[i]=favourite;
        }
        
        Arrays.sort(favourites);
        return favourites;
    }
    
    
    private void storeFavourites(Favourite[] favs) {
        
        Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);
        
        // Clear connection node
        Preferences favouritesNode = prefs.node(FAVOURITES_NODE);
        try {
            favouritesNode.removeNode();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
        
        // Recreate connection node
        favouritesNode = prefs.node(FAVOURITES_NODE);
        
        // Write all favourites
        for (int i=0; i < favs.length; i++) {
            
            if(favs[i]!=null){
                
                // Create node
                Preferences favouriteNode = favouritesNode.node(favs[i].getName());
                
                // Fill node
                favouriteNode.put(Favourite.USERNAME, favs[i].getUsername());
                favouriteNode.put(Favourite.PASSWORD, favs[i].getPassword());
                favouriteNode.put(Favourite.URL, favs[i].getUrl());
                favouriteNode.put(Favourite.CONFIGURATION, favs[i].getConfiguration());
            }
        }
    }
    
    /**
     * Saves the connections favourites using the Preferences API.
     *
     * @param model the list model
     */
    private void storeFavourites(ListModel model) {
        
        Favourite favs[] = new Favourite[model.getSize()];
        
        // Write a node for each item in model.
        for (int i=0; i < model.getSize(); i++) {
            favs[i]= (Favourite)model.getElementAt(i);
        }
        
        storeFavourites(favs);
    }
    
    public static boolean importFavourites(File importFile){
        
        boolean importOk=false;
        
        Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);
        
        try{
            FileInputStream fis = new FileInputStream(importFile);
            prefs.importPreferences(fis);
            importOk=true;
            fis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return importOk;
    }
    
    public static boolean exportFavourites(File exportFile){
        
        boolean exportOk=false;
        
        Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);
        
        try {
            FileOutputStream fos = new FileOutputStream(exportFile);
            prefs.exportSubtree(fos);
            exportOk=true;
            fos.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return exportOk;
    }
    
    
    /**
     * Wrapper used to hold a favourite's connection information.
     *
     * @author Tobias Wunden
     */
    static class Favourite implements Comparable {
        
        public static final String NAME=Messages.getString("LoginPanel.42"); //$NON-NLS-1$
        public static final String USERNAME=Messages.getString("LoginPanel.43"); //$NON-NLS-1$
        public static final String PASSWORD=Messages.getString("LoginPanel.44"); //$NON-NLS-1$
        public static final String URL=Messages.getString("LoginPanel.45"); //$NON-NLS-1$
        public static final String CONFIGURATION=Messages.getString("LoginPanel.46"); //$NON-NLS-1$
        
        private String name;
        private String username;
        private String password;
        private String url;
        /** path to an alternate configuration file for emebeded mode */
        private String configuration;
        
        /**
         * Creates a new connection favourite from the given parameters.
         *
         * @param name the favourite's name
         * @param username the username
         * @param password the password
         * @param url the url
         */
        public Favourite(String name, String username, String password, String url, String configuration) {
            this.name = name;
            this.username = username;
            this.password = password;
            this.url = url;
            this.configuration = configuration;
        }
        
        /**
         * Returns the connection name.
         *
         * @return the connection name
         */
        public String getName() {
            return name;
        }
        
        /**
         * Returns the username.
         *
         * @return the username
         */
        public String getUsername() {
            return username;
        }
        
        /**
         * Returns the password.
         *
         * @return the password
         */
        public String getPassword() {
            return password;
        }
        
        /**
         * Returns the url.
         *
         * @return the url
         */
        public String getUrl() {
            return url;
        }

        /**
         * Returns the configuration file path for emebeded mode.
         *
         * @return the url
         */
        public String getConfiguration() {
            return configuration;
        }

        /**
         * Compares <code>o</code> to this favourite by comparing the
         * connection names to the object's toString() output.
         *
         * @see java.util.Comparator#compareTo(Object)
         */
        public int compareTo(Object o) {
            return name.compareTo(o.toString());
        }
        
        /**
         * Returns the favourite's hashcode.
         *
         * @see java.lang.Object#hashCode()
         */
        public int hashCode() {
            return name.hashCode();
        }
        
        /**
         * Returns <code>true</code> if this favourite equals the given object.
         *
         * @see java.lang.Object#equals(Object)
         */
        public boolean equals(Object o) {
            return name.equals(o.toString());
        }
        
        /**
         * Returns the connection name.
         *
         * @return the connection name
         */
        public String toString() {
            return name;
        }
    }
    
}
