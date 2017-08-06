/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.management.impl;

import org.exist.storage.ProcessMonitor;

/**
 * Detail information about recently executed XQuery.
 */
public class RecentQueryHistory {

    private int idx;
    private String sourceKey;
    private int recentInvocationCount;
    private long mostRecentExecutionTime;
    private long mostRecentExecutionDuration;
    private String requestURI;

    public RecentQueryHistory(int idx, ProcessMonitor.QueryHistory queryHistory) {
        this.idx = idx;
        this.sourceKey = queryHistory.getSource();
        this.recentInvocationCount = queryHistory.getInvocationCount();
        this.mostRecentExecutionTime = queryHistory.getMostRecentExecutionTime();
        this.mostRecentExecutionDuration = queryHistory.getMostRecentExecutionDuration();
        this.requestURI = queryHistory.getRequestURI();
    }

    public int getIdx() {
        return idx;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public int getRecentInvocationCount() {
        return recentInvocationCount;
    }

    public long getMostRecentExecutionTime() {
        return mostRecentExecutionTime;
    }

    public long getMostRecentExecutionDuration() {
        return mostRecentExecutionDuration;
    }

    public String getRequestURI() {
        return requestURI;
    }
}
