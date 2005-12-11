/**
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

/**
 * This class implements the graphical login panel used to log into
 * local and remote eXist database instances.
 *
 * @author Wolfgang M. Meier <wolfgang@exist-db.org>
 * @author Tobias Wunden <tobias.wunden@o2it.ch>
 */
public class LoginPanel extends JPanel {
    
    public static final int TYPE_REMOTE = 0;
    public static final int TYPE_LOCAL = 1;
    
    /** Uri for local connections */
    public static final String URI_LOCAL = "xmldb:exist://";
    
    /** Default uri for remote connections */
    public static final String URI_REMOTE = "xmldb:exist://localhost:8080/exist/xmlrpc";
    
    /** Name of Preference node containing favourites */
    public static final String FAVOURITES_NODE = "favourites";
    
    /** Ui components */
    JTextField username;
    JPasswordField password;
    JTextField cur_url;
    JComboBox type;
    JList favourites;
    DefaultListModel favouritesModel;
    JTextField title;
    JButton btnAddFavourite;
    JButton btnRemoveFavourite;
    JButton btnLoadFavourite;
    
    JButton btnExportFavourite;
    
    /**
     * Creates a new login panel with the given user and uri.
     *
     * @param defaultUser the initial user
     * @param uri the uri to connect to
     */
    public LoginPanel(String defaultUser, String uri) {
        super(false);
        setupComponents(defaultUser, uri);
    }
    
    /**
     * Sets up the graphical components.
     *
     * @param defaultUser the initial user
     * @param uri the uri to connect to
     */
    private void setupComponents(final String defaultUser, final String uri) {
        GridBagLayout grid = new GridBagLayout();
        setLayout(grid);
        GridBagConstraints c = new GridBagConstraints();
        final int inset = 5;
        c.insets = new Insets(inset, inset, inset, inset);
        
        JLabel label = new JLabel("Username");
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        username = new JTextField(defaultUser, 12);
        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(username, c);
        add(username);
        
        label = new JLabel("Password");
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        password = new JPasswordField(12);
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(password, c);
        add(password);
        
        label = new JLabel("Type");
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        type = new JComboBox();
        type.addItem("Remote");
        
        type.addItem("Local");
        type.setSelectedIndex(uri.equals(URI_LOCAL) ? TYPE_LOCAL : TYPE_REMOTE);
        type.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switch (type.getSelectedIndex()) {
                    case TYPE_LOCAL:
                        cur_url.setText(URI_LOCAL);
                        cur_url.setEnabled(false);
                        break;
                    case TYPE_REMOTE:
                        cur_url.setText(!uri.equals(URI_LOCAL) ? uri : URI_REMOTE);
                        cur_url.setEnabled(true);
                        break;
                }
            }
        });
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        grid.setConstraints(type, c);
        add(type);
        
        label = new JLabel("URL");
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        grid.setConstraints(label, c);
        add(label);
        
        cur_url = new JTextField(uri, 20);
        cur_url.setEnabled(!uri.equals(URI_LOCAL));
        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(cur_url, c);
        add(cur_url);
        
        label = new JLabel("Title");
        c.gridx = 0;
        c.gridy = 4;
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
        c.gridy = 4;
        c.gridwidth = 2;
        c.insets = new Insets(20, inset, inset, inset);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(title, c);
        add(title);
        
        label = new JLabel("Favourites");
        c.gridx = 0;
        c.gridy = 5;
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
                    type.setSelectedIndex(URI_LOCAL.equals(f.getUrl()) ? TYPE_LOCAL : TYPE_REMOTE);
                    cur_url.setText(f.getUrl());
                }
            }
        });
        JScrollPane scroll = new JScrollPane(favourites);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(200, 130));
        c.gridx = 1;
        c.gridy = 5;
        c.gridheight = 4;
        c.insets = new Insets(inset, inset, inset, inset);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(scroll, c);
        add(scroll);
        
        btnLoadFavourite = new JButton("Select");
        btnLoadFavourite.setToolTipText("Select favourite");
        btnLoadFavourite.setEnabled(false);
        btnLoadFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Favourite f = (Favourite)favourites.getSelectedValue();
                title.setText(f.getName());
                username.setText(f.getUsername());
                password.setText(f.getPassword());
                type.setSelectedIndex(URI_LOCAL.equals(f.getUrl()) ? TYPE_LOCAL : TYPE_REMOTE);
                cur_url.setText(f.getUrl());
            }
        });
        c.gridx = 2;
        c.gridy = 5;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(inset, inset, 15, inset);
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(btnLoadFavourite, c);
        add(btnLoadFavourite);
        
        btnAddFavourite = new JButton("Save");
        btnAddFavourite.setToolTipText("Save settings");
        btnAddFavourite.setEnabled(false);
        btnAddFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String t = title.getText();
                for (int i=0; i < favouritesModel.getSize(); i++) {
                    if (favouritesModel.elementAt(i).equals(t)) {
                        int result = JOptionPane.showConfirmDialog(LoginPanel.this, "A connection with this name already exists. Ok to overwrite?", "Conflict", JOptionPane.YES_NO_OPTION);
                        if (result == JOptionPane.NO_OPTION) {
                            return;
                        }
                        favouritesModel.remove(i);
                        break;
                    }
                }
                Favourite f = new Favourite(
                        title.getText(),
                        username.getText(),
                        new String(password.getPassword()),
                        cur_url.getText()
                        );
                favouritesModel.addElement(f);
                storeFavourites(favouritesModel);
            }
        });
        c.gridx = 2;
        c.gridy = 6;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(inset, inset, inset, inset);
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(btnAddFavourite, c);
        add(btnAddFavourite);
        
        btnRemoveFavourite = new JButton("Remove");
        btnRemoveFavourite.setEnabled(false);
        btnRemoveFavourite.setToolTipText("Remove favourite");
        btnRemoveFavourite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                favouritesModel.remove(favourites.getSelectedIndex());
                btnRemoveFavourite.setEnabled(false);
                btnLoadFavourite.setEnabled(false);
                storeFavourites(favourites.getModel());
                repaint();
            }
        });
        c.gridx = 2;
        c.gridy = 7;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.setConstraints(btnRemoveFavourite, c);
        add(btnRemoveFavourite);
        
