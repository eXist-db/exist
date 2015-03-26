/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
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
	private Map<String, IndexStats> indexStats = new TreeMap<String, IndexStats>();
	
	public DatabaseStatus(BrokerPool pool) {
		final Configuration conf = pool.getConfiguration();
		
		// get id for this instance
		id = pool.getId();
		
		// paths
		//confPath = conf.getPath();
		dataDir = (String)conf.getProperty(BrokerPool.PROPERTY_DATA_DIR);
		
		// broker statistics
		runningBrokers = pool.countActiveBrokers();
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
		return indexStats.get(dbName);
	}

}
