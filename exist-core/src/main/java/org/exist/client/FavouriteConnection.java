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

/**
 * Wrapper used to hold a favourite's connection information.
 *
 * @author Tobias Wunden
 */
public class FavouriteConnection extends Connection implements Comparable<Object> {

    public static final String NAME = Messages.getString("LoginPanel.42"); //$NON-NLS-1$
    //$NON-NLS-1$
    public static final String USERNAME = Messages.getString("LoginPanel.43"); //$NON-NLS-1$
    //$NON-NLS-1$
    public static final String PASSWORD = Messages.getString("LoginPanel.44"); //$NON-NLS-1$
    //$NON-NLS-1$
    public static final String URI = Messages.getString("LoginPanel.45"); //$NON-NLS-1$
    //$NON-NLS-1$
    public static final String CONFIGURATION = Messages.getString("LoginPanel.46"); //$NON-NLS-1$
    //$NON-NLS-1$
    public static final String SSL = Messages.getString("LoginPanel.47");
    
    private final String name;

    /**
     * Creates a new connection favourite from the given parameters.
     *
     * @param name the favourite's name
     * @param username the username
     * @param password the password
     * @param url the url
     */
    /**
     * Creates a new connection favourite from the given parameters.
     *
     * @param name the favourite's name.
     * @param username the username.
     * @param password the password
     * @param ssl flag to have SSL connection.
     * @param url the url.
     */
    public FavouriteConnection(final String name, final String username, final String password, final String url, final boolean ssl) {
        super(username, password, url, ssl);
        this.name = name;
    }

    /**
     * Creates a new connection favourite from the given parameters.
     *
     * @param name the favourite's name.
     * @param username the username.
     * @param password the password.
     * @param configuration location of configuration file.
     */
    public FavouriteConnection(final String name, final String username, final String password, final String configuration) {
        super(username, password, configuration);
        this.name = name;
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
     * Compares <code>o</code> to this favourite by comparing the
     * connection names to the object's toString() output.
     *
     * @see java.util.Comparator#compare(Object, Object)
     */
    @Override
    public int compareTo(final Object o) {
        return name.compareTo(o.toString());
    }

    /**
     * Returns the favourite's hashcode.
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Returns <code>true</code> if this favourite equals the given object.
     *
     * @see java.lang.Object#equals(Object)
     */
    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }
        return name.equals(o.toString());
    }

    /**
     * Returns the connection name.
     *
     * @return the connection name
     */
    @Override
    public String toString() {
        return name;
    }
}
