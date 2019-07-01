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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.AuthenticationException;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;

import java.util.Properties;

/**
 * Generic class representing an eXist Resource.
 *
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 */
public abstract class ExistResource {

    protected final static Logger LOG = LogManager.getLogger(ExistResource.class);
    protected boolean isInitialized = false;
    protected BrokerPool brokerPool;
    protected Subject subject;
    protected XmldbURI xmldbUri;
    protected Permission permissions;
    protected Long creationTime;
    protected Long lastModified;
    protected boolean readAllowed = false;
    protected boolean writeAllowed = false;
    protected boolean executeAllowed = false;

    protected String ownerUser;
    protected String ownerGroup;

    protected Properties configuration = new Properties();

    abstract void initMetadata();

    protected boolean isReadAllowed() {
        return readAllowed;
    }

    protected boolean isWriteAllowed() {
        return writeAllowed;
    }

    protected boolean isUpdateAllowed() {
        return executeAllowed;
    }

    protected Subject getUser() {
        return subject;
    }

    protected void setUser(Subject user) {
        this.subject = user;
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

    public Properties getSerializationConfiguration() {
        return configuration;
    }

    public void setSerializationConfiguration(Properties config) {
        configuration = config;
    }

    /**
     * Authenticate subject with password. NULL is returned when
     * the subject could not be authenticated.
     *
     * @param username Username
     * @param password Password
     * @return Authenticated subject, or NULL when authentication failed.
     */
    protected Subject authenticate(String username, String password) {

        if (username == null) {
            return null;
        }

        SecurityManager securityManager = brokerPool.getSecurityManager();
        try {
            subject = securityManager.authenticate(username, password);

        } catch (AuthenticationException e) {
            LOG.info(String.format("User %s could not be authenticated. %s", username, e.getMessage()));
        }
        return subject;
    }

    protected enum Mode {
        MOVE, COPY
    }
}
