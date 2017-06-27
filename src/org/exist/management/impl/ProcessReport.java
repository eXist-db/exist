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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private ProcessMonitor processMonitor;

    private Scheduler scheduler;

    public ProcessReport(BrokerPool pool) {
        processMonitor = pool.getProcessMonitor();
        scheduler = pool.getScheduler();
    }

    @Override
    public List<Job> getScheduledJobs() {
        final List<Job> jobList = new ArrayList<>();

        final List<ScheduledJobInfo> jobs = scheduler.getScheduledJobs();
        for (final ScheduledJobInfo job : jobs) {
            jobList.add(new Job(job.getName(), job.getGroup(), job.getTriggerExpression()));
        }
        return jobList;
    }

    @Override
    public List<Job> getRunningJobs() {
        final List<Job> jobList = new ArrayList<>();

        final ProcessMonitor.JobInfo[] jobs = processMonitor.runningJobs();
        for (final ProcessMonitor.JobInfo job : jobs) {
            jobList.add(new Job(job.getThread().getName(), job.getAction(), job.getAddInfo().toString()));
        }
        return jobList;
    }

    @Override
    public List<RunningQuery> getRunningQueries() {
        final List<RunningQuery> queries = new ArrayList<>();

        final XQueryWatchDog[] watchdogs = processMonitor.getRunningXQueries();
        for (final XQueryWatchDog watchdog : watchdogs) {
            String requestURI = null;
            if (processMonitor.getTrackRequestURI()) {
                requestURI = ProcessMonitor.getRequestURI(watchdog);
            }

            queries.add(new RunningQuery(watchdog, requestURI));
        }
        return queries;
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
    public List<RecentQueryHistory> getRecentQueryHistory() {
        final List<RecentQueryHistory> history = new ArrayList<>();
        final QueryHistory[] queryHistories = processMonitor.getRecentQueryHistory();
        int i = 0;
        for(final QueryHistory queryHistory : queryHistories) {
            history.add(new RecentQueryHistory(i++, queryHistory));
        }
        return history;
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