/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id:
 */
package org.exist.storage;

import java.util.Stack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.exist.util.*;
import org.exist.*;

/**
 *  This class controls all available instances of the database.
 * Use it to configure, start and stop database instances. You may
 * have multiple instances defined, each using its own configuration,
 * database directory etc.. To define multiple instances, pass an
 * identification string to the static method configure() and use
 * getInstance(id) to retrieve an instance.
 * 
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    9. Mai 2002
 */
public class BrokerPool {

	private static Logger LOG = Logger.getLogger(BrokerPool.class);
	private static TreeMap instances = new TreeMap();
	private static long timeOut = 30000L;

	public final static String DEFAULT_INSTANCE = "exist";

	public final static void configure(
		int minBrokers,
		int maxBrokers,
		Configuration config)
		throws EXistException {
		configure(DEFAULT_INSTANCE, minBrokers, maxBrokers, config);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  minBrokers          Description of the Parameter
	 *@param  maxBrokers          Description of the Parameter
	 *@param  config              Description of the Parameter
	 *@exception  EXistException  Description of the Exception
	 */
	public final static void configure(
		String id,
		int minBrokers,
		int maxBrokers,
		Configuration config)
		throws EXistException {
		BrokerPool instance = (BrokerPool) instances.get(id);
		if (instance == null) {
			LOG.debug("configuring database instance '" + id + "' ...");
			instance = new BrokerPool(id, minBrokers, maxBrokers, config);
			instances.put(id, instance);
		} else
			LOG.warn("instance with id " + id + " already configured");
		
	}

	public final static boolean isConfigured(String id) {
		BrokerPool instance = (BrokerPool) instances.get(id);
		if(instance == null)
			return false;
		return instance.isInstanceConfigured();
	}
	
	public final static boolean isConfigured() {
		return isConfigured(DEFAULT_INSTANCE);
	}
	
	/**
	 *  Singleton method. Get the BrokerPool.
	 *
	 *@return                     The instance.
	 *@exception  EXistException  Description of the Exception
	 */
	public final static BrokerPool getInstance(String id)
		throws EXistException {
		BrokerPool instance = (BrokerPool) instances.get(id);
		if (instance != null)
			return instance;
		else
			throw new EXistException(
				"instance with id " + id + " has not been configured yet");
	}

	public final static BrokerPool getInstance() throws EXistException {
		return getInstance(DEFAULT_INSTANCE);
	}

	/**
	 *  Shutdown running brokers. After calling this method, the BrokerPool is
	 *  no longer configured. You have to configure it again by calling
	 *  configure().
	 */
	public final static void stop(String id) throws EXistException {
		BrokerPool instance = (BrokerPool)instances.get(id);
		if (instance != null) {
			instance.shutdown();
			instances.remove(id);
		} else
			throw new EXistException("instance with id " +
				" is not available");
	}

	public final static void stop() throws EXistException {
		stop(DEFAULT_INSTANCE);
	}
	
	public final static void stopAll() {
		for(Iterator i = instances.values().iterator(); i.hasNext(); )
			((BrokerPool)i.next()).shutdown();
		instances.clear();
	}
	
	private int max = 15;
	private int min = 1;
	private Configuration conf = null;
	protected ArrayList active = new ArrayList();
	protected int brokers = 0;
	protected Stack pool = new Stack();
	private org.exist.security.SecurityManager secManager = null;
	private long lastRequest = System.currentTimeMillis();
	private long idleTime = 900000L;
	private String instanceId;

	/**
	 *  Constructor for the BrokerPool object
	 *
	 *@exception  EXistException  Description of the Exception
	 */
	public BrokerPool(String id, int minBrokers, int maxBrokers, Configuration config)
		throws EXistException {
		instanceId = id;
		min = minBrokers;
		max = maxBrokers;
		Integer minInt = (Integer)config.getProperty("db-connection.pool.min");
		Integer maxInt = (Integer)config.getProperty("db-connection.pool.max");
		Integer idleInt = (Integer)config.getProperty("db-connection.pool.idle");
		if(minInt != null)
			min = minInt.intValue();
		if(maxInt != null)
			max = maxInt.intValue();
		if(idleInt != null)
			idleTime = idleInt.intValue() * 1000;
		LOG.debug("min = " + min + "; max = "+ max + "; idle = " + idleTime);
		conf = config;
		initialize();
	}

	/**
	 *  Number of active Brokers in this pool.
	 *
	 *@return    Description of the Return Value
	 */
	public int active() {
		return active.size();
	}

	/**
	 *  Number of available Brokers in this pool.
	 *
	 *@return    Description of the Return Value
	 */
	public int available() {
		return pool.size();
	}

	public Configuration getConfiguration() {
		return conf;
	}
	
	/**
	 *  Description of the Method
	 *
	 *@return                     Description of the Return Value
	 *@exception  EXistException  Description of the Exception
	 */
	protected DBBroker createBroker() throws EXistException {
		DBBroker broker = BrokerFactory.getInstance(this, conf);
		LOG.debug("creating new instance of " + broker.getClass().getName());
		pool.push(broker);
		active.add(broker);
		brokers++;
		return broker;
	}

	/**
	 *  Get a DBBroker instance from the pool.
	 *
	 *@return                     Description of the Return Value
	 *@exception  EXistException  Description of the Exception
	 */
	public synchronized DBBroker get() throws EXistException {
		if(!isInstanceConfigured())
			throw new EXistException("database instance is not available");
		if (pool.isEmpty()) {
			if (brokers < max)
				return createBroker();
			else
				while (pool.isEmpty()) {
					LOG.debug(
						"waiting for broker instance to become available");
					try {
						this.wait();
					} catch (InterruptedException e) {
					}
				}
		}
		DBBroker broker = (DBBroker) pool.pop();
		this.notifyAll();
		return broker;
	}

	/**
	 *  Returns the security manager responsible for this pool
	 *
	 *@return    The securityManager value
	 */
	public org.exist.security.SecurityManager getSecurityManager() {
		return secManager;
	}

	public void reloadSecurityManager(DBBroker broker) {
		LOG.debug("reloading security manager");
		secManager = new org.exist.security.SecurityManager(this, broker);
	}
	
	/**
	 *  Description of the Method
	 *
	 *@exception  EXistException  Description of the Exception
	 */
	protected void initialize() throws EXistException {
		for (int i = 0; i < min; i++)
			createBroker();
		DBBroker broker = (DBBroker) pool.peek();
		secManager = new org.exist.security.SecurityManager(this, broker);
		LOG.debug("database engine initialized.");
	}

	/**
	 *  Release a DBBroker instance into the pool.
	 *	If all active instances are in the pool (i.e.
	 * 	the database is currently not used), release
	 *  will call sync() to flush unwritten buffers 
	 *  to the disk. 
	 * 
	 *@param  broker  Description of the Parameter
	 */
	public synchronized void release(DBBroker broker) {
		if (broker == null)
			return;
		pool.push(broker);
		if(pool.size() == brokers && 
			(System.currentTimeMillis() - lastRequest) > idleTime)
			sync(broker);
        lastRequest = System.currentTimeMillis();
		this.notifyAll();
	}

	/**
	 * Write buffers to disk. release() calls this
	 * method after a specified period of time
	 * to flush buffers.
	 * 
	 * @param broker
	 */
	public void sync(DBBroker broker) {
		LOG.debug("database is idle; syncing buffers to disk");
		broker.sync();
	}
	
	/**  Shutdown all brokers. */
	public synchronized void shutdown() {
		while (pool.size() < brokers)
			try {
				this.wait();
			} catch (InterruptedException e) {
			}
		LOG.debug("calling shutdown ...");
		for (Iterator i = active.iterator(); i.hasNext();)
			 ((DBBroker) i.next()).shutdown();
		LOG.debug("shutdown!");
		conf = null;
	}

	/**
		 *  Returns maximum of concurrent Brokers.
		 *
		 *@return    The max value
		 */
	public int getMax() {
		return max;
	}

	public String getId() {
		return instanceId;
	}
	
	/**
	 *  Has this BrokerPool been configured?
	 *
	 *@return    The configured value
	 */
	public final boolean isInstanceConfigured() {
		return conf != null;
	}

	protected class ShutdownThread extends Thread {

		/**  Constructor for the ShutdownThread object */
		public ShutdownThread() {
			super();
		}

		/**  Main processing method for the ShutdownThread object */
		public void run() {
			LOG.debug("shutdown forced");
			BrokerPool.stopAll();
		}
	}
	{
		Runtime.getRuntime().addShutdownHook(new ShutdownThread());
	}

}
