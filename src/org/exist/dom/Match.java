/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *  and others (see http://exist-db.org)
 * 
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.dom;

/**
 * Used to track fulltext matches throughout the query.
 * 
 * {@link org.exist.storage.TextSearchEngine} will add a
 * match object to every {@link org.exist.dom.NodeProxy}
 * that triggered a fulltext match for every term matched. The 
 * Match object contains the nodeId of the text node that triggered the
 * match, the string value of the matching term and a frequency count,
 * indicating the frequency of the matching term string within the corresponding
 * single text node.
 * 
 * All path operations copy existing match objects, i.e. the match objects
 * are copied to the selected descendant or child nodes. This means that
 * every NodeProxy being the direct or indirect result of a fulltext
 * selection will have one or more match objects, indicating which text nodes
 * among its descendant nodes contained a fulltext match.
 * 
 * @author wolf
 */
public class Match implements Comparable {
	
	private String matchTerm;
	private long nodeId;
	private int frequency = 1;
	
	protected Match nextMatch = null;
	protected Match prevMatch = null;
	
	public Match(String matchTerm, long nodeId) {
		this.matchTerm = matchTerm;
		this.nodeId = nodeId;
	}
	
	public Match(Match match) {
		this.matchTerm = match.matchTerm;
		this.nodeId = match.nodeId;
		this.frequency = match.frequency;
	}
	
	public String getMatchingTerm() {
		return matchTerm;
	}
	
	public long getNodeId() {
		return nodeId;
	}
	
	public void setFrequency(int freq) {
		this.frequency = freq;
	}
	
	public int getFrequency() {
		return frequency;
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
		return matchTerm.compareTo(other.matchTerm);
	}
}
