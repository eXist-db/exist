package org.exist.xmldb;

import org.exist.storage.BrokerPool;
import org.exist.storage.IndexStats;
import org.exist.storage.store.BFile;
import org.exist.storage.store.DOMFile;
import org.exist.util.Configuration;

import java.util.Map;
import java.util.TreeMap;

public class DatabaseStatus {

	private String id;
	private String confPath;
	private String dataDir;
	private int runningBrokers = 0;
	private int availableBrokers = 0;
	private int maxBrokers = 0;
	private Map indexStats = new TreeMap();
	
	public DatabaseStatus(BrokerPool pool) {
		Configuration conf = pool.getConfiguration();
		
		// get id for this instance
		id = pool.getId();
		
		// paths
		confPath = conf.getPath();
		dataDir = (String)conf.getProperty("db-connection.data-dir");
		
		// broker statistics
		runningBrokers = pool.active();
		availableBrokers = pool.available();
		maxBrokers = pool.getMax();
		
		// generate index statistics
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
	
	/**
	 * Number of brokers for this instance, which are
	 * currently active (i.e. actually processing requests).
	 * 
	 * @return int
	 */
	public int getAvailableBrokers() {
		return availableBrokers;
	}

	/**
	 * Path to the configuration file used to create
	 * this database instance.
	 *  
	 * @return String
	 */
	public String getConfPath() {
		return confPath;
	}

	/**
	 * Path to the data directory.
	 * 
	 * @return String
	 */
	public String getDataDir() {
		return dataDir;
	}

	/**
	 * The identifier for this instance.
	 * 
	 * Used to distinguish between multiple database 
	 * instances.
	 * 
	 * @return String
	 */
	public String getId() {
		return id;
	}

	/**
	 * The maximum number of brokers allowed for this instance.
	 * 
	 * @return int
	 */
	public int getMaxBrokers() {
		return maxBrokers;
	}

	/**
	 * Number of brokers currently running.
	 * 
	 * @return int
	 */
	public int getRunningBrokers() {
		return runningBrokers;
	}
	
	public IndexStats getIndexStats(String dbName) {
		return (IndexStats)indexStats.get(dbName);
	}

}
