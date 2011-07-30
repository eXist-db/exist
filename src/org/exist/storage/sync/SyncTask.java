/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2011 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.storage.sync;

import java.util.Properties;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.util.Configuration;

public class SyncTask implements SystemTask {

	@SuppressWarnings("unused")
	private final static String JOB_GROUP = "eXist.internal";
	private final static String JOB_NAME = "Sync";
	
	public static String getJobName() {
		return JOB_NAME;
	}
	
	public static String getJobGroup() {
		return JOB_GROUP;
	}
	
	public boolean afterCheckpoint() {
		// a checkpoint is created by the MAJOR_SYNC event
		return false;
	}
	
	public void configure(Configuration config, Properties properties)
			throws EXistException {
	}

	public void execute(DBBroker broker) throws EXistException {
		BrokerPool pool = broker.getBrokerPool();
		if(System.currentTimeMillis() - pool.getLastMajorSync() > pool.getMajorSyncPeriod())
		{
			pool.sync(broker, Sync.MAJOR_SYNC);
		}
		else
		{
			pool.sync(broker, Sync.MINOR_SYNC);
		}
	}
}
