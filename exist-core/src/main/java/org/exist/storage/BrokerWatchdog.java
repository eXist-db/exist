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

import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;

import org.exist.EXistException;

/**
 * Traces the lease of a Broker
 *
 * Note that tracing the stack is expensive
 * this should only be done in debug mode by
 * enabling the System Property {@link #TRACE_BROKERS_PROPERTY_NAME}
 */
public class BrokerWatchdog {

	public static final String TRACE_BROKERS_PROPERTY_NAME = "trace.brokers";

	private static final DateFormat df = DateFormat.getDateTimeInstance();
	private static final String EOL = System.lineSeparator();

	private static class WatchedBroker {
		private final DBBroker broker;
		private long timeAdded;
		private final StringBuilder trace;
		
		WatchedBroker(final DBBroker broker) {
			this.broker = broker;
			this.timeAdded = System.currentTimeMillis();
			this.trace = new StringBuilder();
			trace();
		}

		void trace() {
			trace.append("Reference count: ").append(broker.getReferenceCount()).append(EOL);
			final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			final int showElementCount = stack.length > 20 ? 20 : stack.length;
			for (int i = 4; i < showElementCount; i++) {
				trace.append(stack[i].toString()).append(EOL);
			}
			trace.append(EOL);
		}
	}
	
	private final Map<DBBroker, WatchedBroker> watched = new IdentityHashMap<>();
	
	public void add(final DBBroker broker) throws EXistException {
		final WatchedBroker old = watched.get(broker);
		if (old == null) {
			checkForTimeout();
			watched.put(broker, new WatchedBroker(broker));
		} else {
			old.timeAdded = System.currentTimeMillis();
			old.trace();
		}
	}
	
	public void remove(final DBBroker broker) {
		watched.remove(broker);
	}
	
	public String get(final DBBroker broker) {
		final WatchedBroker w = watched.get(broker);
		if (w != null) {
			return w.trace.toString();
		}
		return "";
	}
	
	public void checkForTimeout() throws EXistException {
		for (final WatchedBroker broker : watched.values()) {
			if (System.currentTimeMillis() - broker.timeAdded > 30000) {
				throw new EXistException("Broker: " + broker.broker.getId() + 
						" did not return for 30sec." + EOL + EOL + broker.trace);
			}
		}
	}
	
	public void dump(final PrintWriter writer) {
		writer.println("Active brokers:");
		for (final WatchedBroker broker: watched.values()) {
			writer.format("%20s: %s%s", "Broker", broker.broker.getId(), EOL);
			writer.format("%20s: %s%s", "Active since", df.format(new Date(broker.timeAdded)), EOL);
			writer.println("");
			writer.println("Stack:");
			writer.println(broker.trace);
			writer.println("----------------------------------------------------------------");
		}
	}
}
