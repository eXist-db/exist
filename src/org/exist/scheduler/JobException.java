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

import org.quartz.JobExecutionException;

/**
 * Exception class can be thrown by implementations of
 * org.exist.scheduler.Job
 * 
 * Also provides a mechanism for cleaning up a job after
 * failed execution
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class JobException extends Exception
{
	public final static int JOB_ABORT = 0;			//Abort this job, but continue scheduling
	public final static int JOB_ABORT_THIS = 1;		//Abort this job and cancel this trigger
	public final static int JOB_ABORT_ALL = 2;		//Abort this job and cancel all triggers
	public final static int JOB_REFIRE = 3;			//Refire this job now
	
	private int action = JOB_ABORT;
	private String message;
	
	public JobException(int action, String message)
	{
		super(message);
		
		this.action = action;
		this.message = message;
	}
	
	/**
	 * Should be called after this exception is caught
	 * it cleans up the job, with regards to the scheduler
	 * 
	 * Jobs may be removed, refired immediately or left
	 * for their next execution
	 */
	public void cleanupJob() throws JobExecutionException
	{
		switch(action)
		{
			case JOB_REFIRE:
				throw new JobExecutionException(message, true);
		
			case JOB_ABORT_THIS:
				JobExecutionException jat = new JobExecutionException(message, false); 
				jat.setUnscheduleFiringTrigger(true);
				throw jat;
			
			case JOB_ABORT_ALL:
				JobExecutionException jaa = new JobExecutionException(message, false);
				jaa.setUnscheduleAllTriggers(true);
				throw jaa;
		
			case JOB_ABORT:
			default:
				throw new JobExecutionException(message, false);
		}
	}
}
