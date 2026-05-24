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
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;

import java.util.ArrayList;
import java.util.List;

/**
 * Lock manager using /db/system/webdav-locks/ for persistent lock storage.
 * Supports both exclusive and shared write locks (WebDAV Level 2).
 *
 * @author Joe Wicentowski
 */
public class ExistLockManager implements LockManager {

    private final WebDavLockStore lockStore;

    public ExistLockManager(final BrokerPool brokerPool) {
        this.lockStore = new WebDavLockStore(brokerPool);
    }

    @Override
    public ActiveLock createLock(final LockInfo reqLockInfo, final DavResource resource)
            throws DavException {
        final ExistDavResource davResource = validateLockRequest(reqLockInfo, resource);
        final Scope scope = reqLockInfo.getScope();
        final String resourceUri = davResource.getXmldbUri().toString();
        checkExistingLockCompatibility(resourceUri, scope);
        final String token = storeLockEntry(reqLockInfo, resourceUri, scope);
        return buildActiveLock(token, reqLockInfo.getOwner(), reqLockInfo.isDeep(),
                reqLockInfo.getTimeout(), scope, resource);
    }

    /**
     * Verifies the request can be honored by this lock manager — resource type,
     * lock type, and scope must all be supported. Returns the narrowed resource.
     */
    private static ExistDavResource validateLockRequest(final LockInfo reqLockInfo, final DavResource resource)
            throws DavException {
        if (!(resource instanceof ExistDavResource davResource)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Locking is only supported for eXist resources");
        }
        if (!Type.WRITE.equals(reqLockInfo.getType())) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Only write locks are supported");
        }
        final Scope scope = reqLockInfo.getScope();
        if (!Scope.SHARED.equals(scope) && !Scope.EXCLUSIVE.equals(scope)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Only exclusive or shared locks are supported");
        }
        return davResource;
    }

    /**
     * Throws SC_LOCKED if the requested scope is incompatible with locks already
     * held on the resource (RFC 4918 §6.2): exclusive vs. anything is denied; a
     * new exclusive on a shared-locked resource is denied; shared + shared is
     * permitted.
     */
    private void checkExistingLockCompatibility(final String resourceUri, final Scope requestedScope)
            throws DavException {
        final List<WebDavLockStore.LockInfo> existing = lockStore.getLocks(resourceUri);
        if (existing.isEmpty()) {
            return;
        }
        final boolean existingIsExclusive = existing.stream().anyMatch(l -> "exclusive".equals(l.scope));
        if (existingIsExclusive) {
            throw new DavException(DavServletResponse.SC_LOCKED, "Resource has an exclusive lock");
        }
        if (Scope.EXCLUSIVE.equals(requestedScope)) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Resource has shared locks; cannot add exclusive lock");
        }
    }

    /**
     * Persists the lock and returns its newly-allocated token.
     */
    private String storeLockEntry(final LockInfo reqLockInfo, final String resourceUri, final Scope scope)
            throws DavException {
        final String owner = reqLockInfo.getOwner();
        final long timeout = reqLockInfo.getTimeout();
        try {
            return lockStore.storeLock(
                    resourceUri,
                    owner != null ? owner : "unknown",
                    Scope.SHARED.equals(scope) ? "shared" : "exclusive",
                    "write",
                    reqLockInfo.isDeep(),
                    timeout > 0 ? timeout : 3600
            );
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

        final String cleanToken = stripTokenPrefix(lockToken);

        // Find the lock — either on this resource or on an ancestor collection
        String lockUri = davResource.getXmldbUri().toString();
        WebDavLockStore.LockInfo lock = lockStore.getLockByToken(lockUri, cleanToken);

        if (lock == null) {
            // Walk up ancestor URIs to find a matching collection lock
            XmldbURI ancestorUri = davResource.getXmldbUri().removeLastSegment();
            while (ancestorUri != null && !"/".equals(ancestorUri.toString())) {
                final String ancestorPath = ancestorUri.toString();
                final WebDavLockStore.LockInfo ancestorLock = lockStore.getLockByToken(ancestorPath, cleanToken);
                if (ancestorLock != null) {
                    lock = ancestorLock;
                    lockUri = ancestorPath;
                    break;
                }
                ancestorUri = ancestorUri.removeLastSegment();
            }
        }

        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "No lock found with the given token");
        }

        // Refresh: remove the specific lock entry and re-add with new timeout
        try {
            lockStore.removeLockByToken(lockUri, lock.token);
            final long timeout = reqLockInfo.getTimeout();
            lockStore.storeLockWithToken(
                    lock.token, lockUri, lock.owner, lock.scope, lock.type,
                    lock.deep, timeout > 0 ? timeout : 3600
            );
            final Scope scope = "shared".equals(lock.scope) ? Scope.SHARED : Scope.EXCLUSIVE;
            return buildActiveLock(lock.token, lock.owner, lock.deep, timeout, scope, resource);
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

        final WebDavLockStore.LockInfo lock = lockStore.getLockByToken(resourceUri, cleanToken);
        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "No lock found with the given token");
        }

        try {
            lockStore.removeLockByToken(resourceUri, cleanToken);
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to release lock: " + e.getMessage());
        }
    }

    @Override
    public ActiveLock getLock(final Type type, final Scope scope, final DavResource resource) {
        if (!Type.WRITE.equals(type)) {
            return null;
        }

        if (!(resource instanceof ExistDavResource davResource)) {
            return null;
        }

        final String resourceUri = davResource.getXmldbUri().toString();
        final String scopeStr = Scope.SHARED.equals(scope) ? "shared" : "exclusive";

        for (final WebDavLockStore.LockInfo lock : lockStore.getLocks(resourceUri)) {
            if (scopeStr.equals(lock.scope)) {
                return buildActiveLock(lock.token, lock.owner, lock.deep, 0,
                        scope, resource);
            }
        }

        return null;
    }

    /**
     * Get all locks on a resource (both exclusive and shared).
     */
    public List<ActiveLock> getAllLocks(final DavResource resource) {
        if (!(resource instanceof ExistDavResource davResource)) {
            return List.of();
        }

        final String resourceUri = davResource.getXmldbUri().toString();
        final List<ActiveLock> result = new ArrayList<>();

        for (final WebDavLockStore.LockInfo lock : lockStore.getLocks(resourceUri)) {
            final Scope scope = "shared".equals(lock.scope) ? Scope.SHARED : Scope.EXCLUSIVE;
            result.add(buildActiveLock(lock.token, lock.owner, lock.deep, 0, scope, resource));
        }

        return result;
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
                                       final Scope scope, final DavResource resource) {
        final ExistActiveLock activeLock = new ExistActiveLock();
        activeLock.setOwner(owner);
        activeLock.setIsDeep(deep);
        activeLock.setLockroot(resource.getHref());
        activeLock.setToken("opaquelocktoken:" + token);
        activeLock.setScope(scope);

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
