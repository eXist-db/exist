package org.exist.xmlrpc;

import org.exist.xquery.value.Sequence;

public class QueryResult {
	long queryTime = 0;
	Sequence result;
	long timestamp = 0;

	public QueryResult(Sequence result) {
		this(result, 0);
	}
	
	public QueryResult(Sequence result, long queryTime) {
		this.result = result;
		this.queryTime = queryTime;
		this.timestamp = System.currentTimeMillis();
	}
}
