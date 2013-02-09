/*
 * Created on 5 sept. 2004
$Id$
 */
package org.exist.storage.report;

import org.exist.storage.IndexStats;
import org.exist.storage.NativeTextEngine;
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

    /** generate index statistics
     * @param conf
     * @param indexStats
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
        db = (BFile) conf.getProperty(NativeTextEngine.FILE_KEY_IN_CONFIG);
        if(db != null)
            {indexStats.put(NativeTextEngine.FILE_NAME, new IndexStats(db));}
    }

}
