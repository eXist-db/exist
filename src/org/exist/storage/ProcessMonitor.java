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

import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.XQueryWatchDog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.exist.xquery.util.ExpressionDumper;

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
public class ProcessMonitor {

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

    private final static int MAX_QUERY_HISTORY = 16; //The maximum number of queries to record history for

    private final Set<XQueryWatchDog> runningQueries = new HashSet<XQueryWatchDog>();
    private final Map<String, QueryHistory> queryHistories = new FixedSizeLinkedHashMap<String, QueryHistory>(MAX_QUERY_HISTORY);

    private Map<Thread, JobInfo> processes = new HashMap<Thread, JobInfo>();

    private long maxShutdownWait;

	public ProcessMonitor(long maxShutdownWait) {
		this.maxShutdownWait = maxShutdownWait;
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
            runningQueries.add(watchdog);
        }
    }
	
    public void queryCompleted(XQueryWatchDog watchdog) {
        synchronized (runningQueries) {
            runningQueries.remove(watchdog);
        }

        final String sourceKey = watchdog.getContext().getXacmlSource().getKey();
        synchronized(queryHistories) {
            QueryHistory qh = queryHistories.get(sourceKey);
            if(qh == null) {
                qh = new QueryHistory(sourceKey);
            }

            qh.setMostRecentExecutionTime(watchdog.getStartTime());
            qh.setMostRecentExecutionDuration(System.currentTimeMillis() - watchdog.getStartTime());
            qh.incrementInvocationCount();

            queryHistories.put(sourceKey, qh);
        }
    }

    /**
     * Linked HashMap that has a fixed size
     *
     * Oldest items are removed when new items are added
     * if the max size is exceeded
     */
    public class FixedSizeLinkedHashMap<K,V> extends LinkedHashMap<K,V> {

        private final int maxSize;

        public FixedSizeLinkedHashMap(int maxSize) {
            super(maxSize);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Entry<K, V> entry) {
            return size() >= maxSize;
        }
     }


    public class QueryHistory {

        private final String source;
        private long mostRecentExecutionTime;
        private long mostRecentExecutionDuration;
        private int invocationCount = 0;

        public QueryHistory(String source) {
            this.source = source;
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
    }

    public QueryHistory[] getRecentQueryHistory() {
        final QueryHistory result[] = new QueryHistory[queryHistories.size()];
        return (QueryHistory[])queryHistories.values().toArray(result);
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
            final XQueryWatchDog watchdogs[] = new XQueryWatchDog[runningQueries.size()];
            int j = 0;
            for (final Iterator<XQueryWatchDog> i = runningQueries.iterator(); i.hasNext(); j++) {
                watchdogs[j] = i.next();
            }
            return watchdogs;
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
}
