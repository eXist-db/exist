/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage.journal;

import java.lang.reflect.Constructor;

import org.exist.storage.DBBroker;
import org.exist.storage.txn.Checkpoint;
import org.exist.storage.txn.TxnAbort;
import org.exist.storage.txn.TxnCommit;
import org.exist.storage.txn.TxnStart;
import org.exist.util.hashtable.Int2ObjectHashMap;

/**
 * Registry for log entry types. All classes that can be read from or written to the journal
 * have to be registered here. The recovery manager uses this information to create
 * the correct {@link org.exist.storage.journal.Loggable} object when reading the log.
 * 
 * @author wolf
 */
public class LogEntryTypes {

    /**
     * Used to register a class for a given log entry type.
     */
    private static class LogEntry {
        
        private static Class constructorArgs[] = { DBBroker.class, long.class };
        
        private byte type;
        private Class clazz;
        
        public LogEntry(byte type, Class myClass) {
            this.type = type;
            this.clazz = myClass;
        }
        
        public Loggable newInstance(DBBroker broker, long transactId) throws Exception {
            Constructor constructor = clazz.getConstructor(constructorArgs);
            return (Loggable)
                constructor.newInstance(new Object[] { broker, new Long(transactId) });
        }
    }
    
	public final static byte TXN_START = 0;
	public final static byte TXN_COMMIT = 1;
	public final static byte CHECKPOINT = 2;
	public final static byte TXN_ABORT = 3;
	
    private final static Int2ObjectHashMap entryTypes = new Int2ObjectHashMap();
    
    // register the common entry types
    static {
        addEntryType(TXN_START, TxnStart.class);
        addEntryType(TXN_COMMIT, TxnCommit.class);
        addEntryType(CHECKPOINT, Checkpoint.class);
        addEntryType(TXN_ABORT, TxnAbort.class);
    }
    
    /**
     * Add an entry type to the registry.
     * 
     * @param type
     * @param clazz the class implementing {@link Loggable}.
     */
    public final static void addEntryType(byte type, Class clazz) {
        LogEntry entry = new LogEntry(type, clazz);
        entryTypes.put(type, entry);
    }
    
    /**
     * Create a new loggable object for the given type.
     * 
     * @param type
     * @param transactId the id of the current transaction.
     * @throws LogException
     */
	public final static Loggable create(byte type, DBBroker broker, long transactId) throws LogException {
		LogEntry entry = (LogEntry) entryTypes.get(type);
        if (entry == null)
            return null;
        try {
            return entry.newInstance(broker, transactId);
        } catch (Exception e) {
            throw new LogException("Failed to create log entry object", e);
        }
	}

}
