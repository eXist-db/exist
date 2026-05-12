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
 * Interface DiskUsageMXBean.
 *
 * @author dizzzz@exist-db.org
 */
public interface DiskUsageMXBean extends PerInstanceMBean {

    /**
     * No disk space could be determined.
     */
    long NO_VALUE = -1;

    /**
     * Directory is not defined.
     */
    String NOT_CONFIGURED = "NOT_CONFIGURED";

    /**
     * Get the absolute path of the data directory.
     *
     * @return the data directory path, or {@link #NOT_CONFIGURED} if not set
     */
    String getDataDirectory();

    /**
     * Get the usable space (in bytes) on the file store containing the data directory.
     *
     * @return usable space in bytes, or {@link #NO_VALUE} if unavailable
     */
    long getDataDirectoryUsableSpace();

    /**
     * Get the total space (in bytes) on the file store containing the data directory.
     *
     * @return total space in bytes, or {@link #NO_VALUE} if unavailable
     */
    long getDataDirectoryTotalSpace();

    /**
     * Get the space (in bytes) used by database data files in the data directory.
     *
     * @return used space in bytes, or {@link #NO_VALUE} if unavailable
     */
    long getDataDirectoryUsedSpace();

    /**
     * Get the absolute path of the journal directory.
     *
     * @return the journal directory path, or {@link #NOT_CONFIGURED} if not set
     */
    String getJournalDirectory();

    /**
     * Get the usable space (in bytes) on the file store containing the journal directory.
     *
     * @return usable space in bytes, or {@link #NO_VALUE} if unavailable
     */
    long getJournalDirectoryUsableSpace();

    /**
     * Get the total space (in bytes) on the file store containing the journal directory.
     *
     * @return total space in bytes, or {@link #NO_VALUE} if unavailable
     */
    long getJournalDirectoryTotalSpace();

    /**
     * Get the space (in bytes) used by journal files in the journal directory.
     *
     * @return used space in bytes, or {@link #NO_VALUE} if unavailable
     */
    long getJournalDirectoryUsedSpace();

    /**
     * Get the number of journal files present in the journal directory.
     *
     * @return journal file count, or {@link #NO_VALUE} if unavailable
     */
    long getJournalDirectoryNumberOfFiles();
}
