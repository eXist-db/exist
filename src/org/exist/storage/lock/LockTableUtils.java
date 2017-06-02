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
package org.exist.storage.lock;

import java.util.List;
import java.util.Map;

/**
 * Utilities for working with the Lock Table
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class LockTableUtils {

    private static final String EOL = System.getProperty("line.separator");

    public static String stateToString(final LockTable lockTable) {
        final Map<String, Map<Lock.LockType, List<LockTable.LockModeOwner>>> attempting = lockTable.getAttempting();
        final Map<String, Map<Lock.LockType, Map<Lock.LockMode, Map<String, Integer>>>> acquired = lockTable.getAcquired();

        final StringBuilder builder = new StringBuilder();

        builder
                .append(EOL)
                .append("Acquired Locks").append(EOL)
                .append("------------------------------------").append(EOL);

        for(final Map.Entry<String, Map<Lock.LockType, Map<Lock.LockMode, Map<String, Integer>>>> acquire : acquired.entrySet()) {
            builder.append(acquire.getKey()).append(EOL);
            for(final Map.Entry<Lock.LockType, Map<Lock.LockMode, Map<String, Integer>>> type : acquire.getValue().entrySet()) {
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

        for(final Map.Entry<String, Map<Lock.LockType, List<LockTable.LockModeOwner>>> attempt : attempting.entrySet()) {
            builder.append(attempt.getKey()).append(EOL);
            for(final Map.Entry<Lock.LockType, List<LockTable.LockModeOwner>> type : attempt.getValue().entrySet()) {
                builder.append('\t').append(type.getKey()).append(EOL);
                for(final LockTable.LockModeOwner lockModeOwner : type.getValue()) {
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
