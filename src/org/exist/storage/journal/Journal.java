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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.txn.Checkpoint;
import org.exist.storage.txn.TransactionException;
import org.exist.util.Configuration;
import org.exist.util.sanity.SanityCheck;

/**
 * Manages the journalling log. The database uses one central journal for
 * all data files. If the journal exceeds the predefined maximum size, a new file is created.
 * Every journal file has a unique number, which keeps growing during the lifetime of the db.
 * The name of the file corresponds to the file number. The file with the highest
 * number will be used for recovery.
 * 
 * A buffer is used to temporarily buffer journal entries. To guarantee consistency, the buffer will be flushed
 * and the journal is synched after every commit or whenever a db page is written to disk.
 * 
 * Each entry has the structure:
 * 
 * <pre>[byte: entryType, long: transactionId, short length, byte[] data, short backLink]</pre>
 * 
 * <ul>
 *  <li>entryType is a unique id that identifies the log record. Entry types are registered via the 
 * {@link org.exist.storage.journal.LogEntryTypes} class.</li>
 *  <li>transactionId: the id of the transaction that created the record.</li>
 *  <li>length: the length of the log entry data.</li>
 *  <li>data: the payload data provided by the {@link org.exist.storage.journal.Loggable} object.</li>
 *  <li>backLink: offset to the start of the record. Used when scanning the log file backwards.</li>
 * </ul>
 * 
 * @author wolf
 */
public class Journal {
    /**
     * Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(Journal.class);

    public final static String LOG_FILE_SUFFIX = "log";
    public final static String BAK_FILE_SUFFIX = ".bak";
    
    /** the length of the header of each entry: entryType + transactionId + length */
    public final static int LOG_ENTRY_HEADER_LEN = 11;
	
	/** header length + trailing back link */
    public final static int LOG_ENTRY_BASE_LEN = LOG_ENTRY_HEADER_LEN + 2;
	
    /** default maximum journal size */
    public final static int DEFAULT_MAX_SIZE = 10 * 1024 * 1024;

    /** minimal size the journal needs to have to be replaced by a new file during a checkpoint */
	private static final long MIN_REPLACE = 1024 * 1024;
    
    /** 
     * size limit for the journal file. A checkpoint will be triggered if the file
     * exceeds this size limit.
     */
    private int journalSizeLimit = DEFAULT_MAX_SIZE;
    
    /** the current output channel */ 
    private FileChannel channel;
    
    /** Synching the journal is done by a background thread */
    private FileSyncThread syncThread;
    
    /** latch used to synchronize writes to the channel */
    private Object latch = new Object();
    
    /** the data directory where journal files are written to */
    private File dir;
    
    /** the current file number */
    private int currentFile = 0;
    
    /** used to keep track of the current position in the file */
    private int inFilePos = 0;
    
    /** temp buffer */
    private ByteBuffer currentBuffer;
    
    /** the last LSN written by the JournalManager */
    private long currentLsn = Lsn.LSN_INVALID;
    
    /** the last LSN actually written to the file */
    private long lastLsnWritten = Lsn.LSN_INVALID;
    
    /** stores the current LSN of the last file sync on the file */ 
    private long lastSyncLsn = Lsn.LSN_INVALID;
        
    /** set to true while recovery is in progress */
    private boolean inRecovery = false;
    
    /** the {@link BrokerPool} that created this manager */
    private BrokerPool pool;
    
    /** if set to true, a sync will be triggered on the log file after every commit */
    private boolean syncOnCommit = true;
    
