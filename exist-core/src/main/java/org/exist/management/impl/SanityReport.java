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
package org.exist.management.impl;

import java.util.*;

import javax.management.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.ErrorReport;
import org.exist.management.TaskStatus;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.ConsistencyCheckTask;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.storage.XQueryPool;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

public class SanityReport extends NotificationBroadcasterSupport implements SanityReportMXBean {

    private final static Logger LOG = LogManager.getLogger(SanityReport.class.getName());

    public final static String STATUS_OK = "OK";
    public final static String STATUS_FAIL = "FAIL";

    public final static StringSource TEST_XQUERY = new StringSource("<r>{current-dateTime()}</r>");

    public final static int PING_WAITING = -1;
    public final static int PING_ERROR = -2;

    private static List<ErrorReport> NO_ERRORS = new LinkedList<>();

    private int seqNum = 0;

    private Date actualCheckStart = null;

    private Date lastCheckStart = null;

    private Date lastCheckEnd = null;

    private String lastActionInfo = "nothing done";

    private long lastPingRespTime = 0;

    private String output = "";

    private TaskStatus taskstatus = new TaskStatus(TaskStatus.Status.NEVER_RUN);

    private List<ErrorReport> errors = NO_ERRORS;

    private BrokerPool pool;

    public SanityReport(BrokerPool pool) {
        this.pool = pool;
    }

    public static String getAllInstancesQuery() {
        return "org.exist.management." + '*' + ":type=SanityReport";
    }

    public static ObjectName getName(final String instanceId) throws MalformedObjectNameException {
        return new ObjectName("org.exist.management." + instanceId + ".tasks:type=SanityReport");
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return getName(pool.getId());
    }

    @Override
    public String getInstanceId() {
        return pool.getId();
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        final String[] types = new String[]{AttributeChangeNotification.ATTRIBUTE_CHANGE};
        final String name = AttributeChangeNotification.class.getName();
        final String description = "The status attribute of this MBean has changed";
        final MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[]{info};
    }

    @Override
    public Date getLastCheckEnd() {
        return lastCheckEnd;
    }

    @Override
    public Date getLastCheckStart() {
        return lastCheckStart;
    }

    @Override
    public Date getActualCheckStart() {
        return actualCheckStart;
    }

    @Override
    public String getStatus() {
        return taskstatus.getStatusString();
    }

    @Override
    public String getLastActionInfo() {
        return lastActionInfo;
    }

    @Override
    public long getPingTime() {
        return lastPingRespTime;
    }

    @Override
    public List<Error> getErrors() {
        final List<Error> errorList = new ArrayList<>();
        for (final ErrorReport error : errors) {
            errorList.add(new Error(error.getErrcodeString(), error.getMessage()));
        }
        return errorList;
    }

    @Override
    public void triggerCheck(String output, String backup, String incremental) {
        try {
            this.output = output;
            final SystemTask task = new ConsistencyCheckTask();
            final Properties properties = parseParameter(output, backup, incremental);
            task.configure(pool.getConfiguration(), properties);
            pool.triggerSystemTask(task);

        } catch (final EXistException existException) {
            taskstatus.setStatus(TaskStatus.Status.STOPPED_ERROR);

            final List<ErrorReport> errors = new ArrayList<>();
            errors.add(
                    new ErrorReport(
                            ErrorReport.CONFIGURATION_FAILD,
                            existException.getMessage(), existException));

            taskstatus.setReason(errors);
            changeStatus(taskstatus);
            taskstatus.setStatusChangeTime();
            taskstatus.setReason(existException.toString());
            LOG.warn("Failed to trigger db sanity check: {}", existException.getMessage(), existException);
        }
    }

