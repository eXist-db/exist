/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Original code is
 * 
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 * $Id$
 */
package org.exist.storage.lock;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.util.LockException;

/**
 * A reentrant read/write lock, which allows multiple readers to acquire a lock.
 * Waiting writers are preferred.
 * 
 * This is an adapted and bug-fixed version of code taken from Apache's Turbine
 * JCS.
 *  
 */
public class MultiReadReentrantLock implements Lock {

	private final static Logger LOG = Logger.getLogger(MultiReadReentrantLock.class);

    /** Number of threads waiting to read. */
    private int waitingForReadLock = 0;

    /** Number of threads reading. */
    private int outstandingReadLocks = 0;

    /** The thread that has the write lock or null. */
    private Thread writeLockedThread;

    /**
     * The number of (nested) write locks that have been requested from
     * writeLockedThread.
     */
    private int outstandingWriteLocks = 0;

    /**
     * Threads waiting to get a write lock are tracked in this ArrayList to
     * ensure that write locks are issued in the same order they are requested.
     */
    private List waitingForWriteLock = null;

    /** Default constructor. */
    public MultiReadReentrantLock() {
    }

    /* @deprecated Use other method
     * @see org.exist.storage.lock.Lock#acquire()
     */
    public boolean acquire() throws LockException {
        return acquire(Lock.READ_LOCK);
    }

    public boolean acquire(int mode) throws LockException {
        switch (mode) {
        case Lock.WRITE_LOCK:
            return writeLock();
        default:
            return readLock();
        }
    }

    /* (non-Javadoc)
	 * @see org.exist.util.Lock#attempt(int)
	 */
	public boolean attempt(int mode) {
		throw new RuntimeException("Not implemented");
	}
	
    /**
     * Issue a read lock if there is no outstanding write lock or threads
     * waiting to get a write lock. Caller of this method must be careful to
     * avoid synchronizing the calling code so as to avoid deadlock.
     */
    private synchronized boolean readLock() throws LockException {
        if (writeLockedThread == Thread.currentThread()) {
            outstandingReadLocks++;
            return true;
        } 
        waitingForReadLock++;
        while (writeLockedThread != null) {
            //            log.debug( "readLock wait" );
            try {
                wait(100);
            } catch (InterruptedException e) {
                throw new LockException("Interrupted while waiting for read lock");
            }
            //            log.debug( "wake up from readLock wait" );
        }

        //        log.debug( "readLock acquired" );

        waitingForReadLock--;
        outstandingReadLocks++;
        return true;
    }

    /**
     * Issue a write lock if there are no outstanding read or write locks.
     * Caller of this method must be careful to avoid synchronizing the calling
     * code so as to avoid deadlock.
     */
    private boolean writeLock() throws LockException {
        Thread thisThread = Thread.currentThread();
        synchronized (this) {
            if (writeLockedThread == null && outstandingReadLocks == 0) {
                writeLockedThread = Thread.currentThread();
                outstandingWriteLocks++;

//                log.debug( "writeLock acquired without waiting by " + writeLockedThread.getName());

                return true;
            }
            //            if ( writeLockedThread == thisThread )
            //            {
            //                log.debug("nested write lock: " + outstandingWriteLocks);
            //            }
            if(waitingForWriteLock == null)
                waitingForWriteLock = new ArrayList(3);
            waitingForWriteLock.add(thisThread);
        }
        synchronized (thisThread) {
            while (thisThread != writeLockedThread) {
                //                log.debug( "writeLock wait: outstanding: " +
                // outstandingWriteLocks + " / " + outstandingReadLocks);
                try {
                    // set this so if there is an error the app will not
                    // completely die!
                    thisThread.wait();
                } catch (InterruptedException e) {
                    throw new LockException("Interrupted");
                }
                //                log.debug( "wake up from writeLock wait" );
            }
            outstandingWriteLocks++; //testing
//            log.debug( "writeLock acquired " + writeLockedThread.getName());
        }
        synchronized (this) {
            int i = waitingForWriteLock.indexOf(thisThread);
            waitingForWriteLock.remove(i);
        }
        return true;
    }

