/*
 * Session.java - Jun 30, 2003
 * 
 * @author wolf
 */
package org.exist.soap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.exist.security.Subject;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

public class Session implements Serializable {

	private static final long serialVersionUID = -5863393640379705401L;

	public static class QueryResult {
		Sequence result;

		public QueryResult(Sequence value) {
			this.result = value;
		}
	}

	private Subject user_;
	private QueryResult lastQuery_ = null;
	private long lastAccessTime_ = System.currentTimeMillis();
	
	/**
	 * 
	 */
	public Session(Subject user) {
		user_ = user;
	}

	public Subject getUser() {
		return user_;
	}
	
	public void addQueryResult(Sequence value) {
		lastQuery_ = new QueryResult(value);
	}
	
	public QueryResult getQueryResult() {
		return lastQuery_;
	}
	
	public long getLastAccessTime() {
		return lastAccessTime_;
	}
	
	public void updateLastAccessTime() {
		lastAccessTime_ = System.currentTimeMillis();
	}
        
        private List<XQueryContext> contexts;
        public void registerContext(XQueryContext context) {
            if(contexts == null) {
                contexts = new ArrayList<XQueryContext>();
            }
            contexts.add(context);
        }
        
        public void cleanupContexts() {
            if(contexts != null) {
                for(final XQueryContext context : contexts) {
                    context.runCleanupTasks();
                }
            }
        }
}
