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
package org.exist.storage.report;

import org.exist.storage.IndexStats;
import org.exist.storage.NativeValueIndex;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.index.BFile;
import org.exist.storage.index.CollectionStore;
import org.exist.util.Configuration;

import java.util.Map;

/**
 * @author jmv
 */
public class Statistics {

    /**
     * Generate index statistics.
     *
     * @param conf the configuration
     * @param indexStats the index stats
     */
    public static void generateIndexStatistics(Configuration conf, Map<String, IndexStats> indexStats) {
        final DOMFile dom = (DOMFile) conf.getProperty(DOMFile.CONFIG_KEY_FOR_FILE);
        if(dom != null)
            {indexStats.put(DOMFile.FILE_NAME, new IndexStats(dom));}
        BFile db = (BFile) conf.getProperty(CollectionStore.FILE_KEY_IN_CONFIG);
        if(db != null)
            {indexStats.put(CollectionStore.FILE_NAME, new IndexStats(db));}
        db = (BFile) conf.getProperty(NativeValueIndex.FILE_KEY_IN_CONFIG);
        if(db != null) 
            {indexStats.put(NativeValueIndex.FILE_NAME, new IndexStats(db));}
    }

}
