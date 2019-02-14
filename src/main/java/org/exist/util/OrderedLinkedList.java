package org.exist.util;

/* eXist Native XML Database
 * Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id$
 */

import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OrderedLinkedList {

	@SuppressWarnings("unused")
	private final static Logger LOG = LogManager.getLogger(OrderedLinkedList.class);
	
	public abstract static class Node {
		Node next = null;
		Node prev = null;

		public Node getNextNode() { return next; }
		public Node getPrevNode() { return prev; }

		public abstract int compareTo(Node other);
		public abstract boolean equals(Node other);
	}

	public static class SimpleNode extends Node {
		Comparable data;
		
		public SimpleNode(Comparable data) {
			this.data = data;
		}
		
		public int compareTo(Node other) {
			final SimpleNode o = (SimpleNode)other;
			return data.compareTo(o.data);
		}
		
		public boolean equals(Node other) {
			return ((SimpleNode)other).data.equals(data);
		}
		
		public Comparable getData() {
			return data;
		}
	}
	
	protected Node header = null;
    protected Node last = null;
    
	private int size = 0;

	
	public Node add(Node newNode) {
		newNode.next = null;
		newNode.prev = null;
		size++;
		if (header == null) {
			header = newNode;
            last = header;
			return header;
		}
		Node node = header;
		while (newNode.compareTo(node) > 0) {
			if (node.next == null) {
				// append to end of list
				node.next = newNode;
				newNode.prev = node;
                last = newNode;
				return newNode;
			}
			node = node.next;
		}
		// insert node
		newNode.prev = node.prev;
		if (newNode.prev != null)
			{newNode.prev.next = newNode;}
		node.prev = newNode;
		newNode.next = node;
		if (node == header)
			{header = newNode;}
        return newNode;
	}
    
	public void remove(Node n) {
		Node node = header;
		while (node != null) {
			if (node.equals(n)) {
				removeNode(n);
				return;
			}
			node = node.next;
		}
	}
    
	public void removeNode(Node node) {
		--size;
		if (node.prev == null) {
			if (node.next != null) {
				node.next.prev = null;
				header = node.next;
			} else
				{header = null;}
		} else {
			node.prev.next = node.next;
			if (node.next != null)
				{node.next.prev = node.prev;}
            else
                {last = node.prev;}
		}
	}
	
    public Node removeFirst() {
        final Node node = header;
        header = node.next;
        if(header != null)
            {header.prev = null;}
        --size;
        return  node;
    }

    public Node removeLast() {
        final Node node = last;
        last = node.prev;
        last.next = null;
        --size;
        return node;
    }
    
    public Node getLast() {
    	return last == null ? null : last;
    }
    
	public Node get(int pos) {
		Node node = header;
		int count = 0;
		while (node != null) {
			if (count++ == pos)
				{return node;}
			node = node.next;
		}
		return null;
	}

	public Node[] getData() {
		final Node[] data = new Node[size];
		Node next = header;
		int i = 0;
		while( next != null ) {
			data[i++] = next;
			next = next.next;
		}
		return data;
    }
	
	public Node[] toArray(Node[] data) {
		Node next = header;
		int i = 0;
		while( next != null ) {
			data[i++] = next;
			next = next.next;
		}
		return data;
	}
	
	public boolean contains(Node c) {
    	Node next = header;
    	while( next != null ) {
    		if(next.equals(c))
    			{return true;}
    		next = next.next;
    	}
    	return false;
    }
	
	public int size() {
		return size;
	}

	public void reset() {
		header = null;
        last = null;
        size = 0;
	}

	public Iterator iterator() {
		return new OrderedListIterator(header);
	}

	private final static class OrderedListIterator implements Iterator {

		private Node next;

		public OrderedListIterator(Node header) {
			this.next = header;
		}

		public boolean hasNext() {
			return (next != null);
		}

		public Object next() {
			if (next == null)
				{return null;}
			final Node current = next;
			next = next.next;
			return current;
		}

		public void remove() {
			throw new RuntimeException("not implemented");
		}
	}
}
