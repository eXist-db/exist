/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.storage.txn;

import java.util.ArrayList;
import java.util.List;

import net.jcip.annotations.NotThreadSafe;
import org.exist.Transaction;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;

/**
 * @author wolf
 *
 */
@NotThreadSafe
public class Txn implements Transaction {

    public enum State { STARTED, ABORTED, COMMITTED, CLOSED }
    
    private final TransactionManager tm;
    private final long id;
    private State state;
    private String originId;

    private List<LockInfo> locksHeld = new ArrayList<>();
    private List<TxnListener> listeners = new ArrayList<>();

    public Txn(final TransactionManager tm, final long transactionId) {
        this.tm = tm;
        this.id = transactionId;
        this.state = State.STARTED;
    }

    public State getState() {
        return state;
    }

    protected void setState(final State state) {
        this.state = state;
    }

    public long getId() {
        return id;
    }

    /**
     * @deprecated Moving a Lock to a Txn is error prone and should not be done
     *   instead use {@link #acquireLock(Lock, LockMode)} to take a second lock
     */
    @Deprecated
    public void registerLock(final Lock lock, final LockMode lockMode) {
        locksHeld.add(new LockInfo(lock, lockMode));
    }

    public void acquireLock(final Lock lock, final LockMode lockMode) throws LockException {
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

    public void registerListener(final TxnListener listener) {
        listeners.add(listener);
    }

    protected void signalAbort() {
        state = State.ABORTED;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).abort();
        }
    }

    protected void signalCommit() {
        state = State.COMMITTED;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).commit();
        }
    }

    private static class LockInfo {
        final Lock lock;
        final LockMode lockMode;
        
        public LockInfo(final Lock lock, final LockMode lockMode) {
            this.lock = lock;
            this.lockMode = lockMode;
        }
    }
 
    @Override
    public void success() throws TransactionException {
        commit();
    }

    @Override
    public void commit() throws TransactionException {
        tm.commit(this);
    }

    @Override
    public void failure() {
        abort();
    }
 
    @Override
    public void abort() {
        tm.abort(this);
    }

    @Override
    public void close() {
        tm.close(this);
    }

    /**
     * Get origin of transaction
     * @return Id
     */
    @Deprecated
    public String getOriginId() {
        return originId;
    }

    /**
     *  Set origin of transaction. Purpose is to be able to
     * see the origin of the transaction.
     *
     * @param id  Identifier of origin, FQN or URI.
     */
    @Deprecated
    public void setOriginId(String id) {
        originId = id;
    }

}

