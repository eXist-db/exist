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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.SystemTask;
import org.exist.util.Configuration;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;


/**
 * A Scheduler to trigger Startup, System and User defined jobs
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class Scheduler
{
	public static final String CONFIGURATION_ELEMENT_NAME = "scheduler";
	public static final String CONFIGURATION_JOB_ELEMENT_NAME = "job";
	public static final String JOB_TYPE_ATTRIBUTE = "type";
	public static final String JOB_CLASS_ATTRIBUTE = "class";
	public static final String JOB_XQUERY_ATTRIBUTE = "xquery";
	public static final String JOB_CRON_TRIGGER_ATTRIBUTE = "cron-trigger";
	public static final String JOB_PERIOD_ATTRIBUTE = "period";
	public static final String JOB_DELAY_ATTRIBUTE = "delay";
	public static final String JOB_REPEAT_ATTRIBUTE = "repeat";
	public static final String CONFIGURATION_JOB_PARAMETER_ELEMENT_NAME = "parameter";
	public static final String PROPERTY_SCHEDULER_JOBS = "scheduler.jobs";
	public static final String JOB_TYPE_USER = "user";
	public static final String JOB_TYPE_STARTUP = "startup";
	public static final String JOB_TYPE_SYSTEM = "system";
    public static final String JOB_NAME_ATTRIBUTE = "name";
	
	//the scheduler
	private org.quartz.Scheduler scheduler = null;
	
	//startup jobs
	private Vector startupJobs = new Vector();
	
	private BrokerPool brokerpool = null;
	private Configuration config = null;
	
	private final static Logger LOG = Logger.getLogger(Scheduler.class); //Logger
	
	
	/**
	 * Create and Start a new Scheduler
	 * 
	 * @param brokerpool	The brokerpool for which this scheduler is intended
	 */
	public Scheduler(BrokerPool brokerpool, Configuration config) throws EXistException
	{
		this.brokerpool = brokerpool;
		this.config = config;
		
		try
		{
			//load the properties for quartz
            InputStream is = Scheduler.class.getResourceAsStream("quartz.properties");
            Properties properties = new Properties();
            try
            {
                properties.load(is);
            }
            catch (IOException e)
            {
                throw new EXistException("Failed to load scheduler settings from org/exist/scheduler/quartz.properties");
            }
            properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, brokerpool.getId() + "_QuartzScheduler");
            SchedulerFactory schedulerFactory = new StdSchedulerFactory(properties);
			scheduler = schedulerFactory.getScheduler();
		}
		catch(SchedulerException se)
		{
			throw new EXistException("Unable to create Scheduler", se);
		}
	}
	
	public void run()
	{
		try
		{
			setupConfiguredJobs();
		
			executeStartupJobs();
		
			scheduler.start();
		}
		catch(SchedulerException se)
		{
			LOG.error(se);
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
			LOG.warn(se);
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
			LOG.warn(se);
			return false;
		}
	}
	
	/**
	 * Creates a startup job
	 * 
	 * @param job The job to trigger at startup 
	 * @param params Any parameters to pass to the job
	 */
	private void createStartupJob(UserJob job, Properties params)
	{
		//Create the job details
		JobDetail jobDetail = new JobDetail(job.getName(), job.getGroup(), job.getClass());

		//Setup the job's data map
		JobDataMap jobDataMap = jobDetail.getJobDataMap();
		setupJobDataMap(job, jobDataMap, params);
		
		//create the minimum quartz supporting classes to execute a job
		SimpleTrigger trig = new SimpleTrigger();
		trig.setJobDataMap(jobDataMap);
		JobExecutionContext jec = new JobExecutionContext(null, new org.quartz.spi.TriggerFiredBundle(jobDetail, trig, null, false, null, null, null, null), job);
		
		startupJobs.add(jec);		
	}
	
	
	/**
	 * Executes all startup jobs
	 */
	public void executeStartupJobs()
	{
		for(Iterator itStartupJob = startupJobs.iterator(); itStartupJob.hasNext();)
		{
			JobExecutionContext jec = (JobExecutionContext)itStartupJob.next();
			
			org.quartz.Job j = jec.getJobInstance();
			
			if(LOG.isInfoEnabled())
				LOG.info("Running startup job '" + jec.getJobDetail().getName() + "'");

			try
			{
				//execute the job
				j.execute(jec);
			}
			catch(SchedulerException se)
			{
				LOG.error("Unable to run startup job '" + jec.getJobDetail().getName() + "'", se);
			}
		}
	}
	
	/**
	 * @param period	The period, in milliseconds.
	 * @param job 	The job to trigger after each period
	 * @param delay	<= 0, start now, otherwise start in specified number of milliseconds
	 * 
	 * 
	 * @return	true if the job was successfully scheduled, false otherwise
	 */
	public boolean createPeriodicJob(long period, JobDescription job, long delay)
	{
		return createPeriodicJob(period, job, delay, null, SimpleTrigger.REPEAT_INDEFINITELY);
	}
	
	
	/**
	 * @param period	The period, in milliseconds.
	 * @param job 	The job to trigger after each period
	 * @param delay	<= 0, start now, otherwise start in specified number of milliseconds
	 * @param params	Any parameters to pass to the job
	 * 
	 * @return	true if the job was successfully scheduled, false otherwise
	 */
	public boolean createPeriodicJob(long period, JobDescription job, long delay, Properties params)
	{
		return createPeriodicJob(period, job, delay, params, SimpleTrigger.REPEAT_INDEFINITELY);
	}
		
	
	/**
	 * @param period	The period, in milliseconds.
	 * @param job 	The job to trigger after each period
	 * @param delay	<= 0, start now, otherwise start in specified number of milliseconds
	 * @param params	Any parameters to pass to the job
	 * @param repeatCount	Number of times to repeat this job.
	 * 
	 * @return	true if the job was successfully scheduled, false otherwise
	 */
	public boolean createPeriodicJob(long period, JobDescription job, long delay, Properties params, int repeatCount)
	{
		//Create the job details
		JobDetail jobDetail = new JobDetail(job.getName(), job.getGroup(), job.getClass());

		//Setup the job's data map
		JobDataMap jobDataMap = jobDetail.getJobDataMap();
		setupJobDataMap(job, jobDataMap, params);
		
		//setup a trigger for the job, millisecond based
		SimpleTrigger trigger = new SimpleTrigger();
		
		trigger.setRepeatInterval(period);
		trigger.setRepeatCount(repeatCount);

		//when should the trigger start
		if(delay <= 0)
		{
			//start now
			trigger.setStartTime(new Date());
		}
		else
		{
			//start after period
			Calendar start = Calendar.getInstance();
			start.add(Calendar.MILLISECOND, (int)delay);
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
			LOG.error("Failed to schedule periodic job '" + job.getName() + "'", se);
			return false;
		}
		
		//Successfully scheduled Job
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
	public boolean createCronJob(String cronExpression, JobDescription job, Properties params)
	{
		//Create the job details
		JobDetail jobDetail = new JobDetail(job.getName(), job.getGroup(), job.getClass());
		
		//Setup the job's data map
		JobDataMap jobDataMap = jobDetail.getJobDataMap();
		setupJobDataMap(job, jobDataMap, params);
		
		try
		{
			//setup a trigger for the job, Cron based
			CronTrigger trigger = new CronTrigger(job.getName() + " Trigger", job.getGroup(), cronExpression);
			
			//schedule the job
			scheduler.scheduleJob(jobDetail, trigger);
		}
		catch(ParseException pe)
		{
			//Failed to schedule Job
			LOG.error("Failed to schedule cron job '" + job.getName() + "'", pe);
			return false;
		}
		catch(SchedulerException se)
		{
			//Failed to schedule Job
			LOG.error("Failed to schedule cron job '" + job.getName() + "'", se);
			return false;
		}
		
		//Successfully scheduled Job
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
			LOG.error("Failed to delete job '" + jobName + "'", se);
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
			LOG.error("Failed to pause job '" + jobName + "'", se);
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
			LOG.error("Failed to resume job '" + jobName + "'", se);
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
			LOG.error("Failed to get job group names", se);
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
			LOG.error("Failed to get scheduled jobs", se);
			return null;
		}
		
		//copy the array list to a correctly typed array
		Object[] oJobsArray = jobs.toArray();
		ScheduledJobInfo[] jobsArray = new ScheduledJobInfo[oJobsArray.length]; 
		System.arraycopy(oJobsArray, 0, jobsArray, 0, oJobsArray.length);
		
		return jobsArray;
	}
	
	/**
	 * Gets information about currently Executing Jobs
	 * 
	 * @return An array of ScheduledJobInfo
	 */
	public ScheduledJobInfo[] getExecutingJobs()
	{
		List executingJobs = null;
		
		try
		{
			executingJobs = scheduler.getCurrentlyExecutingJobs();
		}
		catch(SchedulerException se)
		{
			LOG.error("Failed to get executing jobs", se);
			return null;
		}
		
		ScheduledJobInfo[] jobs = new ScheduledJobInfo[executingJobs.size()];
		
		for(int i = 0; i < executingJobs.size(); i++)
		{
			JobExecutionContext jec = (JobExecutionContext)executingJobs.get(i);
			jobs[i] = new ScheduledJobInfo(scheduler, jec.getTrigger());
		}
		
		return jobs;
	}
	
	/**
	 * Set's up all the jobs that are listed in conf.xml and loaded
	 * through org.exist.util.Configuration
	 */
	public void setupConfiguredJobs()
	{
		Configuration.JobConfig jobList[] = (Configuration.JobConfig[])config.getProperty(Scheduler.PROPERTY_SCHEDULER_JOBS);
		
		if(jobList == null)
			return;
		
		for(int i = 0; i < jobList.length; i ++)
		{
			Configuration.JobConfig jobConfig = jobList[i];
			JobDescription job = null;
			
			if(jobConfig.getResourceName().startsWith("/db/"))
			{
				
				if(jobConfig.getType().equals(JOB_TYPE_SYSTEM))
				{
					LOG.error("System jobs may only be written in Java");
				}
				else
				{
					//create an XQuery job
					User guestUser = brokerpool.getSecurityManager().getUser(SecurityManager.GUEST_USER);
					job = new UserXQueryJob(jobConfig.getJobName(), jobConfig.getResourceName(), guestUser);
				}
			}
			else
			{
				//create a Java job
				try
				{
					Class jobClass = Class.forName(jobConfig.getResourceName());
					Object jobObject = jobClass.newInstance();
					
					if(jobConfig.getType().equals(JOB_TYPE_SYSTEM))
					{
						if(jobObject instanceof SystemTask)
						{
							SystemTask task = (SystemTask)jobObject;
							task.configure(config, jobConfig.getParameters());
							job = new SystemTaskJob(jobConfig.getJobName(), task);
						}
						else
						{
							LOG.error("System jobs must extend SystemTask");
						}
					}
					else
					{
						job = (JobDescription)jobObject;
                        if (jobConfig.getJobName() != null)
                            job.setName(jobConfig.getJobName());
					}
				}
				catch(Exception e)
				{
					LOG.error("Unable to schedule '" + jobConfig.getType() + "' job " +  jobConfig.getResourceName(), e);
				}
			}
			
			//if there is a job, schedule it
			if(job != null)
			{
				
				if(jobConfig.getType().equals(JOB_TYPE_STARTUP))
				{
					//startup job - one off execution - no period, delay or repeat
					createStartupJob((UserJob)job, jobConfig.getParameters());
				}
				else
				{
					//timed job
					
					//trigger is Cron or period?
					if(jobConfig.getSchedule().indexOf(' ') > -1)
					{
						//schedule job with Cron trigger
						createCronJob(jobConfig.getSchedule(), job, jobConfig.getParameters());
					}
					else
					{
						//schedule job with periodic trigger
						createPeriodicJob(Long.parseLong(jobConfig.getSchedule()), job, jobConfig.getDelay(), jobConfig.getParameters(), jobConfig.getRepeat());
					}
				}
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
	private void setupJobDataMap(JobDescription job, JobDataMap jobDataMap, Properties params)
	{
		//if this is a system job, store the BrokerPool in the job's data map
		jobDataMap.put("brokerpool", brokerpool);
		
		//if this is a system task job, store the SystemTask in the job's data map
		if(job instanceof SystemTaskJob)
		{
			jobDataMap.put("systemtask", ((SystemTaskJob)job).getSystemTask());
		}
		
		//if this is a users XQuery job, store the XQuery resource and user in the job's data map
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
