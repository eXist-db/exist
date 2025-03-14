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
package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.xquery.Module;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;

import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.util.ExpressionDumper;

import java.util.*;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Class to keep track of all running queries in a database instance. The main
 * purpose of this class is to signal running queries that the database is going to
 * shut down. This is done through the {@link org.exist.xquery.XQueryWatchDog}
 * registered by each query. It is up to the query to check the watchdog's state.
 * If it simply ignores the terminate signal, it will be killed after the shutdown
 * timeout is reached.
 *
 * @author wolf
 */
public class ProcessMonitor implements BrokerPoolService {

    public static final String ACTION_UNSPECIFIED = "unspecified";
    public static final String ACTION_VALIDATE_DOC = "validating document";
    public static final String ACTION_STORE_DOC = "storing document";
    public static final String ACTION_STORE_BINARY = "storing binary resource";
    public static final String ACTION_REMOVE_XML = "remove XML resource";
    public static final String ACTION_REMOVE_BINARY = "remove binary resource";
    public static final String ACTION_REMOVE_COLLECTION = "remove collection";
    public static final String ACTION_REINDEX_COLLECTION = "reindex collection";
    public static final String ACTION_COPY_COLLECTION = "copy collection";
    public static final String ACTION_MOVE_COLLECTION = "move collection";
    public static final String ACTION_BACKUP = "backup";

    private static final Logger LOG = LogManager.getLogger(ProcessMonitor.class);
    private static final long QUERY_HISTORY_TIMEOUT = 2 * 60 * 1000; // 2 minutes
    private static final long MIN_TIME = 100;

    private final Set<XQueryWatchDog> runningQueries = new HashSet<>();
    private final DelayQueue<QueryHistory> history = new DelayQueue<>();
    private final Map<Thread, JobInfo> processes = new HashMap<>();
    private long maxShutdownWait;
    private long historyTimespan = QUERY_HISTORY_TIMEOUT;
    private long minTime = MIN_TIME;
    private boolean trackRequests = false;

    @Override
    public void configure(final Configuration configuration) {
        this.maxShutdownWait = configuration.getProperty(BrokerPool.PROPERTY_SHUTDOWN_DELAY, BrokerPool.DEFAULT_MAX_SHUTDOWN_WAIT);
    }

    public void startJob(final String action) {
        startJob(action, null);
    }

    public void startJob(final String action, final Object addInfo) {
        startJob(action, addInfo, null);
    }

    //TODO: addInfo = XmldbURI ? -shabanovd
    public void startJob(final String action, final Object addInfo, final Monitor monitor) {
        final JobInfo info = new JobInfo(action, monitor);
        info.setAddInfo(addInfo);
        synchronized (this) {
            processes.put(info.getThread(), info);
        }
    }

    public synchronized void endJob() {
        processes.remove(Thread.currentThread());
        notifyAll();
    }

    public JobInfo[] runningJobs() {
        synchronized (this) {
            final JobInfo[] jobs = new JobInfo[processes.size()];
            int j = 0;
            for (final Iterator<JobInfo> i = processes.values().iterator(); i.hasNext(); j++) {
                //BUG: addInfo = XmldbURI ? -shabanovd
                jobs[j] = i.next();
            }
            return jobs;
        }
    }

    public void stopRunningJobs() {
        final long waitStart = System.currentTimeMillis();
        synchronized (this) {
            if (maxShutdownWait > -1) {
                while (!processes.isEmpty()) {
                    try {
                        //Wait until they become inactive...
                        this.wait(1000);
                    } catch (final InterruptedException e) {
                        //no op
                        Thread.currentThread().interrupt(); // pass on interrupted status
                    }
                    //...or force the shutdown
                    if (maxShutdownWait > -1 && System.currentTimeMillis() - waitStart > maxShutdownWait) {
                        break;
                    }
                }
            }
            for (final JobInfo job : processes.values()) {
                job.stop();
            }
        }
    }

    public void queryStarted(final XQueryWatchDog watchdog) {
        synchronized (runningQueries) {
            watchdog.setRunningThread(Thread.currentThread().getName());
            runningQueries.add(watchdog);
        }
    }

    public void queryCompleted(final XQueryWatchDog watchdog) {
        boolean found;
        synchronized (runningQueries) {
            found = runningQueries.remove(watchdog);
        }

        // add to query history if elapsed time > minTime
        final long elapsed = System.currentTimeMillis() - watchdog.getStartTime();
        if (found && elapsed > minTime) {
            synchronized (history) {
                final Source source = watchdog.getContext().getSource();
                final String sourceKey = source == null ? "unknown" : source.pathOrShortIdentifier();
                QueryHistory qh = new QueryHistory(sourceKey, historyTimespan);
                qh.setMostRecentExecutionTime(watchdog.getStartTime());
                qh.setMostRecentExecutionDuration(elapsed);
                qh.incrementInvocationCount();
                if (trackRequests) {
                    qh.setRequestURI(getRequestURI(watchdog));
                }
                history.add(qh);
                cleanHistory();
            }
        }
    }

    private void cleanHistory() {
        // remove timed out entries
        while (history.poll() != null) ;
    }

