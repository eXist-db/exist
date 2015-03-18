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

import java.util.List;
import org.exist.scheduler.ScheduledJobInfo;
import org.exist.scheduler.Scheduler;
import org.exist.storage.BrokerPool;
import org.exist.storage.ProcessMonitor;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.openmbean.TabularData;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.CompositeDataSupport;
import org.exist.storage.ProcessMonitor.QueryHistory;

public class ProcessReport implements ProcessReportMBean {

    private final static Logger LOG = LogManager.getLogger(ProcessReport.class);

    private static String[] pItemNames = { "id", "action", "info" };
    private static String[] pItemDescriptions = {
        "Process ID",
        "Description of the current action",
        "Additional info provided by thread"
    };
    private static String[] pIndexNames = { "id" };


    private static String[] qItemNames = { "id", "sourceType", "sourceKey", "terminating" };
    private static String[] qItemDescriptions = {
        "XQuery ID",
        "Type of the query source",
        "Description of the source",
        "Is query terminating?"
    };
    private static String[] qIndexNames = { "id" };

    private static String[] qhItemNames = { "sourceKey", "recentInvocationCount", "mostRecentExecutionTime", "mostRecentExecutionDuration" };
    private static String[] qhItemDescriptions = {
        "Description of the source",
        "Recent invocation count",
        "Most recent query invocation start time",
        "Most recent query invocation duration",
    };
    private static String[] qhIndexNames = { "sourceKey" };
    

    private ProcessMonitor processMonitor;

    private Scheduler scheduler;

    public ProcessReport(BrokerPool pool) {
        processMonitor = pool.getProcessMonitor();
        scheduler = pool.getScheduler();
    }

    @Override
    public TabularData getScheduledJobs() {
        final OpenType<?>[] itemTypes = { SimpleType.STRING, SimpleType.STRING, SimpleType.STRING };
        CompositeType infoType;
        try {
            infoType = new CompositeType("scheduledJobs", "Lists currently scheduled jobs in eXist",
                    pItemNames, pItemDescriptions, itemTypes);
            final TabularType tabularType = new TabularType("jobList", "List of currently scheduled jobs", infoType, pIndexNames);
            final TabularDataSupport data = new TabularDataSupport(tabularType);
            final List<ScheduledJobInfo> jobs = scheduler.getScheduledJobs();
            for (final ScheduledJobInfo job : jobs) {
                final Object[] itemValues = { job.getName(), job.getGroup(),
                        job.getTriggerExpression() };
                data.put(new CompositeDataSupport(infoType, pItemNames, itemValues));
            }
            return data;
        } catch (final OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public TabularData getRunningJobs() {
        final OpenType<?>[] itemTypes = { SimpleType.STRING, SimpleType.STRING, SimpleType.STRING };
        CompositeType infoType;
        try {
            infoType = new CompositeType("runningJobs", "Lists currently running jobs in eXist",
                    pItemNames, pItemDescriptions, itemTypes);
            final TabularType tabularType = new TabularType("jobList", "List of currently running jobs", infoType, pIndexNames);
            final TabularDataSupport data = new TabularDataSupport(tabularType);
            final ProcessMonitor.JobInfo[] jobs = processMonitor.runningJobs();
            for (ProcessMonitor.JobInfo job : jobs) {
                final Object[] itemValues = {job.getThread().getName(), job.getAction(), job.getAddInfo().toString()};
                data.put(new CompositeDataSupport(infoType, pItemNames, itemValues));
            }
            return data;
        } catch (final OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public TabularData getRunningQueries() {
        final OpenType<?>[] itemTypes = { SimpleType.INTEGER, SimpleType.STRING, SimpleType.STRING, SimpleType.BOOLEAN };
        CompositeType infoType;
        try {
            infoType = new CompositeType("runningQueries", "Lists currently running XQueries",
                    qItemNames, qItemDescriptions, itemTypes);
            final TabularType tabularType = new TabularType("queryList", "List of currently running XQueries", infoType, qIndexNames);
            final TabularDataSupport data = new TabularDataSupport(tabularType);
            final XQueryWatchDog[] watchdogs = processMonitor.getRunningXQueries();
            for (XQueryWatchDog watchdog : watchdogs) {
                final Object[] itemValues = {new Integer(watchdog.getContext().hashCode()), watchdog.getContext().getXacmlSource().getType(), watchdog.getContext().getXacmlSource().getKey(), Boolean.valueOf(watchdog.isTerminating())};
                data.put(new CompositeDataSupport(infoType, qItemNames, itemValues));
            }
            return data;
        } catch (final OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }

    public void killQuery(int id) {
        final XQueryWatchDog[] watchdogs = processMonitor.getRunningXQueries();
        for (XQueryWatchDog watchdog : watchdogs) {
            final XQueryContext context = watchdog.getContext();

            if( id == context.hashCode() ) {
                if( !watchdog.isTerminating() ) {
                    watchdog.kill(1000);
                }
                break;
            }
        }
    }

    @Override
    public TabularData getRecentQueryHistory() {
        final OpenType<?>[] itemTypes = { SimpleType.STRING, SimpleType.INTEGER, SimpleType.LONG, SimpleType.LONG };
        CompositeType infoType;
        try {
            infoType = new CompositeType("recentQueryHistory", "Lists recently completed XQueries", qhItemNames, qhItemDescriptions, itemTypes);

            final TabularType tabularType = new TabularType("queryList", "List of recently completed XQueries", infoType, qhIndexNames);
            final TabularDataSupport data = new TabularDataSupport(tabularType);
            final QueryHistory[] queryHistories = processMonitor.getRecentQueryHistory();
            for(final QueryHistory queryHistory : queryHistories) {
                final Object[] itemValues = { queryHistory.getSource(), queryHistory.getInvocationCount(), queryHistory.getMostRecentExecutionTime(), queryHistory.getMostRecentExecutionDuration()};
                data.put(new CompositeDataSupport(infoType, qhItemNames, itemValues));
            }
            return data;
        } catch (final OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }
}