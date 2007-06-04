package org.exist.xmldb;

import java.util.Map;
import java.util.TreeMap;

import org.exist.storage.BrokerPool;
import org.exist.storage.IndexStats;
import org.exist.storage.report.Statistics;
import org.exist.util.Configuration;

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
		//confPath = conf.getPath();
		dataDir = (String)conf.getProperty(BrokerPool.PROPERTY_DATA_DIR);
		
		// broker statistics
		runningBrokers = pool.active();
		availableBrokers = pool.available();
		maxBrokers = pool.getMax();
		
		Statistics.generateIndexStatistics(conf, indexStats);
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
