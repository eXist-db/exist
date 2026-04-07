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

import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.lock.*;
import org.apache.jackrabbit.webdav.property.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Abstract base class for Jackrabbit WebDAV resources backed by eXist-db.
 * Provides common implementations for resource metadata, properties,
 * and lock management delegation.
 *
 * @author Joe Wicentowski
 */
public abstract class ExistDavResource implements DavResource {

    protected static final Logger LOG = LogManager.getLogger(ExistDavResource.class);

    /** WebDAV compliance class: Level 1 (basic) + Level 2 (locking). */
    protected static final String COMPLIANCE_CLASSES =
            DavCompliance.concatComplianceClasses(new String[]{
                    DavCompliance._1_, DavCompliance._2_
            });

    protected final Properties configuration;
    protected final XmldbURI xmldbUri;
    protected final BrokerPool brokerPool;
    protected final Subject subject;
    protected final DavResourceLocator locator;
    protected final DavSession session;
    protected final DavResourceFactory factory;

    protected LockManager lockManager;

    /**
     * Constructor.
     *
     * @param configuration WebDAV serialization options
     * @param xmldbUri      the database URI of this resource
     * @param brokerPool    the eXist-db broker pool
     * @param subject       the authenticated subject
     * @param locator       the DAV resource locator
     * @param session       the DAV session
     * @param factory       the resource factory
     */
    protected ExistDavResource(final Properties configuration, final XmldbURI xmldbUri,
            final BrokerPool brokerPool, final Subject subject,
            final DavResourceLocator locator, final DavSession session,
            final DavResourceFactory factory) {
        this.configuration = configuration;
        this.xmldbUri = xmldbUri;
        this.brokerPool = brokerPool;
        this.subject = subject;
        this.locator = locator;
        this.session = session;
        this.factory = factory;
    }

    /**
     * Get the underlying eXist-db resource. Subclasses must provide
     * the appropriate ExistDocument or ExistCollection instance.
     *
     * @return the eXist-db resource
     */
    protected abstract ExistResource getExistResource();

    @Override
    public String getComplianceClass() {
        return COMPLIANCE_CLASSES;
    }

    @Override
    public String getSupportedMethods() {
        return DavResource.METHODS;
    }

    @Override
    public DavResourceLocator getLocator() {
        return locator;
    }

    @Override
    public String getResourcePath() {
        return locator.getResourcePath();
    }

    @Override
    public String getHref() {
        return locator.getHref(isCollection());
    }

    @Override
    public String getDisplayName() {
        return xmldbUri.lastSegment().toString();
    }

    @Override
    public long getModificationTime() {
        final ExistResource resource = getExistResource();
        if (resource != null && resource.getLastModified() != null) {
            return resource.getLastModified();
        }
        return DavConstants.UNDEFINED_TIME;
    }

    @Override
    public DavPropertyName[] getPropertyNames() {
        return getProperties().getPropertyNames();
    }

    @Override
    public DavProperty<?> getProperty(final DavPropertyName name) {
        return getProperties().get(name);
    }

    @Override
    public DavPropertySet getProperties() {
        final DavPropertySet properties = new DavPropertySet();

        // Display name
        properties.add(new DefaultDavProperty<>(DavPropertyName.DISPLAYNAME, getDisplayName()));

        // Resource type
        if (isCollection()) {
            properties.add(new ResourceType(ResourceType.COLLECTION));
        } else {
            properties.add(new ResourceType(ResourceType.DEFAULT_RESOURCE));
        }

        // Last modified
        final ExistResource resource = getExistResource();
        if (resource != null) {
            if (resource.getLastModified() != null) {
                properties.add(new DefaultDavProperty<>(DavPropertyName.GETLASTMODIFIED,
                        DavConstants.modificationDateFormat.format(
                                new java.util.Date(resource.getLastModified()))));
            }

            if (resource.getCreationTime() != null) {
                properties.add(new DefaultDavProperty<>(DavPropertyName.CREATIONDATE,
                        DavConstants.creationDateFormat.format(
                                new java.util.Date(resource.getCreationTime()))));
            }
        }

        // isCollection property
        properties.add(new DefaultDavProperty<>(DavPropertyName.ISCOLLECTION,
                isCollection() ? "1" : "0"));

        // Supported lock types (RFC 4918 §15.10)
        final SupportedLock supportedLock = new SupportedLock();
        supportedLock.addEntry(Type.WRITE, Scope.EXCLUSIVE);
        supportedLock.addEntry(Type.WRITE, Scope.SHARED);
        properties.add(supportedLock);

        // Lock discovery (RFC 4918 §15.8)
        final ActiveLock[] activeLocks = getLocks();
        properties.add(new LockDiscovery(activeLocks));

        // Dead properties (custom user-defined properties)
        final DeadPropertyStore deadProps = getDeadPropertyStore();
        if (deadProps != null) {
            final DavPropertySet dead = deadProps.getProperties(xmldbUri.toString());
            for (final DavPropertyName name : dead.getPropertyNames()) {
                properties.add(dead.get(name));
            }
        }

        return properties;
    }

    @Override
    public void setProperty(final DavProperty<?> property) throws DavException {
        final DeadPropertyStore deadProps = getDeadPropertyStore();
        if (deadProps != null) {
            deadProps.setProperty(xmldbUri.toString(), property);
        } else {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Property modification is not supported");
        }
    }

