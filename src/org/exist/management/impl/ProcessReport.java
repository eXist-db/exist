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

public class ProcessReport implements ProcessReportMXBean {

    private final static Logger LOG = LogManager.getLogger(ProcessReport.class);

    private static String[] pItemNames = { "id", "action", "info" };
    private static String[] pItemDescriptions = {
        "Process ID",
        "Description of the current action",
        "Additional info provided by thread"
    };
    private static String[] pIndexNames = { "id" };


    private static String[] qItemNames = { "id", "sourceType", "sourceKey", "terminating", "requestURI", "thread", "elapsed" };
    private static String[] qItemDescriptions = {
        "XQuery ID",
        "Type of the query source",
        "Description of the source",
        "Is query terminating?",
        "The URI by which the query was called (if any)",
        "The thread running this query",
        "The time in milliseconds since the query was started"
    };

    private static String[] qIndexNames = { "id" };

    private static String[] qhItemNames = { "idx", "sourceKey", "recentInvocationCount", "mostRecentExecutionTime", "mostRecentExecutionDuration",
        "requestURI" };

    private static String[] qhItemDescriptions = {
        "Index of the query in the history",
        "Description of the source",
        "Recent invocation count",
        "Most recent query invocation start time",
        "Most recent query invocation duration",
        "The URI by which the query was called (if any)"
    };

    private static String[] qhIndexNames = { "idx" };

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
        final OpenType<?>[] itemTypes = { SimpleType.INTEGER, SimpleType.STRING, SimpleType.STRING, SimpleType.BOOLEAN,
                SimpleType.STRING, SimpleType.STRING, SimpleType.LONG };
        CompositeType infoType;
        try {
            infoType = new CompositeType("runningQueries", "Lists currently running XQueries",
                    qItemNames, qItemDescriptions, itemTypes);
            final TabularType tabularType = new TabularType("queryList", "List of currently running XQueries", infoType, qIndexNames);
            final TabularDataSupport data = new TabularDataSupport(tabularType);
            final XQueryWatchDog[] watchdogs = processMonitor.getRunningXQueries();
            for (XQueryWatchDog watchdog : watchdogs) {
                String requestURI = null;
                if (processMonitor.getTrackRequestURI()) {
                    requestURI = ProcessMonitor.getRequestURI(watchdog);
                }
                final Object[] itemValues = { Integer.valueOf(watchdog.getContext().hashCode()), watchdog.getContext().getSource().type(),
                        watchdog.getContext().getSource().path(), Boolean.valueOf(watchdog.isTerminating()), requestURI,
                        watchdog.getRunningThread(), System.currentTimeMillis() - watchdog.getStartTime()};
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
        final OpenType<?>[] itemTypes = { SimpleType.INTEGER, SimpleType.STRING, SimpleType.INTEGER, SimpleType.LONG, SimpleType.LONG, SimpleType.STRING };
        CompositeType infoType;
        try {
            infoType = new CompositeType("recentQueryHistory", "Lists recently completed XQueries", qhItemNames, qhItemDescriptions, itemTypes);

            final TabularType tabularType = new TabularType("queryList", "List of recently completed XQueries", infoType, qhIndexNames);
            final TabularDataSupport data = new TabularDataSupport(tabularType);
            final QueryHistory[] queryHistories = processMonitor.getRecentQueryHistory();
            int i = 0;
            for(final QueryHistory queryHistory : queryHistories) {
                final Object[] itemValues = { i++, queryHistory.getSource(), queryHistory.getInvocationCount(), queryHistory.getMostRecentExecutionTime(),
                        queryHistory.getMostRecentExecutionDuration(), queryHistory.getRequestURI() };
                data.put(new CompositeDataSupport(infoType, qhItemNames, itemValues));
            }
            return data;
        } catch (final OpenDataException e) {
            LOG.warn(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Sets the time span (in milliseconds) for which the stats for an executed query should
     * be kept in the recent query history.
     *
     * @param time
     */
    @Override
    public void setHistoryTimespan(long time) {
        processMonitor.setHistoryTimespan(time);
    }

    @Override
    public long getHistoryTimespan() {
        return processMonitor.getHistoryTimespan();
    }

    /**
     * Sets the minimum execution time of queries recorded in the recent query history.
     * Queries faster than this are not recorded.
     *
     * @param time
     */
    @Override
    public void setMinTime(long time) {
        processMonitor.setMinTime(time);
    }

    @Override
    public long getMinTime() {
        return processMonitor.getMinTime();
    }

    /**
     * Enable request tracking: for every executed query, try to figure out which HTTP
     * URL triggered it (if applicable). For performance reasons this is disabled by default,
     * though the overhead should be small.
     *
     * @param track
     */
    @Override
    public void setTrackRequestURI(boolean track) {
        processMonitor.setTrackRequestURI(track);
    }

    @Override
    public boolean getTrackRequestURI() {
        return processMonitor.getTrackRequestURI();
    }

    /**
     * Configure all settings related to recent query history.
     *
     * @param minTimeRecorded The minimum duration of a query (in milliseconds) to be added to the query history
     *                        (see {@link ProcessMonitor#setMinTime(long)}).
     * @param historyTimespan The max duration (in milliseconds) for which queries are tracked in the query history
     *                        (see {@link ProcessMonitor#setHistoryTimespan(long)}).
     * @param trackURI Set to true if the class should attempt to determine the HTTP URI through which the query was triggered
     *                 (see {@link ProcessMonitor#setHistoryTimespan(long)}).
     */
    @Override
    public void configure(long minTimeRecorded, long historyTimespan, boolean trackURI) {
        processMonitor.setMinTime(minTimeRecorded);
        processMonitor.setHistoryTimespan(historyTimespan);
        processMonitor.setTrackRequestURI(trackURI);
    }
}