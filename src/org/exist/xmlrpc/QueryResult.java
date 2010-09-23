package org.exist.xmlrpc;

import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.Option;
import org.exist.xquery.value.Sequence;

import java.util.Properties;

/**
 * Simple container for the results of a query. Used to cache
 * query results that may be retrieved later by the client.
 * 
 * @author wolf
 * @author jmfernandez
 */
public class QueryResult
	extends AbstractCachedResult
{
	protected Sequence result;
    protected Properties serialization = null;
	
	// set upon failure
	protected XPathException exception = null;
	
	public QueryResult(Sequence result, Properties outputProperties) {
		this(result, outputProperties, 0);
	}
	
	public QueryResult(Sequence result, Properties outputProperties, long queryTime) {
		super(queryTime);
        this.serialization = outputProperties;
		this.result = result;
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
	 * @return Returns the result.
	 */
	public Sequence getResult() {
		return result;
	}
	
	public void free() {
		// Really, nothing to explicitly free
		if(result!=null) {
			result=null;
		}
	}
}
