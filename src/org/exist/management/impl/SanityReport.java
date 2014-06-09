/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-10 The eXist Project
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

import java.util.ArrayList;
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
import org.exist.security.xacml.AccessContext;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.ConsistencyCheckTask;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.storage.XQueryPool;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

public class SanityReport extends NotificationBroadcasterSupport implements SanityReportMBean {

    private final static Logger LOG = Logger.getLogger(SanityReport.class.getName());

    public final static String STATUS_OK = "OK";
    public final static String STATUS_FAIL = "FAIL";

    public final static StringSource TEST_XQUERY = new StringSource("<r>{current-dateTime()}</r>");
    
    public final static int PING_WAITING = -1;
    public final static int PING_ERROR = -2;
    
    private static String[] itemNames = { "errcode", "description" };
    private static String[] itemDescriptions = { "Error code", "Description of the error" };
    private static String[] indexNames = { "errcode" };

    private static List<ErrorReport> NO_ERRORS = new LinkedList<ErrorReport>();

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

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        final String[] types = new String[] { AttributeChangeNotification.ATTRIBUTE_CHANGE };
        final String name = AttributeChangeNotification.class.getName();
        final String description = "The status attribute of this MBean has changed";
        final MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[] { info };
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
    public TabularData getErrors() {
        final OpenType<?>[] itemTypes = { SimpleType.STRING, SimpleType.STRING };
        CompositeType infoType;
        try {
            infoType = new CompositeType("errorInfo", "Provides information on a consistency check error", itemNames,
                    itemDescriptions, itemTypes);
            final TabularType tabularType = new TabularType("errorList", "List of consistency check errors", infoType, indexNames);
            final TabularDataSupport data = new TabularDataSupport(tabularType);
            for (final ErrorReport error : errors) {
                final Object[] itemValues = { error.getErrcodeString(), error.getMessage() };
                data.put(new CompositeDataSupport(infoType, itemNames, itemValues));
            }
            return data;

        } catch (final OpenDataException e) {
            LOG.warn(e.getMessage(), e);
            return null;
        }
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
            LOG.warn("Failed to trigger db sanity check: " + existException.getMessage(), existException);
        }
    }

    @Override
    public long ping(boolean checkQueryEngine) {
    	final long start = System.currentTimeMillis();
    	lastPingRespTime = -1;
    	lastActionInfo = "Ping";
    	
    	taskstatus.setStatus(TaskStatus.Status.PING_WAIT);
    	
    	DBBroker broker = null;
    	try {
    		// try to acquire a broker. If the db is deadlocked or not responsive,
    		// this will block forever.
    		broker = pool.get(pool.getSecurityManager().getGuestSubject());
    		
    		if (checkQueryEngine) {
    			final XQuery xquery = broker.getXQueryService();
    			final XQueryPool xqPool = xquery.getXQueryPool();
    			CompiledXQuery compiled = xqPool.borrowCompiledXQuery(broker, TEST_XQUERY);
    			if (compiled == null) {
    				final XQueryContext context = xquery.newContext(AccessContext.TEST);
    				compiled = xquery.compile(context, TEST_XQUERY);
    			}
				try {
					xquery.execute(compiled, null);
				} finally {
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
    		pool.release(broker);
    		
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
        final boolean doBackup = backup.equalsIgnoreCase("YES");

        // This should be simplified
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
                lastActionInfo = taskstatus.toString() + " to [" + output + "] ended with status [" + status.toString() + "]";
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
