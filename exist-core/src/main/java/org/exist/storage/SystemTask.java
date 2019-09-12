/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Team
 *
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
package org.exist.storage;

import java.util.Properties;

import org.exist.EXistException;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;

/**
 * Interface to be implemented by tasks used for system
 * maintenance. System tasks require the database to be in
 * a consistent state. All database operations will be stopped 
 * until the {@link #execute(DBBroker, Txn)} method returned
 * or throws an exception. Any exception will be caught and a warning
 * written to the log.
 * 
 * A task can be scheduled for execution 
 * via {@link BrokerPool#triggerSystemTask(SystemTask)} or
 * {@link org.exist.scheduler.Scheduler}.
 *
 * IMPORTANT: SystemTask implementations should avoid to acquire
 * locks on collections! Doing so may lead to a deadlock situation.
 * The system task runs in a privileged mode. Locking collections
 * and resources is not required as no other transactions will be
 * taking place.
 * 
 * @author wolf
 */
public interface SystemTask {

	String getName();

    void configure(Configuration config, Properties properties) throws EXistException;
    
	/**
	 * Execute this task.
	 * 
	 * @param broker a DBBroker object that can be used
	 * @param transaction the database transaction
	 * 
	 * @throws EXistException if a database error occurs
	 */
	void execute(DBBroker broker, Txn transaction) throws EXistException;
	
	/**
	 * @return true if a checkpoint should be generated before this system task
	 * runs. A checkpoint guarantees that all changes were written to disk.
	 */
	boolean afterCheckpoint();
}
