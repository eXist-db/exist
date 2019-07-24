/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 *
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
package org.exist.storage.journal;

import java.util.function.BiFunction;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Checkpoint;
import org.exist.storage.txn.TxnAbort;
import org.exist.storage.txn.TxnCommit;
import org.exist.storage.txn.TxnStart;

/**
 * Registry for log entry types. All classes that can be read from or written to the journal
 * have to be registered here. The recovery manager uses this information to create
 * the correct {@link org.exist.storage.journal.Loggable} object when reading the log.
 *
 * @author wolf
 */
public class LogEntryTypes {

    public final static byte TXN_START = 0;
    public final static byte TXN_COMMIT = 1;
    public final static byte CHECKPOINT = 2;
    public final static byte TXN_ABORT = 3;

    private final static Int2ObjectMap<BiFunction<DBBroker, Long, Loggable>> entryTypes = new Int2ObjectOpenHashMap<>();

    static {
        // register the common entry types
        entryTypes.put(TXN_START, TxnStart::new);
        entryTypes.put(TXN_COMMIT, TxnCommit::new);
        entryTypes.put(CHECKPOINT, Checkpoint::new);
        entryTypes.put(TXN_ABORT, TxnAbort::new);
    }

    /**
     * Add an entry type to the registry.
     *
     * @param type The type of the Loggable
     * @param cstr Function for constructing a Loggable of the indicated type
     */
    public final static void addEntryType(final byte type, final BiFunction<DBBroker, Long, Loggable> cstr) {
        entryTypes.put(type, cstr);
    }

    /**
     * Create a new loggable for the given type.
     *
     * @param broker The broker that will perform the operation
     * @param type The type of the loggable
     * @param broker the database broker
     * @param transactionId the id of the current transaction
     *
     * @return The loggable for the type, or null if no loggable for the type is known
     *
     * @throws LogException if the entry could not be created
     */
    public final static Loggable create(final byte type, final DBBroker broker, final long transactionId) throws LogException {
        final BiFunction<DBBroker, Long, Loggable> cstr = entryTypes.get(type);
        if (cstr == null) {
            return null;
        } else {
            return cstr.apply(broker, transactionId);
        }
    }
}
