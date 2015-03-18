package org.exist.xmlrpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;

/**
 * Used by {@link XmldbRequestProcessorFactory} to cache query results. Each query result
 * is identified by a unique integer id.
 */
public class QueryResultCache {

    public final static int TIMEOUT = 180000;

    private static final int INITIAL_SIZE = 254;
    
    public AbstractCachedResult[] results;

    private static final Logger LOG = LogManager.getLogger(QueryResultCache.class);

    public QueryResultCache() {
        results = new AbstractCachedResult[INITIAL_SIZE];
    }

    public int add(AbstractCachedResult qr) {
        for (int i = 0; i < results.length; i++) {
            if (results[i] == null) {
                results[i] = qr;
                return i;
            }
        }
        // no empty bucket. need to resize.
        AbstractCachedResult[] temp = new AbstractCachedResult[(results.length * 3) / 2];
        System.arraycopy(results, 0, temp, 0, results.length);
        final int pos = results.length;
        temp[pos] = qr;
        results = temp;
        return pos;
    }

    public AbstractCachedResult get(int pos) {
        if (pos < 0 || pos >= results.length)
            {return null;}
        return results[pos];
    }
    
    public QueryResult getResult(int pos) {
    	final AbstractCachedResult acr = get(pos);
    	
    	return (acr!=null && acr instanceof QueryResult)?(QueryResult)acr:null;
    }
    
    public SerializedResult getSerializedResult(int pos) {
    	final AbstractCachedResult acr = get(pos);
    	
    	return (acr!=null && acr instanceof SerializedResult)?(SerializedResult)acr:null;
    }
    
    public void remove(int pos) {
        if (pos > -1 && pos < results.length) {
        	// Perhaps we should not free resources here
        	// but an explicit remove implies you want
        	// to free resources
        	
        	if (results[pos] != null) { // Prevent NPE
        		results[pos].free();
        		results[pos] = null;
        	}
        }
    }

    public void remove(int pos, int hash) {
        if (pos > -1 && pos < results.length && (results[pos] != null && results[pos].hashCode() == hash)) {
        	// Perhaps we should not free resources here
        	// but an explicit remove implies you want
        	// to free resources
        	results[pos].free();
            results[pos] = null;
        }
    }

    public void checkTimestamps() {
        final long now = System.currentTimeMillis();
        for (int i = 0; i < results.length; i++) {
        	final AbstractCachedResult result = results[i];
            if (result != null) {
                if (now - result.getTimestamp() > TIMEOUT) {
                    if (LOG.isDebugEnabled())
                        {LOG.debug("Removing result set " + new Date(result.getTimestamp()).toString());}
                    // Here we should not free resources, because they could be still in use
                    // by other threads, so leave the work to the garbage collector
                    results[i] = null;
                }
            }
        }
    }
}
