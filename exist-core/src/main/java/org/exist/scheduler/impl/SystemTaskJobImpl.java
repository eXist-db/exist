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

import org.exist.scheduler.SystemTaskJob;
import org.exist.storage.BrokerPool;
import org.exist.storage.SystemTask;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;


/**
 * Class to represent a SystemTask Job Can be used by SystemTasks to schedule themselves as job's.
 *
 * SystemTaskJobs may only have a Single Instance running in the scheduler at once, intersecting schedules will be queued.
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class SystemTaskJobImpl implements SystemTaskJob, StatefulJob {
    
    private final static String JOB_GROUP = "eXist.System";
    private String name  = "SystemTask";

    private SystemTask task = null;

    /**
     * Default Constructor for Quartz.
     */
    public SystemTaskJobImpl() {
    }


    /**
     * Constructor for Creating a new SystemTask Job.
     *
     * @param  jobName  DOCUMENT ME!
     * @param  task     DOCUMENT ME!
     */
    public SystemTaskJobImpl(final String jobName, final SystemTask task) {
        this.task = task;

        if(jobName == null) {
            this.name += ": " + task.getClass().getName();
        } else {
            this.name = jobName;
        }
    }

    @Override
    public final String getName() {
        return name;
    }


    @Override
    public final void setName(final String name)
    {
        this.name = name;
    }


    @Override
    public final String getGroup() {
        return JOB_GROUP;
    }


    /**
     * Returns the SystemTask for this Job.
     *
     * @return  The SystemTask for this Job
     */
    protected SystemTask getSystemTask() {
        return task;
    }


    @Override
    public final void execute(final JobExecutionContext jec) throws JobExecutionException {
        final JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();
        final BrokerPool pool = (BrokerPool)jobDataMap.get(DATABASE);
        final SystemTask task = (SystemTask)jobDataMap.get(SYSTEM_TASK);

        //if invalid arguments then abort
        if((pool == null) || (task == null)) {

            //abort all triggers for this job
            final JobExecutionException jaa = new JobExecutionException("SystemTaskJob Failed: BrokerPool or SystemTask was null! Unscheduling SystemTask", false);
            jaa.setUnscheduleAllTriggers( true );
            throw jaa;
        }

        //trigger the system task
        pool.triggerSystemTask(task);
    }
}
