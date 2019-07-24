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
package org.exist.scheduler;

import java.util.List;
import java.util.Properties;

/**
 * A Scheduler to trigger Startup, System and User defined jobs.
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public interface Scheduler {

    void run();

    /**
     * Shutdown the running Scheduler.
     *
     * Asynchronous method. use isShutdown() to determine if the Scheduler has Shutdown
     *
     * @param  waitForJobsToComplete Should we wait for currently executing jobs
     * to complete before shutting down?
     */
    void shutdown(final boolean waitForJobsToComplete);

    boolean isShutdown();

    /**
     * Create Periodic Job
     *
     * @param   period  The period, in milliseconds.
     * @param   job     The job to trigger after each period
     * @param   delay   &lt;= 0, start now, otherwise start in specified number of milliseconds
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    boolean createPeriodicJob(final long period, final JobDescription job, final long delay);

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
    boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params);

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
    boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params, final int repeatCount);

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
    boolean createPeriodicJob(final long period, final JobDescription job, final long delay, final Properties params, final int repeatCount, final boolean unschedule);

    /**
     * Create Cron Job
     *
     * @param   cronExpression  The Cron scheduling expression
     * @param   job             The job to trigger after each period
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    boolean createCronJob(final String cronExpression, final JobDescription job);

    /**
     * Create Cron Job
     *
     * @param   cronExpression  The Cron scheduling expression
     * @param   job             The job to trigger after each period
     * @param   params          Any parameters to pass to the job
     *
     * @return  true if the job was successfully scheduled, false otherwise
     */
    boolean createCronJob(final String cronExpression, final JobDescription job, final Properties params);

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
    boolean createCronJob(final String cronExpression, final JobDescription job, final Properties params, final boolean unschedule);

    /**
     * Removes a Job from the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was deleted, false otherwise
     */
    boolean deleteJob(final String jobName, final String jobGroup);

    /**
     * Pauses a Job with the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was paused, false otherwise
     */
    boolean pauseJob(final String jobName, final String jobGroup);

    /**
     * Resume a Job with the Scheduler.
     *
     * @param   jobName   The name of the Job
     * @param   jobGroup  The group that the Job was Scheduled in
     *
     * @return  true if the job was resumed, false otherwise
     */
    boolean resumeJob(final String jobName, final String jobGroup);

    /**
     * Gets the names of the Job groups.
     *
     * @return  List of the Job group names
     */
    List<String> getJobGroupNames();

    /**
     * Gets information about currently Scheduled Jobs.
     *
     * @return List of ScheduledJobInfo
     */
    List<ScheduledJobInfo> getScheduledJobs();

    /**
     * Gets information about currently Executing Jobs.
     *
     * @return  An array of ScheduledJobInfo
     */
    ScheduledJobInfo[] getExecutingJobs();

    /**
     * Set's up all the jobs that are listed in conf.xml and loaded through org.exist.util.Configuration.
     */
    void setupConfiguredJobs();
}