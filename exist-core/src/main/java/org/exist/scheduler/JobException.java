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

import org.quartz.JobExecutionException;

/**
 * Exception class can be thrown by implementations of org.exist.scheduler.Job.
 *
 * Also provides a mechanism for cleaning up a job after failed execution
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class JobException extends Exception {
    
    private static final long serialVersionUID = 1567438994821964637L;

    public enum JobExceptionAction {
        JOB_ABORT, //Abort this job, but continue scheduling
        JOB_ABORT_THIS, //Abort this job and cancel this trigger
        JOB_ABORT_ALL, //Abort this job and cancel all triggers
        JOB_REFIRE //Refire this job now
    }
    
    private final JobExceptionAction jobExceptionAction;

    public JobException(final JobExceptionAction jobExceptionAction, final String message ) {
        super(message);

        this.jobExceptionAction = jobExceptionAction;
    }

    /**
     * Should be called after this exception is caught it cleans up the job, with regards to the scheduler.
     *
     * Jobs may be removed, re-fired immediately or left for their next execution
     *
     * @throws  JobExecutionException  DOCUMENT ME!
     */
    public void cleanupJob() throws JobExecutionException {
        switch(jobExceptionAction) {

            case JOB_REFIRE:
                throw new JobExecutionException(getMessage(), true);

            case JOB_ABORT_THIS:
                final JobExecutionException jat = new JobExecutionException(getMessage(), false);
                jat.setUnscheduleFiringTrigger(true);
                throw jat;

            case JOB_ABORT_ALL:
                final JobExecutionException jaa = new JobExecutionException(getMessage(), false);
                jaa.setUnscheduleAllTriggers(true);
                throw jaa;

            case JOB_ABORT:
            default:
                throw new JobExecutionException(getMessage(), false);
        }
    }
}
