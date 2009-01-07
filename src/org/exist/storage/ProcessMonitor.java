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

import org.apache.log4j.Logger;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.util.ExpressionDumper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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

	private final static Logger LOG = Logger.getLogger(ProcessMonitor.class);
	
	private Set runningQueries = new HashSet();

    private Map processes = new HashMap();

	public ProcessMonitor() {
		super();
	}

    public void startJob(String action) {
        startJob(action, null);
    }

    public void startJob(String action, Object addInfo) {
        JobInfo info = new JobInfo(action);
        info.setAddInfo(addInfo);
        synchronized (processes) {
            processes.put(info.getThread(), info);
        }
    }

    public void endJob() {
        synchronized (processes) {
            processes.remove(Thread.currentThread());
        }
    }

    public JobInfo[] runningJobs() {
        synchronized (processes) {
            JobInfo jobs[] = new JobInfo[processes.size()];
            int j = 0;
            for (Iterator i = processes.values().iterator(); i.hasNext(); j++) {
                jobs[j] = (JobInfo) i.next();
            }
            return jobs;
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
    }
	
	public void killAll(long waitTime) {
        // directly called from BrokerPool itself. no need to synchronize.
		XQueryWatchDog watchdog;
		for(Iterator i = runningQueries.iterator(); i.hasNext(); ) {
			watchdog = (XQueryWatchDog) i.next();
			LOG.debug("Killing query: " + 
			        ExpressionDumper.dump(watchdog.getContext().getRootExpression()));
			watchdog.kill(waitTime);
		}
	}
	
	public XQueryWatchDog[] getRunningXQueries()
	{
        synchronized (runningQueries) {
            XQueryWatchDog watchdogs[] = new XQueryWatchDog[runningQueries.size()];
            int j = 0;
            for (Iterator i = runningQueries.iterator(); i.hasNext(); j++) {
                watchdogs[j] = (XQueryWatchDog) i.next();
            }
            return watchdogs;
        }
	}

    public final static class JobInfo {

        private Thread thread;
		private String action;
		private long startTime;
        private Object addInfo = null;

		public JobInfo(String action) {
            this.thread = Thread.currentThread();
			this.action = action;
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
	}
}
