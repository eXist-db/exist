/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.util;

/**
 * Represents a traceable change of state
 *
 * @param <S> Information about the state which was modified
 * @param <C> the change which was applied to the state
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class TraceableStateChange<S, C> {
    private final C change;
    private final StackTraceElement trace[];
    private final S state;
    private final Thread thread;

    public TraceableStateChange(final C change, final S subject) {
        this.change = change;
        this.trace = Stacktrace.substack(Thread.currentThread().getStackTrace(), 2, Stacktrace.DEFAULT_STACK_TOP);
        this.state = subject;
        this.thread = Thread.currentThread();
    }

    public abstract String getId();

    public C getChange() {
        return change;
    }

    public S getState() {
        return state;
    }

    public String describeState() {
        return state.toString();
    }

    public StackTraceElement[] getTrace() {
        return trace;
    }

    public Thread getThread() {
        return thread;
    }
}
