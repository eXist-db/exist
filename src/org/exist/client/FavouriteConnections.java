/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
package org.exist.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.io.IOException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.BackingStoreException;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import org.exist.xmldb.XmldbURI;

public class FavouriteConnections {
    
    /** Name of Preference node containing favourites */
    private static final String FAVOURITES_NODE = Messages.getString("LoginPanel.1"); //$NON-NLS-1$
    
    public static void store(final List<FavouriteConnection> favourites) {
        
        final Preferences prefs = Preferences.userNodeForPackage(FavouriteConnections.class);
        
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
        for(final FavouriteConnection favourite : favourites) {
            
            if(favourites != null){
                
                // Create node
                final Preferences favouriteNode = favouritesNode.node(favourite.getName());
                
                // Fill node
                favouriteNode.put(FavouriteConnection.USERNAME, favourite.getUsername());
                favouriteNode.put(FavouriteConnection.PASSWORD, favourite.getPassword());
                favouriteNode.put(FavouriteConnection.URI, favourite.getUri());
                favouriteNode.put(FavouriteConnection.CONFIGURATION, favourite.getConfiguration());
                favouriteNode.put(FavouriteConnection.SSL, Boolean.valueOf(favourite.isSsl()).toString().toUpperCase());
            }
        }
    }
    
    public static List<FavouriteConnection> load() {
        
        final Preferences prefs = Preferences.userNodeForPackage(FavouriteConnections.class);
        final Preferences favouritesNode = prefs.node(FAVOURITES_NODE);
        
        // Get all favourites
        String favouriteNodeNames[] =new String[0];
        try {
            favouriteNodeNames = favouritesNode.childrenNames();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
        
        // Copy for each connection data into Favourite array
        final List<FavouriteConnection> favourites = new ArrayList<FavouriteConnection>();
        
        for(final String favouriteNodeName : favouriteNodeNames) {        
            final Preferences node = favouritesNode.node(favouriteNodeName);

            final FavouriteConnection favourite;
            
            if((!node.get(FavouriteConnection.URI, "").equals("")) && (!node.get(FavouriteConnection.CONFIGURATION, "").equals(""))) {
                //backwards compatibility with old login favourites
                
                if(node.get(FavouriteConnection.URI, "").equals(XmldbURI.EMBEDDED_SERVER_URI.toString())) {
                    //embedded
                    favourite = getEmbeddedFavourite(favouriteNodeName, node);
                } else {
                    //remote
                    favourite = getRemoteFavourite(favouriteNodeName, node);
                }
            } else {
                if(node.get(FavouriteConnection.URI, "").equals("")) {
                    //embedded
                    favourite = getEmbeddedFavourite(favouriteNodeName, node);
                } else {
                    //remote
                    favourite = getRemoteFavourite(favouriteNodeName, node);
                }
            }
            
            favourites.add(favourite);
        }
        
        Collections.sort(favourites);
        return favourites;
    }
    
    private static FavouriteConnection getRemoteFavourite(final String favouriteNodeName, final Preferences node) {
        return new FavouriteConnection(
            favouriteNodeName,
            node.get(FavouriteConnection.USERNAME, ""),
            node.get(FavouriteConnection.PASSWORD, ""),
            node.get(FavouriteConnection.URI, ""),
            Boolean.parseBoolean(node.get(FavouriteConnection.SSL, "FALSE"))
        );
    }
    
    private static FavouriteConnection getEmbeddedFavourite(final String favouriteNodeName, final Preferences node) {
        return new FavouriteConnection(
            favouriteNodeName,
            node.get(FavouriteConnection.USERNAME, ""),
            node.get(FavouriteConnection.PASSWORD, ""),
            node.get(FavouriteConnection.CONFIGURATION, "")
        );
    }
    
    public static void importFromFile(final File f) throws IOException, InvalidPreferencesFormatException {
        
        final Preferences prefs = Preferences.userNodeForPackage(FavouriteConnections.class);
        
        InputStream is = null;
        try{
            is = new FileInputStream(f);
            prefs.importPreferences(is);
        } finally {
            if(is != null) {
                try {
                   is.close(); 
                } catch(final IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
    }
    
    public static void exportToFile(final File f) throws IOException, BackingStoreException {
        
        final Preferences prefs = Preferences.userNodeForPackage(FavouriteConnections.class);
        
        OutputStream os = null;
        try {
            os = new FileOutputStream(f);
            prefs.exportSubtree(os);
        } finally {
            if(os != null) {
                try {
                   os.close(); 
                } catch(final IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
    }
}
