/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.scheduler;

import java.util.Objects;
import java.util.Properties;
import org.exist.util.Configuration;
import org.exist.scheduler.JobException.JobExceptionAction;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public final class JobConfig {

    /** start conf.xml names **/
    public static final String CONFIGURATION_ELEMENT_NAME = "scheduler";
    public static final String CONFIGURATION_JOB_ELEMENT_NAME = "job";
    public static final String JOB_TYPE_ATTRIBUTE = "type";
    public static final String JOB_CLASS_ATTRIBUTE  = "class";
    public static final String JOB_XQUERY_ATTRIBUTE = "xquery";
    public static final String JOB_CRON_TRIGGER_ATTRIBUTE = "cron-trigger";
    public static final String JOB_PERIOD_ATTRIBUTE = "period";
    public static final String JOB_DELAY_ATTRIBUTE = "delay";
    public static final String JOB_REPEAT_ATTRIBUTE = "repeat";
    public static final String JOB_NAME_ATTRIBUTE = "name";
    public static final String JOB_UNSCHEDULE_ON_EXCEPTION = "unschedule-on-exception";
    public static final String PROPERTY_SCHEDULER_JOBS = "scheduler.jobs";
    /** end conf.xml names **/
    
    
    private final JobType jobType;
    private final String jobName;
    private final String resourceName;
    private final String schedule;
    private final boolean unscheduleOnException;

    private long delay = -1;
    private int repeat = -1; //repeat indefinetly

    private final Properties parameters = new Properties();

    public JobConfig(final JobType jobType, final String jobName, final String resourceName, final String schedule, final String unscheduleOnException) throws JobException {
        this.jobType = Objects.requireNonNullElse(jobType, JobType.USER);

        this.jobName = jobName;

        if(resourceName != null) {
            this.resourceName = resourceName;
        } else {
            throw(new JobException(JobExceptionAction.JOB_ABORT, "Job must have a resource for execution"));
        }

        if(schedule == null) {
            throw(new JobException(JobExceptionAction.JOB_ABORT, "Job must have a schedule"));
        } else {
            this.schedule = schedule;
        }

        this.unscheduleOnException = Configuration.parseBoolean(unscheduleOnException, true);
    }

    public JobType getType() {
        return jobType;
    }

    public String getJobName() {
        return jobName;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getSchedule() {
        return schedule;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(final long delay) {
        this.delay = delay;
    }

    public int getRepeat() {
        return repeat;
    }

    public void setRepeat(final int repeat) {
        this.repeat = repeat;
    }

    public void addParameter(final String name, final String value) {
        parameters.put(name, value);
    }

    public Properties getParameters() {
        return parameters;
    }

    public boolean unscheduleOnException() {
        return unscheduleOnException;
    }
}