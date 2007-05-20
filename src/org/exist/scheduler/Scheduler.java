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

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.exist.EXistException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;


/**
 * A Scheduler to trigger System and User defined jobs
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class Scheduler
{
	public static final String PROPERTY_SCHEDULER_JOBS = "scheduler.jobs";
	
	//the scheduler
	private org.quartz.Scheduler scheduler = null;
	
	//the brokerpool for this scheduler
	private BrokerPool brokerpool = null;
	
	/**
	 * Create and Start a new Scheduler
	 * 
	 * @param brokerpool	The brokerpool for which this scheduler is intended
	 */
	public Scheduler(BrokerPool brokerpool, Configuration config) throws EXistException
	{
		this.brokerpool = brokerpool;
		
		try
		{
			//load the properties for quartz
            InputStream is = Scheduler.class.getResourceAsStream("quartz.properties");
            Properties properties = new Properties();
            try {
                properties.load(is);
            } catch (IOException e) {
                throw new EXistException("Failed to load scheduler settings from org/exist/scheduler/quartz.properties");
            }

            SchedulerFactory schedulerFactory = new StdSchedulerFactory(properties);
			scheduler = schedulerFactory.getScheduler();

			//start quartz
			scheduler.start();
		}
		catch(SchedulerException se)
		{
			throw new EXistException("Unable to create Scheduler", se);
		}
	}
	
	/**
	 * Shutdown the running Scheduler
	 * 
	 * Asynchronous method. use isShutdown() to determine if the
	 * Scheduler has Shutdown
	 */
	public void shutdown(boolean waitForJobsToComplete)
	{
		try
		{
			scheduler.shutdown(waitForJobsToComplete);
		}
		catch(SchedulerException se)
		{
			//TODO: LOG something here!?!
		}
	}
	
	public boolean isShutdown()
	{
		try
		{
			return scheduler.isShutdown();
		}
		catch(SchedulerException se)
		{
			//TODO: LOG something here!?!
			
			return false;
		}
	}
	
	/**
	 * @param period	The period, in milliseconds.
	 * @param job 	The job to trigger after each period
	 * @param startNow	true if the cycle should start with execution
	 * of the task now. Otherwise, the cycle starts with a delay of
	 * <code>period</code> milliseconds.
	 * 
	 * @return	true if the job was successfully scheduled, false otherwise
	 */
	public boolean createPeriodicJob(long period, JobDescription job, boolean startNow)
	{
		return createPeriodicJob(period, job, startNow, null);
	}
	
	/**
	 * @param period	The period, in milliseconds.
	 * @param job 	The job to trigger after each period
	 * @param startNow	true if the cycle should start with execution
	 * of the task now. Otherwise, the cycle starts with a delay of
	 * <code>period</code> milliseconds.
	 * @param params	Any parameters to pass to the job
	 * 
	 * @return	true if the job was successfully scheduled, false otherwise
	 */
	public boolean createPeriodicJob(long period, JobDescription job, boolean startNow, Map params)
	{
		//Create the job details
		JobDetail jobDetail = new JobDetail(job.getName(), job.getGroup(), job.getClass());

		//Setup the job's data map
		JobDataMap jobDataMap = jobDetail.getJobDataMap();
		setupJobDataMap(job, jobDataMap, params);
		
		//setup a trigger for the job, millisecond based
		SimpleTrigger trigger = new SimpleTrigger();
		trigger.setRepeatInterval(period);
		trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);

		//when should the trigger start
		if(startNow)
		{
			//start now
			trigger.setStartTime(new Date());
		}
		else
		{
			//start after period
			Calendar start = Calendar.getInstance();
			start.add(Calendar.MILLISECOND, (int)period);
			trigger.setStartTime(start.getTime());
		}
		
		//set the trigger's name
		trigger.setName(job.getName() + " Trigger");
		
		//schedule the job
		try
		{
			scheduler.scheduleJob(jobDetail, trigger);
		}
		catch(SchedulerException se)
		{
			//Failed to schedule Job
			return false;
		}
		
		//Succesfully scheduled Job
		return true;
	}
	
	/**
	 * @param cronExpression	The Cron scheduling expression
	 * @param job 	The job to trigger after each period
	 * 
	 * @return	true if the job was successfully scheduled, false otherwise
	 */
	public boolean createCronJob(String cronExpression, JobDescription job)
	{
		return createCronJob(cronExpression, job, null);
	}
	
	/**
	 * @param cronExpression	The Cron scheduling expression
	 * @param job 	The job to trigger after each period
	 * @param params	Any parameters to pass to the job
	 * 
	 * @return	true if the job was successfully scheduled, false otherwise
	 */
	public boolean createCronJob(String cronExpression, JobDescription job, Map params)
	{
		//Create the job details
		JobDetail jobDetail = new JobDetail(job.getName(), job.getGroup(), job.getClass());
		
		//Setup the job's data map
		JobDataMap jobDataMap = jobDetail.getJobDataMap();
		setupJobDataMap(job, jobDataMap, params);
		
		try
		{
			//setup a trigger for the job, cron based
			CronTrigger trigger = new CronTrigger(job.getName() + " Trigger", job.getGroup(), cronExpression);
			
			//schedule the job
			scheduler.scheduleJob(jobDetail, trigger);
		}
		catch(ParseException pe)
		{
			//Failed to schedule Job
			return false;
		}
		catch(SchedulerException se)
		{
			//Failed to schedule Job
			return false;
		}
		
		//Succesfully scheduled Job
		return true;
	}
	
	/**
	 * Removes a Job from the Scheduler
	 * 
	 * @param jobName	The name of the Job
	 * @param jobGroup The group that the Job was Scheduled in
	 * 
	 * @return true if the job was deleted, false otherwise
	 */
	public boolean deleteJob(String jobName, String jobGroup)
	{
		try
		{
			return scheduler.deleteJob(jobName, jobGroup);
		}
		catch(SchedulerException se)
		{
			return false;
		}
	}
	
	/**
	 * Pauses a Job with the Scheduler
	 * 
	 * @param jobName	The name of the Job
	 * @param jobGroup The group that the Job was Scheduled in
	 */
	public boolean pauseJob(String jobName, String jobGroup)
	{
		try
		{
			scheduler.pauseJob(jobName, jobGroup);
			return true;
		}
		catch(SchedulerException se)
		{
			//TODO: log an error?
			return false;
		}
	}
	
	/**
	 * Resume a Job with the Scheduler
	 * 
	 * @param jobName	The name of the Job
	 * @param jobGroup The group that the Job was Scheduled in
	 */
	public boolean resumeJob(String jobName, String jobGroup)
	{
		try
		{
			scheduler.resumeJob(jobName, jobGroup);
			return true;
		}
		catch(SchedulerException se)
		{
			//TODO: log an error?
			return false;
		}
	}
	
	/**
	 * Gets the names of the Job groups
	 * 
	 * @return String array of the Job group names
	 */
	public String[] getJobGroupNames()
	{
		try
		{
			return scheduler.getJobGroupNames();
		}
		catch(SchedulerException se)
		{
			return null;
		}
	}
	
	/**
	 * Gets information about currently Scheduled Jobs
	 * 
	 * @return An array of ScheduledJobInfo
	 */
	public ScheduledJobInfo[] getScheduledJobs()
	{
		ArrayList jobs = new ArrayList();
		
		try
		{
			//get the trigger groups
			String[] trigGroups = scheduler.getTriggerGroupNames();
			for(int tg = 0; tg < trigGroups.length; tg++)
			{
				//get the trigger names for the trigger group
				String[] trigNames = scheduler.getTriggerNames(trigGroups[tg]);
				for(int tn = 0; tn < trigNames.length; tn++)
				{
					//add information about the job to the result
					jobs.add(new ScheduledJobInfo(scheduler, scheduler.getTrigger(trigNames[tn], trigGroups[tg])));
				}
			}
		}
		catch(SchedulerException se)
		{
			return null;
		}
		
		//copy the array list to a correctly typed array
		Object[] oJobsArray = jobs.toArray();
		ScheduledJobInfo[] jobsArray = new ScheduledJobInfo[oJobsArray.length]; 
		System.arraycopy(oJobsArray, 0, jobsArray, 0, oJobsArray.length);
		
		return jobsArray;
	}
	
	/**
	 * Set's up all the jobs that are listed in conf.xml and loaded
	 * through org.exist.util.Configuration
	 */
	public void setupConfiguredJobs(Configuration config)
	{
		String jobList[][] = (String[][])config.getProperty(Scheduler.PROPERTY_SCHEDULER_JOBS);
		
		if(jobList == null)
			return;
		
		for(int i = 0; i < jobList.length; i ++)
		{
			String jobResource = jobList[i][0];
			String jobSchedule = jobList[i][1];
			
			//must be a resource and a schedule
			if(jobResource == null || jobSchedule == null)
				return;
			
			JobDescription job = null;
			
			if(jobResource.startsWith("/db/"))
			{
				//create an xquery job
				User guestUser = brokerpool.getSecurityManager().getUser(SecurityManager.GUEST_USER);
				job = new UserXQueryJob(jobResource, guestUser);
			}
			else
			{
				//create a java job
				try
				{
					//Check if the Class is a UserJob
					Class jobClass = Class.forName(jobResource);
					job = (JobDescription)jobClass.newInstance();
					if(!(job instanceof UserJavaJob))
					{
						return;
					}
				}
				catch(ClassNotFoundException cnfe)
				{
					return;
				}
				catch(IllegalAccessException iae)
				{
					return;
				}
				catch(InstantiationException ie)
				{
					return;
				}
			}
			
			//trigger is cron or period?
			if(jobSchedule.indexOf(' ') > -1)
			{
				//schedule job with cron trigger
				createCronJob(jobSchedule, job);
			}
			else
			{
				//schedule job with periodic trigger
				long period = Long.parseLong(jobSchedule);
				createPeriodicJob(period, job, true);
			}
		}
	}
	
	/**
	 * Sets up the Job's Data Map
	 * 
	 * @param job	The Job
	 * @param jobDataMap	The Job's Data Map
	 * @param params	Any parameters for the job
	 */
	private void setupJobDataMap(JobDescription job, JobDataMap jobDataMap, Map params)
	{
		//if this is a system job, store the brokerpool in the job's data map
		jobDataMap.put("brokerpool", brokerpool);
		
		//if this is a system task job, store the systemtask in the job's data map
		if(job instanceof SystemTaskJob)
		{
			jobDataMap.put("systemtask", ((SystemTaskJob)job).getSystemTask());
		}
		
		//if this is a users xquery job, store the xquery resource and user in the job's data map
		if(job instanceof UserXQueryJob)
		{
			jobDataMap.put("xqueryresource", ((UserXQueryJob)job).getXQueryResource());
			jobDataMap.put("user", ((UserXQueryJob)job).getUser());
		}
		
		//copy any parameters into the job's data map
		if(params != null)
		{
			jobDataMap.put("params", params);
		}
	}
}
