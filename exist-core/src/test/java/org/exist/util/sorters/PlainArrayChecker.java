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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.util.Comparator;

/**
 * check sort(Comparable[])
 *
 * This work was undertaken as part of the development of the taxonomic
 * repository at http://biodiversity.org.au . See <A
 * href="ghw-at-anbg.gov.au">Greg&nbsp;Whitbread</A> for further details.
 * 
 * @author pmurray@bigpond.com
 * @author pmurray@anbg.gov.au
 * @author https://sourceforge.net/users/paulmurray
 * @author http://www.users.bigpond.com/pmurray
 * 
 */
class PlainArrayChecker extends ComparatorChecker {
	PlainArrayChecker(SortingAlgorithmTester sorter) {
		super(sorter);
	}

	Integer[] a;
	
	/**
	 * It asserts the ascending ordering of an Integer array
	 */
	void check(int lo, int hi) {
		for (int i = lo; i < hi; i++) {
			assertTrue(a[i].intValue() <= a[i + 1].intValue());
		}
	}

	/**
	 * It loads an input int array into the internal Integer one
	 */
	void init(int[] values)
		throws Exception
	{
		a = new Integer[values.length];
		for (int i = 0; i < values.length; i++) {
			a[i] = Integer.valueOf(values[i]);
		}

	}
	
	/**
	 * It returns the length of the array to be used on assertion
	 */
	int getLength() {
		return a.length;
	}
	
	/**
	 * This method invokes sort routine on selected sorter
	 */
	void sort(int lo, int hi)
		throws Exception
	{
		sorter.sort(a, lo, hi);
	}

	/**
	 * This method invokes sort routine with a given
	 * comparator on selected sorter
	 */
	void sort(SortOrder sortOrder, int lo, int hi)
		throws Exception
	{
		sorter.sort(a, getComparator(sortOrder), lo, hi);
	}

	/**
	 * This method asserts single values
	 */
	void checkValue(int idx, int v) {
		assertEquals("@" + idx, v, a[idx].intValue());
	}

	/**
	 * It asserts the ascending ordering of an Integer array
	 * given an specific comparator
	 */
	void check(SortOrder sortOrder, int lo, int hi)
		throws Exception
	{
		Comparator<Integer> c = getComparator(sortOrder);
		for (int i = lo; i < hi; i++) {
			assertTrue(c.compare(a[i], a[i + 1]) <= 0);
		}
	}

	/**
	 * This method returns an Integer comparator based
	 * on input sort order
	 * @param sortOrder
	 * @return
	 */
	Comparator<Integer> getComparator(SortOrder sortOrder) {
		switch (sortOrder) {
			case ASCENDING:
				return new Comparator<Integer>() {
					public int compare(Integer o1, Integer o2) {
						return o1.intValue() - o2.intValue();
					}
				};
			case DESCENDING:
				return new Comparator<Integer>() {
					public int compare(Integer o1, Integer o2) {
						return o2.intValue() - o1.intValue();
					}
				};
			case RANDOM:
				return new Comparator<Integer>() {
					public int compare(Integer o1, Integer o2) {
						return rnd.nextBoolean() ? -1 : 1;
					}
				};
			case UNSTABLE:
				return new Comparator<Integer>() {
					public int compare(Integer o1, Integer o2) {
						if (o1.intValue() <= o2.intValue())
							return (o2.intValue() - o1.intValue()) % 3 - 1;
						else
							return 1 - (o1.intValue() - o2.intValue()) % 3;
					}
				};
		}
		return null; // should never happen
	}

}