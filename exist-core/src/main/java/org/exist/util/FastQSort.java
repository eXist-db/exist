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
  * This class implements a version 
  * of the Introspective Sort Algorithm.
  * 
  * Reference: David R. Musser
  * "Introspective Sorting and Selection Algorithms"
  * Software--Practice and Experience, (8): 983-993 (1997)
  * 
  * The implementation is mainly inspired
  * on the article describing the algorithm,
  * but also in the work of Michael
  * Maniscalco in C++. It is also slightly
  * based on the previous implementation of
  * FastQSort in eXist.
  * 
  * http://www.cs.rpi.edu/~musser/
  * http://www.cs.rpi.edu/~musser/gp/introsort.ps
  * http://www.michael-maniscalco.com/sorting.htm
  * 
  * See also an alternate implementation at:
  * 
  * http://ralphunden.net/?q=a-guide-to-introsort#AB2
  * 
  * @author José María Fernández (jmfg@users.sourceforge.net)
  */
public final class FastQSort {
	private final static int M = 10;
	private final static double LOG2 = Math.log(2.0);
	
	private final static <C extends Comparable<? super C>> void IntroSort(C[] a, int lo, int hi)
	{
		IntroSortLoop(a,lo,hi,2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
		InsertionSort.sort(a,lo,hi);
	}

	private final static <C extends Comparable<? super C>> void IntroSort(C[] a, int lo, int hi, int[] b)
	{
		IntroSortLoop(a,lo,hi,b,2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
		InsertionSort.sort(a,lo,hi,b);
	}
	
	private final static <C> void IntroSort(C[] a, Comparator<C> comp, int lo, int hi)
	{
		IntroSortLoop(a,comp,lo,hi,2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
		InsertionSort.sort(a,comp,lo,hi);
	}
	
	private final static <C extends Comparable<? super C>> void IntroSort(List<C> a, int lo, int hi)
	{
		IntroSortLoop(a,lo,hi,2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
		InsertionSort.sort(a,lo,hi);
	}
	
	private final static void IntroSort(long[] a, int lo, int hi, Object[] b)
	{
		IntroSortLoop(a,lo,hi,b,2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
		InsertionSort.sort(a,lo,hi,b);
	}
	
	private final static void IntroSortByNodeId(NodeProxy[] a, int lo, int hi)
	{
		IntroSortLoopByNodeId(a,lo,hi,2*(int)Math.floor(Math.log(hi-lo+1)/LOG2));
		InsertionSort.sortByNodeId(a,lo,hi);
	}
	
	private final static <C extends Comparable<? super C>> void IntroSortLoop(C[] a, int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r);
				return;
			}
			
			maxdepth--;
			
			int i = ( l + r ) / 2;
			int j;

			C partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l].compareTo(a[i]) > 0)
				{SwapVals.swap(a, l, i);} // Tri-Median Methode!
			if (a[l].compareTo(a[r]) > 0)
				{SwapVals.swap(a, l, r);}
			if (a[i].compareTo(a[r]) > 0)
				{SwapVals.swap(a, i, r);}
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
				{IntroSortLoop( a, l, j, maxdepth );}
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			// Right partition sorting has been inlined
			if( i >= r )  {break;}
			l=i;
		}
	}