//        btnExportFavourite = new JButton("Export");
//        btnExportFavourite.setEnabled(true);
//        btnExportFavourite.setToolTipText("Export favourite");
//        btnExportFavourite.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                importFavourites();
//                repaint();
//            }
//        });
//        c.gridx = 2;
//        c.gridy = 7;
//        c.gridwidth = 1;
//        c.gridheight = 1;
//        c.anchor = GridBagConstraints.WEST;
//        c.fill = GridBagConstraints.HORIZONTAL;
//        grid.setConstraints(btnExportFavourite, c);
//        add(btnExportFavourite);
        
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
                    node.get(Favourite.NAME, ""),
                    node.get(Favourite.USERNAME, ""),
                    node.get(Favourite.PASSWORD, ""),
                    node.get(Favourite.URL, ""));
            
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
                favouriteNode.put(Favourite.NAME, favs[i].getName());
                favouriteNode.put(Favourite.USERNAME, favs[i].getUsername());
                favouriteNode.put(Favourite.PASSWORD, favs[i].getPassword());
                favouriteNode.put(Favourite.URL, favs[i].getUrl());
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
    
    private void importFavourites(){
        JFileChooser chooser = new JFileChooser();
        chooser.showOpenDialog(this);
        
        File selectedFile = chooser.getSelectedFile();
        Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);
        
        try{
            FileInputStream fis = new FileInputStream(selectedFile);
            prefs.importPreferences(fis);
            fis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            ClientFrame.showErrorMessage("Problems importing favourites", ex);
        }
    }
    
    private void exportFavourites(){
        JFileChooser chooser = new JFileChooser();
        chooser.showSaveDialog(this);
        
        File selectedFile = chooser.getSelectedFile();
        Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);
        
        try {
            FileOutputStream fos = new FileOutputStream(selectedFile);
            prefs.exportSubtree(fos);
            fos.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            ClientFrame.showErrorMessage("Problems exporting favourites", ex);
        }
    }
    
    /**
     * Returns the username that is used to connect to the database.
     *
     * @return the username
     */
    public String getUsername() {
        return username.getText();
    }
    
    /**
     * Returns the password that is used to connect to the database.
     *
     * @return the password
     */
    public String getPassword() {
        return new String(password.getPassword());
    }
    
    /**
     * Returns the database uri.
     *
     * @return the uri
     */
    public String getUri() {
        return cur_url.getText();
    }
    
    /**
     * Wrapper used to hold a favourite's connection information.
     *
     * @author Tobias Wunden
     */
    static class Favourite implements Comparable {
        
        public static final String NAME="name";
        public static final String USERNAME="username";
        public static final String PASSWORD="password";
        public static final String URL="url";
        
        private String name;
        private String username;
        private String password;
        private String url;
        
        /**
         * Creates a new connection favourite from the given parameters.
         *
         * @param name the favourite's name
         * @param username the username
         * @param password the password
         * @param url the url
         */
        public Favourite(String name, String username, String password, String url) {
            this.name = name;
            this.username = username;
            this.password = password;
            this.url = url;
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