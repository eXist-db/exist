/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist Project
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

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Locked {
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReadLock readLock = lock.readLock();
    private final WriteLock writeLock = lock.writeLock();

    public final <R> R read(final Callable<R> readOp) {
        readLock.lock();
        try {
            return readOp.call();
        } catch (Exception e) {
            //can't be ignore
            return (R)e;
        } finally {
            readLock.unlock();
        }
    }

    public final <R> R write(final Callable<R> writeOp) {
        writeLock.lock();
        try {
            return writeOp.call();
        } catch (Exception e) {
            //can't be ignore
            return (R)e;
        } finally {
            writeLock.unlock();
        }
    }

    public final <R> R writeE(final Callable<R> writeOp) throws Exception {
        writeLock.lock();
        try {
            return writeOp.call();
        } finally {
            writeLock.unlock();
        }
    }
}
