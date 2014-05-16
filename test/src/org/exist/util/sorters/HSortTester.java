/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.util.sorters;

import java.util.Comparator;
import java.util.List;

import org.exist.dom.NodeProxy;
import org.exist.util.HSort;

/**
 * Interface to the improved heapsort methods.
 * 
 * @author José María Fernández (jmfg@users.sourceforge.net)
 * @see HSort
 * 
 */

class HSortTester extends SortingAlgorithmTester {
	public <C extends Comparable<? super C>> void invokeSort(C[] a, int lo, int hi)
		throws Exception
	{
		HSort.sort(a, lo, hi);
	}

	public <C> void invokeSort(C a[], Comparator<C> c, int lo, int hi)
		throws Exception
	{
		HSort.sort(a, c, lo, hi);
	}

	public <C extends Comparable<? super C>>  void sort(C[] a, int lo, int hi)
		throws Exception
	{
		HSort.sort(a, lo, hi);
	}

	public <C extends Comparable<? super C>> void sort(C[] a, int lo, int hi, int[] b)
		throws Exception
	{
		HSort.sort(a, lo, hi, b);
	}

	public <C> void sort(C[] a, Comparator<C> c, int lo,
			int hi)
		throws Exception
	{
		HSort.sort(a, c, lo, hi);
	}

	public <C extends Comparable<? super C>> void sort(List<C> a, int lo, int hi)
		throws Exception
	{
		HSort.sort(a, lo, hi);
	}

	public void sort(int lo, int hi,NodeProxy[] a)
		throws Exception
	{
		sort(a, lo, hi);
	}

	public void sort(long[] a, int lo, int hi, Object[] b)
		throws Exception
	{
		HSort.sort(a, lo, hi, b);
	}

	public void sortByNodeId(NodeProxy[] a, int lo, int hi)
		throws Exception
	{
		HSort.sortByNodeId(a, lo, hi);
	}
}
