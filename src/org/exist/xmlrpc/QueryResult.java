/*
 * QueryResult.java - Mar 28, 2003
 * 
 * @author wolf
 */
package org.exist.xmlrpc;

import org.exist.xpath.Value;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class QueryResult {
	long queryTime = 0;
	Value result;
	long timestamp = 0;

	public QueryResult(Value result, long queryTime) {
		this.result = result;
		this.queryTime = queryTime;
		this.timestamp = System.currentTimeMillis();
	}
}
