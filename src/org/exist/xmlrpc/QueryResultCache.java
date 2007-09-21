package org.exist.xmlrpc;

import org.apache.log4j.Logger;

import java.util.Date;

/**
 * Used by {@link RpcServer} to cache query results. Each query result
 * is identified by a unique integer id.
 */
public class QueryResultCache {

    public final static int TIMEOUT = 180000;

    private static final int INITIAL_SIZE = 254;
    
    public QueryResult[] results;

    private static final Logger LOG = Logger.getLogger(QueryResultCache.class);

    public QueryResultCache() {
        results = new QueryResult[INITIAL_SIZE];
    }

    public int add(QueryResult qr) {
        for (int i = 0; i < results.length; i++) {
            if (results[i] == null) {
                results[i] = qr;
                return i;
            }
        }
        // no empty bucket. need to resize.
        QueryResult[] temp = new QueryResult[(results.length * 3) / 2];
        System.arraycopy(results, 0, temp, 0, results.length);
        int pos = results.length;
        temp[pos] = qr;
        results = temp;
        return pos;
    }

    public QueryResult get(int pos) {
        if (pos < 0 || pos >= results.length)
            return null;
        return results[pos];
    }

    public void remove(int pos) {
        if (pos > -1 && pos < results.length)
            results[pos] = null;
    }

    public void checkTimestamps() {
        final long now = System.currentTimeMillis();
        for (int i = 0; i < results.length; i++) {
            QueryResult result = results[i];
            if (result != null) {
                if (now - result.getTimestamp() > TIMEOUT) {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Removing result set " + new Date(result.getTimestamp()).toString());
                    results[i] = null;
                }
            }
        }
    }
}
