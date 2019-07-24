/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id: ProcessMonitor.java 8235 2008-10-17 16:03:27Z chaeron $
 */
package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;

import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.util.ExpressionDumper;

import java.util.*;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

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

    public final static String ACTION_UNSPECIFIED = "unspecified";
    public final static String ACTION_VALIDATE_DOC = "validating document";
    public final static String ACTION_STORE_DOC = "storing document";
    public final static String ACTION_STORE_BINARY = "storing binary resource";
    public final static String ACTION_REMOVE_XML = "remove XML resource";
    public final static String ACTION_REMOVE_BINARY = "remove binary resource";
    public final static String ACTION_REMOVE_COLLECTION = "remove collection";
    public final static String ACTION_REINDEX_COLLECTION = "reindex collection";
    public final static String ACTION_COPY_COLLECTION = "copy collection";
    public final static String ACTION_MOVE_COLLECTION = "move collection";
    public final static String ACTION_BACKUP = "backup";

    private final static Logger LOG = LogManager.getLogger(ProcessMonitor.class);

    public final static long QUERY_HISTORY_TIMEOUT = 2 * 60 * 1000; // 2 minutes
    public final static long MIN_TIME = 100;

    private final Set<XQueryWatchDog> runningQueries = new HashSet<XQueryWatchDog>();
    private final DelayQueue<QueryHistory> history = new DelayQueue<>();

    private Map<Thread, JobInfo> processes = new HashMap<Thread, JobInfo>();

    private long maxShutdownWait;

    private long historyTimespan = QUERY_HISTORY_TIMEOUT;

    private long minTime = MIN_TIME;

    private boolean trackRequests = false;

	@Override
    public void configure(final Configuration configuration) {
        this.maxShutdownWait = configuration.getProperty(BrokerPool.PROPERTY_SHUTDOWN_DELAY, BrokerPool.DEFAULT_MAX_SHUTDOWN_WAIT);
    }

    public void startJob(String action) {
        startJob(action, null);
    }

    public void startJob(String action, Object addInfo) {
        startJob(action, addInfo, null);
    }

    //TODO: addInfo = XmldbURI ? -shabanovd
    public void startJob(String action, Object addInfo, Monitor monitor) {
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
            final JobInfo jobs[] = new JobInfo[processes.size()];
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
                while (processes.size() > 0) {
                    try {
                        //Wait until they become inactive...
                        this.wait(1000);
                    } catch (final InterruptedException e) {
                    }
                    //...or force the shutdown
                    if(maxShutdownWait > -1 && System.currentTimeMillis() - waitStart > maxShutdownWait){
                        break;
                    }
                }
            }
            for (final JobInfo job : processes.values()) {
                job.stop();
            }
        }
    }

    public void queryStarted(XQueryWatchDog watchdog) {
        synchronized (runningQueries) {
            watchdog.setRunningThread(Thread.currentThread().getName());
            runningQueries.add(watchdog);
        }
    }
	
    public void queryCompleted(XQueryWatchDog watchdog) {
        boolean found;
        synchronized (runningQueries) {
            found = runningQueries.remove(watchdog);
        }

        // add to query history if elapsed time > minTime
        final long elapsed = System.currentTimeMillis() - watchdog.getStartTime();
        if (found && elapsed > minTime) {
            synchronized (history) {
                final Source source = watchdog.getContext().getSource();
                final String sourceKey = source == null ? "unknown" : source.path();
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
        while (history.poll() != null);
    }

    /**
     * The max duration (in milliseconds) for which queries are tracked in the query history. Older queries
     * will be removed (default is {@link #QUERY_HISTORY_TIMEOUT}).
     *
     * @param time max duration in ms
     */
    public void setHistoryTimespan(long time) {
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
    public void setMinTime(long time) {
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
    public void setTrackRequestURI(boolean track) {
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

        public QueryHistory(String source, long delay) {
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

        public void setMostRecentExecutionTime(long mostRecentExecutionTime) {
            this.mostRecentExecutionTime = mostRecentExecutionTime;
        }

        public long getMostRecentExecutionDuration() {
            return mostRecentExecutionDuration;
        }

        public void setMostRecentExecutionDuration(long mostRecentExecutionDuration) {
            this.mostRecentExecutionDuration = mostRecentExecutionDuration;
        }

        public String getRequestURI() {
            return requestURI;
        }

        public void setRequestURI(String uri) {
            requestURI = uri;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expires - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (expires < ((QueryHistory) o).expires) {
                return -1;
            }
            if (expires > ((QueryHistory) o).expires) {
                return 1;
            }
            return 0;
        }
    }

    public QueryHistory[] getRecentQueryHistory() {
        synchronized (history) {
            cleanHistory();
            return
                history.stream()
                    .sorted((o1, o2) -> o1.expires > o2.expires ? -1 : (o1.expires < o2.expires ? 1 : 0))
                    .toArray(QueryHistory[]::new);
        }
    }

	
	public void killAll(long waitTime) {
        // directly called from BrokerPool itself. no need to synchronize.
		for(final XQueryWatchDog watchdog : runningQueries) {
			LOG.debug("Killing query: " + 
			        ExpressionDumper.dump(watchdog.getContext().getRootExpression()));
			watchdog.kill(waitTime);
		}
	}
	
	public XQueryWatchDog[] getRunningXQueries()
	{
        synchronized (runningQueries) {
            return runningQueries.stream().toArray(XQueryWatchDog[]::new);
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

        private Thread thread;
		private String action;
		private long startTime;
        private Object addInfo = null;
        private Monitor monitor = null;

		public JobInfo(String action, Monitor monitor) {
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

        public void setAddInfo(Object info) {
            this.addInfo = info;
        }

        public Object getAddInfo() {
            return addInfo;
        }

        public void stop() {
            if (monitor != null) {
                monitor.stop();
            }
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
    public static String getRequestURI(XQueryWatchDog watchdog) {
        final RequestModule reqModule = (RequestModule)watchdog.getContext().getModule(RequestModule.NAMESPACE_URI);
        if (reqModule == null) {
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
