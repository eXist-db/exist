/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 */

package org.exist.util.sorters;

import java.util.Comparator;
import java.util.List;

import org.exist.dom.NodeProxy;
import org.exist.util.InsertionSort;

/**
 * Interface to the insertion sort methods.
 * <p>
 * This work was undertaken as part of the development of the taxonomic
 * repository at http://biodiversity.org.au . See <A
 * href="ghw-at-anbg.gov.au">Greg&nbsp;Whitbread</A> for further details.
 * 
 * @author pmurray@bigpond.com
 * @author pmurray@anbg.gov.au
 * @author https://sourceforge.net/users/paulmurray
 * @author http://www.users.bigpond.com/pmurray
 * @see InsertionSort
 */

class InsertionSortTester extends SortingAlgorithmTester {
	public void invokeSort(Comparable<?>[] a, int lo, int hi)
		throws Exception
	{
		InsertionSort.sort(a, lo, hi);
	}

	public <T> void invokeSort(T a[], Comparator<T> c, int lo, int hi)
		throws Exception
	{
		InsertionSort.sort(a, c, lo, hi);
	}

	public void sort(Comparable<?>[] a, int lo, int hi)
		throws Exception
	{
		InsertionSort.sort(a, lo, hi);
	}

	public void sort(Comparable<?>[] a, int lo, int hi, int[] b)
		throws Exception
	{
		InsertionSort.sort(a, lo, hi, b);
	}

	public <T extends Comparable<T>> void sort(T[] a, Comparator<T> c, int lo,
			int hi)
		throws Exception
	{
		InsertionSort.sort(a, c, lo, hi);
	}

	public <T extends Comparable<T>> void sort(List<T> a, int lo, int hi)
		throws Exception
	{
		InsertionSort.sort(a, lo, hi);
	}

	public void sort(int lo, int hi, NodeProxy[] a)
		throws Exception
	{
		InsertionSort.sort(a, lo, hi);
	}

	public void sort(long[] a, int lo, int hi, Object[] b)
		throws Exception
	{
		InsertionSort.sort(a, lo, hi, b);
	}

	public void sortByNodeId(NodeProxy[] a, int lo, int hi)
		throws Exception
	{
		InsertionSort.sortByNodeId(a, lo, hi);
	}
}
