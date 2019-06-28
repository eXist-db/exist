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

import java.util.Date;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * Information about a Scheduled Job.
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ScheduledJobInfo {
    
    public enum TriggerState {
        ERROR,
        NONE,
        NORMAL,
        PAUSED,
        BLOCKED,
        COMPLETE
    }
    
    private final Scheduler scheduler;
    private final Trigger trigger;

    public ScheduledJobInfo(final Scheduler scheduler, final Trigger trigger) {
        this.scheduler = scheduler;
        this.trigger = trigger;
    }

    /**
     * Get the Job's Name.
     *
     * @return  the Job's Name
     */
    public String getName() {
        return trigger.getJobKey().getName();
    }


    /**
     * Get the Job's Group.
     *
     * @return  the Job's Group
     */
    public String getGroup() {
        return trigger.getJobKey().getGroup();
    }


    /**
     * Get the Name of the Job's Trigger.
     *
     * @return  the Name of the Job's Trigger
     */
    public String getTriggerName() {
        return trigger.getKey().getName();
    }


    /**
     * Get the Start time of the Job.
     *
     * @return  the Start time of the Job
     */
    public Date getStartTime() {
        return trigger.getStartTime();
    }


    /**
     * Get the End time of the Job.
     *
     * @return  the End time of the Job, or null of the job is Scheduled forever
     */
    public Date getEndTime() {
        return trigger.getEndTime();
    }


    /**
     * Get the Previous Fired time of the Job.
     *
     * @return  the time the Job was Previously Fired, or null if the job hasnt fired yet
     */
    public Date getPreviousFireTime() {
        return trigger.getPreviousFireTime();
    }


    /**
     * Get the Time the Job will Next be Fired.
     *
     * @return  the time the Job will Next be Fired, or null if the job wont fire again
     */
    public Date getNextFireTime() {
        return trigger.getNextFireTime();
    }


    /**
     * Get the Final Time the Job will be Fired.
     *
     * @return  the time the Job will be Fired for the Final time, or null if the job is Scheduled forever
     */
    public Date getFinalFireTime() {
        return trigger.getFinalFireTime();
    }


    /**
     * Get the Expression that was used to configure the Triggers firing pattern.
     *
     * @return  The expression that was used to configure the Triggers firing pattern
     */
    public String getTriggerExpression(){
        if(trigger instanceof CronTrigger) {
            return ((CronTrigger)trigger).getCronExpression();
        } else if(trigger instanceof SimpleTrigger) {
            return String.valueOf(((SimpleTrigger)trigger).getRepeatInterval());
        }

        return null;
    }

    /**
     * Get the State of the Job's Trigger.
     *
     * @return  the TRIGGER_STATE_*
     */
    public TriggerState getTriggerState() {
        try {
            switch(scheduler.getTriggerState(trigger.getKey())) {

                case ERROR:
                    return TriggerState.ERROR;

                case NONE:
                    return TriggerState.NONE;

                case NORMAL:
                    return TriggerState.NORMAL;

                case PAUSED:
                    return TriggerState.PAUSED;

                case BLOCKED:
                    return TriggerState.BLOCKED;

                case COMPLETE:
                    return TriggerState.COMPLETE;

                default:
                    return TriggerState.ERROR;
            }
        } catch(final SchedulerException se) {
            return TriggerState.ERROR;
        }
    }
}
