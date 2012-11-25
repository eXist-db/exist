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

public class Connection {
    
    private final String username;
    private final String password;
    
    /* remote mode */
    protected String uri;
    protected boolean ssl;
    
    /** path to an alternate configuration file for emebeded mode */
    protected String configuration;
    
    public Connection(final String username, final String password, final String uri, final boolean ssl) {
        this.username = username;
        this.password = password;
        this.uri = uri;
        this.ssl = ssl;
        this.configuration = "";
    }
    
    public Connection(final String username, final String password, final String configuration) {
        this.username = username;
        this.password = password;
        this.uri = "";
        this.ssl = false;
        this.configuration = configuration;
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
     * Returns the uri.
     *
     * @return the uri
     */
    public String getUri() {
        return uri;
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
     *  Returns whether to use SSL or not
     *
     * @return true if SSL should be enabled.
     */
    public boolean isSsl() {
        return ssl;
    }
}
