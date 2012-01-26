/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist;

import java.util.Observer;

import org.exist.debuggee.Debuggee;
import org.exist.indexing.IndexManager;
import org.exist.numbering.NodeIdFactory;
import org.exist.scheduler.Scheduler;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.CacheManager;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.util.Configuration;
import org.exist.xquery.PerformanceStats;

/**
 * Database controller, all operation synchronized by this instance. (singleton)
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public interface Database {

	//TODO: javadocs
	
	public String getId(); 
	
	public void addObserver(Observer o);
	
	/**
	 * 
	 * @return SecurityManager
	 */
	public SecurityManager getSecurityManager();

	/**
	 * 
	 * @return IndexManager
	 */
	public IndexManager getIndexManager();

	/**
	 * 
	 * @return TransactionManager
	 */
	public TransactionManager getTransactionManager();

	/**
	 * 
	 * @return CacheManager
	 */
	public CacheManager getCacheManager();
	
    /** 
     * 
     * @return Scheduler
     */
    public Scheduler getScheduler();


	/**
	 * 
	 */
	public void shutdown();

	/**
	 * 
	 * @return Subject
	 */
	public Subject getSubject();

	/**
	 * 
	 * @param subject
	 */
	public boolean setSubject(Subject subject);

	public DBBroker getBroker() throws EXistException; //TODO: remove 'throws EXistException'?
	
	/*
	 * @Deprecated ?
	 * try {
	 * 	broker = database.authenticate(account, credentials);
	 * 
	 * 	broker1 = database.get();
	 * 	broker2 = database.get();
	 * 	...
	 * 	brokerN = database.get();
	 * 
	 * } finally {
	 * 	database.release(broker);
	 * }
	 */
	public DBBroker get(Subject subject) throws EXistException;   
	public DBBroker getActiveBroker();
	public void release(DBBroker broker);
	
	/**
	 * 
	 * @return Debuggee
	 */
	public Debuggee getDebuggee();

	public PerformanceStats getPerformanceStats();

	//old configuration
	public Configuration getConfiguration();

	public NodeIdFactory getNodeFactory();
}
