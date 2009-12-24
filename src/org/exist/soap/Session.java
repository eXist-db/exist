/*
 * Session.java - Jun 30, 2003
 * 
 * @author wolf
 */
package org.exist.soap;

import java.io.Serializable;

import org.exist.security.User;
import org.exist.xquery.value.Sequence;

public class Session implements Serializable {

	private static final long serialVersionUID = -5863393640379705401L;

	public static class QueryResult {
		Sequence result;

		public QueryResult(Sequence value) {
			this.result = value;
		}
	}

	private User user_;
	private QueryResult lastQuery_ = null;
	private long lastAccessTime_ = System.currentTimeMillis();
	
	/**
	 * 
	 */
	public Session(User user) {
		user_ = user;
	}

	public User getUser() {
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
}
