/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
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

import java.util.TreeMap;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Arrays;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.apache.log4j.Category;

public class DocumentSet extends TreeMap implements NodeList {

    protected static Category LOG = Category.getInstance(DocumentSet.class.getName());
    protected ArrayList list = new ArrayList();
    protected boolean allDocuments = false;
    protected TreeSet collections = new TreeSet();

    public DocumentSet() {
        super();
    }

    public void setAllDocuments(boolean all) { allDocuments = all; }

    public boolean hasAllDocuments() { return allDocuments; }

    public void add(DocumentImpl doc) {
        Integer docId = new Integer(doc.docId);
        if(containsKey(docId))
            return;
        put(docId, doc);
        list.add(doc);
	if(doc.getCollection() != null && 
	   (!collections.contains(doc.getCollection())))
	    collections.add(doc.getCollection());
    }

    public void add(Node node) {
        if(!(node instanceof DocumentImpl))
            throw new RuntimeException("wrong implementation");
        add((DocumentImpl)node);
    }

    public void addAll(NodeList other) {
        for(int i = 0; i < other.getLength(); i++)
            add(other.item(i));
    }

    public Iterator iterator() {
        return values().iterator();
    }

    public Iterator getCollectionIterator() {
	return collections.iterator();
    }

    public int getLength() {
        return size();
    }

    public Node item(int pos) {
	return (Node)list.get( pos );
    }

    /*
    public boolean contains(int docId) {
        return containsKey(docId);
        //return containsKey(new Integer(docId));
    }
    */

    public DocumentImpl getDoc(int docId) {
        return (DocumentImpl)get(new Integer(docId));
    }

    public String[] getNames() {
	String result[] = new String[list.size()];
	DocumentImpl d;
	int j = 0;
	for(Iterator i = list.iterator(); i.hasNext(); j++) {
	    d = (DocumentImpl)i.next();
	    result[j] = d.getFileName();
	}
	Arrays.sort(result);
	return result;
    }

    public DocumentSet intersection(DocumentSet other) {
        DocumentSet r = new DocumentSet();
        DocumentImpl d;
	Integer docId;
        for(Iterator i = list.iterator(); i.hasNext(); ) {
            d = (DocumentImpl)i.next();
	    docId = new Integer(d.docId);
            if(other.containsKey(docId))
                r.add(d);
        }
        for(Iterator i = other.list.iterator(); i.hasNext(); ) {
            d = (DocumentImpl)i.next();
	    docId = new Integer(d.docId);
            if(containsKey(docId) && (!r.containsKey(docId)))
                r.add(d);
        }
        return r;
    }
    
    public DocumentSet union(DocumentSet other) {
        DocumentSet result = new DocumentSet();
        result.addAll(other);
        DocumentImpl d;
	Integer docId;
        for(Iterator i = list.iterator(); i.hasNext(); ) {
            d = (DocumentImpl)i.next();
	    docId = new Integer(d.docId);
            if(!result.containsKey(docId))
                result.add(d);
        }
        return result;
    }
    
    public boolean contains(DocumentSet other) {
        if(other.list.size() > list.size())
            return false;
        DocumentImpl d;
        boolean equal = false;
        for(Iterator i = other.list.iterator(); i.hasNext(); ) {
            d = (DocumentImpl)i.next();
            if(containsKey(new Integer(d.docId)))
                equal = true;
            else
                equal = false;
        }
        return equal;
    }

    public boolean contains(int id) {
	return containsKey(new Integer(id));
    }

    public int getMinDocId() {
	int min = -1;
	DocumentImpl d;
	for(Iterator i = list.iterator(); i.hasNext(); ) {
	    d = (DocumentImpl)i.next();
	    if(min < 0)
		min = d.getDocId();
	    else if(d.getDocId() < min)
		min = d.getDocId();
	}
	return min;
    }

    public int getMaxDocId() {
	int max = -1;
	DocumentImpl d;
	for(Iterator i = list.iterator(); i.hasNext(); ) {
	    d = (DocumentImpl)i.next();
	    if(d.getDocId() > max)
		max = d.getDocId();
	}
	return max;
    }

    public boolean equals(Object other) {
        DocumentSet o = (DocumentSet)other;
        if(list.size() != o.list.size())
            return false;
        DocumentImpl d;
        boolean equal = false;
        for(Iterator i = list.iterator(); i.hasNext(); ) {
            d = (DocumentImpl)i.next();
            if(o.containsKey(new Integer(d.docId)))
                equal = true;
            else
                equal = false;
        }
        return equal;
    }
}
