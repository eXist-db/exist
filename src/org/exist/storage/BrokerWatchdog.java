package org.exist.storage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;

public class BrokerWatchdog {

	private final static Logger LOG = LogManager.getLogger(BrokerWatchdog.class);
	
	private final static DateFormat df = DateFormat.getDateTimeInstance();
	
	private class WatchedBroker {
		
		DBBroker broker;
		StringBuilder trace;
		long timeAdded;
		
		WatchedBroker(DBBroker broker) {
			this.broker = broker;
			this.timeAdded = System.currentTimeMillis();
			
			this.trace = new StringBuilder();
			trace();
		}
		
		void trace() {
			trace.append("Reference count: ").append(broker.getReferenceCount()).append("\n");
			final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			final int showElementCount = stack.length > 20 ? 20 : stack.length;
			for (int i = 4; i < showElementCount; i++) {
				trace.append(stack[i].toString()).append('\n');
			}
			trace.append("\n");
		}
	}
	
	private Map<DBBroker, WatchedBroker> watched = new IdentityHashMap<DBBroker, WatchedBroker>();
	
	public void add(DBBroker broker) throws EXistException {
		final WatchedBroker old = watched.get(broker);
		if (old == null) {
			checkForTimeout();
			watched.put(broker, new WatchedBroker(broker));
		} else {
			old.timeAdded = System.currentTimeMillis();
			old.trace();
		}
	}
	
	public void remove(DBBroker broker) {
		watched.remove(broker);
	}
	
	public String get(DBBroker broker) {
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
						" did not return for 30sec.\n\n" + broker.trace.toString());
			}
		}
	}
	
	public void dump(PrintWriter writer) {
		writer.println("Active brokers:");
		for (final WatchedBroker broker: watched.values()) {
			writer.format("%20s: %s\n", "Broker", broker.broker.getId());
			writer.format("%20s: %s\n", "Active since", df.format(new Date(broker.timeAdded)));
			writer.println("\nStack:");
			writer.println(broker.trace);
			writer.println("----------------------------------------------------------------");
		}
	}
}
