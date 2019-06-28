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

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a traceable history of state changes
 *
 * @param <S> Information about the state which was modified
 * @param <C> the change which was applied to the state
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class TraceableStateChanges<S, C> {
    private List<TraceableStateChange<S, C>> stateChangeTrace = new ArrayList<>();

    /**
     * Add a state change to the tail.
     *
     * @param stateChange the state change
     */
    public void add(final TraceableStateChange<S, C> stateChange) {
        stateChangeTrace.add(stateChange);
    }

    /**
     * Clear all state changes
     */
    public void clear() {
        stateChangeTrace.clear();
    }

    /**
     * Makes a copy of the list of state changes
     * useful for archival purposes
     *
     * @return A copy of the state changes
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        final TraceableStateChanges<S, C> copy = new TraceableStateChanges<>();
        copy.stateChangeTrace = new ArrayList<>(stateChangeTrace);
        return copy;
    }

    /**
     * Writes the history fo state changes to the
     * provided logger at TRACE level
     *
     * @param logger The logger to write the changes to
     */
    public final void logTrace(final Logger logger) {
        if (!logger.isTraceEnabled()) {
            throw new IllegalStateException("This is only enabled at TRACE level logging");
        }

        for (int i = 0; i < stateChangeTrace.size(); i++) {
            final TraceableStateChange<S, C> traceableStateChange = stateChangeTrace.get(i);
            logger.trace(String.format("%d: %s: %s(%s) from: %s(%s)", i + 1, traceableStateChange.getId(), traceableStateChange.getChange(), traceableStateChange.describeState(), traceableStateChange.getThread(), Stacktrace.asString(traceableStateChange.getTrace())));
        }
    }
}