    /**
     * The max duration (in milliseconds) for which queries are tracked in the query history. Older queries
     * will be removed (default is {@link #QUERY_HISTORY_TIMEOUT}).
     *
     * @param time max duration in ms
     */
    public void setHistoryTimespan(final long time) {
        historyTimespan = time;
    }

    public long getHistoryTimespan() {
        return historyTimespan;
    }

    /**
     * The minimum duration of a query (in milliseconds) to be added to the query history. Use this to filter out
     * very short-running queries (default is {@link #MIN_TIME}).
     *
     * @param time min duration in ms
     */
    public void setMinTime(final long time) {
        this.minTime = time;
    }

    public long getMinTime() {
        return minTime;
    }

    /**
     * Set to true if the class should attempt to determine the HTTP URI through which the query was triggered.
     * This is an important piece of information for diagnosis, but gathering it might be expensive, so request
     * URI tracking is disabled by default.
     *
     * @param track attempt to track URIs if true
     */
    public void setTrackRequestURI(final boolean track) {
        trackRequests = track;
    }

    public boolean getTrackRequestURI() {
        return trackRequests;
    }

    public static class QueryHistory implements Delayed {

        private final String source;
        private String requestURI = null;
        private long mostRecentExecutionTime;
        private long mostRecentExecutionDuration;
        private int invocationCount = 0;
        private long expires;

        public QueryHistory(final String source, final long delay) {
            this.source = source;
            this.expires = System.currentTimeMillis() + delay;
        }

        public String getSource() {
            return source;
        }

        public void incrementInvocationCount() {
            invocationCount++;
        }

        public int getInvocationCount() {
            return invocationCount;
        }

        public long getMostRecentExecutionTime() {
            return mostRecentExecutionTime;
        }

        public void setMostRecentExecutionTime(final long mostRecentExecutionTime) {
            this.mostRecentExecutionTime = mostRecentExecutionTime;
        }

        public long getMostRecentExecutionDuration() {
            return mostRecentExecutionDuration;
        }

        public void setMostRecentExecutionDuration(final long mostRecentExecutionDuration) {
            this.mostRecentExecutionDuration = mostRecentExecutionDuration;
        }

        public String getRequestURI() {
            return requestURI;
        }

        public void setRequestURI(final String uri) {
            requestURI = uri;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return unit.convert(expires - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(final Delayed o) {
            return Long.compare(expires, ((QueryHistory) o).expires);
        }
    }

    public QueryHistory[] getRecentQueryHistory() {
        synchronized (history) {
            cleanHistory();
            return
                    history.stream()
                            .sorted((o1, o2) -> Long.compare(o2.expires, o1.expires))
                            .toArray(QueryHistory[]::new);
        }
    }


    public void killAll(final long waitTime) {
        synchronized(runningQueries) {
            for (final XQueryWatchDog watchdog : runningQueries) {
                LOG.debug("Killing query: {}", ExpressionDumper.dump(watchdog.getContext().getRootExpression()));
                watchdog.kill(waitTime);
            }
        }
    }

    public XQueryWatchDog[] getRunningXQueries() {
        synchronized (runningQueries) {
            return runningQueries.toArray(new XQueryWatchDog[0]);
        }
    }

    public final static class Monitor {
        boolean stop = false;

        public boolean proceed() {
            return !stop;
        }

        public void stop() {
            LOG.debug("Terminating job");
            this.stop = true;
        }
    }

    public final static class JobInfo {
        private final Thread thread;
        private final String action;
        private final long startTime;
        private final Monitor monitor;

        private Object addInfo = null;

        public JobInfo(final String action, final Monitor monitor) {
            this.thread = Thread.currentThread();
            this.action = action;
            this.monitor = monitor;
            this.startTime = System.currentTimeMillis();
        }

        public String getAction() {
            return action;
        }

        public Thread getThread() {
            return thread;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setAddInfo(final Object info) {
            this.addInfo = info;
        }

        public Object getAddInfo() {
            return addInfo;
        }

        public void stop() {
            monitor.stop();
        }
    }

    /**
     * Try to figure out the HTTP request URI by which a query was called.
     * Request tracking is not enabled unless {@link #setTrackRequestURI(boolean)}
     * is called.
     *
     * @param watchdog XQuery WatchDog
     * @return HTTP request URI by which a query was called
     */
    public static String getRequestURI(final XQueryWatchDog watchdog) {
        final Module[] modules = watchdog.getContext().getModules(RequestModule.NAMESPACE_URI);
        if (isEmpty(modules)) {
            return null;
        }

        final Optional<RequestWrapper> maybeRequest = Optional.ofNullable(watchdog.getContext())
                .map(XQueryContext::getHttpContext)
                .map(XQueryContext.HttpContext::getRequest);

        if (!maybeRequest.isPresent()) {
            return null;
        }

        final RequestWrapper request = maybeRequest.get();
        final Object attr = request.getAttribute(XQueryURLRewrite.RQ_ATTR_REQUEST_URI);
        String uri;
        if (attr == null) {
            uri = request.getRequestURI();
        } else {
            uri = attr.toString();
        }
        String queryString = request.getQueryString();
        if (queryString != null) {
            uri += "?" + queryString;
        }
        return uri;
    }
}
