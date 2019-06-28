/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.scheduler.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import com.evolvedbinary.j8fu.Either;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.scheduler.*;
import org.exist.scheduler.Scheduler;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.SystemTask;
import org.exist.util.Configuration;

import org.quartz.*;

import static org.exist.util.ThreadUtils.nameInstanceSchedulerThread;
import static org.exist.util.ThreadUtils.nameInstanceThread;
import static org.exist.util.ThreadUtils.newInstanceSubThreadGroup;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import static org.exist.scheduler.JobDescription.*;
import static org.quartz.impl.StdSchedulerFactory.*;

import org.quartz.Job;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import javax.annotation.Nullable;

/**
 * A Scheduler to trigger Startup, System and User defined jobs.
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 */
public class QuartzSchedulerImpl implements Scheduler, BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(QuartzSchedulerImpl.class);

    // the real scheduler
    private org.quartz.Scheduler scheduler;

    private final BrokerPool brokerPool;
    private Configuration config;

    public QuartzSchedulerImpl(final BrokerPool brokerpool) {
        this.brokerPool = brokerpool;
    }

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        this.config = configuration;
    }

    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {

        // NOTE: we create the scheduler in a separate thread with its own thread-group so that the thread group is used by Quartz
        final ThreadGroup instanceQuartzThreadGroup = newInstanceSubThreadGroup(brokerPool, "scheduler.quartz-simple-thread-pool");
        final QuartzSchedulerCreator creator = new QuartzSchedulerCreator();
        final Thread schedulerCreatorThread = new Thread(instanceQuartzThreadGroup, creator, nameInstanceThread(brokerPool, "prepare-quartz-scheduler"));
        schedulerCreatorThread.start();

        try {
            schedulerCreatorThread.join();
            this.scheduler = creator
                    .getScheduler()
                    .valueOrThrow(e -> new BrokerPoolServiceException("Unable to create Scheduler: " + e.getMessage(), e));

        } catch (final InterruptedException e) {
            // restore interrupted state
            Thread.currentThread().interrupt();
            throw new BrokerPoolServiceException("Unable to create Scheduler: " + e.getMessage(), e);
        }
    }
    @Override
    public void startMultiUser(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        run(); // start running all the defined jobs
    }

    private final static Properties defaultQuartzProperties = new Properties();

    static {
        defaultQuartzProperties.setProperty(PROP_SCHED_RMI_EXPORT, "false");
        defaultQuartzProperties.setProperty(PROP_SCHED_RMI_PROXY, "false");
        defaultQuartzProperties.setProperty(PROP_SCHED_WRAP_JOB_IN_USER_TX, "false");
        defaultQuartzProperties.setProperty(PROP_THREAD_POOL_CLASS, "org.exist.scheduler.impl.ExistQuartzSimpleThreadPool");
        defaultQuartzProperties.setProperty("org.quartz.threadPool.threadCount", "4");
        defaultQuartzProperties.setProperty("org.quartz.threadPool.threadPriority", "5");
        defaultQuartzProperties.setProperty("org.quartz.threadPool.threadsInheritGroupOfInitializingThread", "true");
        defaultQuartzProperties.setProperty("org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread", "true");
        defaultQuartzProperties.setProperty(PROP_JOB_STORE_CLASS, "org.quartz.simpl.RAMJobStore");
        defaultQuartzProperties.setProperty("org.quartz.jobStore.misfireThreshold", "60000");
    }

    private Properties getQuartzProperties() {
        //try and load the properties for quartz
        final Properties properties = new Properties();
        try (final InputStream is = this.getClass().getResourceAsStream("quartz.properties")) {
            if(is != null) {
                properties.load(is);
                LOG.info("Successfully loaded quartz.properties");
            } else {
                LOG.warn("Could not load quartz.properties, will use defaults.");
            }
        } catch(final IOException ioe) {
            LOG.warn("Could not load quartz.properties, will defaults. " + ioe.getMessage(), ioe);
        }
        if (properties == null || properties.size() == 0) {
            LOG.warn("Using default properties for Quartz scheduler");
            properties.putAll(defaultQuartzProperties);
        }

        // always set the scheduler name
        properties.setProperty(PROP_SCHED_INSTANCE_NAME, nameInstanceSchedulerThread(brokerPool, "quartz-scheduler"));

        // always set the thread prefix for the thread pool
        final String schedulerThreadNamePrefix = nameInstanceSchedulerThread(brokerPool, "quartz-worker");
        properties.setProperty(PROP_THREAD_POOL_PREFIX + ".threadNamePrefix", schedulerThreadNamePrefix);

        return properties;
    }

    protected org.quartz.Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void run() {
        try {
            setupConfiguredJobs();
            getScheduler().start();
        } catch(final SchedulerException se) {
            LOG.error("Unable to start the Scheduler: " + se.getMessage(), se);
        }
    }

    /**
     * Shutdown the running Scheduler.
     *
     * Asynchronous method. use isShutdown() to determine if the Scheduler has Shutdown
     *
     * @param  waitForJobsToComplete Should we wait for currently executing jobs
     * to complete before shutting down?
     */
    @Override
    public void shutdown(final boolean waitForJobsToComplete) {
        try {
            getScheduler().shutdown(waitForJobsToComplete);
        } catch(final SchedulerException se) {
            LOG.warn("Unable to shutdown the Scheduler:" + se.getMessage(), se);
        }
    }

    @Override
    public boolean isShutdown() {
        try {
            return getScheduler().isShutdown();
        } catch(final SchedulerException se) {
            LOG.warn("Unable to determine the status of the Scheduler: " + se.getMessage(), se);
        }
        return false;
    }

    /**
     * Create Periodic Job
     *
     * @param period  The period, in milliseconds.
     * @param job     The job to trigger after each period
     * @param delay   &lt;= 0, start now, otherwise start in specified number of milliseconds
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    @Override
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay) {
        return createPeriodicJob(period, job, delay, null, SimpleTrigger.REPEAT_INDEFINITELY);
    }

    /**
     * Create Periodic Job
     *
     * @param   period  The period, in milliseconds.
     * @param   job     The job to trigger after each period
     * @param   delay   &lt;= 0, start now, otherwise start in specified number of milliseconds
     * @param   params  Any parameters to pass to the job
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    @Override
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params) {
        return createPeriodicJob(period, job, delay, params, SimpleTrigger.REPEAT_INDEFINITELY);
    }

    /**
     * Create Periodic Job
     *
     * @param   period       The period, in milliseconds.
     * @param   job          The job to trigger after each period
     * @param   delay        &lt;= 0, start now, otherwise start in specified number of milliseconds
     * @param   params       Any parameters to pass to the job
     * @param   repeatCount  Number of times to repeat this job.
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    @Override
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params, final int repeatCount) {
        return createPeriodicJob(period, job, delay, params, repeatCount, true);
    }

    /**
     * Create Periodic Job
     *
     * @param   period       The period, in milliseconds.
     * @param   job          The job to trigger after each period
     * @param   delay        &lt;= 0, start now, otherwise start in specified number of milliseconds
     * @param   params       Any parameters to pass to the job
     * @param   repeatCount  Number of times to repeat this job.
     * @param   unschedule   Unschedule job on XPathException?
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    @Override
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params, final int repeatCount, final boolean unschedule) {
        //Create the job details
        final JobDetail jobDetail = newJob((Class<? extends Job>)job.getClass())
            .withIdentity(job.getName(), job.getGroup()).build();
        
        //Setup the job's data map
        final JobDataMap jobDataMap = jobDetail.getJobDataMap();
        setupJobDataMap(job, jobDataMap, params, unschedule);

        //setup a trigger for the job, millisecond based
        final TriggerBuilder triggerBuilder = newTrigger()
            .withIdentity(job.getName() + " Trigger", job.getGroup())
            .withSchedule(
                simpleSchedule()
                    .withIntervalInMilliseconds(period)
                    .withRepeatCount(repeatCount)
            );

        //when should the trigger start
        final Trigger trigger;
        if(delay <= 0) {
            //start now
            trigger = triggerBuilder.startNow().build();
        } else {
            //start after period
            final Calendar start = Calendar.getInstance();
            start.add(Calendar.MILLISECOND, (int)delay);
            final Date triggerStart = start.getTime();
            trigger = triggerBuilder.startAt(triggerStart).build();
        }
        
        //schedule the job
        try {
            getScheduler().scheduleJob(jobDetail, trigger);
        } catch(final SchedulerException se) {
            //Failed to schedule Job
            LOG.error("Failed to schedule periodic job '" + job.getName() + "': " + se.getMessage(), se);
            return false ;
        }
        //Successfully scheduled Job
        return true;
    }

    /**
     * Create Cron Job
     *
     * @param   cronExpression  The Cron scheduling expression
     * @param   job             The job to trigger after each period
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    @Override
    public boolean createCronJob(final String cronExpression, final JobDescription job) {
        return createCronJob(cronExpression, job, null);
    }

    /**
     * Create Cron Job
     *
     * @param   cronExpression  The Cron scheduling expression
     * @param   job             The job to trigger after each period
     * @param   params          Any parameters to pass to the job
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    @Override
    public boolean createCronJob(final String cronExpression, final JobDescription job, final Properties params) {
        return createCronJob(cronExpression, job, params, true);
    }

    /**
     * Create Cron Job
     *
     * @param   cronExpression  The Cron scheduling expression
     * @param   job             The job to trigger after each period
     * @param   params          Any parameters to pass to the job
     * @param   unschedule   Unschedule job on XPathException?.
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    @Override
    public boolean createCronJob(final String cronExpression, final JobDescription job, final Properties params, final boolean unschedule) {
        //Create the job details
        final JobDetail jobDetail = newJob((Class<? extends Job>)job.getClass())
            .withIdentity(job.getName(), job.getGroup()).build();
        
        //Setup the job's data map
        final JobDataMap jobDataMap = jobDetail.getJobDataMap();
        setupJobDataMap(job, jobDataMap, params, unschedule);
        
        try {
            //setup a trigger for the job, Cron based
            final Trigger trigger = newTrigger()
                .withIdentity(job.getName() + " Trigger", job.getGroup())
                .withSchedule(cronSchedule(cronExpression)).build();
            
            //schedule the job
            getScheduler().scheduleJob(jobDetail, trigger);
        } catch(final SchedulerException se) {
            //Failed to schedule Job
            LOG.error("Failed to schedule cron job '" + job.getName() + "': " + se.getMessage(), se);
            return false;
        }
        //Successfully scheduled Job
        return true;
    }

    /**
     * Removes a Job from the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was deleted, false otherwise
     */
    @Override
    public boolean deleteJob(final String jobName, final String jobGroup) {
        boolean deletedJob = false;
        try {
            deletedJob = getScheduler().deleteJob(new JobKey(jobName, jobGroup));
        } catch(final SchedulerException se) {
            LOG.error("Failed to delete job '" + jobName + "': " + se.getMessage(), se);
        }
        return deletedJob;
    }

    /**
     * Pauses a Job with the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was paused, false otherwise
     */
    @Override
    public boolean pauseJob(final String jobName, final String jobGroup) {
        try {
            getScheduler().pauseJob(new JobKey(jobName, jobGroup));
            return true;
        } catch(final SchedulerException se) {
            LOG.error( "Failed to pause job '" + jobName + "': " + se.getMessage(), se);
        }
        return false;
    }

    /**
     * Resume a Job with the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was resumed, false otherwise
     */
    @Override
    public boolean resumeJob(final String jobName, final String jobGroup) {
        try {
            getScheduler().resumeJob(new JobKey(jobName, jobGroup));
            return true;
        } catch(final SchedulerException se) {
            LOG.error("Failed to resume job '" + jobName + "': " + se.getMessage(), se);
        }
        return false;
    }

    /**
     * Gets the names of the Job groups.
     *
     * @return  String array of the Job group names
     */
    @Override
    public List<String> getJobGroupNames() {
        final List<String> jobNames = new ArrayList<>();
        try {
            jobNames.addAll(getScheduler().getJobGroupNames());
        } catch(final SchedulerException se) {
            LOG.error( "Failed to get job group names: " + se.getMessage(), se );
        }
        return jobNames;
    }

    /**
     * Gets information about currently Scheduled Jobs.
     *
     * @return  An array of ScheduledJobInfo
     */
    @Override
    public List<ScheduledJobInfo> getScheduledJobs() {
        final List<ScheduledJobInfo> scheduledJobs = new ArrayList<>();
        try {
            //get the trigger groups
            for(final String triggerGroupName : getScheduler().getTriggerGroupNames()) {
                //get the trigger names for the trigger group
                for(final TriggerKey triggerKey : getScheduler().getTriggerKeys(GroupMatcher.triggerGroupEquals(triggerGroupName))) {
                    //add information about the job to the result
                    scheduledJobs.add(new ScheduledJobInfo(getScheduler(), getScheduler().getTrigger(triggerKey)));
                }
            }
        } catch(final SchedulerException se) {
            LOG.error("Failed to get scheduled jobs: " + se.getMessage(), se);
        }
        return scheduledJobs;
    }

    /**
     * Gets information about currently Executing Jobs.
     *
     * @return  An array of ScheduledJobInfo
     */
    @Override
    public ScheduledJobInfo[] getExecutingJobs() {
        ScheduledJobInfo result[] = null;
        try {
            final List<ScheduledJobInfo> jobs = getScheduler()
                .getCurrentlyExecutingJobs()
                .stream()
                .map(jobExecutionCtx -> new ScheduledJobInfo(getScheduler(), jobExecutionCtx.getTrigger()))
                .collect(Collectors.toList());

            result = new ScheduledJobInfo[jobs.size()];
            jobs.toArray(result);
        } catch(final SchedulerException se) {
            LOG.error("Failed to get executing jobs: " + se.getMessage(), se);
        }
        return result;
    }

    /**
     * Set's up all the jobs that are listed in conf.xml and loaded through org.exist.util.Configuration.
     */
    @Override
    public void setupConfiguredJobs() {
        final JobConfig[] jobList = (JobConfig[])config.getProperty(JobConfig.PROPERTY_SCHEDULER_JOBS);
        
        if(jobList == null) {
            return;
        }
        
        for(final JobConfig jobConfig : jobList) {
            JobDescription job = null;
            if(jobConfig.getResourceName().startsWith("/db/") || jobConfig.getResourceName().indexOf(':') > 0) {
                if(jobConfig.getType().equals(JobType.SYSTEM)) {
                    LOG.error("System jobs may only be written in Java");
                } else {
                    //create an XQuery job
                    final Subject guestUser = brokerPool.getSecurityManager().getGuestSubject();
                    job = new UserXQueryJob(jobConfig.getJobName(), jobConfig.getResourceName(), guestUser);
                    try {
                        // check if a job with the same name is already registered
                        if(getScheduler().getJobDetail(new JobKey(job.getName(), UserJob.JOB_GROUP)) != null) {
                            // yes, try to make the job's name unique
                            job.setName(job.getName() + job.hashCode());
                        }
                        
                    } catch(final SchedulerException e) {
                        LOG.error("Unable to set job name: " + e.getMessage(), e);
                    }
                }
                
            } else {
                //create a Java job
                try {
                    final Class<?> jobClass = Class.forName(jobConfig.getResourceName());
                    final Object jobObject = jobClass.newInstance();
                    if(jobConfig.getType().equals(JobType.SYSTEM)) {
                        if(jobObject instanceof SystemTask) {
                            final SystemTask task = (SystemTask)jobObject;
                            task.configure(config, jobConfig.getParameters());
                            job = new SystemTaskJobImpl(jobConfig.getJobName(), task);
                        } else {
                            LOG.error("System jobs must extend SystemTask");
                            // throw exception? will be handled nicely
                        }
                        
                    } else {
                        if(jobObject instanceof JobDescription) {
                            job = (JobDescription)jobObject;
                            if(jobConfig.getJobName() != null) {
                                job.setName(jobConfig.getJobName());
                            }
                        } else {
                            LOG.error("Startup job " + jobConfig.getJobName() +"  must extend org.exist.scheduler.StartupJob");
                            // throw exception? will be handled nicely
                        }
                    }
                    
                } catch(final Exception e) { // Throwable?
                    LOG.error("Unable to schedule '" + jobConfig.getType() + "' job " + jobConfig.getResourceName() + ": " + e.getMessage(), e);
                }
            }
            
            //if there is a job, schedule it
            if(job != null) {
                //timed job
                //trigger is Cron or period?
                if(jobConfig.getSchedule().indexOf(' ') > -1) {
                    //schedule job with Cron trigger
                    createCronJob(jobConfig.getSchedule(), job, jobConfig.getParameters());
                } else {
                    //schedule job with periodic trigger
                    createPeriodicJob(Long.parseLong(jobConfig.getSchedule()), job, jobConfig.getDelay(), jobConfig.getParameters(), jobConfig.getRepeat(), jobConfig.unscheduleOnException());
                }
            }
        }
    }

    /**
     * Sets up the Job's Data Map.
     *
     * @param  job         The Job
     * @param  jobDataMap  The Job's Data Map
     * @param  params      Any parameters for the job
     */
    private void setupJobDataMap(final JobDescription job, final JobDataMap jobDataMap, final Properties params, final boolean unschedule) {
        //if this is a system job, store the BrokerPool in the job's data map
        jobDataMap.put(DATABASE, brokerPool);
        //if this is a system task job, store the SystemTask in the job's data map
        if(job instanceof SystemTaskJobImpl) {
            jobDataMap.put(SYSTEM_TASK, ((SystemTaskJobImpl)job).getSystemTask());
        }
        //if this is a users XQuery job, store the XQuery resource and user in the job's data map
        if(job instanceof UserXQueryJob) {
            jobDataMap.put(XQUERY_SOURCE, ((UserXQueryJob)job).getXQueryResource());
            jobDataMap.put(ACCOUNT, ((UserXQueryJob)job).getUser());
        }
        //copy any parameters into the job's data map
        if(params != null) {
            jobDataMap.put(PARAMS, params);
        }
        //Store the value of the unschedule setting
        jobDataMap.put(UNSCHEDULE, Boolean.valueOf(unschedule));
    }

    /**
     * Creates a new Scheduler
     */
    private class QuartzSchedulerCreator implements Runnable {
        @Nullable
        private volatile Either<SchedulerException, org.quartz.Scheduler> scheduler = null;

        @Nullable
        public Either<SchedulerException, org.quartz.Scheduler> getScheduler() {
            return scheduler;
        }

        @Override
        public void run() {
            try {
                final SchedulerFactory schedulerFactory = new StdSchedulerFactory(getQuartzProperties());
                this.scheduler = Either.Right(schedulerFactory.getScheduler());
            } catch(final SchedulerException e) {
                this.scheduler = Either.Left(e);
            }
        }
    }
}
