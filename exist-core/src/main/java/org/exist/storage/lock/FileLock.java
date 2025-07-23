/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.util.FileUtils;
import org.exist.util.ReadOnlyException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.WRITE;

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
 * the lock file's heart-beat value.
 * 
 * @author Wolfgang Meier
 * 
 */
public class FileLock {

    private final static Logger LOG = LogManager.getLogger(FileLock.class);

    /** The heartbeat period in milliseconds */
    private final long HEARTBEAT = 10100;

    /** Magic word to be written to the start of the lock file */
    private final static byte[] MAGIC =
        { 0x65, 0x58, 0x69, 0x73, 0x74, 0x2D, 0x64, 0x62 }; // "eXist-db"

    /** BrokerPool provides access the SyncDaemon */
    private BrokerPool pool;

    /** The lock file */
    private Path lockFile;

    /** An open channel to the lock file */
    private SeekableByteChannel channel = null;

    /** Temporary buffer used for writing */
    private final ByteBuffer buf = ByteBuffer.allocate(MAGIC.length + 8);

    /** The time (in milliseconds) of the last heartbeat written to the lock file */
    private long lastHeartbeat = -1L;

    public FileLock(final BrokerPool pool, final Path path) {
        this.pool = pool;
        this.lockFile = path;
    }

    /**
     * Attempt to create the lock file and thus acquire a lock.
     * 
     * @return false if another process holds the lock
     * @throws ReadOnlyException if the lock file could not be created or saved
     * due to IO errors. The caller may want to switch to read-only mode.
     */
    public boolean tryLock() throws ReadOnlyException {
        int attempt = 0;
        while (Files.exists(lockFile)) {
            if (++attempt > 2) {
                return false;
            }
            
            try {
                read();
            } catch (final IOException e) {
                message("Failed to read lock file", null);
                e.printStackTrace();
            }
            
            //Check if there's a heart-beat. If not, remove the stale .lck file and try again
            if (checkHeartbeat()) {
                //There seems to be a heart-beat...
                //Sometimes Java does not properly delete files, so we may have an old
                //lock file from a previous db run, which has not timed out yet. We thus
                //give the db a second chance and wait for HEARTBEAT + 100 milliseconds
                //before we check the heart-beat a second time.
                synchronized (this) {
                    try {
                        message("Waiting a short time for the lock to be released...", null);
                        wait(HEARTBEAT + 100);
                    } catch (final InterruptedException e) {
                        //Nothing to do
                    }
                }
                
                try {
                    //Close the open channel, so it can be read again
                    if (channel.isOpen()) {
                        channel.close();
                    }
                    channel = null;
                } catch (final IOException e) {
                    //Nothing to do
                }
            }
        }



        try {
            this.lockFile = Files.createFile(lockFile);
        } catch (final IOException e) {
            throw new ReadOnlyException(message("Could not create lock file", e));
        }
        
        try {
            save();
        } catch (final IOException e) {
            throw new ReadOnlyException(message("Caught exception while trying to write lock file", e));
        }
        
        //Schedule the heart-beat for the file lock
        final Properties params = new Properties();
        params.put(FileLock.class.getName(), this);
        pool.getScheduler().createPeriodicJob(HEARTBEAT,
                new FileLockHeartBeat(lockFile.toAbsolutePath().toString()), -1, params);
        
        return true;
    }

    /**
     * Release the lock. Removes the lock file and closes all
     * open channels.
     */
    public void release() {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
            channel = null;
            
        } catch (final Exception e) {
            message("Failed to close lock file", e);
        }

        if(Files.exists(lockFile)) {
            LOG.info("Deleting lock file: {}", lockFile.toAbsolutePath().toString());
            FileUtils.deleteQuietly(lockFile);
        }
    }

    /**
     * Returns the last heartbeat written to the lock file.
     * 
     * @return last heartbeat
     */
    public Date getLastHeartbeat() {
        return new Date(lastHeartbeat);
    }

    /**
     * Returns the lock file that represents the active lock held by
     * the FileLock.
     * 
     * @return lock file
     */
    public Path getFile() {
        return lockFile;
    }

    /**
     * Check if the lock has an active heartbeat, i.e. if it was updated
     * during the past {@link #HEARTBEAT} milliseconds.
     * 
     * @return true if there's an active heartbeat
     */
    private boolean checkHeartbeat() {
        final long now = System.currentTimeMillis();
        if (lastHeartbeat < 0 || now - lastHeartbeat > HEARTBEAT) {
            message("Found a stale lockfile. Trying to remove it: ", null);
            release();
            return false;
        }
        
        return true;
    }

    private void open() throws IOException {
        this.channel = Files.newByteChannel(lockFile, READ, WRITE, SYNC);
    }

    protected void save() throws IOException {
        try {
            if (channel == null) {
                open();
            }
            
            long now = System.currentTimeMillis();
            ((java.nio.Buffer) buf).clear();
            buf.put(MAGIC);
            buf.putLong(now);
            ((java.nio.Buffer) buf).flip();
            channel.position(0);
            channel.write(buf);
            //channel.force(true); //handled by SYNC on open option
            lastHeartbeat = now;
            
        } catch(final NullPointerException npe) {
            if(pool.isShuttingDown()) {
                LOG.info("No need to save FileLock, database is shutting down");
            } else {
                throw npe;
            }
        }
    }

    private void read() throws IOException {
        if (channel == null) {
            open();
        }
        
        channel.read(buf);
        ((java.nio.Buffer) buf).flip();
        if (buf.limit() < 16) {
            ((java.nio.Buffer) buf).clear();
            throw new IOException(message("Could not read file lock.", null));
        }
        
        final byte[] magic = new byte[8];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException(message("Bad signature in lock file. It does not seem to be an eXist lock file", null));
        }
        
        lastHeartbeat = buf.getLong();
        ((java.nio.Buffer) buf).clear();
        
        final DateFormat df = DateFormat.getDateInstance();
        message("File lock last access timestamp: " + df.format(getLastHeartbeat()), null);
    }

    protected String message(String message, final Exception e) {
        final StringBuilder str = new StringBuilder(message);
        str.append(' ').append(lockFile.toAbsolutePath().toString());
        if (e != null) {
            str.append(": ").append(e.getMessage());
        }
        
        message = str.toString();
        if (LOG.isInfoEnabled()) {
            LOG.info(message);
        }

        return message;
    }
}