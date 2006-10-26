package org.exist.xmlrpc;

import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

/**
 * Simple container for the results of a query. Used to cache
 * query results that may be retrieved later by the client.
 * 
 * @author wolf
 */
public class QueryResult {
	
	protected long queryTime = 0;
	protected Sequence result;
	protected XQueryContext context;
	protected long timestamp = 0;
	
	// set upon failure
	protected XPathException exception = null;
	
	public QueryResult(XQueryContext context, Sequence result) {
		this(context, result, 0);
	}
	
	public QueryResult(XQueryContext context, Sequence result, long queryTime) {
		this.context = context;
		this.result = result;
		this.queryTime = queryTime;
		this.timestamp = System.currentTimeMillis();
	}
	
	public QueryResult(XPathException e) {
		exception = e;
	}
	
	public boolean hasErrors() {
		return exception != null;
	}
	
	public XPathException getException() {
		return exception;
	}
	
	/**
	 * @return Returns the context.
	 */
	public XQueryContext getContext() {
		return context;
	}
	
	/**
	 * @return Returns the queryTime.
	 */
	public long getQueryTime() {
		return queryTime;
	}
	
	/**
	 * @return Returns the result.
	 */
	public Sequence getResult() {
		return result;
	}
	
	/**
	 * @return Returns the timestamp.
	 */
	public long getTimestamp() {
		return timestamp;
	}
}
