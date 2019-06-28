/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.management.impl;

import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockTable;
import org.exist.storage.lock.LockTable.LockCountTraces;
import org.exist.storage.lock.LockTable.LockModeOwner;

import java.util.List;
import java.util.Map;

/**
 * JMX MXBean interface for examining the LockTable
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface LockTableMXBean extends PerInstanceMBean {

    /**
     * Get information about acquired locks
     *
     * @return information about acquired locks
     */
    Map<String, Map<Lock.LockType, Map<Lock.LockMode, Map<String, LockCountTraces>>>> getAcquired();

    /**
     * Get information about outstanding attempts to acquire locks
     *
     * @return information about outstanding attempts to acquire locks
     */
    Map<String, Map<Lock.LockType, List<LockModeOwner>>> getAttempting();

    void dumpToConsole();

    void dumpToLog();

    void xmlDumpToConsole();

    void xmlDumpToLog();

    void fullDumpToConsole();

    void fullDumpToLog();

    void xmlFullDumpToConsole();

    void xmlFullDumpToLog();
}
