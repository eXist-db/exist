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
package org.exist.indexing.impl;

import org.apache.log4j.Logger;
import org.exist.indexing.Index;
import org.exist.indexing.IndexWorker;
import org.exist.storage.BrokerPool;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BFile;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.io.File;

/**
 */
public class NGramIndex implements Index {

    private final static Logger LOG = Logger.getLogger(NGramIndex.class);

    private BFile db;
    private int gramSize = 3;

    public void open(BrokerPool pool, String dataDir, Element config) throws DatabaseConfigurationException {
        String fileName = "ngram.dbx";
        if (config.hasAttribute("file"))
            fileName = config.getAttribute("file");
        if (config.hasAttribute("n"))
            try {
                gramSize = Integer.parseInt(config.getAttribute("n"));
            } catch (NumberFormatException e) {
                throw new DatabaseConfigurationException("Configuration parameter 'n' should be an integer.");
            }
        File file = new File(dataDir, fileName);
        try {
            db = new BFile(pool, (byte) 0, false, file, pool.getCacheManager(), 0.1, 0.1, 0.1);
        } catch (DBException e) {
            throw new DatabaseConfigurationException("Failed to create index file: " + file.getAbsolutePath() + ": " +
                e.getMessage());
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Created NGram index: " + file.getAbsolutePath());
    }

    public void close() throws DBException {
        db.close();
    }

    public void sync() throws DBException {
        db.flush();
    }

    public IndexWorker getWorker() {
        return new NGramIndexWorker(this);
    }

    public int getN() {
        return gramSize;
    }
}
