package org.exist.util;

/* eXist xml document repository and xpath implementation
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

public class OrderedLinkedList {

	private final static class Node {
		Comparable data;
		Node next = null;
		Node prev = null;

		public Node(Comparable data) {
			this.data = data;
		}
        
	}

	private Node header = null;
    private Node last = null;
    
	private int size = 0;

	public OrderedLinkedList() {
	}

	public Node add(Comparable obj) {
		size++;
		if (header == null) {
			header = new Node(obj);
            last = header;
			return header;
		}
		Node newNode = new Node(obj);
		Node node = header;
		while (obj.compareTo(node.data) > 0) {
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
			newNode.prev.next = newNode;
		node.prev = newNode;
		newNode.next = node;
		if (node == header)
			header = newNode;
        return newNode;
	}
    
	public void remove(Comparable obj) {
		Node node = header;
		while (node != null) {
			if (node.data == obj) {
				if (node.prev == null) {
					if (node.next != null) {
						node.next.prev = null;
						header = node.next;
					} else
						header = null;
				} else {
					node.prev.next = node.next;
					if (node.next != null)
						node.next.prev = node.prev;
                    else
                        last = node.prev;
				}
				size--;
				return;
			}
			node = node.next;
		}
	}
    
    public Object removeFirst() {
        Node node = header;
        header = node.next;
        if(header != null)
            header.prev = null;
        --size;
        return  node.data;
    }

    public Object removeLast() {
        Node node = last;
        last = node.prev;
        last.next = null;
        --size;
        return node.data;
    }
           
	public Object get(int pos) {
		Node node = header;
		int count = 0;
		while (node != null) {
			if (count == pos)
				return node.data;
			node = node.next;
		}
		return null;
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
				return null;
			Node current = next;
			next = next.next;
			return current.data;
		}

		public void remove() {
			throw new RuntimeException("not implemented");
		}
	}

	public static void main(String args[]) {
		OrderedLinkedList list = new OrderedLinkedList();
		list.add("Adam");
		list.add("Sabine");
		list.add("Adam");
		list.add("Georg");
		list.add("Heinrich");
		list.add("Georg");
		list.add("Wolfgang");
		list.add("Egon");
		list.add("Berta");
		list.add("Fritz");
		list.remove("Berta");
		list.add("Hans");
		list.add("Xerces");
		list.add("Hubert");
		list.add("Georg");
		list.remove("Xerces");
		list.remove("Wolfgang");
		for (Iterator i = list.iterator(); i.hasNext();)
			System.out.println((String) i.next());
	}
}
