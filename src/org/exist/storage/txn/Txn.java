/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
package org.exist.storage.txn;

import java.util.ArrayList;
import java.util.List;

import org.exist.storage.lock.Lock;
import org.exist.util.LockException;

/**
 * @author wolf
 *
 */
public class Txn {

    public enum State { STARTED, ABORTED, COMMITTED };

    private long id;

    private State state;

    private List<LockInfo> locksHeld = new ArrayList<LockInfo>();

    private List<TxnListener> listeners = new ArrayList<TxnListener>();
    
    private String originId;

    public Txn(long transactionId) {
        this.id = transactionId;
        this.state = State.STARTED;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getId() {
        return id;
    }
    
    public void registerLock(Lock lock, int lockMode) {
        locksHeld.add(new LockInfo(lock, lockMode));
    }
    
    public void acquireLock(Lock lock, int lockMode) throws LockException {
        lock.acquire(lockMode);
        locksHeld.add(new LockInfo(lock, lockMode));
    }
    
    public void releaseAll() {
        for (int i = locksHeld.size() - 1; i >= 0; i--) {
            final LockInfo info = locksHeld.get(i);
            info.lock.release(info.lockMode);
        }
        locksHeld.clear();
    }

    public void registerListener(TxnListener listener) {
        listeners.add(listener);
    }

    public void signalAbort() {
        state = State.ABORTED;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).abort();
        }
    }

    public void signalCommit() {
        state = State.COMMITTED;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).commit();
        }
    }

    private class LockInfo {
        Lock lock;
        int lockMode;
        
        public LockInfo(Lock lock, int lockMode) {
            this.lock = lock;
            this.lockMode = lockMode;
        }
    }
    
    /**
     * Get origin of transaction
     * @return Id
     */
    public String getOriginId() {
        return originId;
    }

    /**
     *  Set origin of transaction. Purpose is to be able to 
     * see the origin of the transaction.
     * 
     * @param id  Identifier of origin, FQN or URI.
     */
    public void setOriginId(String id) {
        originId = id;
    }
}