	private final static <C extends Comparable<? super C>> void IntroSortLoop(C[] a, int l, int r, int[] b, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r,b);
				return;
			}
			
			maxdepth--;
			
			int i = ( l + r ) / 2;
			int j;

			C partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l].compareTo(a[i]) > 0) {
				SwapVals.swap(a, l, i); // Tri-Median Methode!
				if(b!=null)
					{SwapVals.swap(b, l, i);} // Tri-Median Methode!
			}
			if (a[l].compareTo(a[r]) > 0) {
				SwapVals.swap(a, l, r);
				if(b!=null)
					{SwapVals.swap(b, l, r);}
			}
			if (a[i].compareTo(a[r]) > 0) {
				SwapVals.swap(a, i, r);
				if(b!=null)
					{SwapVals.swap(b, i, r);}
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
						{SwapVals.swap(b, i, j);}
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				{IntroSortLoop( a, l, j, b, maxdepth );}
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  {break;}
			l=i;
		}
	}

	private final static <C> void IntroSortLoop(C[] a, Comparator<C> comp, int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,comp,l,r);
				return;
			}
			
			maxdepth--;
			
			int i = ( l + r ) / 2;
			int j;

			C partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (comp.compare(a[l],a[i]) > 0)
				{SwapVals.swap(a, l, i);} // Tri-Median Methode!
			if (comp.compare(a[l],a[r]) > 0)
				{SwapVals.swap(a, l, r);}
			if (comp.compare(a[i],a[r]) > 0)
				{SwapVals.swap(a, i, r);}
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
				{IntroSortLoop( a, comp, l, j, maxdepth );}
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  {break;}
			l=i;
		}
	}

	private final static <C extends Comparable<? super C>> void IntroSortLoop(List<C> a, int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r);
				return;
			}
			
			maxdepth--;
			
			int i = ( l + r ) / 2;
			int j;

			C partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if ((a.get(l)).compareTo(a.get(i)) > 0)
				{SwapVals.swap(a, l, i);} // Tri-Median Methode!
			if ((a.get(l)).compareTo(a.get(r)) > 0)
				{SwapVals.swap(a, l, r);}
			if ((a.get(i)).compareTo(a.get(r)) > 0)
				{SwapVals.swap(a, i, r);}
			partionElement = a.get(i);
			// loop through the array until indices cross
			i = l+1;
			j = r-1;
			while( i <= j ) {
				// find the first element that is greater than or equal to
				// the partionElement starting from the leftIndex.
				while( ( i < r ) && ( partionElement.compareTo(a.get(i))>0 ) )
					++i;
				// find an element that is smaller than or equal to
				// the partionElement starting from the rightIndex.
				while( ( j > l ) && ( partionElement.compareTo(a.get(j))<0 ) )
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
				{IntroSortLoop( a, l, j, maxdepth );}
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  {break;}
			l=i;
		}
	}

	private final static void IntroSortLoop(long[] a, int l, int r, Object[] b, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sort(a,l,r,b);
				return;
			}
			
			maxdepth--;
			
			int i = ( l + r ) / 2;
			int j;

			long partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l] > a[i] ) {
				SwapVals.swap(a, l, i); // Tri-Median Methode!
				if(b!=null)
					{SwapVals.swap(b, l, i);} // Tri-Median Methode!
			}
			if (a[l] > a[r] ) {
				SwapVals.swap(a, l, r);
				if(b!=null)
					{SwapVals.swap(b, l, r);}
			}
			if (a[i] > a[r] ) {
				SwapVals.swap(a, i, r);
				if(b!=null)
					{SwapVals.swap(b, i, r);}
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
						{SwapVals.swap(b, i, j);}
					++i;
					--j;
				}
			}
			// If the right index has not reached the left side of array
			// must now sort the left partition.
			if( l < j )
				{IntroSortLoop( a, l, j, b, maxdepth );}
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  {break;}
			l=i;
		}
	}
	
	private final static void IntroSortLoopByNodeId(NodeProxy[] a, int l, int r, int maxdepth)
	//----------------------------------------------------
	{
		while ( (r - l) > M ) {
			if(maxdepth<=0) {
				HeapSort.sortByNodeId(a,l,r);
				return;
			}
			
			maxdepth--;
			
			int i = ( l + r ) / 2;
			int j;

			NodeProxy partionElement;
			// Arbitrarily establishing partition element as the midpoint of
			// the array.
			if (a[l].getNodeId().compareTo(a[i].getNodeId()) > 0)
				{SwapVals.swap(a, l, i);} // Tri-Median Methode!
			if (a[l].getNodeId().compareTo(a[r].getNodeId()) > 0)
				{SwapVals.swap(a, l, r);}
			if (a[i].getNodeId().compareTo(a[r].getNodeId()) > 0)
				{SwapVals.swap(a, i, r);}
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
				{IntroSortLoopByNodeId( a, l, j, maxdepth );}
			// If the left index has not reached the right side of array
			// must now sort the right partition.
			if( i >= r )  {break;}
			l=i;
		}
	}
	
	public static <C extends Comparable<? super C>> void sort(C[] a, int lo, int hi) {
		if (lo >= hi)
			{return;} // just one item, doesn't need sorting
		IntroSort(a, lo, hi);
	}

	public static <C extends Comparable<? super C>> void sort(C[] a, int lo, int hi, int[] b) {
		if (lo >= hi)
			{return;} // just one item, doesn't need sorting
		IntroSort(a, lo, hi, b);
	}

	public static <C> void sort(C[] a, Comparator<C> c, int lo, int hi) {
		if (lo >= hi)
			{return;} // just one item, doesn't need sorting
		IntroSort(a, c, lo, hi);
	}
	
	public static <C extends Comparable<? super C>> void sort(List<C> a, int lo, int hi) {
		if (lo >= hi)
			{return;} // just one item, doesn't need sorting
		IntroSort(a, lo, hi);
	}
	
	public static void sortByNodeId(NodeProxy[] a, int lo, int hi) {
		if (lo >= hi)
			{return;} // just one item, doesn't need sorting
		IntroSortByNodeId(a, lo, hi);
	}

	public static void sort(long[] a, int lo, int hi, Object[] b) {
		if (lo >= hi)
			{return;} // just one item, doesn't need sorting
		IntroSort(a, lo, hi, b);
	}
}
