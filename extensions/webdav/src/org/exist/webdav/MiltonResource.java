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

import com.bradmcevoy.http.Auth;
import com.bradmcevoy.http.LockInfo;
import com.bradmcevoy.http.LockTimeout;
import com.bradmcevoy.http.LockToken;
import com.bradmcevoy.http.Request;
import com.bradmcevoy.http.Request.Method;
import com.bradmcevoy.http.Resource;

import java.util.Date;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;

import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;

/**
 * Generic class representing a Milton Resource.
 * 
 * @author Dannes Wessels <dannes@exist-db.org>
 */
public class MiltonResource implements Resource {

    protected final static Logger LOG = Logger.getLogger(MiltonResource.class);
    protected XmldbURI resourceXmldbUri;
    protected BrokerPool brokerPool;
    protected String host;
    protected Subject subject;
    protected final static String AUTHENTICATED = "AUTHENTICATED";
    protected String REALM = "exist";
    protected ExistResource existResource;

    // Used for Long to DateTime conversion
    private DatatypeFactory datatypeFactory;

    public MiltonResource() {
        if(datatypeFactory==null){
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
     * @param date Representation of data
     * @return ISO8601 like formatted representation of date.s
     */
    protected String getXmlDateTime(Long date){
        // Convert to Calendar
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime( new Date(date) );

        // COnvert to XML dateTimes
        XMLGregorianCalendar xgc = datatypeFactory.newXMLGregorianCalendar(gc);
        return xgc.toXMLFormat();
    }

    /**
     *  Converts an org.exist.dom.LockToken into com.bradmcevoy.http.LockToken.
     *
     * @param existLT Exist-db representation of a webdav token.
     * @return Milton representation of a webdav token.
     */
    protected LockToken convertToken(org.exist.dom.LockToken existLT) {

        // LockInfo : construct scope
        LockInfo.LockScope scope = null;

        switch (existLT.getScope()) {
            case org.exist.dom.LockToken.LOCK_SCOPE_SHARED:
                scope = LockInfo.LockScope.SHARED;
                break;

            case org.exist.dom.LockToken.LOCK_SCOPE_EXCLUSIVE:
                scope = LockInfo.LockScope.EXCLUSIVE;
                break;

            default:
                scope = LockInfo.LockScope.NONE;
                break;
        }

        // LockInfo : construct type
        LockInfo.LockType type = null;

        switch (existLT.getType()) {
            case org.exist.dom.LockToken.LOCK_TYPE_WRITE:
                type = LockInfo.LockType.WRITE;
                break;

            default: // DWES: if not WRITE then READ. typical :-)
                type = LockInfo.LockType.READ;
                break;

        }

        // LockInfo : get owner
        String owner = existLT.getOwner();

        // LockInfo : construct depth
        LockInfo.LockDepth depth = null;

        switch (existLT.getDepth()) {
            case org.exist.dom.LockToken.LOCK_DEPTH_INFINIY:
                depth = LockInfo.LockDepth.INFINITY;
                break;

            default: // TODO either zero or infinity?
                depth = LockInfo.LockDepth.ZERO;
                break;
        }


        // LockInfo
        LockInfo li = new LockInfo(scope, type, owner, depth);

        // Lock Timeout
        Long timeout = existLT.getTimeOut();
        LockTimeout lt = new LockTimeout(timeout);

        // Token Id
        String id = existLT.getOpaqueLockToken();

        // Return values in Milton object
        return new LockToken(id, li, lt);

    }

    /**
     *  Converts an org.exist.dom.LockToken into com.bradmcevoy.http.LockToken.
     */
    protected org.exist.dom.LockToken convertToken(LockTimeout timeout, LockInfo lockInfo) {

        org.exist.dom.LockToken existToken = new org.exist.dom.LockToken();

        // Set lock depth
        switch (lockInfo.depth) {
            case ZERO:
                existToken.setDepth(org.exist.dom.LockToken.LOCK_DEPTH_0);
                break;
            case INFINITY:
                existToken.setDepth(org.exist.dom.LockToken.LOCK_DEPTH_INFINIY);
                break;
        }


        // Set lock scope
        switch (lockInfo.scope) {
            case EXCLUSIVE:
                existToken.setScope(org.exist.dom.LockToken.LOCK_SCOPE_EXCLUSIVE);
                break;
            case SHARED:
                existToken.setScope(org.exist.dom.LockToken.LOCK_SCOPE_SHARED);
                break;
            case NONE:
                existToken.setScope(org.exist.dom.LockToken.LOCK_SCOPE_NONE);
                break;
        }

        // Set lock type (read,write)
        switch (lockInfo.type) {
            case READ:
                existToken.setScope(org.exist.dom.LockToken.LOCK_TYPE_NONE);
                break;
            case WRITE:
                existToken.setScope(org.exist.dom.LockToken.LOCK_TYPE_WRITE);
                break;
        }


        // Set timeouts
        existToken.setTimeOut(timeout.getSeconds());

        return existToken;
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
        return resourceXmldbUri.lastSegment().toString();
    }

    @Override
    public Object authenticate(String username, String password) {

        LOG.debug("Authenticating user " + username + " for " + resourceXmldbUri);

        // Check if username is provided.
        if (username == null) {
            return null;
        }

        // Check is subject was already authenticated.
        if (subject != null) {
            LOG.debug("User was already authenticated.");
            return AUTHENTICATED;
        }

        // Authenticate subject with password
        subject = existResource.authenticate(username, password);

        // Quick return if no subject object was returned
        if (subject == null) {
            LOG.debug("User could not be authenticated.");
            return null;
        }

        // Guest is not allowed to access.
        Subject guest = brokerPool.getSecurityManager().getGuestSubject();
        if (guest.equals(subject)) {
            LOG.error("The user " + guest.getName() + " is prohibited from logging in through WebDAV.");
            return null;
        }

        // Note: If User object is returned, authentication was OK

        // Collect data for this resource
        existResource.initMetadata();

        LOG.debug("User '" + subject.getName() + "' has been authenticated.");
        return AUTHENTICATED;
    }

    @Override
    public boolean authorise(Request request, Method method, Auth auth) {

        LOG.info("" + method.toString() + "  " + method.isWrite + " for " + resourceXmldbUri);

        /*
         * First perform checks on Milton authentication
         */
        if (auth == null) {
            LOG.debug("User hasn't been authenticated.");
            return false;
        }

        // Get effective username
        String userName = auth.getUser();

        // Get authentication object
        Object tag = auth.getTag();

        // Get URI. no idea why value is null.s
        String authURI = auth.getUri();

        // If object does not exist, there was no successfull authentication
        if (tag == null) {
            LOG.debug("No tag, user " + userName + " not authenticated");
            return false;

        } else if (tag instanceof String) {
            String value = (String) tag;
            if (AUTHENTICATED.equals(value)) {
                // The correct TAG is returned!

            } else {
                LOG.debug("Authentication tag contains wrong value, user "
                                                        + userName + " is not authenticated");
                return false;
            }
        }

        /*
         * Second perform checks on actual exist-db permissions
         */
        if (method.isWrite) {
            if (!existResource.writeAllowed) {
                LOG.debug("User " + userName + " is NOT authorized to write resource, abort.");
                return false;
            }

        } else {
            if (!existResource.readAllowed) {
                LOG.debug("User " + userName + " is NOT authorized to read resource, abort.");
                return false;
            }
        }

        if(auth.getUri()==null){
            LOG.debug("URI is null");
            // not sure why the null value can be there
        }

        String action = method.isWrite ? "write" : "read";
        LOG.debug("User " + userName + " is authorized to " + action 
                                                    + " resource " + resourceXmldbUri.toString());

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

        LOG.debug("Modified date=" + modifiedDate);

        return modifiedDate;
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }
}
