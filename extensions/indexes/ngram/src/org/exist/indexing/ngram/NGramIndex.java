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
package org.exist.indexing.ngram;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BFile;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 */
public class NGramIndex extends AbstractIndex implements RawBackupSupport {

    public final static String ID = NGramIndex.class.getName();

    private final static Logger LOG = LogManager.getLogger(NGramIndex.class);

	protected BFile db;
    private int gramSize = 3;
    private Path dataFile = null;
    
    public NGramIndex() {
        //Nothing to do
    }

    @Override
    public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
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
        dataFile = dataDir.resolve(fileName);
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        try {
            db = new BFile(pool, (byte) 0, false, dataFile, pool.getCacheManager(), 1.4, 0.07);
        } catch (DBException e) {
            throw new DatabaseConfigurationException("Failed to create index file: " + dataFile.toAbsolutePath().toString() + ": " +
                e.getMessage());
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Created NGram index: " + dataFile.toAbsolutePath().toString());
    }

    @Override
    public void close() throws DBException {
        LOG.debug("SYNC NGRAM");
        db.close();
    }

    @Override
    public void sync() throws DBException {
        LOG.debug("SYNC NGRAM");
        db.flush();
    }

    @Override
    public void remove() throws DBException {
        db.closeAndRemove();
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return true;
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        //TODO : ensure singleton ? a pool ?
        return new NGramIndexWorker(broker, this);
    }

    public int getN() {
        return gramSize;
    }

    public BTree getStorage() {
        return db;
    }

    public void backupToArchive(RawDataBackup backup) throws IOException {
        OutputStream os = backup.newEntry(FileUtils.fileName(db.getFile()));
        db.backupToStream(os);
        backup.closeEntry();
    }
}
