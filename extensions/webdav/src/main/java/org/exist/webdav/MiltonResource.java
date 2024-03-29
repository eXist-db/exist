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
package org.exist.webdav;

import com.bradmcevoy.http.*;
import com.bradmcevoy.http.Request.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;

/**
 * Generic class representing a Milton Resource.
 *
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 */
public class MiltonResource implements Resource {

    protected final static Logger LOG = LogManager.getLogger(MiltonResource.class);
    protected final static String AUTHENTICATED = "AUTHENTICATED";
    protected XmldbURI resourceXmldbUri;
    protected BrokerPool brokerPool;
    protected String host;
    protected Subject subject;
    protected String REALM = "exist";
    protected ExistResource existResource;

    // Used for Long to DateTime conversion
    private DatatypeFactory datatypeFactory;

    protected final Properties configuration;

    public MiltonResource(final Properties configuration) {
        this.configuration = configuration;

        if (datatypeFactory == null) {
            try {
                datatypeFactory = DatatypeFactory.newInstance();
            } catch (DatatypeConfigurationException ex) {
                LOG.error(ex);
            }
        }
    }

    protected XmldbURI getXmldbUri() {
        return resourceXmldbUri;
    }

    protected String getHost() {
        return host;
    }

    private Subject getUserAsSubject() {
        return subject;
    }

    /**
     * Convert date to dateTime XML format.
     * s
     *
     * @param date Representation of data
     * @return ISO8601 like formatted representation of date.s
     */
    protected String getXmlDateTime(Long date) {
        // Convert to Calendar
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(new Date(date));

        // COnvert to XML dateTimes
        XMLGregorianCalendar xgc = datatypeFactory.newXMLGregorianCalendar(gc);
        return xgc.toXMLFormat();
    }

    /**
     * Converts an org.exist.dom.persistent.LockToken into com.bradmcevoy.http.LockToken.
     *
     * @param existLT Exist-db representation of a webdav token.
     * @return Milton representation of a webdav token.
     */
    protected LockToken convertToken(org.exist.dom.persistent.LockToken existLT) {

        // LockInfo : construct scope
        final LockInfo.LockScope scope = switch (existLT.getScope()) {
            case SHARED -> LockInfo.LockScope.SHARED;
            case EXCLUSIVE -> LockInfo.LockScope.EXCLUSIVE;
            default -> LockInfo.LockScope.NONE;
        };

        // LockInfo : construct type
        final LockInfo.LockType type = switch (existLT.getType()) {
            case WRITE -> LockInfo.LockType.WRITE;
            default -> // DWES: if not WRITE then READ. typical :-)
                    LockInfo.LockType.READ;
        };

        // LockInfo : get owner
        final String owner = existLT.getOwner();

        // LockInfo : construct depth
        final LockInfo.LockDepth depth = switch (existLT.getDepth()) {
            case INFINITY -> LockInfo.LockDepth.INFINITY;
            default -> // TODO either zero or infinity?
                    LockInfo.LockDepth.ZERO;
        };


        // LockInfo
        LockInfo li = new LockInfo(scope, type, owner, depth);

        // Lock Timeout
        Long timeout = existLT.getTimeOut();

        // Special treatment when no LOCK was present
        if (timeout == org.exist.dom.persistent.LockToken.NO_LOCK_TIMEOUT) {
            timeout = null;

            // Special treatment infinite lock
        } else if (timeout == org.exist.dom.persistent.LockToken.LOCK_TIMEOUT_INFINITE) {
            timeout = Long.MAX_VALUE;
        }

        LockTimeout lt = new LockTimeout(timeout);

        // Token Id
        String id = existLT.getOpaqueLockToken();

        // Return values in Milton object
        return new LockToken(id, li, lt);

    }

    /**
     *  Converts an org.exist.dom.persistent.LockToken into com.bradmcevoy.http.LockToken.
     * @param timeout Lock time out
     * @param lockInfo Lock info
     * @return The lock token
     */
    protected org.exist.dom.persistent.LockToken convertToken(final LockTimeout timeout, final LockInfo lockInfo) {

        // Set lock depth
        final org.exist.dom.persistent.LockToken.LockDepth lockDepth = switch (lockInfo.depth) {
            case ZERO -> org.exist.dom.persistent.LockToken.LockDepth.ZERO;
            case INFINITY -> org.exist.dom.persistent.LockToken.LockDepth.INFINITY;
            default -> org.exist.dom.persistent.LockToken.LockDepth.NOT_SET;
        };

        // Set lock scope
        final org.exist.dom.persistent.LockToken.LockScope lockScope = switch (lockInfo.scope) {
            case EXCLUSIVE -> org.exist.dom.persistent.LockToken.LockScope.EXCLUSIVE;
            case SHARED -> org.exist.dom.persistent.LockToken.LockScope.SHARED;
            case NONE -> org.exist.dom.persistent.LockToken.LockScope.NONE;
            default -> org.exist.dom.persistent.LockToken.LockScope.NOT_SET;
        };

        // Set lock type (read,write)
        final org.exist.dom.persistent.LockToken.LockType lockType = switch (lockInfo.type) {
            case READ -> org.exist.dom.persistent.LockToken.LockType.NONE;
            case WRITE -> org.exist.dom.persistent.LockToken.LockType.WRITE;
            default -> org.exist.dom.persistent.LockToken.LockType.NOT_SET;
        };


        // Set timeouts
        final long lockTimeout;
        if (timeout == null || timeout.getSeconds() == null) {
            lockTimeout = org.exist.dom.persistent.LockToken.NO_LOCK_TIMEOUT;
        } else if (timeout.getSeconds() == Long.MAX_VALUE) {
            lockTimeout = org.exist.dom.persistent.LockToken.LOCK_TIMEOUT_INFINITE;
        } else {
            final long futureDate = (new Date().getTime()) / 1000 + timeout.getSeconds();
            lockTimeout = futureDate;
        }

        // Copy username if existent
        final String lockOwner = lockInfo.lockedByUser != null ? lockInfo.lockedByUser : null;

        final org.exist.dom.persistent.LockToken existToken = new org.exist.dom.persistent.LockToken(
                lockType, lockDepth, lockScope, lockOwner, lockTimeout, null,
                org.exist.dom.persistent.LockToken.ResourceType.NOT_SPECIFIED);
        return existToken;
    }


