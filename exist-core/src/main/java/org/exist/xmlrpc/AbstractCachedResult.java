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
package org.exist.xmlrpc;

import org.xmldb.api.base.XMLDBException;

import java.io.Closeable;

/**
 * Simple abstract container for serialized resources or results of a query.
 * Used to cache them that may be retrieved by chunks later by the client.
 *
 * @author wolf
 * @author jmfernandez
 */
public abstract class AbstractCachedResult implements Closeable {

    protected long queryTime = 0;
    protected long creationTimestamp = 0;
    protected long timestamp = 0;
    private boolean closed;

    public AbstractCachedResult() {
        this(0);
    }

    public AbstractCachedResult(final long queryTime) {
        this.queryTime = queryTime;
        touch();
        this.creationTimestamp = this.timestamp;
    }

    /**
     * @return Returns the queryTime.
     */
    public long getQueryTime() {
        return queryTime;
    }

    /**
     * @return Returns the timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * This method can be used to explicitly update the
     * last time the cached result has been used
     */
    public void touch() {
        timestamp = System.currentTimeMillis();
    }

    /**
     * @return Returns the timestamp.
     */
    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    /**
     * This abstract method returns the cached result
     * or null
     *
     * @return The object which is being cached
     */
    public abstract Object getResult();

    /**
     * Returns true if the Cached Result
     * has been closed.
     *
     * @return true if the cached result has been closed.
     */
    public final boolean isClosed() {
        return closed;
    }

    /**
     * Implement this in your sub-class if you need
     * to do cleanup.
     *
     * The method will only be called once, no matter
     * how many times the user calls {@link #close()}.
     */
    protected void doClose() {
        //no-op
    }

    @Override
    public final void close() {
        if(!isClosed()) {
            try {
                doClose();
            } finally {
                closed = true;
            }
        }
    }

    /**
     * This abstract method must be used
     * to free internal variables.
     *
     * @deprecated Call {@link #close()} instead.
     */
    @Deprecated
    public final void free() {
        close();
    }
}
