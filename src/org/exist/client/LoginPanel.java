/**
 * LoginPanel.java
 *
 * Copyright 2005 by O2 IT Engineering
 * Zurich,  Switzerland (CH)
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
 * @author Tobias Wunden
 */
public class LoginPanel extends JPanel {

	static final int TYPE_REMOTE = 0;
	static final int TYPE_LOCAL = 1;

	public static final String URI_LOCAL = "xmldb:exist://";
	public static final String URI_REMOTE = "xmldb:exist://localhost:8080/exist/xmlrpc";

    public final static String CONNECTION_PREFIX = "connection.";
    public final static String FAVOURITES_FILE = "connections.properties";
	
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
    
    public LoginPanel(String defaultUser, String uri) {
        super(false);
        setupComponents(defaultUser, uri);
    }
    
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
        type.setSelectedIndex(TYPE_REMOTE);
        type.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				switch (type.getSelectedIndex()) {
					case TYPE_LOCAL:
						cur_url.setText(URI_LOCAL);
						cur_url.setEnabled(false);
						break;
					case TYPE_REMOTE:
						cur_url.setText(uri);
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

        btnLoadFavourite = new JButton("Load");
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

        btnAddFavourite = new JButton("Save...");
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
     * Loads the favourites from the properties.
     * 
     * @return the favourites
     */
    private Favourite[] loadFavourites() {
    	Properties connectionProps = new Properties();
    	
    	String home = System.getProperty("exist.home");
    	File propFile;
    	if (home == null)
    		propFile = new File(FAVOURITES_FILE);
    	else
    		propFile = new File(home
    				+ System.getProperty("file.separator", "/")
    				+ FAVOURITES_FILE);
    	
    	InputStream pin = null;
    	
    	// Try to load from file
    	try {
    		pin = new FileInputStream(propFile);
    	} catch (FileNotFoundException ex) {
    		// File not found, no exception handling
    	}
    	
    	if (pin == null){
    		// Try to load via classloader
    		pin = InteractiveClient.class
    		.getResourceAsStream(FAVOURITES_FILE);
    	}
    	
    	if (pin != null){
    		// Try to load properties from stream
    		try{
    			connectionProps.load(pin);
    			pin.close();
    		} catch (IOException ex) { }
    	}
    	
    	Map favourites = new HashMap();
    	final int l = CONNECTION_PREFIX.length();
    	Iterator pi = connectionProps.keySet().iterator();
    	while (pi.hasNext()) {
    		String key = (String)pi.next();
    		if (key.startsWith(CONNECTION_PREFIX)) {
    			String id = key.substring(l, key.indexOf(".", l));
    			if (!favourites.containsKey(id)) {
    				favourites.put(id, new Favourite(connectionProps, id));
    			}
    		}
    	}
    	Favourite[] result = new Favourite[favourites.size()];
    	result = (Favourite[])favourites.values().toArray(result);
    	Arrays.sort(result);
    	return result;
    }
    
    /**
     * Stores the connection favourites into connections.properties.
     * 
     * @param model the list model
     */
    private void storeFavourites(ListModel model) {
    	Properties connectionProps = new Properties();
    	for (int i=0; i < model.getSize(); i++) {
    		Favourite f = (Favourite)model.getElementAt(i);
    		connectionProps.put(CONNECTION_PREFIX + i + ".name", f.getName());
    		connectionProps.put(CONNECTION_PREFIX + i + ".username", f.getUsername());
    		connectionProps.put(CONNECTION_PREFIX + i + ".password", f.getPassword());
    		connectionProps.put(CONNECTION_PREFIX + i + ".url", f.getUrl());
    	}
    	String home = System.getProperty("exist.home");
    	File propFile;
    	if (home == null)
    		propFile = new File("connections.properties");
    	else
    		propFile = new File(home
    				+ System.getProperty("file.separator", "/")
    				+ "connections.properties");
    	OutputStream os;
		try {
			os = new FileOutputStream(propFile);
        	connectionProps.store(os, "eXist connection favourites");
        	os.flush();
        	os.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public String getUsername() {
    	return username.getText();
    }
    
    public String getPassword() {
    	return new String(password.getPassword());
    }
    
    public String getUri() {
    	return cur_url.getText();
    }
    
    /**
     * Wrapper used to hold a favourite's connection information.
     * 
     * @author Tobias Wunden
     */
    static class Favourite implements Comparable {

    	String name;
    	String username;
    	String password;
    	String url;
    	
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
    	 * Creates a new favourite with id <code>favourite</code> from the 
    	 * given properties.
    	 * 
    	 * @param props the properties
    	 * @param favourite the favourite id
    	 */
    	public Favourite(Properties props, String favourite) {
    		this.name = props.getProperty("connection." + favourite + ".name", "Untitled");
    		this.username = props.getProperty("connection." + favourite + ".username");
    		this.password = props.getProperty("connection." + favourite + ".password");
    		this.url = props.getProperty("connection." + favourite + ".url");
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
    	
    	public int compareTo(Object o2) {
    		return name.compareTo(o2.toString());
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