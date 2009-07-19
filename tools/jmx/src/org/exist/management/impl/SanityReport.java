/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *
 * $Id$
 */
package org.exist.management.impl;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.ErrorReport;
import org.exist.management.TaskStatus;
import org.exist.storage.BrokerPool;
import org.exist.storage.ConsistencyCheckTask;
import org.exist.storage.SystemTask;

public class SanityReport extends NotificationBroadcasterSupport implements SanityReportMBean {

    private final static Logger LOG = Logger.getLogger(SanityReport.class.getName());

    public final static String STATUS_OK = "OK";
    public final static String STATUS_FAIL = "FAIL";

    private static String[] itemNames = { "errcode", "description" };
    private static String[] itemDescriptions = { "Error code", "Description of the error" };
    private static String[] indexNames = { "errcode" };

    private static List NO_ERRORS = new LinkedList();

    private int seqNum = 0;

    private Date actualCheckStart = null;

    private Date lastCheckStart = null;

    private Date lastCheckEnd = null;

    private String lastActionInfo = "nothing done";

    private String output = "";

    private TaskStatus taskstatus = new TaskStatus(TaskStatus.NEVER_RUN);

    private List errors = NO_ERRORS;

    private BrokerPool pool;

    public SanityReport(BrokerPool pool) {
        this.pool = pool;
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        String[] types = new String[] { AttributeChangeNotification.ATTRIBUTE_CHANGE };
        String name = AttributeChangeNotification.class.getName();
        String description = "The status attribute of this MBean has changed";
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[] { info };
    }

    public Date getLastCheckEnd() {
        return lastCheckEnd;
    }

    public Date getLastCheckStart() {
        return lastCheckStart;
    }

    public Date getActualCheckStart() {
        return actualCheckStart;
    }

    public String getStatus() {
        return taskstatus.getStatusString();
    }

    public String getLastActionInfo() {
        return lastActionInfo;
    }

    public TabularData getErrors() {
        OpenType[] itemTypes = { SimpleType.STRING, SimpleType.STRING };
        CompositeType infoType;
        try {
            infoType = new CompositeType("errorInfo", "Provides information on a consistency check error", itemNames,
                    itemDescriptions, itemTypes);
            TabularType tabularType = new TabularType("errorList", "List of consistency check errors", infoType, indexNames);
            TabularDataSupport data = new TabularDataSupport(tabularType);
            for (int i = 0; i < errors.size(); i++) {
                ErrorReport error = (ErrorReport) errors.get(i);
                Object[] itemValues = { error.getErrcodeString(), error.getMessage() };
                data.put(new CompositeDataSupport(infoType, itemNames, itemValues));
            }
            return data;
        } catch (OpenDataException e) {
            LOG.warn(e.getMessage(), e);
            return null;
        }
    }

    public void triggerCheck(String output, String backup, String incremental) {
        try {
            this.output = output;
            SystemTask task = new ConsistencyCheckTask();
            Properties properties = parseParameter(output, backup, incremental);
            task.configure(pool.getConfiguration(), properties);
            pool.triggerSystemTask(task);
        } catch (EXistException existException) {
            taskstatus.setStatus(TaskStatus.STOPPED_ERROR);
            taskstatus.setReason(existException.toString());
            changeStatus(taskstatus);
            taskstatus.setStatusChangeTime();
            taskstatus.setReason(existException.toString());
            LOG.warn("Failed to trigger db sanity check: " + existException.getMessage(), existException);
        }
    }

    private Properties parseParameter(String output, String backup, String incremental) {
        Properties properties = new Properties();
        final boolean doBackup = backup.equalsIgnoreCase("YES");
        if (backup != null && (doBackup) || backup.equalsIgnoreCase("no")) {
            properties.put("backup", backup);
        }
        if (incremental != null && (incremental.equalsIgnoreCase("YES") || incremental.equalsIgnoreCase("no"))) {
            properties.put("incremental", incremental);
        }
        if (output != null) {
            properties.put("output", output);
        } else {
            properties.put("backup", "no");
        }
        return properties;
    }

    protected void updateErrors(List errorList) {
        try {
            if (errorList == null || errorList.isEmpty()) {
                taskstatus.setStatus(TaskStatus.STOPPED_OK);
                this.errors = NO_ERRORS;
            } else {
                this.errors = errorList;
                taskstatus.setStatus(TaskStatus.STOPPED_ERROR);
            }
        } catch (Exception e) {
            // ignore
        }

    }

    protected void changeStatus(TaskStatus status) {
        status.setStatusChangeTime();
        switch (status.getStatus()) {
        case TaskStatus.INIT:
            actualCheckStart = status.getStatusChangeTime();
            break;
        case TaskStatus.STOPPED_ERROR:
        case TaskStatus.STOPPED_OK:
            lastCheckStart = actualCheckStart;
            actualCheckStart = null;
            lastCheckEnd = status.getStatusChangeTime();
            if (status.getReason() != null) {
                this.errors = (List) status.getReason();
            }
            lastActionInfo = taskstatus.toString() + " to [" + output + "] ended with status [" + status.toString() + "]";
            break;
        default:
            break;
        }
        TaskStatus oldState = taskstatus;
        try {
            taskstatus = status;
            Notification event = new AttributeChangeNotification(this, seqNum++, taskstatus.getStatusChangeTime().getTime(),
                    "Status change", "status", "String", oldState.toString(), taskstatus.toString());
            event.setUserData(taskstatus.getCompositeData());
            sendNotification(event);
        } catch (Exception e) {
            // ignore
        }
    }

    protected void updateStatus(int percentage) {
        try {
            int oldPercentage = taskstatus.getPercentage();
            taskstatus.setPercentage(percentage);
            Notification event = new AttributeChangeNotification(this, seqNum++, taskstatus.getStatusChangeTime().getTime(),
                    "Work percentage change", "status", "int", String.valueOf(oldPercentage), String.valueOf(taskstatus
                            .getPercentage()));
            event.setUserData(taskstatus.getCompositeData());
            sendNotification(event);
        } catch (Exception e) {
            // ignore
        }
    }
}
