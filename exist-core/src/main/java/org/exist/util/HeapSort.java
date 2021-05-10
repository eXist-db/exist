/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util;

import java.util.Comparator;
import java.util.List;

import org.exist.dom.persistent.NodeProxy;

/**
 * This class implements a simple version 
 * of the heapsort algorithm.
 *
 * This work was undertaken as part of the development of the taxonomic
 * repository at http://biodiversity.org.au . See <A
 * href="ghw-at-anbg.gov.au">Greg&nbsp;Whitbread</A> for further details.
 * 
 * @author pmurray@bigpond.com
 * @author pmurray@anbg.gov.au
 * @author https://sourceforge.net/users/paulmurray
 * @author http://www.users.bigpond.com/pmurray
 */
public final class HeapSort {
    /*
     * Briefly: we impose a tree structure on an array by treating the "child"
     * nodes of node N as nodes N2 and N2+1. Obviously, the root of the tree is
     * node 1.
     * 
     * A "valid" tree has the property that both child nodes of every node have
     * a value <= the value of the node. Note that this is considerably weaker
     * than the tree being sorted.
     * 
     * The "heap fix" routine does the following: assume that the trees rooted
     * at location 2 and 3 are valid but that the node at location 1 is any value.
     * While the node of interest is not greater than both its child nodes, move it
     * down into the tree by swapping it with the greater of its two child nodes.
     * 
     * This class has additional complications in that we cannot assume that 
     * our "heap" starts at index 0, because the top-level sorts may be asked 
     * to sort a subsection of an array.
     */
	
	public static <C extends Comparable<? super C>> void sort(C[] a, int lo, int hi) {
		// Establish the heap property.

		for (int i=hi-1; i>=lo; i--)
			fixHeap(a,lo,i,hi);

		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		while(lo < hi) {
			// a[lo] is the next-biggest element.
			SwapVals.swap(a,lo,hi);

			// Heap shrinks by 1 element.
			hi--;

			fixHeap(a,lo,lo,hi);
		}
	}

	public static <C extends Comparable<? super C>> void sort(C[] a, int lo, int hi, int[] b) {
		// Establish the heap property.

		for (int i=hi-1; i>=lo; i--)
			fixHeap(a,b,lo,i,hi);

		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		while(lo < hi) {
			// a[lo] is the next-biggest element.
			SwapVals.swap(a,lo,hi);
			if(b!=null)
				{SwapVals.swap(b,lo,hi);}

			// Heap shrinks by 1 element.
			hi --;

			fixHeap(a,b,lo,lo,hi);
		}
	}

	public static <C> void sort(C[] a, Comparator<C> c, int lo, int hi) {
		// Establish the heap property.
		for (int i=hi-1; i>=lo; i--)
			fixHeap(a,c,lo,i,hi);

		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		while(lo < hi) {
			// a[lo] is the next-biggest element.
			SwapVals.swap(a,lo,hi);

			// Heap shrinks by 1 element.
			hi--;

			fixHeap(a,c,lo,lo,hi);
		}
	}

	public static <C extends Comparable<? super C>> void sort(List<C> a, int lo, int hi) {
		// Establish the heap property.
		for (int i=hi-1; i>=lo; i--)
			fixHeap(a,lo,i,hi);

		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		while(lo<hi) {
			// a[lo] is the next-biggest element.
			SwapVals.swap(a,lo,hi);

			// Heap shrinks by 1 element.
			hi --;

			fixHeap(a,lo,lo,hi);
		}
	}

	public static void sort(long[] a, int lo, int hi, Object[] b) {
		// Establish the heap property.
		for (int i=hi-1; i>=lo; i--)
			fixHeap(a,b,lo,i,hi);

		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		while(lo<hi) {
			// a[lo] is the next-biggest element.
			SwapVals.swap(a,lo,hi);
			if(b!=null)
				{SwapVals.swap(b,lo,hi);}

			// Heap shrinks by 1 element.
			hi --;

			fixHeap(a,b,lo,lo,hi);
		}
	}

