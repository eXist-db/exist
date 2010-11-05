/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.webdav;

import org.apache.log4j.Logger;

import org.exist.security.SecurityManager;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;

/**
 * Generic class representing an eXist Resource.
 * 
 *  @author Dannes Wessels <dannes@exist-db.org>
 */
public abstract class ExistResource {

    protected final static Logger LOG = Logger.getLogger(ExistResource.class);
    protected boolean isInitialized = false;
    protected BrokerPool brokerPool;
    protected User user;
    protected XmldbURI xmldbUri;
    protected Permission permissions;
    protected Long creationTime;
    protected Long lastModified;
    protected boolean readAllowed = false;
    protected boolean writeAllowed = false;
    protected boolean updateAllowed = false;
    protected ExistResource existResource;

    protected String ownerUser;
    protected String ownerGroup;

    protected enum Mode {
        MOVE, COPY
    }

    abstract void initMetadata();

    protected boolean isReadAllowed() {
        return readAllowed;
    }

    protected boolean isWriteAllowed() {
        return writeAllowed;
    }

    protected boolean isUpdateAllowed() {
        return updateAllowed;
    }

    protected User getUser() {
        return user;
    }

    protected void setUser(User user) {
        this.user = user;
    }

    protected Long getCreationTime() {
        return creationTime;
    }

    protected Long getLastModified() {
        return lastModified;
    }

    protected Permission getPermissions() {
        return permissions;
    }

    public String getOwnerGroup() {
        return ownerGroup;
    }

    public String getOwnerUser() {
        return ownerUser;
    }

    /**
     * Authenticate user with password. NULL is returned when
     * the user could not be authenticated.
     */
    protected User authenticate(String username, String password) {

        if (username == null) {
            return null;
        }

        SecurityManager securityManager = brokerPool.getSecurityManager();
        user = securityManager.getUser(username);

        if(user==null){
            LOG.debug("Username " + username + " does not exist.");
            return null;
        }

        if(user.validate(password)){
            return user;

        } else {
            LOG.debug("User " + username + " could not be authenticated. ");
            return null;
        }

    }
}
