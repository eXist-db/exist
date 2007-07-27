/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-05 The eXist Project
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.exist.dom.NodeProxy;
import org.exist.util.SwapVals;

/**
	This class implements a simple version 
	of the heapsort algorithm, improved.

	The implementation is based on the
	implementation of J. Mohr, which was
	based on the implementation of a sorting
	framework by Cay Horstmann.

	@author José María Fernández
*/
public final class HeapSort {
	public static void sort(Comparable[] a, int lo, int hi) {
		// Establish the heap property.
		int i;
		
		for (i=hi/2; i>=lo; i--)
			fixHeap(a,i,hi,a[i]);
      
		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		for (i=hi; i>lo; i--) {
			// a[1] is the next-biggest element.
			SwapVals.swap(a,lo,i);

			// Heap shrinks by 1 element.
			fixHeap(a,lo,i-1,a[lo]);
		}
	}
	
	public static void sort(Comparable[] a, int lo, int hi, int[] b) {
		// Establish the heap property.
		int i;
		
		for (i=hi/2; i>=lo; i--)
			// The parameter must be set to something
			// compatible with the type
			fixHeap(a,b,i,hi,a[i],(b!=null)?b[i]:0);
      
		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		for (i=hi; i>lo; i--) {
			// a[1] is the next-biggest element.
			SwapVals.swap(a,lo,i);
			if(b!=null)
				SwapVals.swap(b,lo,i);

			// Heap shrinks by 1 element.
			
			// The parameter must be set to something
			// compatible with the type
			fixHeap(a,b,lo,i-1,a[lo],(b!=null)?b[lo]:0);
		}
	}
	
	public static void sort(Object[] a, Comparator c, int lo, int hi) {
		// Establish the heap property.
		int i;
		
		for (i=hi/2; i>=lo; i--)
			fixHeap(a,c,i,hi,a[i]);
      
		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		for (i=hi; i>lo; i--) {
			// a[1] is the next-biggest element.
			SwapVals.swap(a,lo,i);

			// Heap shrinks by 1 element.
			fixHeap(a,c,lo,i-1,a[lo]);
		}
	}
	
	public static void sort(List a, int lo, int hi) {
		// Establish the heap property.
		int i;
		
		for (i=hi/2; i>=lo; i--)
			fixHeap(a,i,hi,a.get(i));
      
		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		for (i=hi; i>lo; i--) {
			// a[1] is the next-biggest element.
			SwapVals.swap(a,lo,i);

			// Heap shrinks by 1 element.
			fixHeap(a,lo,i-1,a.get(lo));
		}
	}
	
	public static void sort(long[] a, int lo, int hi, Object b[]) {
		// Establish the heap property.
		int i;
		
		for (i=hi/2; i>=lo; i--)
			fixHeap(a,b,i,hi,a[i],(b!=null)?b[i]:null);
      
		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		for (i=hi; i>lo; i--) {
			// a[1] is the next-biggest element.
			SwapVals.swap(a,lo,i);
			if(b!=null)
				SwapVals.swap(b,lo,i);

			// Heap shrinks by 1 element.
			fixHeap(a,b,lo,i-1,a[lo],(b!=null)?b[lo]:null);
		}
	}
	
	public static void sort(NodeProxy[] a, int lo, int hi) {
		// Establish the heap property.
		int i;
		
		for (i=hi/2; i>=lo; i--)
			fixHeap(a,i,hi,a[i]);
      
		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		for (i=hi; i>lo; i--) {
			// a[1] is the next-biggest element.
			SwapVals.swap(a,lo,i);

			// Heap shrinks by 1 element.
			fixHeap(a,lo,i-1,a[lo]);
		}
	}
	
	public static void sortByNodeId(NodeProxy[] a, int lo, int hi) {
		// Establish the heap property.
		int i;
		
		for (i=hi/2; i>=lo; i--)
			fixHeapByNodeId(a,i,hi,a[i]);
      
		// Now place the largest element last,
		// 2nd largest 2nd last, etc.
		for (i=hi; i>lo; i--) {
			// a[1] is the next-biggest element.
			SwapVals.swap(a,lo,i);

			// Heap shrinks by 1 element.
			fixHeapByNodeId(a,lo,i-1,a[lo]);
		}
	}
	
	private static void fixHeap(Comparable[] a, int root, int end, Comparable key) {
		boolean itera;
		do {
			int child=2*root; // left child

			// Find the larger child.
			if(child<end && a[child].compareTo(a[child+1])<0) {
				child++;  // right child is larger
			}

			// If the larger child is larger than the
			// element at the root, move the larger child
			// to the root and filter the former root 
			// element down into the "larger" subtree.
			itera = child<=end && key.compareTo(a[child])<0;
			if(itera) {
				a[root] = a[child];
				root=child;
			} else {
				a[root] = key;
			}
		} while(itera);
	}
	