    public Journal(BrokerPool pool, File directory) throws EXistException {
        this.dir = directory;
        this.pool = pool;
        // we use a 1 megabyte buffer:
        currentBuffer = ByteBuffer.allocateDirect(1024 * 1024);
        
        syncThread = new FileSyncThread(latch);
        syncThread.start();
        
        Boolean syncOpt = (Boolean) pool.getConfiguration().getProperty("db-connection.recovery.sync-on-commit");
        if (syncOpt != null) {
        	syncOnCommit = syncOpt.booleanValue();
        	if (LOG.isDebugEnabled())
        		LOG.debug("SyncOnCommit = " + syncOnCommit);
        }
                        
        String logDir = (String) pool.getConfiguration().getProperty("db-connection.recovery.journal-dir");
        if (logDir != null) {
            File f = Configuration.lookup(logDir);
            if (!f.exists()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Output directory for journal files does not exist. Creating " + f.getAbsolutePath());
                try {
                    f.mkdirs();
                } catch (SecurityException e) {
                    throw new EXistException("Failed to create output directory: " + f.getAbsolutePath());
                }
            }
            if (!(f.canWrite())) {
                throw new EXistException("Cannot write to journal output directory: " + f.getAbsolutePath());
            }
            this.dir = f;
            
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Using directory for the journal: " + dir.getAbsolutePath());
        
        Integer sizeOpt = (Integer) pool.getConfiguration().getProperty("db-connection.recovery.size-limit");
        if (sizeOpt != null)
        	journalSizeLimit = sizeOpt.intValue() * 1024 * 1024;
    }
    
    /**
     * Write a log entry to the journalling log.
     * 
     * @param loggable
     * @throws TransactionException
     */
    public synchronized void writeToLog(Loggable loggable) throws TransactionException {
        SanityCheck.ASSERT(!inRecovery, "Write to log during recovery. Should not happen!");
        final int size = loggable.getLogSize();
        final int required = size + LOG_ENTRY_BASE_LEN;
        if (required > currentBuffer.capacity() - currentBuffer.position())
            flushToLog(false);
        currentLsn = Lsn.create(currentFile, inFilePos + currentBuffer.position() + 1);
        currentBuffer.put(loggable.getLogType());
        currentBuffer.putLong(loggable.getTransactionId());
        currentBuffer.putShort((short) loggable.getLogSize());
        loggable.write(currentBuffer);
        currentBuffer.putShort((short) (size + LOG_ENTRY_HEADER_LEN));
        loggable.setLsn(currentLsn);
    }
    
    /**
     * Returns the last LSN physically written to the journal.
     * 
     * @return
     */
    public long lastWrittenLsn() {
        return lastLsnWritten;
    }
    
    /**
     * Flush the current buffer to disk. If fsync is true, a sync will
     * be called on the file to force all changes to disk.
     * 
     * @param fsync forces all changes to disk if true and syncMode is set to {@link #SYNC_ON_COMMIT}.
     * @throws TransactionException
     */
    public void flushToLog(boolean fsync) {
    	flushToLog(fsync, false);
    }
    
    /**
     * Flush the current buffer to disk. If fsync is true, a sync will
     * be called on the file to force all changes to disk.
     * 
     * @param fsync forces all changes to disk if true and syncMode is set to {@link #SYNC_ON_COMMIT}.
     * @param forceSync force changes to disk even if syncMode doesn't require it.
     * @throws TransactionException
     */
    public synchronized void flushToLog(boolean fsync, boolean forceSync) {
        if (inRecovery)
            return;
        flushBuffer();
        if (forceSync || (fsync && syncOnCommit && currentLsn > lastSyncLsn)) {
            syncThread.triggerSync();
            lastSyncLsn = currentLsn;
        }
        try {
            if (channel.size() >= journalSizeLimit)
                pool.triggerCheckpoint();
        } catch (IOException e) {
            LOG.warn("Failed to trigger checkpoint!", e);
        }
    }

    /**
     * 
     */
    private void flushBuffer() {
        synchronized (latch) {
            try {
                if (currentBuffer.position() > 0) {
                    currentBuffer.flip();
                    int size = currentBuffer.remaining();
                    while (currentBuffer.hasRemaining()) {
                        channel.write(currentBuffer);
                    }
                    currentBuffer.clear();
                    
                    inFilePos += size;
                    lastLsnWritten = currentLsn;
                }
            } catch (IOException e) {
                LOG.warn("Flushing log file failed!", e);
            }
        }
    }
    
    /**
     * Write a checkpoint record to the journal and flush it. If switchLogFiles is true,
     * a new journal will be started, but only if the file is larger than
     * {@link #MIN_REPLACE}. The old log is removed.
     * 
     * @param txnId
     * @param switchLogFiles
     * @throws TransactionException
     */
    public void checkpoint(long txnId, boolean switchLogFiles) throws TransactionException {
        LOG.debug("Checkpoint reached");
    	writeToLog(new Checkpoint(txnId));
        if (switchLogFiles)
            // if we switch files, we don't need to sync.
            // the file will be removed anyway.
            flushBuffer();
        else
            flushToLog(true, true);
        try {
			if (switchLogFiles && channel.position() > MIN_REPLACE) {
                File oldFile = getFile(currentFile);
                RemoveThread rt = new RemoveThread(channel, oldFile);
			    try {
			        switchFiles();
			    } catch (LogException e) {
			        LOG.warn("Failed to create new journal: " + e.getMessage(), e);
			    }
			    rt.start();
			}
		} catch (IOException e) {
			LOG.warn("IOException while writing checkpoint", e);
		}
    }
    
    /**
     * Set the file number of the last file used.
     * 
     * @param fileNum the log file number
     */
    public void setCurrentFileNum(int fileNum) {
        currentFile = fileNum;
    }
    
    /**
     * Create a new journal with a larger file number
     * than the previous file.
     * 
     * @throws LogException
     */
    public void switchFiles() throws LogException {
        ++currentFile;
        String fname = getFileName(currentFile);
        File file = new File(dir, fname);
        if (file.exists()) {
            if (LOG.isDebugEnabled())
                LOG.debug("Journal file " + file.getAbsolutePath() + " already exists. Copying it.");
            boolean renamed = file.renameTo(new File(file.getAbsolutePath() + BAK_FILE_SUFFIX));
            if (renamed && LOG.isDebugEnabled())
                LOG.debug("Old file renamed to " + file.getAbsolutePath());
            file = new File(dir, fname);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Creating new journal: " + file.getAbsolutePath());
        synchronized (latch) {
	        close();
	        try {
//				RandomAccessFile raf = new RandomAccessFile(file, "rw");
                FileOutputStream os = new FileOutputStream(file, true);
				channel = os.getChannel();
	            
				if (channel.tryLock() == null)
					throw new LogException("Failed to open journal file: " + file.getAbsolutePath() +
							". It is locked by another process.");
	            syncThread.setChannel(channel);
			} catch (FileNotFoundException e) {
				throw new LogException("Failed to open new journal: " + file.getAbsolutePath(), e);
			} catch (IOException e) {
				throw new LogException("Failed to open new journal: " + file.getAbsolutePath() +
						". It might be used by another process.", e);
			}
        }
        inFilePos = 0;
    }
    
    public void close() {
        if (channel != null) {
        	try {
        		channel.close();
        	} catch (IOException e) {
        		LOG.warn("Failed to close journal", e);
        	}
        }
    }
    
    /**
     * Find the journal file with the highest file number.
     * 
     * @param files
     * @return
     */
	public final static int findLastFile(File files[]) {
		File last = null;
		int max = -1;
		for (int i = 0; i < files.length; i++) {
			int p = files[i].getName().indexOf('.');
			String baseName = files[i].getName().substring(0, p);
			int num = Integer.parseInt(baseName, 16);
			if (num > max) {
				max = num;
				last = files[i];
			}
		}
		return max;
	}

    /**
     * Returns all journal files found in the data directory.
     * 
     * @return
     */
	public File[] getFiles() {
		File files[] = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(LOG_FILE_SUFFIX);
			}
		});
		return files;
	}
	
    /**
     * Returns the file corresponding to the specified
     * file number.
     * 
     * @param fileNum
     * @return
     */
	public File getFile(int fileNum) {
		return new File(dir, getFileName(fileNum));
	}
	
	/**
	 * Shut down the journal. This will write a checkpoint record
	 * to the log, so recovery manager knows the file has been
	 * closed in a clean way.
	 * 
	 * @param txnId
	 */
    public void shutdown(long txnId) {
    	if (!BrokerPool.FORCE_CORRUPTION) {
	    	try {
				writeToLog(new Checkpoint(txnId));
			} catch (TransactionException e) {
				LOG.error("An error occurred while closing the journal file: " + e.getMessage(), e);
			}
			flushBuffer();
    	}
        syncThread.shutdown();
        try {
			syncThread.join();
		} catch (InterruptedException e) {
		}
        currentBuffer = null;
    }
    
    /**
     * Called to signal that the db is currently in
     * recovery phase, so no output should be written.
     * 
     * @param value
     */
    public void setInRecovery(boolean value) {
        inRecovery = value;
    }
    
    /**
     * Translate a file number into a file name.
     * 
     * @param fileNum
     * @return
     */
    private static String getFileName(int fileNum) {
        String hex = Integer.toHexString(fileNum);
        hex = "0000000000".substring(hex.length()) + hex;
        return hex + '.' + LOG_FILE_SUFFIX;
    }
    
    private static class RemoveThread extends Thread {
        
        FileChannel channel;
        File file;
        
        RemoveThread(FileChannel channel, File file) {
            super("RemoveJournalThread");
            this.channel = channel;
            this.file = file;
        }
        
        public void run() {
            try {
                channel.close();
            } catch (IOException e) {
                LOG.warn("Exception while closing journal file: " + e.getMessage(), e);
            }
            file.delete();
        }
    }
}
