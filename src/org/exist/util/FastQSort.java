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

/**
	This class implements a version 
	of the Introspective Sort Algorithm.
	
	Reference: David R. Musser
	"Introspective Sorting and Selection Algorithms"
	Software--Practice and Experience, (8): 983-993 (1997)

	The implementation is mainly inspired
	on the article describing the algorithm,
	but also in the work of Michael
	Maniscalco in C++. It is also slightly
	based on the previous implementation of
	FastQSort in eXist.
	
	http://www.cs.rpi.edu/~musser/
	http://www.cs.rpi.edu/~musser/gp/introsort.ps
	http://www.michael-maniscalco.com/sorting.htm
	
	@author José María Fernández
*/
public final class FastQSort {
	private final static int M = 10;
	private final static double LOG2 = Math.log(2.0);
	
	private final static void IntroSort(Comparable a[], int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r);
				return;
			}
			
			int i = ( l + r ) / 2;
			int j;

			Comparable partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l].compareTo(a[i]) > 0)
				SwapVals.swap(a, l, i); // Tri-Median Methode!
			if (a[l].compareTo(a[r]) > 0)
				SwapVals.swap(a, l, r);
			if (a[i].compareTo(a[r]) > 0)
				SwapVals.swap(a, i, r);
			partionElement = a[i];
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( partionElement.compareTo(a[i])>0 ) )
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( partionElement.compareTo(a[j])<0 ) )
					--j;
				// if the indexes have not crossed, swap
				if( i <= j ) {
					SwapVals.swap(a, i, j);
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				IntroSort( a, l, j, maxdepth );
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  break;
			l=i;
		}
		InsertionSort.sort(a,l,r);
	}

	private final static void IntroSort(Comparable a[], int l, int r, int b[], int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r,b);
				return;
			}
			
			int i = ( l + r ) / 2;
			int j;

			Comparable partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l].compareTo(a[i]) > 0) {
				SwapVals.swap(a, l, i); // Tri-Median Methode!
				if(b!=null)
					SwapVals.swap(b, l, i); // Tri-Median Methode!
			}
			if (a[l].compareTo(a[r]) > 0) {
				SwapVals.swap(a, l, r);
				if(b!=null)
					SwapVals.swap(b, l, r);
			}
			if (a[i].compareTo(a[r]) > 0) {
				SwapVals.swap(a, i, r);
				if(b!=null)
					SwapVals.swap(b, i, r);
			}
			partionElement = a[i];
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( partionElement.compareTo(a[i])>0 ) )
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( partionElement.compareTo(a[j])<0 ) )
					--j;
				// if the indexes have not crossed, swap
				if( i <= j ) {
					SwapVals.swap(a, i, j);
					if(b!=null)
						SwapVals.swap(b, i, j);
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				IntroSort( a, l, j, b, maxdepth );
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  break;
			l=i;
		}
		InsertionSort.sort(a,l,r,b);
	}

	private final static void IntroSort(Object a[], Comparator comp, int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,comp,l,r);
				return;
			}
			
			int i = ( l + r ) / 2;
			int j;

			Object partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (comp.compare(a[l],a[i]) > 0)
				SwapVals.swap(a, l, i); // Tri-Median Methode!
			if (comp.compare(a[l],a[r]) > 0)
				SwapVals.swap(a, l, r);
			if (comp.compare(a[i],a[r]) > 0)
				SwapVals.swap(a, i, r);
			partionElement = a[i];
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( comp.compare(partionElement,a[i])>0 ) )
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( comp.compare(partionElement,a[j])<0 ) )
					--j;
				// if the indexes have not crossed, swap
				if( i <= j ) {
					SwapVals.swap(a, i, j);
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				IntroSort( a, comp, l, j, maxdepth );
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  break;
			l=i;
		}
		InsertionSort.sort(a,comp,l,r);
	}

	private final static void IntroSort(NodeProxy a[], int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r);
				return;
			}
			
			int i = ( l + r ) / 2;
			int j;

			NodeProxy partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l].compareTo(a[i]) > 0)
				SwapVals.swap(a, l, i); // Tri-Median Methode!
			if (a[l].compareTo(a[r]) > 0)
				SwapVals.swap(a, l, r);
			if (a[i].compareTo(a[r]) > 0)
				SwapVals.swap(a, i, r);
			partionElement = a[i];
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( partionElement.compareTo(a[i])>0 ) )
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( partionElement.compareTo(a[j])<0 ) )
					--j;
				// if the indexes have not crossed, swap
				if( i <= j ) {
					SwapVals.swap(a, i, j);
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				IntroSort( a, l, j, maxdepth );
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  break;
			l=i;
		}
		InsertionSort.sort(a,l,r);
	}
	
	private final static void IntroSort(List a, int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r);
				return;
			}
			
			int i = ( l + r ) / 2;
			int j;

			Object partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (((Comparable)a.get(l)).compareTo(a.get(i)) > 0)
				SwapVals.swap(a, l, i); // Tri-Median Methode!
			if (((Comparable)a.get(l)).compareTo(a.get(r)) > 0)
				SwapVals.swap(a, l, r);
			if (((Comparable)a.get(i)).compareTo(a.get(r)) > 0)
				SwapVals.swap(a, i, r);
			partionElement = a.get(i);
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( ((Comparable)partionElement).compareTo(a.get(i))>0 ) )
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( ((Comparable)partionElement).compareTo(a.get(j))<0 ) )
					--j;
				// if the indexes have not crossed, swap
				if( i <= j ) {
					SwapVals.swap(a, i, j);
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				IntroSort( a, l, j, maxdepth );
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  break;
			l=i;
		}
		InsertionSort.sort(a,l,r);
	}

	private final static void IntroSort(long a[], int l, int r, Object b[], int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r,b);
				return;
			}
			
			int i = ( l + r ) / 2;
			int j;

			long partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l] > a[i] ) {
				SwapVals.swap(a, l, i); // Tri-Median Methode!
				if(b!=null)
					SwapVals.swap(b, l, i); // Tri-Median Methode!
			}
			if (a[l] > a[r] ) {
				SwapVals.swap(a, l, r);
				if(b!=null)
					SwapVals.swap(b, l, r);
			}
			if (a[i] > a[r] ) {
				SwapVals.swap(a, i, r);
				if(b!=null)
					SwapVals.swap(b, i, r);
			}
			partionElement = a[i];
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( partionElement>a[i] ) )
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( partionElement<a[j] ) )
					--j;
				// if the indexes have not crossed, swap
				if( i <= j ) {
					SwapVals.swap(a, i, j);
					if(b!=null)
						SwapVals.swap(b, i, j);
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				IntroSort( a, l, j, b, maxdepth );
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  break;
			l=i;
		}
		InsertionSort.sort(a,l,r,b);
	}
	
	private final static void IntroSortByNodeId(NodeProxy a[], int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r);
				return;
			}
			
			int i = ( l + r ) / 2;
			int j;

			NodeProxy partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
            if (a[l].getNodeId().compareTo(a[i].getNodeId()) > 0)
				SwapVals.swap(a, l, i); // Tri-Median Methode!
			if (a[l].getNodeId().compareTo(a[r].getNodeId()) > 0)
				SwapVals.swap(a, l, r);
			if (a[i].getNodeId().compareTo(a[r].getNodeId()) > 0)
				SwapVals.swap(a, i, r);
			partionElement = a[i];
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( partionElement.getNodeId().compareTo(a[i].getNodeId()) > 0))
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( partionElement.getNodeId().compareTo(a[j].getNodeId()) < 0) )
					--j;
				// if the indexes have not crossed, swap
				if( i <= j ) {
					SwapVals.swap(a, i, j);
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				IntroSortByNodeId( a, l, j, maxdepth );
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  break;
			l=i;
		}
		InsertionSort.sort(a,l,r);
	}
	
	public static void sort(Comparable[] a, int lo, int hi) {
		if (lo == hi)
			return; // just one item, doesn't need sorting
		IntroSort(a, lo, hi, 2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
	}

	public static void sort(Comparable[] a, int lo, int hi, int[] b) {
		if (lo == hi)
			return; // just one item, doesn't need sorting
		IntroSort(a, lo, hi, b, 2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
	}

	public static void sort(Object[] a, Comparator c, int lo, int hi) {
		if (lo == hi)
			return; // just one item, doesn't need sorting
		IntroSort(a, c, lo, hi, 2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
	}
	
	public static void sort(List a, int lo, int hi) {
		if (lo == hi)
			return; // just one item, doesn't need sorting
		IntroSort(a, lo, hi, 2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
	}
	
	public static void sort(NodeProxy[] a, int lo, int hi) {
	    if (lo == hi)
	        return; // just one item, doesn't need sorting
	    IntroSort(a, lo, hi, 2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
	}

	public static void sortByNodeId(NodeProxy[] a, int lo, int hi) {
		if (lo == hi)
			return; // just one item, doesn't need sorting
		IntroSortByNodeId(a, lo, hi, 2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
	}

	public static void sort(long[] a, int lo, int hi, Object b[]) {
		if (lo == hi)
			return; // just one item, doesn't need sorting
		IntroSort(a, lo, hi, b, 2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
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
