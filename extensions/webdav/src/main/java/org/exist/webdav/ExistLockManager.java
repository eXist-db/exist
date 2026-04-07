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

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.lock.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;

/**
 * Lock manager using /db/system/webdav-locks/ for persistent lock storage.
 *
 * <p>Locks are stored as XML documents in a system collection, keyed by the
 * resource URI path. This approach decouples locks from document instances,
 * so locks survive document replacement (PUT), server restarts, and
 * collection moves.</p>
 *
 * <p>The previous implementation stored locks as document metadata via
 * {@code DocumentImpl.setLockToken()}, which was lost when PUT replaced
 * the document.</p>
 *
 * @author Joe Wicentowski
 */
public class ExistLockManager implements LockManager {

    private static final Logger LOG = LogManager.getLogger(ExistLockManager.class);

    private final WebDavLockStore lockStore;

    public ExistLockManager(final BrokerPool brokerPool) {
        this.lockStore = new WebDavLockStore(brokerPool);
    }

    @Override
    public ActiveLock createLock(final LockInfo reqLockInfo, final DavResource resource)
            throws DavException {

        if (!(resource instanceof ExistDavResource davResource)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Locking is only supported for eXist resources");
        }

        // Only exclusive write locks are supported
        if (!Type.WRITE.equals(reqLockInfo.getType())) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Only write locks are supported");
        }

        if (!Scope.EXCLUSIVE.equals(reqLockInfo.getScope())) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Only exclusive locks are supported");
        }

        final String resourceUri = davResource.getXmldbUri().toString();

        // Check if already locked
        final WebDavLockStore.LockInfo existingLock = lockStore.getLock(resourceUri);
        if (existingLock != null) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Resource is already locked");
        }

        try {
            final String owner = reqLockInfo.getOwner();
            final long timeout = reqLockInfo.getTimeout();
            final String token = lockStore.storeLock(
                    resourceUri,
                    owner != null ? owner : "unknown",
                    "exclusive",
                    "write",
                    reqLockInfo.isDeep(),
                    timeout > 0 ? timeout : 3600
            );

            return buildActiveLock(token, owner, reqLockInfo.isDeep(), timeout, resource);

        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create lock: " + e.getMessage());
        }
    }

    @Override
    public ActiveLock refreshLock(final LockInfo reqLockInfo, final String lockToken,
            final DavResource resource) throws DavException {

        if (!(resource instanceof ExistDavResource davResource)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Locking is only supported for eXist resources");
        }

        final String resourceUri = davResource.getXmldbUri().toString();
        final String cleanToken = stripTokenPrefix(lockToken);

        final WebDavLockStore.LockInfo lock = lockStore.getLock(resourceUri);
        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Resource is not locked");
        }

        if (!lock.token.equals(cleanToken)) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Lock token does not match");
        }

        // Refresh: remove and re-create with new timeout
        try {
            lockStore.removeLock(resourceUri);
            final long timeout = reqLockInfo.getTimeout();
            final String newToken = lockStore.storeLock(
                    resourceUri,
                    lock.owner,
                    lock.scope,
                    lock.type,
                    lock.deep,
                    timeout > 0 ? timeout : 3600
            );
            return buildActiveLock(newToken, lock.owner, lock.deep, timeout, resource);
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to refresh lock: " + e.getMessage());
        }
    }

    @Override
    public void releaseLock(final String lockToken, final DavResource resource)
            throws DavException {

        if (!(resource instanceof ExistDavResource davResource)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Locking is only supported for eXist resources");
        }

        final String resourceUri = davResource.getXmldbUri().toString();
        final String cleanToken = stripTokenPrefix(lockToken);

        final WebDavLockStore.LockInfo lock = lockStore.getLock(resourceUri);
        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Resource is not locked");
        }

        if (!lock.token.equals(cleanToken)) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Lock token does not match");
        }

        try {
            lockStore.removeLock(resourceUri);
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to release lock: " + e.getMessage());
        }
    }

    @Override
    public ActiveLock getLock(final Type type, final Scope scope, final DavResource resource) {
        if (!Type.WRITE.equals(type) || !Scope.EXCLUSIVE.equals(scope)) {
            return null;
        }

        if (!(resource instanceof ExistDavResource davResource)) {
            return null;
        }

        final String resourceUri = davResource.getXmldbUri().toString();
        final WebDavLockStore.LockInfo lock = lockStore.getLock(resourceUri);
        if (lock == null) {
            return null;
        }

        return buildActiveLock(lock.token, lock.owner, lock.deep, 0, resource);
    }

    @Override
    public boolean hasLock(final String lockToken, final DavResource resource) {
        if (!(resource instanceof ExistDavResource davResource)) {
            return false;
        }

        final String resourceUri = davResource.getXmldbUri().toString();
        final String cleanToken = stripTokenPrefix(lockToken);
        return lockStore.hasLock(resourceUri, cleanToken);
    }

    // -----------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------

    private ActiveLock buildActiveLock(final String token, final String owner,
                                       final boolean deep, final long timeout,
                                       final DavResource resource) {
        final ExistActiveLock activeLock = new ExistActiveLock();
        activeLock.setOwner(owner);
        activeLock.setIsDeep(deep);
        activeLock.setLockroot(resource.getHref());
        activeLock.setToken("opaquelocktoken:" + token);

        if (timeout > 0) {
            activeLock.setTimeout(timeout);
        } else {
            activeLock.setTimeout(Long.MAX_VALUE / 2);
        }

        return activeLock;
    }

    private String stripTokenPrefix(final String token) {
        if (token != null && token.startsWith("opaquelocktoken:")) {
            return token.substring("opaquelocktoken:".length());
        }
        return token;
    }
}
