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

import org.exist.xquery.XQueryWatchDog;

/**
 * Detail information about a running XQuery
 */
public class RunningQuery {

    int id;
    String sourceType;
    String sourceKey;
    boolean terminating;
    String requestURI;
    String thread;
    long elapsed;

    public RunningQuery(final XQueryWatchDog watchdog, final String requestURI) {
        this.id = watchdog.getContext().hashCode();
        this.sourceType = watchdog.getContext().getSource().type();
        this.sourceKey = watchdog.getContext().getSource().pathOrShortIdentifier();
        this.terminating = watchdog.isTerminating();
        this.requestURI = requestURI;
        this.thread = watchdog.getRunningThread();
        this.elapsed = System.currentTimeMillis() - watchdog.getStartTime();
    }

    public int getId() {
        return id;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getRequestURI() {
        return requestURI;
    }

    public boolean isTerminating() {
        return terminating;
    }

    public String getThread() {
        return thread;
    }

    public long getElapsed() {
        return elapsed;
    }
}
