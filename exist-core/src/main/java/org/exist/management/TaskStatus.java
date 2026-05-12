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
package org.exist.management;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the current status of a background task, including its state,
 * the time the state last changed, an optional reason object, and a
 * percentage-complete indicator.
 */
public class TaskStatus {

    private Status status = Status.NA;
    private Date _statusChangeTime = Calendar.getInstance().getTime();
    private Object _reason = null;
    private int _percentageDone = 0;
    /**
     * Create a new TaskStatus with the given initial status.
     *
     * @param newStatus the initial status
     */
    public TaskStatus(final Status newStatus) {
        setStatus(newStatus);
    }

    /**
     * Reconstruct a TaskStatus from JMX composite data.
     *
     * @param compositeData the composite data previously produced by {@link #getCompositeData()}
     * @return the reconstructed TaskStatus
     */
    public static TaskStatus getTaskStatus(final CompositeDataSupport compositeData) {

        final TaskStatus status = new TaskStatus((Status) compositeData.get("status"));
        status._reason = compositeData.get("reason");
        status._statusChangeTime = (Date) compositeData.get("statusChangeTime");
        status._percentageDone = ((Integer) compositeData.get("percentage"));
        return status;
    }

    /**
     * Get the reason object associated with the current status.
     *
     * @return the reason, or {@code null} if none has been set
     */
    public Object getReason() {
        return _reason;
    }

    /**
     * Set the reason object associated with the current status.
     *
     * @param reason the reason object (ignored if {@code null})
     */
    public void setReason(final Object reason) {
        if (reason != null) {
            _reason = reason;
        }
    }

    /**
     * Get the current status.
     *
     * @return the current status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Set the current status.
     *
     * @param newStatus the new status
     */
    public void setStatus(final Status newStatus) {
        status = newStatus;
    }

    /**
     * Get a human-readable representation of the current status, including
     * the percentage done when applicable.
     *
     * @return the status string
     */
    public String getStatusString() {
        String percentageInfo = "";
        switch (status) {
            case INIT:
            case NA:
            case NEVER_RUN:
            case STOPPED_OK:
            case PING_ERROR:
            case PING_OK:
            case PING_WAIT:
                break;
            default:
                percentageInfo = " - " + _percentageDone + "% done";
                break;
        }
        return this + percentageInfo;
    }

    /**
     * Get the time at which the status last changed.
     *
     * @return the status-change timestamp
     */
    public Date getStatusChangeTime() {
        return _statusChangeTime;
    }

    /**
     * Record the current time as the status-change timestamp.
     */
    public void setStatusChangeTime() {
        _statusChangeTime = Calendar.getInstance().getTime();
    }

    /**
     * Get the percentage of work completed.
     *
     * @return percentage done (0–100)
     */
    public int getPercentage() {
        return _percentageDone;
    }

    /**
     * Set the percentage of work completed.
     * Values outside the range 1–100 are silently ignored.
     *
     * @param percentage the percentage done (1–100)
     */
    public void setPercentage(final int percentage) {
        if (percentage > 0 && percentage < 101) {
            _percentageDone = percentage;
        }
    }

    /**
     * Serialise this TaskStatus as JMX {@link CompositeDataSupport}.
     *
     * @return the composite data representation, or {@code null} if serialisation fails
     */
    public CompositeDataSupport getCompositeData() {
        final Map<String, Object> data = new HashMap<>();
        CompositeDataSupport compositeData = null;
        data.put("status", status);
        data.put("statusChangeTime", _statusChangeTime);
        data.put("reason", _reason);
        data.put("percentage", _percentageDone);
        try {
            compositeData = new CompositeDataSupport(new CompositeType("TaskStatus", "Status of the task", //
                    new String[]{"status", "statusChangeTime", "reason", "percentage"}, //
                    new String[]{"status of the task", "reason for this status", "time when the status has changed",
                            "percentage of work"},//
                    new SimpleType[]{SimpleType.INTEGER, SimpleType.DATE, SimpleType.OBJECTNAME, SimpleType.INTEGER}), data);
        } catch (final OpenDataException e) {
            // TODO TI: Make correct error handling
        }
        return compositeData;
    }

    @Override
    public String toString() {
        return status.toString();
    }

    public enum Status {
        NA, NEVER_RUN, INIT, PAUSED, STOPPED_OK, STOPPED_ERROR, RUNNING_CHECK, RUNNING_BACKUP,
        PING_OK, PING_ERROR, PING_WAIT
    }
}
