/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2017 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.management.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.lock.LockTable.LockModeOwner;
import org.exist.storage.lock.LockTableUtils;

import java.util.List;
import java.util.Map;

/**
 * JMX MXBean for examining the LockTable
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockTable implements LockTableMXBean {

    public static final String OBJECT_NAME = "org.exist.management:type=LockTable";

    @Override
    public Map<String, Map<LockType, Map<Lock.LockMode, Map<String, Integer>>>> getAcquired() {
        return org.exist.storage.lock.LockTable.getInstance().getAcquired();
    }

    @Override
    public Map<String, Map<LockType, List<LockModeOwner>>> getAttempting() {
        return org.exist.storage.lock.LockTable.getInstance().getAttempting();
    }

    @Override
    public void dumpToConsole() {
        System.out.println(LockTableUtils.stateToString(org.exist.storage.lock.LockTable.getInstance()));
    }

    private final static Logger LOCK_LOG = LogManager.getLogger(org.exist.storage.lock.LockTable.class);

    @Override
    public void dumpToLog() {
        LOCK_LOG.info(LockTableUtils.stateToString(org.exist.storage.lock.LockTable.getInstance()));
    }
}
