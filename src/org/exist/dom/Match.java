/*
 * Match.java - Mar 20, 2003
 * 
 * @author wolf
 */
package org.exist.dom;

public class Match implements Comparable {
	
	private String matchTerm;
	private long nodeId;
	protected Match nextMatch = null;
	protected Match prevMatch = null;
	
	public Match(String matchTerm, long nodeId) {
		this.matchTerm = matchTerm;
		this.nodeId = nodeId;
	}
	
	public String getMatchingTerm() {
		return matchTerm;
	}
	
	public long getNodeId() {
		return nodeId;
	}
	
	public Match getNextMatch() {
		return nextMatch;
	}
	
	public boolean equals(Object other) {
		if(!(other instanceof Match))
			return false;
		return ((Match)other).matchTerm.equals(matchTerm) &&
			((Match)other).nodeId == nodeId;
	}
	
	/**
	 * Used to sort matches. Terms are compared by their string 
	 * length to have the longest string first.
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		Match other = (Match)o;
		return other.matchTerm.compareTo(matchTerm);
	}

}
