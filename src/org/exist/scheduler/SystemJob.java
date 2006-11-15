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
package org.exist.scheduler;

import java.util.Map;

import org.exist.storage.BrokerPool;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Class to represent a System Job
 * Should be extended by all classes wishing to
 * schedule as a Job that perform core system functions
 * 
 * Classes extending SystemJob may only have a Single Instance
 * running in the scheduler at once, intersecting schedules will be queued.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public abstract class SystemJob implements Job, org.quartz.StatefulJob
{
	public final static String JOB_GROUP = "eXist.System";
	
	public final String getGroup()
	{
		return JOB_GROUP;
	}
	
	/**
	 *	The execute method as called by the Quartz Scheduler
	 *
	 * @param jec	The execution context of the executing job
	 *
	 * @throws JobExecutionException if there was a problem with the job,
	 * this also describes to Quartz how to cleanup the job
	 */
	public final void execute(JobExecutionContext jec) throws JobExecutionException
	{
		JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();
		
		//get the brokerpool from the data map
		BrokerPool pool = (BrokerPool)jobDataMap.get("brokerpool");
		
		//get any parameters from the data map
		Map params = (Map)jobDataMap.get("params");
		
		try
		{
			//execute the job
			execute(pool, params);
		}
		catch(JobException je)
		{
			//cleanup the job
			je.cleanupJob();
		}
	}
	
	/**
	 * Function that is executed by the Scheduler
	 * 
	 * @param brokerpool	The BrokerPool for the Scheduler of this job 
	 * @param params	Any parameters passed to the job or null otherwise
	 * 
	 * @throws JobException if there is a problem with the job.
	 * cleanupJob() should then be called, which will adjust the
	 * jobs scheduling appropriately
	 */
	public abstract void execute(BrokerPool brokerpool, Map params) throws JobException;
}