    /**
     * Convert % encoded string back to text.
     *
     * @param uri URI pf path
     * @return decoded path
     */
    protected XmldbURI decodePath(XmldbURI uri) {

        XmldbURI retval = null;

        try {
            String path = new URI(uri.toString()).getPath();

            retval = XmldbURI.xmldbUriFor("" + path, false);

        } catch (URISyntaxException ex) {
            // oops
            LOG.error(ex.getMessage());

        }
        return retval;
    }

    /**
     * Convert % encoded string back to text
     *
     * @param uri URI of path
     * @return encoded path
     */
    protected String decodePath(String uri) {

        String path = null;

        try {
            path = new URI(uri).getPath();

        } catch (URISyntaxException ex) {
            // oops
            LOG.error(ex.getMessage());
        }
        return path;
    }

    /* ========
     * Resource
     * ======== */

    @Override
    public String getUniqueId() {
        return null; // disables the ETag field
    }

    @Override
    public String getName() {
        return decodePath("" + resourceXmldbUri.lastSegment());
    }

    @Override
    public Object authenticate(String username, String password) {

        if (LOG.isDebugEnabled())
            LOG.debug("Authenticating user {} for {}", username, resourceXmldbUri);

        // Check if username is provided.
        if (username == null) {
            return null;
        }

        // Check is subject was already authenticated.
        if (subject != null) {
            if (LOG.isDebugEnabled())
                LOG.debug("User was already authenticated.");
            return AUTHENTICATED;
        }

        // Authenticate subject with password
        subject = existResource.authenticate(username, password);

        // Quick return if no subject object was returned
        if (subject == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("User could not be authenticated.");
            return null;
        }

        // Guest is not allowed to access.
        Subject guest = brokerPool.getSecurityManager().getGuestSubject();
        if (guest.equals(subject)) {
            LOG.error("The user {} is prohibited from logging in through WebDAV.", guest.getName());
            return null;
        }

        // Note: If User object is returned, authentication was OK

        // Collect data for this resource
        existResource.initMetadata();

        if (LOG.isDebugEnabled())
            LOG.debug("User '{}' has been authenticated.", subject.getName());
        return AUTHENTICATED;
    }

    @Override
    public boolean authorise(Request request, Method method, Auth auth) {

        LOG.info("{} {} (write={})", method.toString(), resourceXmldbUri, method.isWrite);

        /*
         * First perform checks on Milton authentication
         */
        if (auth == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("User hasn't been authenticated.");
            return false;
        }

        // Get effective username
        String userName = auth.getUser();

        // Get authentication object
        Object tag = auth.getTag();

        // Get URI. no idea why value is null.
        String authURI = auth.getUri();

        // If object does not exist, there was no successfull authentication
        if (tag == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("No tag, user {} not authenticated", userName);
            return false;

        } else if (tag instanceof String value) {
            if (AUTHENTICATED.equals(value)) {
                // The correct TAG is returned!

            } else {
                if (LOG.isDebugEnabled())
                    LOG.debug("Authentication tag contains wrong value, user {} is not authenticated", userName);
                return false;
            }
        }

        /*
         * Second perform checks on actual exist-db permissions
         */
        if (method.isWrite) {
            if (!existResource.writeAllowed) {
                if (LOG.isDebugEnabled())
                    LOG.debug("User {} is NOT authorized to write resource, abort.", userName);
                return false;
            }

        } else {
            if (!existResource.readAllowed) {
                if (LOG.isDebugEnabled())
                    LOG.debug("User {} is NOT authorized to read resource, abort.", userName);
                return false;
            }
        }

        if (auth.getUri() == null) {
            if (LOG.isTraceEnabled())
                LOG.trace("URI is null");
            // not sure why the null value can be there
        }

        String action = method.isWrite ? "write" : "read";
        if (LOG.isDebugEnabled())
            LOG.debug("User {} is authorized to {} resource {}", userName, action, resourceXmldbUri.toString());

        return true;
    }

    @Override
    public String getRealm() {
        return REALM;
    }

    @Override
    public Date getModifiedDate() {

        Date modifiedDate = null;

        Long time = existResource.getLastModified();
        if (time != null) {
            modifiedDate = new Date(time);
        }

//        if(LOG.isDebugEnabled())
//            LOG.debug("Modified date=" + modifiedDate);

        return modifiedDate;
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }
}
