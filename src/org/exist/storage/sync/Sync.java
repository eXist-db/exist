/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.storage.sync;

import org.exist.scheduler.JobException;
import org.exist.scheduler.UserJavaJob;
import org.exist.storage.BrokerPool;

import java.util.Map;

/**
 * It will periodically trigger a cache sync to write
 * cached pages to disk. 
 */
public class Sync extends UserJavaJob {

	private final static String JOB_GROUP = "eXist.internal";
	private final static String JOB_NAME = "Sync";
	
	public final static int MINOR_SYNC = 0;
	public final static int MAJOR_SYNC = 1;
	
	public Sync()
	{
	}
	
	public String getName()
	{
		return JOB_NAME;
	}

    public void setName(String name) {
    }

	public void execute(BrokerPool pool, Map params) throws JobException
	{
		if(System.currentTimeMillis() - pool.getLastMajorSync() > pool.getMajorSyncPeriod())
		{
			pool.triggerSync(MAJOR_SYNC);
		}
		else
		{
			pool.triggerSync(MINOR_SYNC);
		}
	} 
}
