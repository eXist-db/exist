/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist-db team
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

import java.util.List;
import java.util.Properties;

/**
 * A Scheduler to trigger Startup, System and User defined jobs.
 *
 * @author  Adam Retter <adam@existsolutions.com>
 */
public interface Scheduler {
    
    public void run();

    /**
     * Shutdown the running Scheduler.
     *
     * <p>Asynchronous method. use isShutdown() to determine if the Scheduler has Shutdown</p>
     *
     * @param  waitForJobsToComplete Should we wait for currently executing jobs
     * to complete before shutting down?
     */
    public void shutdown(final boolean waitForJobsToComplete);

    public boolean isShutdown();

    /**
     * Create Periodic Job
     *
     * @param   period  The period, in milliseconds.
     * @param   job     The job to trigger after each period
     * @param   delay   <= 0, start now, otherwise start in specified number of milliseconds
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay);

    /**
     * Create Periodic Job
     *
     * @param   period  The period, in milliseconds.
     * @param   job     The job to trigger after each period
     * @param   delay   <= 0, start now, otherwise start in specified number of milliseconds
     * @param   params  Any parameters to pass to the job
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params);

    /**
     * Create Periodic Job
     *
     * @param   period       The period, in milliseconds.
     * @param   job          The job to trigger after each period
     * @param   delay        <= 0, start now, otherwise start in specified number of milliseconds
     * @param   params       Any parameters to pass to the job
     * @param   repeatCount  Number of times to repeat this job.
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params, final int repeatCount);

    /**
     * Create Periodic Job
     *
     * @param   period       The period, in milliseconds.
     * @param   job          The job to trigger after each period
     * @param   delay        <= 0, start now, otherwise start in specified number of milliseconds
     * @param   params       Any parameters to pass to the job
     * @param   repeatCount  Number of times to repeat this job.
     * @param   unschedule   Unschedule job on XPathException?
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    public boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params, final int repeatCount, final boolean unschedule);

    /**
     * Create Cron Job
     *
     * @param   cronExpression  The Cron scheduling expression
     * @param   job             The job to trigger after each period
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    public boolean createCronJob(final String cronExpression, final JobDescription job);

    /**
     * Create Cron Job
     *
     * @param   cronExpression  The Cron scheduling expression
     * @param   job             The job to trigger after each period
     * @param   params          Any parameters to pass to the job
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    public boolean createCronJob(final String cronExpression, final JobDescription job, final Properties params);

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
    public boolean createCronJob(final String cronExpression, final JobDescription job, final Properties params, final boolean unschedule);

    /**
     * Removes a Job from the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was deleted, false otherwise
     */
    public boolean deleteJob(final String jobName, final String jobGroup);

    /**
     * Pauses a Job with the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was paused, false otherwise
     */
    public boolean pauseJob(final String jobName, final String jobGroup);

    /**
     * Resume a Job with the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was resumed, false otherwise
     */
    public boolean resumeJob(final String jobName, final String jobGroup);

    /**
     * Gets the names of the Job groups.
     *
     * @return  List of the Job group names
     */
    public List<String> getJobGroupNames();

    /**
     * Gets information about currently Scheduled Jobs.
     *
     * @return List of ScheduledJobInfo
     */
    public List<ScheduledJobInfo> getScheduledJobs();

    /**
     * Gets information about currently Executing Jobs.
     *
     * @return  An array of ScheduledJobInfo
     */
    public ScheduledJobInfo[] getExecutingJobs();

    /**
     * Set's up all the jobs that are listed in conf.xml and loaded through org.exist.util.Configuration.
     */
    public void setupConfiguredJobs();
}