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
package org.exist.management.impl;

/**
 * JMX MXBean interface for the dense vector persistence layer ({@code vector.dbx}).
 */
public interface VectorStoreMXBean extends PerInstanceMBean {

    /**
     * No file size could be determined.
     */
    long NO_FILE_SIZE = -1;

    /**
     * Entry count could not be determined (store unavailable or scan failed).
     */
    long ENTRY_COUNT_UNKNOWN = -1;

    /**
     * Whether the vector store is available for this database instance.
     *
     * @return {@code true} when {@code vector.dbx} is open
     */
    boolean isAvailable();

    /**
     * Persistence backend represented by this MBean.
     *
     * @return {@code vector.dbx}
     */
    String getPersistenceBackend();

    /**
     * Whether {@link #getEntryCount()} is known for this instance.
     *
     * @return {@code false} when the store is unavailable or counting failed
     */
    boolean isEntryCountKnown();

    /**
     * The vector store file name (always {@code vector.dbx}).
     *
     * @return file name
     */
    String getFileName();

    /**
     * Size of {@code vector.dbx} on disk in bytes.
     *
     * @return file size, or {@link #NO_FILE_SIZE} when missing or unreadable
     */
    long getFileSize();

    /**
     * Number of vector entries in the store.
     * <p>
     * Maintained incrementally on {@code put}/{@code remove}; the first read may
     * scan the BTree if the counter has not yet been initialized.
     *
     * @return entry count, or {@link #ENTRY_COUNT_UNKNOWN} when unavailable
     */
    long getEntryCount();

    /**
     * Format version of {@code vector.dbx}.
     *
     * @return format version id
     */
    short getFormatVersion();

    /**
     * Force a refresh of the cached entry count on the next {@link #getEntryCount()} call.
     */
    void resetEntryCountCache();
}
