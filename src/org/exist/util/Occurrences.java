package org.exist.util;

/**
 * Class to count element and word frequencies.
 * 
 */
public class Occurrences implements Comparable {

	private Comparable term;
	private int occurrences = 0;

	public Occurrences(Comparable name) {
		term = name;
	}

	public Comparable getTerm() {
		return term;
	}

	public int getOccurrences() {
		return occurrences;
	}

	public void addOccurrences(int count) {
		occurrences += count;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return term.compareTo(((Occurrences) o).term);
	}
}
