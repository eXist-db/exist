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

import java.util.Map;

public interface
ProcessReportMXBean extends PerInstanceMBean {

    Map<String, Job> getScheduledJobs();

    Map<String, Job> getRunningJobs();

    Map<QueryKey, RunningQuery> getRunningQueries();

    Map<QueryKey, RecentQueryHistory> getRecentQueryHistory();

    void killQuery(int id);

    /**
     * Configures the recent query history.
     *
     * @param minTimeRecorded minimum execution time of queries recorded in the recent query history
     * @param historyTimespan time span (in milliseconds) for which the stats for an executed query should
     *                        be kept in the recent query history
     * @param trackURI        Enable request tracking: for every executed query, try to figure out which HTTP
     *                        URL triggered it (if applicable)
     */
    void configure(long minTimeRecorded, long historyTimespan, boolean trackURI);

    /**
     * Sets the time span (in milliseconds) for which the stats for an executed query should
     * be kept in the recent query history.
     *
     * @param time time span in milliseconds
     */
    void setHistoryTimespan(long time);

    long getHistoryTimespan();

    /**
     * Sets the minimum execution time of queries recorded in the recent query history.
     * Queries faster than this are not recorded.
     *
     * @param time time span in milliseconds
     */
    void setMinTime(long time);

    long getMinTime();

    /**
     * Enable request tracking: for every executed query, try to figure out which HTTP
     * URL triggered it (if applicable). For performance reasons this is disabled by default,
     * though the overhead should be small.
     *
     * @param track should URLs be tracked?
     */
    void setTrackRequestURI(boolean track);

    boolean getTrackRequestURI();

    class QueryKey implements Comparable<QueryKey> {
        private final int id;
        private final String key;

        public QueryKey(final int id, final String key) {
            this.id = id;
            this.key = key;
        }

        public int getId() {
            return id;
        }

        public String getKey() {
            return key;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }

            QueryKey queryKey = (QueryKey) other;
            if (id != queryKey.id) {
                return false;
            }
            return key.equals(queryKey.key);
        }

        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + key.hashCode();
            return result;
        }

        @Override
        public int compareTo(final QueryKey other) {
            if (other == null) {
                return 1;
            }

            return key.compareTo(other.key);
        }
    }
}
