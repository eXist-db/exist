/*
 * Created on 5 sept. 2004
$Id$
 */
package org.exist.storage.report;

import java.util.Map;
import org.exist.storage.IndexStats;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.index.BFile;
import org.exist.util.Configuration;

/**
 * @author jmv
 */
public class Statistics {

	/** generate index statistics
	 * @param conf
	 * @param indexStats
	 */
	public static void generateIndexStatistics(Configuration conf, Map indexStats) {
		BFile db = (BFile) conf.getProperty("db-connection.elements");
		if(db != null) 
			indexStats.put("elements.dbx", new IndexStats(db));
		db = (BFile) conf.getProperty("db-connection.collections");
		if(db != null)
			indexStats.put("collections.dbx", new IndexStats(db));
		db = (BFile) conf.getProperty("db-connection.words");
		if(db != null)
			indexStats.put("words.dbx", new IndexStats(db));
		DOMFile dom = (DOMFile) conf.getProperty("db-connection.dom");
		if(dom != null)
			indexStats.put("dom.dbx", new IndexStats(dom));
	}

}
