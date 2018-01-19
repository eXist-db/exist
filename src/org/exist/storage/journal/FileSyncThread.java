/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
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

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Sync the current journal file by calling {@link java.nio.channels.FileChannel#force(boolean)}.
 * This operation is quite expensive, so we delegate it to a background thread. The main
 * logging thread can continue to write into the log buffer and does not need to wait until
 * the force operation returns.
 * <p>
 * However, we have to make sure that only one sync operation is running at a time. So if
 * the main logging thread triggers another sync while one is already in progress, it has to
 * wait until the sync operation has finished.
 *
 * @author wolf
 */
public class FileSyncThread extends Thread {

    // guarded by latch
    private FileChannel endOfLog;
    private final Object latch;

    // guarded by this
    private boolean syncTriggered = false;

    // used as termination flag, volatile semantics are sufficient
    private volatile boolean shutdown = false;

    /**
     * Create a new FileSyncThread, using the specified latch
     * to synchronize on.
     *
     * @param latch The object to synchronize on
     */
    public FileSyncThread(final Object latch) {
        super("exist-fileSyncThread");
        this.latch = latch;
    }

    /**
     * Set the channel opened on the current journal file.
     * Called by {@link Journal} when it switches to
     * a new file.
     *
     * @param channel The channel for the file which will be synchronized
     */
    public void setChannel(final FileChannel channel) {
        synchronized (latch) {
            endOfLog = channel;
        }
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
    public void shutdown() {
        shutdown = true;
        interrupt();
    }

    /**
     * Close the underlying channel.
     */
    public void closeChannel() {
        synchronized (latch) {
            if (endOfLog != null) {
                try {
                    endOfLog.close();
                } catch (final IOException e) {
                    // may occur during shutdown
                }
            }
        }
    }

    /**
     * Wait for a sync event or shutdown.
     */
    @Override
    public void run() {
        while (!shutdown) {
            synchronized (this) {
                try {
                    wait();
                } catch (final InterruptedException e) {
                    //Nothing to do
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
            //endOfLog may be null if setChannel wasn't called for some reason.
            if (endOfLog != null) {
                try {
                    endOfLog.force(false);
                } catch (final IOException e) {
                    // may occur during shutdown
                }
            }
            syncTriggered = false;
        }
    }
}
