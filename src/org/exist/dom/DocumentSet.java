/* 
 * eXist Open Source Native XML Database
 * 
 * Copyright (C) 2000,  Wolfgang Meier (wolfgang@exist-db.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.dom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

import org.apache.log4j.Category;
import org.exist.collections.*;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Manages a set of documents.
 * 
 * This class implements the NodeList interface for a collection of documents.
 * It also contains methods to retrieve the collections these documents
 * belong to.
 * 
 * @author wolf
 */
public class DocumentSet extends Int2ObjectHashMap implements NodeList {

	private final static Category LOG =
		Category.getInstance(DocumentSet.class.getName());
	private ArrayList list = null;
	private boolean allDocuments = false;
	private TreeSet collections = new TreeSet();

	public DocumentSet() {
		super();
	}

	public void clear() {
		super.clear();
		collections = new TreeSet();
		list = null;
		allDocuments = false;
	}
	
	public void setAllDocuments(boolean all) {
		allDocuments = all;
	}

	public boolean hasAllDocuments() {
		return allDocuments;
	}

	public void add(DocumentImpl doc) {
		final int docId = doc.getDocId();
		if (containsKey(docId))
			return;
		put(docId, doc);
		if (list != null)
			list.add(doc);
		if (doc.getCollection() != null
			&& (!collections.contains(doc.getCollection())))
			collections.add(doc.getCollection());
	}

	public void add(Node node) {
		if (!(node instanceof DocumentImpl))
			throw new RuntimeException("wrong implementation");
		add((DocumentImpl) node);
	}

	public void addAll(NodeList other) {
		for (int i = 0; i < other.getLength(); i++)
			add(other.item(i));
	}
	
	/**
	 * Fast method to add a bunch of documents from a
	 * Java collection.
	 * 
	 * The method assumes that no duplicate entries are
	 * in the input collection.
	 * 
	 * @param docs
	 */
	public void addAll(java.util.Collection docs) {
		DocumentImpl doc;
		for(Iterator i = docs.iterator(); i.hasNext(); ) {
			doc = (DocumentImpl)i.next();
			if(!doc.isLockedForWrite())
				put(doc.getDocId(), doc);
		}
	}

	public void addCollection(Collection collection) {
		collections.add(collection);
	}

	public Iterator iterator() {
		return valueIterator();
	}

	public Iterator getCollectionIterator() {
		return collections.iterator();
	}

	public int getLength() {
		return size();
	}

	public int getCollectionCount() {
		return collections.size();
	}
	
	public Node item(int pos) {
		if (list == null) {
			list = new ArrayList();
			for(Iterator i = valueIterator(); i.hasNext(); )
				list.add(i.next());
		}
		return (Node) list.get(pos);
	}

	/*
	public boolean contains(int docId) {
	    return containsKey(docId);
	    //return containsKey(new Integer(docId));
	}
	*/

	public DocumentImpl getDoc(int docId) {
		return (DocumentImpl) get(docId);
	}

	public String[] getNames() {
		String result[] = new String[size()];
		DocumentImpl d;
		int j = 0;
		for (Iterator i = iterator(); i.hasNext(); j++) {
			d = (DocumentImpl) i.next();
			result[j] = d.getFileName();
		}
		Arrays.sort(result);
		return result;
	}

	public DocumentSet intersection(DocumentSet other) {
		DocumentSet r = new DocumentSet();
		DocumentImpl d;
		for (Iterator i = iterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (other.containsKey(d.docId))
				r.add(d);
		}
		for (Iterator i = other.iterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (containsKey(d.docId) && (!r.containsKey(d.docId)))
				r.add(d);
		}
		return r;
	}

	public DocumentSet union(DocumentSet other) {
		DocumentSet result = new DocumentSet();
		result.addAll(other);
		DocumentImpl d;
		for (Iterator i = iterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (!result.containsKey(d.docId))
				result.add(d);
		}
		return result;
	}

	public boolean contains(DocumentSet other) {
		if (other.size() > size())
			return false;
		DocumentImpl d;
		boolean equal = false;
		for (Iterator i = other.iterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (containsKey(d.docId))
				equal = true;
			else
				equal = false;
		}
		return equal;
	}

	public boolean contains(int id) {
		return containsKey(id);
	}

	public int getMinDocId() {
		int min = -1;
		DocumentImpl d;
		for (Iterator i = iterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (min < 0)
				min = d.getDocId();
			else if (d.getDocId() < min)
				min = d.getDocId();
		}
		return min;
	}

	public int getMaxDocId() {
		int max = -1;
		DocumentImpl d;
		for (Iterator i = iterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (d.getDocId() > max)
				max = d.getDocId();
		}
		return max;
	}

	public boolean equals(Object other) {
		DocumentSet o = (DocumentSet) other;
		if (size() != o.size())
			return false;
		DocumentImpl d;
		boolean equal = false;
		for (Iterator i = iterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (o.containsKey(d.docId))
				equal = true;
			else
				equal = false;
		}
		return equal;
	}
}
