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

import org.apache.jackrabbit.webdav.lock.AbstractActiveLock;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;

/**
 * ActiveLock backed by an eXist-db lock token. Unlike {@code DefaultActiveLock},
 * this allows setting the token from the database rather than generating a new UUID.
 *
 * @author Joe Wicentowski
 */
class ExistActiveLock extends AbstractActiveLock {

    private String token;
    private String owner;
    private boolean deep;
    private long timeout;

    @Override
    public String getToken() {
        return token;
    }

    void setToken(final String token) {
        this.token = token;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    @Override
    public boolean isDeep() {
        return deep;
    }

    @Override
    public void setIsDeep(final boolean deep) {
        this.deep = deep;
    }

    @Override
    public Type getType() {
        return Type.WRITE;
    }

    @Override
    public Scope getScope() {
        return Scope.EXCLUSIVE;
    }

    @Override
    public boolean isLockedByToken(final String lockToken) {
        if (token == null || lockToken == null) {
            return false;
        }
        return token.equals(lockToken);
    }

    @Override
    public boolean isExpired() {
        return false;
    }
}
