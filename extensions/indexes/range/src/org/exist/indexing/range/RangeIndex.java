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
 *
 *  $Id$
 */
package org.exist.indexing.range;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.storage.DBBroker;

/**
 * Main implementation class for the new range index. This extends the existing LuceneIndex.
 *
 * @author Wolfgang Meier
 */
public class RangeIndex extends LuceneIndex {

    protected static final Logger LOG = LogManager.getLogger(RangeIndex.class);

    public final static String ID = RangeIndex.class.getName();

    private static final String DIR_NAME = "range";

    private Analyzer defaultAnalyzer = new KeywordAnalyzer();

    @Override
    public String getDirName() {
        return DIR_NAME;
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        return new RangeIndexWorker(this, broker);
    }

    @Override
    public String getIndexId() {
        return ID;
    }

    public Analyzer getDefaultAnalyzer() {
        return defaultAnalyzer;
    }
}
