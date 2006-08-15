/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
package org.exist.storage.lock;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Date;

import org.apache.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.util.ReadOnlyException;

/**
 * Cooperative inter-process file locking, used to synchronize access to database files across
 * processes, i.e. across different Java VMs or separate database instances within one
 * VM. This is similar to the native file locks provided by Java NIO. However, the NIO
 * implementation has various problems. Among other things, we observed that locks
 * were not properly released on WinXP.
 * 
 * FileLock implements a cooperative approach. The class attempts to write a lock file
 * at the specified location. Every lock file stores 1) a magic word to make sure that the
 * file was really written by eXist, 2) a heartbeat timestamp. The procedure for acquiring the
 * lock in {@link #tryLock()} is as follows:
 *  
 * If a lock file does already exist in the specified location, we check its heartbeat timestamp.
 * If the timestamp is more than {@link #HEARTBEAT} milliseconds in the past, we assume
 * that the lock is stale and its owner process has died. The lock file is removed and we create
 * a new one.
 * 
 * If the heartbeat indicates that the owner process is still alive, the lock
 * attempt is aborted and {@link #tryLock()} returns false. 
 * 
 * Otherwise, we create a new lock file and start a daemon thread to periodically update
 * the lock file's heartbeat value. 
 * 
 * @author Wolfgang Meier
 *
 */
public class FileLock {

    private final static Logger LOG = Logger.getLogger(FileLock.class);
     
    /** The heartbeat period in milliseconds */
    private final long HEARTBEAT = 10100; 
    
    /** Magic word to be written to the start of the lock file */
    private final static byte[] MAGIC =
        { 0x65, 0x58, 0x69, 0x73, 0x74, 0x2D, 0x64,0x62 }; // "eXist-db" 
    
    /** BrokerPool provides access the SyncDaemon */
    private BrokerPool pool;
    
    /** The lock file */
    private File lockFile;
    
    /** An open channel to the lock file */
    private FileChannel channel = null;

    /** Temp buffer used for writing */
    private final ByteBuffer buf = ByteBuffer.allocate(MAGIC.length + 8);
    
    /** The time (in milliseconds) of the last heartbeat written to the lock file */ 
    private long lastHeartbeat = 0L;
    
    public FileLock(BrokerPool pool, String path) {
        this.pool = pool;
        this.lockFile = new File(path);
    }
    
    public FileLock(BrokerPool pool, File parent, String lockName) {
        this.pool = pool;
        this.lockFile = new File(parent, lockName);
    }
    
    /**
     * Attempt to create the lock file and thus acquire a lock.
     * 
     * @return false if another process holds the lock
     * @throws ReadOnlyException if the lock file could not be created or saved
     * due to IO errors. The caller may want to switch to read-only mode.
     */
    public boolean tryLock() throws ReadOnlyException {
        if (lockFile.exists()) {
            try {
                read();
            } catch (IOException e) {
                message("Failed to read lock file", null);
            }
            if (checkHeartbeat())
                return false;
        }
        try {
            if (!lockFile.createNewFile())
                return false;
        } catch (IOException e) {
            throw new ReadOnlyException(message("Could not create lock file", e));
        }
        
        try {
            save();
        } catch (IOException e) {
            throw new ReadOnlyException(message("Caught exception while trying to write lock file", e));
        }
        pool.getSyncDaemon().executePeriodically(HEARTBEAT, new Runnable() {
            public void run() {
                try {
                    save();
                } catch (IOException e) {
                    message("Caught exception while trying to write lock file", e);
                }
            }
        }, true);
        return true;
    }
    
    /**
     * Release the lock. Removes the lock file and closes all 
     * open channels.
     */
    public void release() {
        try {
            if (channel.isOpen())
                channel.close();
            channel = null;
        } catch (IOException e) {
            message("Failed to close lock file", e);
        }
        lockFile.delete();
    }
    
    /**
     * Returns the last heartbeat written to the lock file.
     * 
     * @return
     */
    public Date getLastHeartbeat() {
        return new Date(lastHeartbeat);
    }
    
    /**
     * Returns the lock file that represents the active lock held by
     * the FileLock.
     * 
     * @return
     */
    public File getFile() {
        return lockFile;
    }
    
    private boolean checkHeartbeat() {
        long now = System.currentTimeMillis();
        if (now - lastHeartbeat > HEARTBEAT) {
            message("Found a stale lockfile. Trying to remove it: ", null);
            release();
            return false;
        }
        return true;
    }
    
    private void open() throws IOException {
        RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
        channel = raf.getChannel();
    }
    
    private void save() throws IOException {
        if (channel == null)
            open();
        long now = System.currentTimeMillis();
        buf.clear();
        buf.put(MAGIC);
        buf.putLong(now);
        buf.flip();
        channel.position(0);
        channel.write(buf);
        channel.force(true);
        lastHeartbeat = now;
    }
    
    private void read() throws IOException {
        if (channel == null)
            open();
        channel.read(buf);
        buf.flip();
        byte[] magic = new byte[8];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC))
            throw new IOException(message("Bad signature in lock file. It does not seem to be an eXist lock file", null));
        lastHeartbeat = buf.getLong();
        buf.clear();
    }
    
    private String message(String message, Exception e) {
        StringBuffer str = new StringBuffer(message);
        str.append(' ').append(lockFile.getAbsolutePath());
        if (e != null)
            str.append(": ").append(e.getMessage());
        message = str.toString();

        LOG.warn(message);
        System.err.println(message);
        return message;
    }
}