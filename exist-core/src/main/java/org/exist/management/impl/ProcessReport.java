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

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

import org.exist.scheduler.ScheduledJobInfo;
import org.exist.scheduler.Scheduler;
import org.exist.storage.BrokerPool;
import org.exist.storage.ProcessMonitor;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.exist.storage.ProcessMonitor.QueryHistory;

public class ProcessReport implements ProcessReportMXBean {
    private final String instanceId;
    private final ProcessMonitor processMonitor;
    private final Scheduler scheduler;

    public ProcessReport(final BrokerPool pool) {
        this.instanceId = pool.getId();
        this.processMonitor = pool.getProcessMonitor();
        this.scheduler = pool.getScheduler();
    }

    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=ProcessReport";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(instanceId));
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public Map<String, Job> getScheduledJobs() {
        final Map<String, Job> jobList = new HashMap<>();

        final List<ScheduledJobInfo> jobs = scheduler.getScheduledJobs();
        for (final ScheduledJobInfo job : jobs) {
            jobList.put(job.getName(), new Job(job.getName(), job.getGroup(), job.getTriggerExpression()));
        }
        return jobList;
    }

    @Override
    public Map<String, Job> getRunningJobs() {
        final Map<String, Job> jobList = new HashMap<>();

        final ProcessMonitor.JobInfo[] jobs = processMonitor.runningJobs();
        for (final ProcessMonitor.JobInfo job : jobs) {
            jobList.put(job.getThread().getName(), new Job(job.getThread().getName(), job.getAction(), job.getAddInfo().toString()));
        }
        return jobList;
    }

    @Override
    public Map<QueryKey, RunningQuery> getRunningQueries() {
        final Map<QueryKey, RunningQuery> queries = new TreeMap<>();

        final XQueryWatchDog[] watchdogs = processMonitor.getRunningXQueries();
        for (final XQueryWatchDog watchdog : watchdogs) {
            String requestURI = null;
            if (processMonitor.getTrackRequestURI()) {
                requestURI = ProcessMonitor.getRequestURI(watchdog);
            }

            final RunningQuery runningQuery = new RunningQuery(watchdog, requestURI);
            queries.put(new QueryKey(runningQuery.getId(), runningQuery.getSourceKey()), runningQuery);
        }
        return queries;
    }

    @Override
    public void killQuery(final int id) {
        final XQueryWatchDog[] watchdogs = processMonitor.getRunningXQueries();
        for (XQueryWatchDog watchdog : watchdogs) {
            final XQueryContext context = watchdog.getContext();

            if (id == context.hashCode()) {
                if (!watchdog.isTerminating()) {
                    watchdog.kill(1000);
                }
                break;
            }
        }
    }

    @Override
    public Map<QueryKey, RecentQueryHistory> getRecentQueryHistory() {
        final Map<QueryKey, RecentQueryHistory> history = new TreeMap<>();
        final QueryHistory[] queryHistories = processMonitor.getRecentQueryHistory();
        for (int i = 0; i < queryHistories.length; i++) {
            final QueryHistory queryHistory = queryHistories[i];
            history.put(new QueryKey(i, queryHistory.getSource()), new RecentQueryHistory(i, queryHistory));
        }
        return history;
    }

    /**
     * Sets the time span (in milliseconds) for which the stats for an executed query should
     * be kept in the recent query history.
     *
     * @param time time span in milliseconds
     */
    @Override
    public void setHistoryTimespan(final long time) {
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
     * @param time time span in milliseconds
     */
    @Override
    public void setMinTime(final long time) {
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
     * @param track should URLs be tracked?
     */
    @Override
    public void setTrackRequestURI(final boolean track) {
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
     * @param trackURI        Set to true if the class should attempt to determine the HTTP URI through which the query was triggered
     *                        (see {@link ProcessMonitor#setHistoryTimespan(long)}).
     */
    @Override
    public void configure(final long minTimeRecorded, final long historyTimespan, final boolean trackURI) {
        processMonitor.setMinTime(minTimeRecorded);
        processMonitor.setHistoryTimespan(historyTimespan);
        processMonitor.setTrackRequestURI(trackURI);
    }
}