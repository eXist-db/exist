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
import org.exist.dom.persistent.LockToken;
import org.exist.security.PermissionDeniedException;
import org.exist.webdav.exceptions.DocumentAlreadyLockedException;
import org.exist.webdav.exceptions.DocumentNotLockedException;

/**
 * Lock manager implementation that delegates to eXist-db's persistent
 * document locking mechanism. Locks are stored in the database metadata,
 * so they survive server restarts.
 *
 * <p>Only document locking is supported (WebDAV Level 2, write/exclusive).
 * Collection locking is not supported by eXist-db's storage layer.</p>
 *
 * @author Joe Wicentowski
 */
public class ExistLockManager implements LockManager {

    private static final Logger LOG = LogManager.getLogger(ExistLockManager.class);

    @Override
    public ActiveLock createLock(final LockInfo reqLockInfo, final DavResource resource)
            throws DavException {

        if (!(resource instanceof ExistDavDocument davDoc)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Locking is only supported for document resources");
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

        // Create the eXist-db lock token
        final LockToken inputToken = new LockToken(
                LockToken.LockType.WRITE,
                reqLockInfo.isDeep() ? LockToken.LockDepth.INFINITY : LockToken.LockDepth.ZERO,
                LockToken.LockScope.EXCLUSIVE,
                reqLockInfo.getOwner(),
                reqLockInfo.getTimeout(),
                null,
                LockToken.ResourceType.NOT_SPECIFIED
        );

        // Initialize the document backend
        final ExistDocument existDoc = new ExistDocument(
                davDoc.configuration, davDoc.getXmldbUri(), davDoc.getBrokerPool());
        existDoc.setUser(davDoc.getSubject());

        // Check if already locked — eXist's lock() silently replaces existing locks
        final LockToken currentLock = existDoc.getCurrentLock();
        if (currentLock != null && currentLock.getOpaqueLockToken() != null) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Resource is already locked");
        }

        try {
            final LockToken resultToken = existDoc.lock(inputToken);
            return toActiveLock(resultToken, resource);
        } catch (final PermissionDeniedException e) {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Permission denied: " + e.getMessage());
        } catch (final DocumentAlreadyLockedException e) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Document is already locked: " + e.getMessage());
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create lock: " + e.getMessage());
        }
    }

    @Override
    public ActiveLock refreshLock(final LockInfo reqLockInfo, final String lockToken,
            final DavResource resource) throws DavException {

        if (!(resource instanceof ExistDavDocument davDoc)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Locking is only supported for document resources");
        }

        // Strip opaquelocktoken: prefix if present
        final String token = stripTokenPrefix(lockToken);

        final ExistDocument existDoc = new ExistDocument(
                davDoc.configuration, davDoc.getXmldbUri(), davDoc.getBrokerPool());
        existDoc.setUser(davDoc.getSubject());

        try {
            final LockToken resultToken = existDoc.refreshLock(token);
            return toActiveLock(resultToken, resource);
        } catch (final PermissionDeniedException e) {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Permission denied: " + e.getMessage());
        } catch (final DocumentAlreadyLockedException e) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Document is locked by another user: " + e.getMessage());
        } catch (final DocumentNotLockedException e) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Document is not locked: " + e.getMessage());
        } catch (final EXistException e) {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to refresh lock: " + e.getMessage());
        }
    }

    @Override
    public void releaseLock(final String lockToken, final DavResource resource)
            throws DavException {

        if (!(resource instanceof ExistDavDocument davDoc)) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Locking is only supported for document resources");
        }

        // Verify the provided token matches the current lock
        final String cleanToken = stripTokenPrefix(lockToken);

        final ExistDocument existDoc = new ExistDocument(
                davDoc.configuration, davDoc.getXmldbUri(), davDoc.getBrokerPool());
        existDoc.setUser(davDoc.getSubject());

        final LockToken currentLock = existDoc.getCurrentLock();
        if (currentLock == null || currentLock.getOpaqueLockToken() == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Document is not locked");
        }

        if (!currentLock.getOpaqueLockToken().equals(cleanToken)) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Lock token does not match");
        }

        try {
            existDoc.unlock();
        } catch (final PermissionDeniedException e) {
            throw new DavException(DavServletResponse.SC_FORBIDDEN,
                    "Permission denied: " + e.getMessage());
        } catch (final DocumentNotLockedException e) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED,
                    "Document is not locked: " + e.getMessage());
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

        if (!(resource instanceof ExistDavDocument davDoc)) {
            return null;
        }

        final ExistDocument existDoc = new ExistDocument(
                davDoc.configuration, davDoc.getXmldbUri(), davDoc.getBrokerPool());
        existDoc.setUser(davDoc.getSubject());

        final LockToken token = existDoc.getCurrentLock();
        if (token == null) {
            return null;
        }

        return toActiveLock(token, resource);
    }

    @Override
    public boolean hasLock(final String lockToken, final DavResource resource) {
        if (!(resource instanceof ExistDavDocument davDoc)) {
            return false;
        }

        final ExistDocument existDoc = new ExistDocument(
                davDoc.configuration, davDoc.getXmldbUri(), davDoc.getBrokerPool());
        existDoc.setUser(davDoc.getSubject());

        final LockToken token = existDoc.getCurrentLock();
        if (token == null || token.getOpaqueLockToken() == null) {
            return false;
        }

        final String cleanToken = stripTokenPrefix(lockToken);
        return token.getOpaqueLockToken().equals(cleanToken);
    }

    // -----------------------------------------------------------------------
    // Utility methods
    // -----------------------------------------------------------------------

    /**
     * Convert an eXist-db LockToken to a Jackrabbit ActiveLock.
     */
    private ActiveLock toActiveLock(final LockToken token, final DavResource resource) {
        final ExistActiveLock activeLock = new ExistActiveLock();
        activeLock.setOwner(token.getOwner());
        activeLock.setIsDeep(token.getDepth() == LockToken.LockDepth.INFINITY);
        activeLock.setLockroot(resource.getHref());

        // Set the lock token — critical for If-header matching and UNLOCK
        if (token.getOpaqueLockToken() != null) {
            activeLock.setToken("opaquelocktoken:" + token.getOpaqueLockToken());
        }

        if (token.getTimeOut() == LockToken.LOCK_TIMEOUT_INFINITE) {
            activeLock.setTimeout(Long.MAX_VALUE / 2);
        } else if (token.getTimeOut() > 0) {
            activeLock.setTimeout(token.getTimeOut());
        }

        return activeLock;
    }

    /**
     * Strip the "opaquelocktoken:" prefix from a lock token string
     * if present.
     */
    private String stripTokenPrefix(final String token) {
        if (token != null && token.startsWith("opaquelocktoken:")) {
            return token.substring("opaquelocktoken:".length());
        }
        return token;
    }
}
