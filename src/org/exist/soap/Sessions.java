
package org.exist.soap;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

import org.exist.xpath.Value;

public class Sessions {

    private static long TIMEOUT = 600000;
    private static Sessions instance = null;

    public static final Sessions getInstance() {
	if(instance == null)
	    instance = new Sessions();
	return instance;
    }

    public static long getTimeout() {
	return TIMEOUT;
    }

    public static void setTimeout(long timeout) {
	TIMEOUT = timeout;
    }

    TIntObjectHashMap resultSets = new TIntObjectHashMap(25);

    public int addQueryResult(Value val) {
	QueryResult result = new QueryResult(val);
	resultSets.put(result.hashCode(), result);
	return result.hashCode();
    }

    public Value getQueryResult(int sessionId) {
	QueryResult qr = (QueryResult)resultSets.get(sessionId);
	return qr != null ? qr.result : null;
    }

    private void checkResultSets() {
	resultSets.forEachEntry(new CheckProcedure());
    }
    
    private class CheckProcedure implements TIntObjectProcedure {
	
	public boolean execute(int hashCode, Object qr) {
	    long ts = ((QueryResult) qr).timestamp;
	    if (System.currentTimeMillis() - ts > TIMEOUT) {
		resultSets.remove(hashCode);
	    }
	    return true;
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
