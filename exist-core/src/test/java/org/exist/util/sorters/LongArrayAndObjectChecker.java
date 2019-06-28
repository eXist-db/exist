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

/**
 * check sort(long[], Object[])
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
class LongArrayAndObjectChecker extends SortMethodChecker {
	LongArrayAndObjectChecker(SortingAlgorithmTester sorter) {
		super(sorter);
	}

	long[] a;
	Integer[] b;

	/**
	 * It returns the length of the array to be used on assertion
	 */
	int getLength() {
		return a.length;
	}

	/**
	 * It asserts the ascending ordering of the couple of
	 * Integer and long arrays given an specific comparator
	 */
	void check(int lo, int hi) {
		for (int i = lo; i < hi; i++) {
			assertTrue(a[i] <= a[i + 1]);
			assertEquals(a[i], b[i].intValue());
		}
		assertEquals(a[hi], b[hi].intValue());
	}

	/**
	 * It loads an input int array into the internal Integer one
	 */
	void init(int[] values) throws Exception {
		a = new long[values.length];
		b = new Integer[values.length];
		for (int i = 0; i < values.length; i++) {
			a[i] = values[i];
			b[i] = Integer.valueOf(values[i]);
		}
	}

	/**
	 * This method invokes sort routine on selected sorter
	 */
	void sort(int lo, int hi) throws Exception {
		sorter.sort(a, lo, hi, b);
	}

	/**
	 * This method asserts single values
	 */
	void checkValue(int idx, int v) {
		assertEquals("@" + idx, v, a[idx]);
		assertEquals("@" + idx, v, b[idx].intValue());
	}
}