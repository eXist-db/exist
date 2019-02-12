package org.exist.management;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;

public class TaskStatus implements Serializable {

    private static final long serialVersionUID = -8405783622910875893L;

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
        return toString() + percentageInfo;
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
