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
package org.exist.storage.journal;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Sync the current journal file by calling {@link java.nio.channels.FileChannel#force(boolean)}.
 * This operation is quite expensive, so we delegate it to a background thread. The main
 * logging thread can continue to write into the log buffer and does not need to wait until
 * the force operation returns. 
 * 
 * However, we have to make sure that only one sync operation is running at a time. So if
 * the main logging thread triggers another sync while one is already in progress, it has to
 * wait until the sync operation has finished.
 * 
 * @author wolf
 *
 */
public class FileSyncThread extends Thread {

    private FileChannel endOfLog;
    
    private boolean syncTriggered = false;
    
    private boolean shutdown = false;
    
    private Object latch;
    
    /**
     * Create a new FileSyncThread, using the specified latch
     * to synchronize on.
     * 
     * @param latch
     */
    public FileSyncThread(Object latch) {
        super();
        this.latch = latch;
    }
    
    /**
     * Set the channel opened on the current journal file.
     * Called by {@link Journal} when it switches to
     * a new file.
     * 
     * @param channel
     */
    public void setChannel(FileChannel channel) {
        endOfLog = channel;
    }
    
    /**
     * Trigger a sync on the journal. If a sync is already in progress,
     * the method will just wait until the sync has completed.
     */
    public synchronized void triggerSync() {
        // trigger a sync
        syncTriggered = true;
        notifyAll();
    }
    
    /**
     * Shutdown the sync thread.
     */
    public synchronized void shutdown() {
        shutdown = true;
        interrupt();
    }
    
    /**
     * Close the underlying channel.
     */
    public void closeChannel() {
        synchronized (latch) {
            try {
                endOfLog.close();
            } catch (IOException e) {
            }
        }
    }
    
    /**
     * Wait for a sync event or shutdown.
     */
    public void run() {
        while (!shutdown) {
            synchronized (this) { 
                try {
                    wait();
                } catch (InterruptedException e) {
                }
                
                if (syncTriggered) {
                    sync();
                }
            }
        }
        // shutdown: sync the file and close it
        sync();
        closeChannel();
    }
    
    private void sync() {
        synchronized (latch) {
            try {
                endOfLog.force(false);
            } catch (IOException e) {
                // may occur during shutdown
            }
            syncTriggered = false;
        }
    }
}
