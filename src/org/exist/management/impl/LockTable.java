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

import java.util.List;
import java.util.Map;

/**
 * JMX MXBean for examining the LockTable
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockTable implements LockTableMXBean {

    private static final String EOL = System.getProperty("line.separator");

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
        System.out.println(stateToString());
    }

    private final static Logger LOCK_LOG = LogManager.getLogger(org.exist.storage.lock.LockTable.class);

    @Override
    public void dumpToLog() {
        LOCK_LOG.info(stateToString());
    }

    private String stateToString() {
        final Map<String, Map<LockType, List<LockModeOwner>>> attempting = getAttempting();
        final Map<String, Map<LockType, Map<Lock.LockMode, Map<String, Integer>>>> acquired = getAcquired();

        final StringBuilder builder = new StringBuilder();

        builder
                .append(EOL)
                .append("Acquired Locks").append(EOL)
                .append("------------------------------------").append(EOL);

        for(final Map.Entry<String, Map<LockType, Map<Lock.LockMode, Map<String, Integer>>>> acquire : acquired.entrySet()) {
            builder.append(acquire.getKey()).append(EOL);
            for(final Map.Entry<LockType, Map<Lock.LockMode, Map<String, Integer>>> type : acquire.getValue().entrySet()) {
                builder.append('\t').append(type.getKey()).append(EOL);
                for(final Map.Entry<Lock.LockMode, Map<String, Integer>> lockModeOwners : type.getValue().entrySet()) {
                    builder
                            .append("\t\t").append(lockModeOwners.getKey())
                            .append('\t');

                    boolean firstOwner = true;
                    for(final Map.Entry<String, Integer> ownerHoldCount : lockModeOwners.getValue().entrySet()) {
                        if(!firstOwner) {
                            builder.append(", ");
                        } else {
                            firstOwner = false;
                        }
                        builder.append(ownerHoldCount.getKey()).append(" (count=").append(ownerHoldCount.getValue()).append(")");
                    }
                    builder.append(EOL);
                }
            }
        }

        builder.append(EOL).append(EOL);

        builder
                .append("Attempting Locks").append(EOL)
                .append("------------------------------------").append(EOL);

        for(final Map.Entry<String, Map<LockType, List<LockModeOwner>>> attempt : attempting.entrySet()) {
            builder.append(attempt.getKey()).append(EOL);
            for(final Map.Entry<LockType, List<LockModeOwner>> type : attempt.getValue().entrySet()) {
                builder.append('\t').append(type.getKey()).append(EOL);
                for(final LockModeOwner lockModeOwner : type.getValue()) {
                    builder
                            .append("\t\t").append(lockModeOwner.getLockMode())
                            .append('\t').append(lockModeOwner.getOwnerThread())
                            .append(EOL);
                }
            }
        }

        return builder.toString();
    }
}
