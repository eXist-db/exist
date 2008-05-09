/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2006 The eXist team
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.scheduler;

import org.exist.storage.BrokerPool;
import org.exist.storage.SystemTask;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Class to represent a SystemTask Job
 * Can be used by SystemTasks to schedule themselves as job's
 * 
 * SystemTaskJobs may only have a Single Instance
 * running in the scheduler at once, intersecting
 * schedules will be queued.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class SystemTaskJob implements JobDescription, org.quartz.StatefulJob
{
	private String JOB_NAME = "SystemTask";
	private final static String JOB_GROUP = "eXist.System";
	
	private SystemTask task = null;
	
	/**
	 * Default Constructor for Quartz
	 */
	public SystemTaskJob()
	{		
	}
	
	/**
	 * Constructor for Creating a new SystemTask Job
	 */
	public SystemTaskJob(String jobName, SystemTask task)
	{
		this.task = task;
        if (jobName == null)
            this.JOB_NAME += ": " + task.getClass().getName();
        else
            this.JOB_NAME = jobName;
	}
	
	public final String getName()
	{
		return JOB_NAME;	
	}
	
    public final void setName(String jobName) {
        this.JOB_NAME = jobName;
    }
    
	public final String getGroup()
	{
		return JOB_GROUP;
	}
	
	/**
	 * Returns the SystemTask for this Job
	 * 
	 * @return The SystemTask for this Job
	 */
	protected SystemTask getSystemTask()
	{
		return task;
	}
	
	public final void execute(JobExecutionContext jec) throws JobExecutionException
	{
		JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();
		BrokerPool pool = (BrokerPool)jobDataMap.get("brokerpool");
		SystemTask task = (SystemTask)jobDataMap.get("systemtask");
		
		//if invalid arguments then abort
		if(pool == null || task == null)
		{
			//abort all triggers for this job
			JobExecutionException jaa = new JobExecutionException("SystemTaskJob Failed: BrokerPool or SystemTask was null! Unscheduling SystemTask", false);
			jaa.setUnscheduleAllTriggers(true);
			throw jaa;
		}
		
		//trigger the system task
		pool.triggerSystemTask(task);
	}
}
