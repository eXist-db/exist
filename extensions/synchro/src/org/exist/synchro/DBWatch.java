/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.synchro;

import java.io.File;

import org.apache.log4j.Logger;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BFile;
import org.exist.storage.lock.Lock;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DBWatch extends AbstractIndex {

    protected static final Logger LOG = Logger.getLogger(DBWatch.class);

    public final static String ID = DBWatch.class.getName();
    public static final String FILE_NAME = "dbWatch.dbx";

    public static double cacheGrowth = 1.25;
    public static double keyThreshold = 0.01;
    public static double valueThreshold = 0.04;

    protected BFile btree;
    
    protected Synchro synchro;

    public DBWatch(Synchro synchro) {
    	this.synchro = synchro;
	}

	public Synchro getSynchro() {
		return synchro;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.AbstractIndex#open()
	 */
	@Override
	public void open() throws DatabaseConfigurationException {
        File file = new File(getDataDir(), FILE_NAME);
        LOG.debug("Creating '" + file.getName() + "'...");
        try {
            btree = new BFile((BrokerPool)synchro.getDatabase(), (byte)0, false, file, 
            		(DefaultCacheManager)synchro.getDatabase().getCacheManager(), 
            		cacheGrowth, keyThreshold, valueThreshold);
        } catch (DBException e) {
            LOG.error("Failed to initialize structural index: " + e.getMessage(), e);
            throw new DatabaseConfigurationException(e.getMessage(), e);
        }
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.AbstractIndex#close()
	 */
	@Override
	public void close() throws DBException {
        btree.close();
        btree = null;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.AbstractIndex#sync()
	 */
	@Override
	public void sync() throws DBException {
        if (btree == null)
            return;
        final Lock lock = btree.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            btree.flush();
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + btree.getFile().getName() + "'", e);
        } catch (DBException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.AbstractIndex#remove()
	 */
	@Override
	public void remove() throws DBException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.AbstractIndex#getWorker(org.exist.storage.DBBroker)
	 */
	@Override
	public IndexWorker getWorker(DBBroker broker) {
		return new DBWatchWorker(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.AbstractIndex#checkIndex(org.exist.storage.DBBroker)
	 */
	@Override
	public boolean checkIndex(DBBroker broker) {
		// TODO Auto-generated method stub
		return false;
	}
}
