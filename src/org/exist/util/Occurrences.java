package org.exist.util;

import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.MutableDocumentSet;

/**
 * Class to count element and word frequencies.
 */
public class Occurrences implements Comparable {

	private Comparable term;
	private int occurrences = 0;
	private MutableDocumentSet docs = new DefaultDocumentSet();
    
	public Occurrences(Comparable name) {
		term = name;
	}

	public Comparable getTerm() {
		return term;
	}

    /**
     * Returns the overall frequency of this term
     * in the document set.
     */
	public int getOccurrences() {
		return occurrences;
	}

	public void addOccurrences(int count) {
		occurrences += count;
	}

    public void addDocument(DocumentImpl doc) {
        if(!docs.contains(doc.getDocId()))
            docs.add(doc);
    }
    
    public void add(Occurrences other) {
    	addOccurrences(other.occurrences);
    	docs.addAll(other.docs);
    }
    
    /**
     * Returns the number of documents from the set in
     * which the term has been found.
     */
    public int getDocuments() {
        return docs.getDocumentCount();
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
