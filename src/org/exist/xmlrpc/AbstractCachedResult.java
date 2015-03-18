/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmlrpc;

/**
 * Simple abstract container for serialized resources or results of a query.
 * Used to cache them that may be retrieved by chunks later by the client.
 *
 * @author wolf
 * @author jmfernandez
 */
public abstract class AbstractCachedResult {

    protected long queryTime = 0;
    protected long creationTimestamp = 0;
    protected long timestamp = 0;

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
     * This abstract method must be used
     * to free internal variables.
     */
    public abstract void free();

    /**
     * This abstract method returns the cached result
     * or null
     *
     * @return The object which is being cached
     */
    public abstract Object getResult();

    @Override
    protected void finalize()
            throws Throwable {
        // Calling free to reclaim pinned resources
        free();
    }
}
