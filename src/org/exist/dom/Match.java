/*
 * Match.java - Mar 20, 2003
 * 
 * @author wolf
 */
package org.exist.dom;

public class Match {
	
	private String matchTerm;
	private long nodeId;
	
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
	
	public boolean equals(Object other) {
		if(!(other instanceof Match))
			return false;
		return ((Match)other).matchTerm.equals(matchTerm) &&
			((Match)other).nodeId == nodeId;
	}
}
