package org.exist.storage;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;

import org.exist.EXistException;

public class BrokerWatchdog {

	private static DateFormat df = DateFormat.getDateTimeInstance();
	private final static String EOL = System.getProperty("line.separator");

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
						" did not return for 30sec." + EOL + EOL + broker.trace.toString());
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
