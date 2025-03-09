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

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;

public class TaskStatus {

    public enum Status {
        NA, NEVER_RUN, INIT, PAUSED, STOPPED_OK, STOPPED_ERROR, RUNNING_CHECK, RUNNING_BACKUP,
        PING_OK, PING_ERROR, PING_WAIT
    }

    private Status status = Status.NA;

    private Date _statusChangeTime = Calendar.getInstance().getTime();
    private Object _reason = null;
    private int _percentageDone = 0;

    public TaskStatus(Status newStatus) {
        setStatus(newStatus);
    }

    public Object getReason() {
        return _reason;
    }

    public void setReason(Object reason) {
        if (reason != null) {
            _reason = reason;
        }
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status newStatus) {
        status=newStatus;
    }

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

    public Date getStatusChangeTime() {
        return _statusChangeTime;
    }

    public void setStatusChangeTime() {
        _statusChangeTime = Calendar.getInstance().getTime();
    }

    public void setPercentage(int percentage) {
        if (percentage > 0 && percentage < 101) {
            _percentageDone = percentage;
        }
    }

    public int getPercentage() {
        return _percentageDone;
    }

    public CompositeDataSupport getCompositeData() {
        final Map<String, Object> data = new HashMap<>();
        CompositeDataSupport compositeData = null;
        data.put("status", status);
        data.put("statusChangeTime", _statusChangeTime);
        data.put("reason", _reason);
        data.put("percentage", _percentageDone);
        try {
            compositeData = new CompositeDataSupport(new CompositeType("TaskStatus", "Status of the task", //
                    new String[] { "status", "statusChangeTime", "reason", "percentage" }, //
                    new String[] { "status of the task", "reason for this status", "time when the status has changed",
                            "percentage of work" },//
                    new SimpleType[] { SimpleType.INTEGER, SimpleType.DATE, SimpleType.OBJECTNAME, SimpleType.INTEGER }), data);
        } catch (final OpenDataException e) {
            // TODO TI: Make correct error handling
        }
        return compositeData;
    }

    public static TaskStatus getTaskStatus(CompositeDataSupport compositeData) {

        final TaskStatus status = new TaskStatus((Status)compositeData.get("status"));
        status._reason = compositeData.get("reason");
        status._statusChangeTime = (Date) compositeData.get("statusChangeTime");
        status._percentageDone = ((Integer) compositeData.get("percentage"));
        return status;
    }

    @Override
    public String toString() {
        return status.toString();
    }
}
