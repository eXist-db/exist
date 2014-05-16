/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2007 The eXist Project
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

    private String JOB_NAME = "FileLockHeartBeat";

    public FileLockHeartBeat() {
        //Nothing to do
    }

    public FileLockHeartBeat(String lockName) {
        JOB_NAME += ": " + lockName;
    }

    public String getName() {
        return JOB_NAME;
    }

    public void setName(String name) {
        //Nothing to do
    }

    public String getGroup() {
        return "eXist.internal";
    }

    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        //get the file lock
        final JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        final Map<String, FileLock> params = (Map<String, FileLock>) jobDataMap.get("params");
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
