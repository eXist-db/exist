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
    public static final int NA = 0;
    public static final int NEVER_RUN = 1;
    public static final int INIT = 2;
    public static final int PAUSED = 3;
    public static final int STOPPED_OK = 4;
    public static final int STOPPED_ERROR = 5;
    public static final int RUNNING_CHECK = 6;
    public static final int RUNNING_BACKUP = 7;

    private static final String[] STATUS_STRINGS = {
    //
            "NA",
            //
            "NEVER_RUN",
            //
            "INIT",
            //
            "PAUSED",
            //
            "STOPPED_OK",
            //
            "STOPPED_ERROR",
            //
            "RUNNING_CHECK",
            //
            "RUNNING_BACKUP" };

    private int _status = 0;
    private Date _statusChangeTime = Calendar.getInstance().getTime();
    private Object _reason = null;
    private int _percentageDone = 0;

    public TaskStatus(int status) {
        setStatus(status);
    }

    public Object getReason() {
        return _reason;
    }

    public void setReason(Object reason) {
        if (reason != null) {
            _reason = reason;
        }
    }

    public int getStatus() {
        return _status;
    }

    public void setStatus(int status) {
        if (status > 0 && status < STATUS_STRINGS.length) {
            _status = status;
        }
    }

    public String getStatusString() {
        String percentageInfo = "";
        switch (_status) {
        case INIT:
        case NA:
        case NEVER_RUN:
        case STOPPED_OK:
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
        Map data = new HashMap();
        CompositeDataSupport compositeData = null;
        data.put("status", new Integer(_status));
        data.put("statusChangeTime", _statusChangeTime);
        data.put("reason", _reason);
        data.put("percentage", Integer.valueOf(_percentageDone));
        try {
            compositeData = new CompositeDataSupport(new CompositeType("TaskStatus", "Status of the task", //
                    new String[] { "status", "statusChangeTime", "reason", "percentage" }, //
                    new String[] { "status of the task", "reason for this status", "time when the status has changed",
                            "percentage of work" },//
                    new SimpleType[] { SimpleType.INTEGER, SimpleType.DATE, SimpleType.OBJECTNAME, SimpleType.INTEGER }), data);
        } catch (OpenDataException e) {
            // TODO TI: Make correct error handling
        }
        return compositeData;
    }

    public static TaskStatus getTaskStatus(CompositeDataSupport compositeData) {
        TaskStatus status = new TaskStatus(((Integer) compositeData.get("status")).intValue());
        status._reason = compositeData.get("reason");
        status._statusChangeTime = (Date) compositeData.get("statusChangeTime");
        status._percentageDone = ((Integer) compositeData.get("percentage")).intValue();
        return status;
    }

    public String toString() {
        return STATUS_STRINGS[_status];
    }
}
