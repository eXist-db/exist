package org.exist.xmlrpc;

import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

public class QueryResult {
	
	protected long queryTime = 0;
	protected Sequence result;
	protected XQueryContext context;
	protected long timestamp = 0;

	public QueryResult(XQueryContext context, Sequence result) {
		this(context, result, 0);
	}
	
	public QueryResult(XQueryContext context, Sequence result, long queryTime) {
		this.context = context;
		this.result = result;
		this.queryTime = queryTime;
		this.timestamp = System.currentTimeMillis();
	}
}
