package org.exist.util;

import org.exist.util.hashtable.ObjectHashSet;

/**
 * Class to count element and word frequencies.
 */
public class Occurrences implements Comparable {

	private Comparable term;
	private int occurrences = 0;
	private ObjectHashSet docs = new ObjectHashSet(1024);
    
	public Occurrences(Comparable name) {
		term = name;
	}

	public Comparable getTerm() {
		return term;
	}

    /**
     * Returns the overall frequency of this term
     * in the document set.
     * 
     * @return
     */
	public int getOccurrences() {
		return occurrences;
	}

	public void addOccurrences(int count) {
		occurrences += count;
	}

    public void addDocument(int docId) {
        Integer i = new Integer(docId);
        if(!docs.contains(i))
            docs.add(i);
    }
    
    /**
     * Returns the number of documents from the set in
     * which the term has been found.
     * 
     * @return
     */
    public int getDocuments() {
        return docs.size();
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