    @Override
    public void removeProperty(final DavPropertyName propertyName) throws DavException {
        final DeadPropertyStore deadProps = getDeadPropertyStore();
        if (deadProps != null) {
            deadProps.removeProperty(xmldbUri.toString(), propertyName);
        } else {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Property removal is not supported");
        }
    }

    @Override
    public MultiStatusResponse alterProperties(final List<? extends PropEntry> changeList)
            throws DavException {
        final DeadPropertyStore deadProps = getDeadPropertyStore();
        if (deadProps == null) {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Property modification is not supported");
        }

        final MultiStatusResponse msr = new MultiStatusResponse(getHref(), null);
        for (final PropEntry entry : changeList) {
            if (entry instanceof DavProperty<?> prop) {
                deadProps.setProperty(xmldbUri.toString(), prop);
                msr.add(prop.getName(), DavServletResponse.SC_OK);
            } else if (entry instanceof DavPropertyName name) {
                deadProps.removeProperty(xmldbUri.toString(), name);
                msr.add(name, DavServletResponse.SC_OK);
            }
        }
        return msr;
    }

    private DeadPropertyStore getDeadPropertyStore() {
        if (factory instanceof ExistDavResourceFactory existFactory) {
            return existFactory.getDeadPropertyStore();
        }
        return null;
    }

    @Override
    public DavResourceFactory getFactory() {
        return factory;
    }

    @Override
    public DavSession getSession() {
        return session;
    }

    // -----------------------------------------------------------------------
    // Lock support - delegates to LockManager
    // -----------------------------------------------------------------------

    @Override
    public boolean isLockable(final Type type, final Scope scope) {
        return Type.WRITE.equals(type)
                && (Scope.EXCLUSIVE.equals(scope) || Scope.SHARED.equals(scope));
    }

    @Override
    public boolean hasLock(final Type type, final Scope scope) {
        return getLock(type, scope) != null;
    }

    @Override
    public ActiveLock getLock(final Type type, final Scope scope) {
        if (lockManager != null) {
            return lockManager.getLock(type, scope, this);
        }
        return null;
    }

    @Override
    public ActiveLock[] getLocks() {
        final List<ActiveLock> locks = new ArrayList<>();
        if (lockManager instanceof ExistLockManager existLockManager) {
            locks.addAll(existLockManager.getAllLocks(this));
        } else {
            final ActiveLock writeLock = getLock(Type.WRITE, Scope.EXCLUSIVE);
            if (writeLock != null) {
                locks.add(writeLock);
            }
        }
        // Include deep locks from ancestor collections — needed for indirect
        // lock refresh (litmus test 35) and If-header matching on child resources
        if (locks.isEmpty()) {
            DavResource parent = getCollection();
            while (parent != null) {
                final ActiveLock parentLock = parent.getLock(Type.WRITE, Scope.EXCLUSIVE);
                if (parentLock != null && parentLock.isDeep()) {
                    locks.add(parentLock);
                    break;
                }
                // Also check shared locks on parent
                final ActiveLock sharedLock = parent.getLock(Type.WRITE, Scope.SHARED);
                if (sharedLock != null && sharedLock.isDeep()) {
                    locks.add(sharedLock);
                    break;
                }
                parent = parent.getCollection();
            }
        }
        return locks.toArray(new ActiveLock[0]);
    }

    @Override
    public ActiveLock lock(final LockInfo reqLockInfo) throws DavException {
        if (lockManager != null) {
            return lockManager.createLock(reqLockInfo, this);
        }
        throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                "No lock manager available");
    }

    @Override
    public ActiveLock refreshLock(final LockInfo reqLockInfo, final String lockToken)
            throws DavException {
        if (lockManager != null) {
            return lockManager.refreshLock(reqLockInfo, lockToken, this);
        }
        throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                "No lock manager available");
    }

    @Override
    public void unlock(final String lockToken) throws DavException {
        if (lockManager != null) {
            lockManager.releaseLock(lockToken, this);
        } else {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "No lock manager available");
        }
    }

    @Override
    public void addLockManager(final LockManager lockManager) {
        this.lockManager = lockManager;
    }

    // -----------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------

    /**
     * Get the XmldbURI for this resource.
     *
     * @return the XmldbURI
     */
    public XmldbURI getXmldbUri() {
        return xmldbUri;
    }

    /**
     * Get the authenticated subject.
     *
     * @return the subject
     */
    public Subject getSubject() {
        return subject;
    }

    /**
     * Get the broker pool.
     *
     * @return the broker pool
     */
    public BrokerPool getBrokerPool() {
        return brokerPool;
    }

    /**
     * Copy dead properties from this resource to a destination.
     * Call after a successful COPY or MOVE operation.
     */
    protected void copyDeadProperties(final DavResource destination) {
        final DeadPropertyStore store = getDeadPropertyStore();
        if (store != null && destination instanceof ExistDavResource existDest) {
            store.copyProperties(xmldbUri.toString(), existDest.getXmldbUri().toString());
        }
    }

    /**
     * Move dead properties from this resource to a destination (copy + delete source).
     * Call after a successful MOVE operation.
     */
    protected void moveDeadProperties(final DavResource destination) {
        final DeadPropertyStore store = getDeadPropertyStore();
        if (store != null && destination instanceof ExistDavResource existDest) {
            store.copyProperties(xmldbUri.toString(), existDest.getXmldbUri().toString());
            store.removeAll(xmldbUri.toString());
        }
    }
}