    /* @deprecated : use other method
     * @see org.exist.storage.lock.Lock#release()
     */
    public void release() {
        release(Lock.READ_LOCK);
    }

    public void release(int mode) {
        switch (mode) {
        case Lock.WRITE_LOCK:
            releaseWrite();
            break;
        default:
            releaseRead();
            break;
        }
    }

    private synchronized void releaseWrite() {
        if (Thread.currentThread() == writeLockedThread) {
            //log.info( "outstandingWriteLocks= " + outstandingWriteLocks );
            if (outstandingWriteLocks > 0)
                outstandingWriteLocks--;
//            else {
//                log.info("extra lock release, writelocks are "
//                        + outstandingWriteLocks + "and done was called");
//            }

            if (outstandingWriteLocks > 0) {
//                log.debug( "writeLock released for a nested writeLock request: " + outstandingWriteLocks +
//                        "; thread: " + writeLockedThread.getName());
                return;
            }

            // could pull out of sub if block to get nested tracking working.
            if (outstandingReadLocks == 0 && waitingForWriteLock != null && waitingForWriteLock.size() > 0) {
                writeLockedThread = (Thread) waitingForWriteLock.get(0);
                //                if ( log.isDebugEnabled() )
                //                {
                //                    log.debug( "writeLock released and before notifying a write
                // lock waiting thread "
                //                         + writeLockedThread );
                //                }
                synchronized (writeLockedThread) {
                    writeLockedThread.notify();
                }
                //                if ( log.isDebugEnabled() )
                //                {
                //                    log.debug( "writeLock released and after notifying a write
                // lock waiting thread "
                //                         + writeLockedThread );
                //                }
            } else {
                writeLockedThread = null;
                if (waitingForReadLock > 0) {
                    //                    log.debug( "writeLock released, notified waiting readers"
                    // );

                    notifyAll();
                } else {
                    //                    log.debug( "writeLock released, no readers waiting" );
                }
            }
        } else {
            LOG.warn("Possible lock problem: a thread released a write lock it didn't hold. Either the " +
                    "thread was interrupted or it never acquired the lock.");
        	Thread.dumpStack();
        }
//        log.debug("writeLock released: " + outstandingWriteLocks +
//                "; thread: " + Thread.currentThread().getName());
    }

    /**
     * Threads call this method to relinquish a lock that they previously got
     * from this object.
     * 
     * @throws IllegalStateException
     *                   if called when there are no outstanding locks or there is a
     *                   write lock issued to a different thread.
     */
    private synchronized void releaseRead() {
        if (outstandingReadLocks > 0) {
        	outstandingReadLocks--;
            if (outstandingReadLocks == 0 && writeLockedThread == null &&
                    waitingForWriteLock != null && waitingForWriteLock.size() > 0) {
                writeLockedThread = (Thread) waitingForWriteLock.get(0);
                //                if ( log.isDebugEnabled() )
                //                {
                //                    log.debug( "readLock released and before notifying a write
                // lock waiting thread "
                //                         + writeLockedThread );
                //                }
                synchronized (writeLockedThread) {
                    writeLockedThread.notifyAll();
                }
                //                if ( log.isDebugEnabled() )
                //                {
                //                    log.debug( "readLock released and after notifying a write
                // lock waiting thread "
                //                         + writeLockedThread );
                //                }
            }
            return;
        } else {
            LOG.warn("Possible lock problem: a thread released a read lock it didn't hold. Either the " +
                    "thread was interrupted or it never acquired the lock.");
        	Thread.dumpStack();
        }
    }

    public synchronized boolean isLockedForWrite() {
        return writeLockedThread != null || (waitingForWriteLock != null && waitingForWriteLock.size() > 0);
    }

    public synchronized boolean hasLock() {
        return outstandingReadLocks > 0 || isLockedForWrite();
    }
}