	public static void sortByNodeId(NodeProxy[] a, int lo, int hi) {
		// Establish the heap property.
		for (int i=hi-1; i>=lo; i--)
			fixHeapByNodeId(a,lo,i,hi);

		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		while(lo<hi) {
			// a[lo] is the next-biggest element.
			SwapVals.swap(a,lo,hi);

			// Heap shrinks by 1 element.
			hi --;

			fixHeapByNodeId(a,lo,lo,hi);
		}
	}

	private static <C extends Comparable<? super C>> void fixHeap(C[] a, int root, int item, int end) {
		for(;;) {
			int child = (item-root) * 2 + 1 + root;

			if((child > end || a[item].compareTo(a[child])>=0)
					&&(child+1 > end || a[item].compareTo(a[child+1])>=0)) {return;}

			if(child+1>end || a[child].compareTo(a[child+1])>=0) {
				SwapVals.swap(a, item, child);
				item = child;
			} else {
				SwapVals.swap(a, item, child+1);
				item = child+1;
			}
		}
	}

	private static <C extends Comparable<? super C>> void fixHeap(C[] a, int[] b, int root, int item, int end) {
		for(;;) {
			int child = (item-root) * 2 + 1 + root;

			if((child > end || a[item].compareTo(a[child])>=0)
					&&(child+1 > end || a[item].compareTo(a[child+1])>=0)) {return;}

			if(child+1>end || a[child].compareTo(a[child+1])>=0) {
				SwapVals.swap(a, item, child);
				if(b!=null) {SwapVals.swap(b, item, child);}
				item = child;
			} else {
				SwapVals.swap(a, item, child+1);
				if(b!=null) {SwapVals.swap(b, item, child+1);}
				item = child+1;
			}
		}
	}

	private static <C> void fixHeap(C[] a, Comparator<C> c, int root, int item, int end) {
		for(;;) {
			int child = (item-root) * 2 + 1 + root;

			if((child > end || c.compare( a[item],a[child])>=0)
					&&(child+1 > end || c.compare(a[item],a[child+1])>=0)) {return;}

			if(child+1>end || c.compare(a[child],a[child+1])>=0) {
				SwapVals.swap(a, item, child);
				item = child;
			} else {
				SwapVals.swap(a, item, child+1);
				item = child+1;
			}
		}
	}

	private static <C extends Comparable<? super C>> void fixHeap(List<C> a, int root, int item, int end) {
		for(;;) {
			int child = (item-root) * 2 + 1 + root;

			if((child > end || a.get(item).compareTo(a.get(child))>=0)
					&&(child+1 > end || a.get(item).compareTo(a.get(child+1))>=0)) {return;}

			if(child+1>end || a.get(child).compareTo(a.get(child+1))>=0) {
				SwapVals.swap(a, item, child);
				item = child;
			} else {
				SwapVals.swap(a, item, child+1);
				item = child+1;
			}
		}
	}

	private static void fixHeap(long[] a, Object[] b, int root, int item, int end) {
		for(;;) {
			int child = (item-root) * 2 + 1 + root;

			if((child > end || a[item]>=a[child])
					&&(child+1 > end || a[item]>=a[child+1])) {return;}

			if(child+1>end || a[child]>=a[child+1]) {
				SwapVals.swap(a, item, child);
				if(b!=null) {SwapVals.swap(b, item, child);}
				item = child;
			} else {
				SwapVals.swap(a, item, child+1);
				if(b!=null) {SwapVals.swap(b, item, child+1);}
				item = child+1;
			}
		}
	}

	private static void fixHeapByNodeId(NodeProxy[] a, int root, int item, int end) {
		for(;;) {
			int child = (item-root) * 2 + 1 + root;

			if((child > end || a[item].getNodeId().compareTo(a[child].getNodeId())>=0)
					&&(child+1 > end || a[item].getNodeId().compareTo(a[child+1].getNodeId())>=0)) {return;}

			if(child+1>end || a[child].getNodeId().compareTo(a[child+1].getNodeId())>=0) {
				SwapVals.swap(a, item, child);
				item = child;
			} else {
				SwapVals.swap(a, item, child+1);
				item = child+1;
			}
		}
	}
}