    @Override
    public long ping(boolean checkQueryEngine) {
        final long start = System.currentTimeMillis();
        lastPingRespTime = -1;
        lastActionInfo = "Ping";

        taskstatus.setStatus(TaskStatus.Status.PING_WAIT);

        // try to acquire a broker. If the db is deadlocked or not responsive,
        // this will block forever.
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getGuestSubject()))) {

            if (checkQueryEngine) {
                final XQuery xquery = pool.getXQueryService();
                final XQueryPool xqPool = pool.getXQueryPool();
                CompiledXQuery compiled = xqPool.borrowCompiledXQuery(broker, TEST_XQUERY);
                if (compiled == null) {
                    final XQueryContext context = new XQueryContext(pool);
                    compiled = xquery.compile(context, TEST_XQUERY);
                } else {
                    compiled.getContext().prepareForReuse();
                }
                try {
                    xquery.execute(broker, compiled, null);
                } finally {
                    compiled.getContext().runCleanupTasks();
                    xqPool.returnCompiledXQuery(TEST_XQUERY, compiled);
                }
            }
        } catch (final Exception e) {
            lastPingRespTime = -2;
            taskstatus.setStatus(TaskStatus.Status.PING_ERROR);
            taskstatus.setStatusChangeTime();
            taskstatus.setReason(e.getMessage());
            changeStatus(taskstatus);

        } finally {
            lastPingRespTime = System.currentTimeMillis() - start;
            taskstatus.setStatus(TaskStatus.Status.PING_OK);
            taskstatus.setStatusChangeTime();
            taskstatus.setReason("ping response time: " + lastPingRespTime);
            changeStatus(taskstatus);
        }
        return lastPingRespTime;
    }

    private Properties parseParameter(String output, String backup, String incremental) {
        final Properties properties = new Properties();
        final boolean doBackup = "YES".equalsIgnoreCase(backup);

        // This should be simplified
        if (backup != null && (doBackup) || "no".equalsIgnoreCase(backup)) {
            properties.put("backup", backup);
        }

        if (incremental != null && ("YES".equalsIgnoreCase(incremental) || "no".equalsIgnoreCase(incremental))) {
            properties.put("incremental", incremental);
        }

        if (output != null) {
            properties.put("output", output);
        } else {
            properties.put("backup", "no");
        }

        return properties;
    }

    protected void updateErrors(List<ErrorReport> errorList) {
        try {
            if (errorList == null || errorList.isEmpty()) {
                taskstatus.setStatus(TaskStatus.Status.STOPPED_OK);
                this.errors = NO_ERRORS;
            } else {
                this.errors = errorList;
                taskstatus.setStatus(TaskStatus.Status.STOPPED_ERROR);
            }
        } catch (final Exception e) {
            // ignore
        }

    }

    protected void changeStatus(TaskStatus status) {
        status.setStatusChangeTime();
        switch (status.getStatus()) {
            case INIT:
                actualCheckStart = status.getStatusChangeTime();
                break;
            case STOPPED_ERROR:
            case STOPPED_OK:
                lastCheckStart = actualCheckStart;
                actualCheckStart = null;
                lastCheckEnd = status.getStatusChangeTime();
                if (status.getReason() != null) {
                    this.errors = (List<ErrorReport>) status.getReason();
                }
                lastActionInfo = taskstatus.toString() + " to [" + output + "] ended with status [" + status + "]";
                break;
            default:
                break;
        }

        final TaskStatus oldState = taskstatus;
        try {
            taskstatus = status;
            final Notification event = new AttributeChangeNotification(this, seqNum++, taskstatus.getStatusChangeTime().getTime(),
                    "Status change", "status", "String", oldState.toString(), taskstatus.toString());
            event.setUserData(taskstatus.getCompositeData());
            sendNotification(event);
        } catch (final Exception e) {
            // ignore
        }
    }

    protected void updateStatus(int percentage) {
        try {
            final int oldPercentage = taskstatus.getPercentage();
            taskstatus.setPercentage(percentage);
            final Notification event = new AttributeChangeNotification(this, seqNum++, taskstatus.getStatusChangeTime().getTime(),
                    "Work percentage change", "status", "int", String.valueOf(oldPercentage), String.valueOf(taskstatus
                    .getPercentage()));
            event.setUserData(taskstatus.getCompositeData());
            sendNotification(event);
        } catch (final Exception e) {
            // ignore
        }
    }
}
