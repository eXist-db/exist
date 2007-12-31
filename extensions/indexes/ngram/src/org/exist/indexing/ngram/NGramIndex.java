/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.indexing.ngram;

import org.apache.log4j.Logger;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BFile;
import org.exist.util.DatabaseConfigurationException;
import org.exist.backup.RawDataBackup;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 */
public class NGramIndex extends AbstractIndex implements RawBackupSupport {
	
    public final static String ID = NGramIndex.class.getName();

    private final static Logger LOG = Logger.getLogger(NGramIndex.class);

	protected BFile db;
    private int gramSize = 3;
    private File dataFile = null;
    
    public NGramIndex() {
    }
    
    public void configure(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {
    	super.configure(pool, dataDir, config);
        String fileName = "ngram.dbx";
        if (config.hasAttribute("file"))
            fileName = config.getAttribute("file");
        if (config.hasAttribute("n"))
            try {
                gramSize = Integer.parseInt(config.getAttribute("n"));
            } catch (NumberFormatException e) {
                throw new DatabaseConfigurationException("Configuration parameter 'n' should be an integer.");
            }
        dataFile = new File(dataDir, fileName);
    }
    
    public void open() throws DatabaseConfigurationException {
        try {
            db = new BFile(pool, (byte) 0, false, dataFile, pool.getCacheManager(), 1.4, 0.01, 0.07);
        } catch (DBException e) {
            throw new DatabaseConfigurationException("Failed to create index file: " + dataFile.getAbsolutePath() + ": " +
                e.getMessage());
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Created NGram index: " + dataFile.getAbsolutePath());
    }

    public void close() throws DBException {
		LOG.debug("SYNC NGRAM");
        db.close();
    }

    public void sync() throws DBException {
		LOG.debug("SYNC NGRAM");
        db.flush();
    }

    public void remove() throws DBException {
        db.closeAndRemove();
    }
    
    public boolean checkIndex(DBBroker broker) {
        return true;
    }    

    public IndexWorker getWorker(DBBroker broker) {
    	//TODO : ensure singleton ? a pool ?    	
        return new NGramIndexWorker(this);
    }

    public int getN() {
        return gramSize;
    }

    public void backupToArchive(RawDataBackup backup) throws IOException {
        OutputStream os = backup.newEntry(db.getFile().getName());
        db.backupToStream(os);
        backup.closeEntry();
    }
}
