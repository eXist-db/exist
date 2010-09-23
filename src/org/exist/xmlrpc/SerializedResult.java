package org.exist.xmlrpc;

import org.exist.util.VirtualTempFile;
import org.exist.xquery.XPathException;

/**
 * Simple container for the results of a query. Used to cache
 * query results that may be retrieved later by the client.
 * 
 * @author jmfernandez
 */
public class SerializedResult
	extends AbstractCachedResult
{
	protected VirtualTempFile result;
	
	// set upon failure
	protected XPathException exception = null;
	
	public SerializedResult(VirtualTempFile result) {
		this(result, 0);
	}
	
	public SerializedResult(VirtualTempFile result, long queryTime) {
		super(queryTime);
		this.result = result;
	}
	
	public SerializedResult(XPathException e) {
		exception = e;
	}
	
	/**
	 * @return Returns the result.
	 */
	public VirtualTempFile getResult() {
		return result;
	}
	
	public void free() {
		if(result!=null) {
			result.delete();
			result = null;
		}
	}
}