	private static void fixHeap(Comparable[] a, int[] b, int root, int end, Comparable key, int keyb) {
		boolean itera;
		do {
			int child=2*root; // left child

			// Find the larger child.
			if(child<end && a[child].compareTo(a[child+1])<0) {
				child++;  // right child is larger
			}

			// If the larger child is larger than the
			// element at the root, move the larger child
			// to the root and filter the former root 
			// element down into the "larger" subtree.
			itera = child<=end && key.compareTo(a[child])<0;
			if(itera) {
				a[root] = a[child];
				if(b!=null)
					b[root] = b[child];
				root=child;
			} else {
				a[root] = key;
				if(b!=null)
					b[root] = keyb;
			}
		} while(itera);
	}
	
	private static void fixHeap(Object[] a, Comparator c,int root, int end, Object key) {
		boolean itera;
		do {
			int child=2*root; // left child

			// Find the larger child.
			if(child<end && c.compare(a[child],a[child+1])<0) {
				child++;  // right child is larger
			}

			// If the larger child is larger than the
			// element at the root, move the larger child
			// to the root and filter the former root 
			// element down into the "larger" subtree.
			itera = child<=end && c.compare(key,a[child])<0;
			if(itera) {
				a[root] = a[child];
				root=child;
			} else {
				a[root] = key;
			}
		} while(itera);
	}
	
	private static void fixHeap(List a, int root, int end, Object key) {
		boolean itera;
		do {
			int child=2*root; // left child

			// Find the larger child.
			if(child<end && ((Comparable)a.get(child)).compareTo(a.get(child+1))<0) {
				child++;  // right child is larger
			}

			// If the larger child is larger than the
			// element at the root, move the larger child
			// to the root and filter the former root 
			// element down into the "larger" subtree.
			itera = child<=end && ((Comparable)key).compareTo(a.get(child))<0;
			if(itera) {
				a.set(root,a.get(child));
				root=child;
			} else {
				a.set(root,key);
			}
		} while(itera);
	}
	
	private static void fixHeap(long[] a, Object[] b, int root, int end, long key, Object keyb) {
		boolean itera;
		do {
			int child=2*root; // left child

			// Find the larger child.
			if(child<end && a[child]<a[child+1]) {
				child++;  // right child is larger
			}

			// If the larger child is larger than the
			// element at the root, move the larger child
			// to the root and filter the former root 
			// element down into the "larger" subtree.
			itera = child<=end && key<a[child];
			if(itera) {
				a[root] = a[child];
				if(b!=null)
					b[root] = b[child];
				root=child;
			} else {
				a[root] = key;
				if(b!=null)
					b[root] = keyb;
			}
		} while(itera);
	}
	
	private static void fixHeap(NodeProxy[] a, int root, int end, NodeProxy key) {
		boolean itera;
		do {
			int child=2*root; // left child

			// Find the larger child.
			if(child<end && a[child].compareTo(a[child+1])<0) {
				child++;  // right child is larger
			}

			// If the larger child is larger than the
			// element at the root, move the larger child
			// to the root and filter the former root 
			// element down into the "larger" subtree.
			itera = child<=end && key.compareTo(a[child])<0;
			if(itera) {
				a[root] = a[child];
				root=child;
			} else {
				a[root] = key;
			}
		} while(itera);
	}
	
	private static void fixHeapByNodeId(NodeProxy[] a, int root, int end, NodeProxy key) {
		boolean itera;
		do {
			int child=2*root; // left child

			// Find the larger child.
			if(child<end && a[child].compareTo(a[child+1]) < 0) {
				child++;  // right child is larger
			}

			// If the larger child is larger than the
			// element at the root, move the larger child
			// to the root and filter the former root 
			// element down into the "larger" subtree.
			itera = child<=end && key.compareTo(a[child]) < 0;
			if(itera) {
				a[root] = a[child];
				root=child;
			} else {
				a[root] = key;
			}
		} while(itera);
	}
	
	public static void main(String[] args) throws Exception {
		List l = new ArrayList();
		
		if(args.length==0) {
			String[] a=new String[] {
				"Rudi",
				"Herbert",
				"Anton",
				"Berta",
				"Olga",
				"Willi",
				"Heinz" };
		
			for (int i = 0; i < a.length; i++)
				l.add(a[i]);
		} else {
			System.err.println("Ordering file "+args[0]+"\n");
			try {
				java.io.BufferedReader is=new java.io.BufferedReader(new java.io.FileReader(args[0]));
				String rr;
				
				while((rr=is.readLine())!=null) {
					l.add(rr);
				}
				
				is.close();
			} catch(Exception e) {
			}
		}
		long a;
		long b;
		a=System.currentTimeMillis();
		sort(l, 0, l.size() - 1);
		b=System.currentTimeMillis();
		System.err.println("Ellapsed time: "+(b-a)+" size: "+l.size());
		for (int i = 0; i < l.size(); i++)
			System.out.println(l.get(i));
	}
}
