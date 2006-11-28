package org.exist.scheduler;

import java.util.Date;

import org.quartz.Trigger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

public class ScheduledJobInfo
{
	private Scheduler scheduler = null;
	private Trigger trigger = null; 
	
	private final static int TRIGGER_STATE_ERROR = -1;
	private final static int TRIGGER_STATE_NONE = 0;
    private final static int TRIGGER_STATE_NORMAL = 1;
    private final static int TRIGGER_STATE_PAUSED = 2;
    private final static int TRIGGER_STATE_BLOCKED = 3;
    private final static int TRIGGER_STATE_COMPLETE = 4;
	
	public ScheduledJobInfo(Scheduler scheduler, Trigger trigger)
	{
		this.scheduler = scheduler;
		this.trigger = trigger;
	}
	
	public String getName()
	{
		return trigger.getJobName();
	}
	
	public String getGroup()
	{
		return trigger.getJobGroup();
	}
	
	public String getTriggerName()
	{
		return trigger.getName();
	}
	
	public Date getStartTime()
	{
		return trigger.getStartTime();
	}
	
	public Date getEndTime()
	{
		return trigger.getEndTime();
	}
	
	public Date getPreviousFireTime()
	{
		return trigger.getPreviousFireTime();
	}
	
	public Date getNextFireTime()
	{
		return trigger.getNextFireTime();
	}
	
	public Date getFinalFireTime()
	{
		return trigger.getNextFireTime();
	}
	
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
