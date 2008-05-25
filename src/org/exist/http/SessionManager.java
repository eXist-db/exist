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
package org.exist.http;

import org.apache.log4j.Logger;
import org.exist.scheduler.JobException;
import org.exist.scheduler.UserJavaJob;
import org.exist.storage.BrokerPool;
import org.exist.xquery.value.Sequence;

import java.util.Map;
import java.util.Properties;

public class SessionManager {

    public final static long TIMEOUT = 120000;

    public final static long TIMEOUT_CHECK_PERIOD = 2000;

    public final static int NO_SESSION = -1;
    
    private final static Logger LOG = Logger.getLogger(SessionManager.class);
    
    private class QueryResult {

        long created;
        String queryString;
        Sequence sequence;

        private QueryResult(String query, Sequence sequence) {
            this.queryString = query;
            this.sequence = sequence;
            this.created = System.currentTimeMillis();
        }
    }

    public static class TimeoutCheck extends UserJavaJob {

        public TimeoutCheck() {
        }

        public String getName() {
            return "REST_TimeoutCheck";
        }

        public void setName(String name) {
        }

        public void execute(BrokerPool brokerpool, Map params) throws JobException {
            SessionManager manager = (SessionManager) params.get("session-manager");
            if (manager == null)
                throw new JobException(JobException.JOB_ABORT, "parameter 'session-manager' is not set");
            manager.timeoutCheck();
        }
    }

    private QueryResult[] slots = new QueryResult[32];

    public SessionManager(BrokerPool pool) {
        Properties props = new Properties();
        props.put("session-manager", this);
        pool.getScheduler().createPeriodicJob(TIMEOUT_CHECK_PERIOD, new TimeoutCheck(), 2000, props);
    }

    public int add(String query, Sequence sequence) {
        final int len = slots.length;
        for (int i = 0; i < len; i++) {
            if (slots[i] == null) {
                slots[i] = new QueryResult(query, sequence);
                return i;
            }
        }
        // no free slots, resize
        QueryResult[] t = new QueryResult[(len * 3) / 2];
        System.arraycopy(slots, 0, t, 0, len);
        t[len] = new QueryResult(query, sequence);
        slots = t;
        return len;
    }

    public Sequence get(String query, int sessionId) {
        if (sessionId < 0 || sessionId >= slots.length)
            return null; // out of scope
        QueryResult cached = slots[sessionId];
        if (cached == null)
            return null;
        if (cached.queryString.equals(query))
            return cached.sequence;
        // wrong query
        return null;
    }

    public void release(int sessionId) {
        if (sessionId < 0 || sessionId >= slots.length)
            return; // out of scope
        slots[sessionId] = null;
    }

    protected void timeoutCheck() {
        final long now = System.currentTimeMillis();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && now - slots[i].created > TIMEOUT) {
                LOG.debug("Removing cached query result for session " + i);
                slots[i] = null;
            }
        }
    }
}