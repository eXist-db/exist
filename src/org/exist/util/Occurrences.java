/*
 * ElementOccurrences.java - Mar 5, 2003
 * 
 * @author wolf
 */
package org.exist.util;

/**
 * @author wolf
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class Occurrences implements Comparable {

	private String term;
	private int occurrences = 0;
	
	public Occurrences(String name) {
		term = name;
	}
	
	public String getTerm() {
		return term;
	}
	
	public int getOccurrences() {
		return occurrences;
	}
	
	public void addOccurrences(int count) {
		occurrences += count;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return term.compareTo(((Occurrences)o).term);
	}

}
