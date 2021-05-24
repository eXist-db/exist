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
	This class implements a version 
	of the insertion sort algorithm.

	The implementation is inspired on
	the work of Michael Maniscalco in
	C++
	http://www.michael-maniscalco.com/sorting.htm
	
	@author José María Fernández (jmfg@users.sourceforge.net)
*/
public final class InsertionSort {
	public final static void sortByNodeId(NodeProxy[] a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  {return;}
		
		// Second case, at least two elements
		if (a[lo0].getNodeId().compareTo(a[lo0 + 1].getNodeId()) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  {return;}
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			NodeProxy temp=a[i+1];
			if (temp.getNodeId().compareTo(a[i].getNodeId()) < 0) {
				int j;
				
				for(j=i;j>=lo0 && temp.getNodeId().compareTo(a[j].getNodeId()) < 0;j--) {
					a[j+1]=a[j];
				}
				a[j+1]=temp;
			}
		}
	}
	
	public final static <C extends Comparable<? super C>> void sort(C[] a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  {return;}
		
		// Second case, at least two elements
		if(a[lo0].compareTo(a[lo0+1]) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  {return;}
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			C temp = a[i+1];
			if(temp.compareTo(a[i]) < 0) {
				int j;
				
				for(j=i;j>=lo0 && temp.compareTo(a[j]) < 0;j--) {
					a[j+1]=a[j];
				}
				a[j+1]=temp;
			}
		}
	}

	public final static <C extends Comparable<? super C>> void sort(C[] a, int lo0, int hi0, int[] b)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  {return;}
		
		// Second case, at least two elements
		if(a[lo0].compareTo(a[lo0+1]) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
			if(b!=null)  {SwapVals.swap(b,lo0,lo0+1);}
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  {return;}
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			C tempa = a[i+1];
			if(tempa.compareTo(a[i]) < 0) {
				int j;
				// Avoiding warnings
				int tempb=0;
				
				if(b!=null)  {tempb=b[i+1];}
				for(j=i;j>=lo0 && tempa.compareTo(a[j]) < 0;j--) {
					a[j+1]=a[j];
					if(b!=null)  {b[j+1]=b[j];}
				}
				a[j+1]=tempa;
				if(b!=null)  {b[j+1]=tempb;}
			}
		}
	}

	public final static <C> void sort(C[] a, Comparator<C> comp, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  {return;}
		
		// Second case, at least two elements
		if(comp.compare(a[lo0],a[lo0+1]) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  {return;}
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			C temp = a[i+1];
			if(comp.compare(temp,a[i]) < 0) {
				int j;
				
				for(j=i;j>=lo0 && comp.compare(temp,a[j]) < 0;j--) {
					a[j+1]=a[j];
				}
				a[j+1]=temp;
			}
		}
	}
		
	public final static <C extends Comparable<? super C>> void sort(List<C> a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  {return;}
		
		// Second case, at least two elements
		if((a.get(lo0)).compareTo(a.get(lo0+1)) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  {return;}
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			final C temp = a.get(i+1);
			if(temp.compareTo(a.get(i)) < 0) {
				int j;
				
				for(j=i;j>=lo0 && temp.compareTo(a.get(j)) < 0;j--) {
					a.set(j+1,a.get(j));
				}
				a.set(j+1,temp);
			}
		}
	}

	public final static void sort(long[] a, int lo0, int hi0, Object[] b)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  {return;}
		
		// Second case, at least two elements
		if(a[lo0] > a[lo0+1]) {
			SwapVals.swap(a,lo0,lo0+1);
			if(b!=null)  {SwapVals.swap(b,lo0,lo0+1);}
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  {return;}
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			long tempa=a[i+1];
			if(tempa<a[i]) {
				int j;
				Object tempb=null;
				
				if(b!=null)  {tempb=b[i+1];}
				for(j=i;j>=lo0 && tempa<a[j];j--) {
					a[j+1]=a[j];
					if(b!=null)  {b[j+1]=b[j];}
				}
				a[j+1]=tempa;
				if(b!=null)  {b[j+1]=tempb;}
			}
		}
	}
}
