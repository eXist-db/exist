package org.exist.xmlrpc;

/**
 * Simple abstract container for serialized resources or results of a query.
 * Used to cache them that may be retrieved by chunks later by the client.
 * 
 * @author wolf
 * @author jmfernandez
 */
public abstract class AbstractCachedResult {
	
	protected long queryTime = 0;
	protected long creationTimestamp = 0; 
	protected long timestamp = 0; 
	
	public AbstractCachedResult() {
		this(0);
	}
	
	public AbstractCachedResult(long queryTime) {
		this.queryTime = queryTime;
		touch();
		this.creationTimestamp = this.timestamp;
	}
	
	/**
	 * @return Returns the queryTime.
	 */
	public long getQueryTime() {
		return queryTime;
	}
	
	/**
	 * @return Returns the timestamp.
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * This method can be used to explicitly update the
	 * last time the cached result has been used
	 */
	public void touch() {
		timestamp = System.currentTimeMillis();
	}
	
	/**
	 * @return Returns the timestamp.
	 */
	public long getCreationTimestamp() {
		return creationTimestamp;
	}
	
	/**
	 * This abstract method must be used
	 * to free internal variables.
	 */
	public abstract void free();
	
	/**
	 * This abstract method returns the cached result
	 * or null
	 * @return The object which is being cached
	 */
	public abstract Object getResult();
	
	protected void finalize()
		throws Throwable
	{
		// Calling free to reclaim pinned resources
		free();
	}
}
