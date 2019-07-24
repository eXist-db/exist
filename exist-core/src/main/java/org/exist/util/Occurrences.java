/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.util;

import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.MutableDocumentSet;

/**
 * Class to count element and word frequencies.
 */
public class Occurrences implements Comparable<Occurrences> {

	private Comparable term;
	private int occurrences = 0;
	private MutableDocumentSet docs = new DefaultDocumentSet();
    
	public Occurrences(final Comparable name) {
		this.term = name;
	}

    public Occurrences(final Comparable name, final int occurrences) {
        term = name;
        this.occurrences = occurrences;
    }

	public Comparable getTerm() {
		return term;
	}

    /**
     * Returns the overall frequency of this term
     * in the document set.
	 *
	 * @return the occurrences
     */
	public int getOccurrences() {
		return occurrences;
	}

	public void addOccurrences(int count) {
		occurrences += count;
	}

    public void addDocument(DocumentImpl doc) {
        if(!docs.contains(doc.getDocId()))
            {docs.add(doc);}
    }
    
    public void add(Occurrences other) {
    	addOccurrences(other.occurrences);
    	docs.addAll(other.docs);
    }
    
    /**
     * Returns the number of documents from the set in
     * which the term has been found.
	 *
	 * @return the number of documents
     */
    public int getDocuments() {
        return docs.getDocumentCount();
    }
    
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Occurrences o) {
		return term.compareTo(o.term);
	}
}
