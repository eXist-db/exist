package org.exist.soap;

import java.util.Iterator;

import it.unimi.dsi.fastUtil.Int2ObjectOpenHashMap;

import org.exist.xpath.Value;

public class Sessions {

	private static long TIMEOUT = 600000;
	private static Sessions instance = null;

	public static final Sessions getInstance() {
		if (instance == null)
			instance = new Sessions();
		return instance;
	}

	public static long getTimeout() {
		return TIMEOUT;
	}

	public static void setTimeout(long timeout) {
		TIMEOUT = timeout;
	}

	Int2ObjectOpenHashMap resultSets = new Int2ObjectOpenHashMap(25);

	public int addQueryResult(Value val) {
		QueryResult result = new QueryResult(val);
		resultSets.put(result.hashCode(), result);
		return result.hashCode();
	}

	public Value getQueryResult(int sessionId) {
		QueryResult qr = (QueryResult) resultSets.get(sessionId);
		return qr != null ? qr.result : null;
	}

	private void checkResultSets() {
		for (Iterator i = resultSets.values().iterator(); i.hasNext();) {
			final QueryResult qr = (QueryResult) i.next();
			long ts = ((QueryResult) qr).timestamp;
			if (System.currentTimeMillis() - ts > TIMEOUT) {
				i.remove();
			}
		}
	}

	private class QueryResult {
		Value result;
		long timestamp = 0;

		public QueryResult(Value value) {
			this.result = value;
			this.timestamp = System.currentTimeMillis();
		}
	}
}
