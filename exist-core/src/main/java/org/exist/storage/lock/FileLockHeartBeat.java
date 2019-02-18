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
package org.exist.storage.lock;

import org.exist.scheduler.JobDescription;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.IOException;
import java.util.Map;

/**
 * Provides a Scheduled HeartBeat for the FileLock
 */
public class FileLockHeartBeat implements JobDescription, Job {

    private final String JOB_NAME;

    public FileLockHeartBeat() {
        JOB_NAME = "FileLockHeartBeat";
    }

    public FileLockHeartBeat(final String lockName) {
        JOB_NAME = "FileLockHeartBeat: " + lockName;
    }

    @Override
    public String getName() {
        return JOB_NAME;
    }

    @Override
    public void setName(final String name) {
        //Nothing to do
    }

    @Override
    public String getGroup() {
        return EXIST_INTERNAL_GROUP;
    }

    @Override
    public void execute(final JobExecutionContext jobExecutionContext) throws JobExecutionException {
        //get the file lock
        final JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        final Map<String, FileLock> params = (Map<String, FileLock>) jobDataMap.get(PARAMS);
        final FileLock lock = params.get(FileLock.class.getName());
        if(lock != null) {
            try {
                lock.save();
            } catch(final IOException e) {
                lock.message("Caught exception while trying to write lock file", e);
            }
        } else {
            //abort this job
            final JobExecutionException jat = new JobExecutionException("Unable to write heart-beat: lock was null");
            jat.setUnscheduleFiringTrigger(true);
        }
    }
}
