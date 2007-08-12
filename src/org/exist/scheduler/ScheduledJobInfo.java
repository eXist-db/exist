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

import java.util.Date;

import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * Information about a Scheduled Job
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class ScheduledJobInfo
{
	private Scheduler scheduler = null;
	private Trigger trigger = null; 
	
	public final static int TRIGGER_STATE_ERROR = -1;
	public final static int TRIGGER_STATE_NONE = 0;
    public final static int TRIGGER_STATE_NORMAL = 1;
    public final static int TRIGGER_STATE_PAUSED = 2;
    public final static int TRIGGER_STATE_BLOCKED = 3;
    public final static int TRIGGER_STATE_COMPLETE = 4;
	
	public ScheduledJobInfo(Scheduler scheduler, Trigger trigger)
	{
		this.scheduler = scheduler;
		this.trigger = trigger;
	}
	
	/**
	 * Get the Job's Name
	 * 
	 * @return the Job's Name
	 */
	public String getName()
	{
		return trigger.getJobName();
	}
	
	/**
	 * Get the Job's Group
	 * 
	 * @return the Job's Group
	 */
	public String getGroup()
	{
		return trigger.getJobGroup();
	}
	
	/**
	 * Get the Name of the Job's Trigger
	 * 
	 * @return the Name of the Job's Trigger
	 */
	public String getTriggerName()
	{
		return trigger.getName();
	}
	
	/**
	 * Get the Start time of the Job
	 * 
	 * @return the Start time of the Job
	 */
	public Date getStartTime()
	{
		return trigger.getStartTime();
	}
	
	/**
	 * Get the End time of the Job
	 * 
	 * @return the End time of the Job, or null of the job is Scheduled forever
	 */
	public Date getEndTime()
	{
		return trigger.getEndTime();
	}
	
	/**
	 * Get the Previous Fired time of the Job
	 * 
	 * @return the time the Job was Previously Fired, or null if the job hasnt fired yet
	 */
	public Date getPreviousFireTime()
	{
		return trigger.getPreviousFireTime();
	}

	/**
	 * Get the Time the Job will Next be Fired
	 * 
	 * @return the time the Job will Next be Fired, or null if the job wont fire again
	 */
	public Date getNextFireTime()
	{
		return trigger.getNextFireTime();
	}
	
	/**
	 * Get the Final Time the Job will be Fired
	 * 
	 * @return the time the Job will be Fired for the Final time, or null if the job is Scheduled forever
	 */
	public Date getFinalFireTime()
	{
		return trigger.getFinalFireTime();
	}

	/**
	 * Get the Expression that was used to configure the Triggers firing pattern
	 * 
	 * @return The expression that was used to configure the Triggers firing pattern
	 */
	public String getTriggerExpression()
	{
		if(trigger instanceof CronTrigger)
		{
			return ((CronTrigger)trigger).getCronExpression();
		}
		else if(trigger instanceof SimpleTrigger)
		{
			return String.valueOf(((SimpleTrigger)trigger).getRepeatInterval());
		}
		
		return null;
	}
	
	/**
	 * Get the State of the Job's Trigger
	 * 
	 * @return the TRIGGER_STATE_*
	 */
	public int getTriggerState()
	{
		try
		{
			switch(scheduler.getTriggerState(trigger.getName(), trigger.getGroup()))
			{
				case Trigger.STATE_ERROR:
					return TRIGGER_STATE_ERROR;
					
				case Trigger.STATE_NONE:
					return TRIGGER_STATE_NONE;
					
				case Trigger.STATE_NORMAL:
					return TRIGGER_STATE_NORMAL;
					
				case Trigger.STATE_PAUSED:
					return TRIGGER_STATE_PAUSED;
					
				case Trigger.STATE_BLOCKED:
					return TRIGGER_STATE_BLOCKED;
					
				case Trigger.STATE_COMPLETE:
					return TRIGGER_STATE_COMPLETE;
				
				default:
					return TRIGGER_STATE_ERROR;
			}
		}
		catch(SchedulerException se)
		{
			return TRIGGER_STATE_ERROR;
		}
	}
}
