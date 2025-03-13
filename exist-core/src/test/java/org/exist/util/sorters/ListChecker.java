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

package org.exist.util.sorters;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

/**
 * Check sort(List).
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

class ListChecker extends SortMethodChecker {
	ListChecker(SortingAlgorithmTester sorter) {
		super(sorter);
	}

	List<Integer> a;

	/**
	 * It asserts the ascending ordering of an Integer List
	 */
	void check(int lo, int hi) {
		for (int i = lo; i < hi; i++) {
			assertTrue(a.get(i).intValue() <= a.get(i + 1).intValue());
		}
	}

	/**
	 * It returns the length of the list to be used on assertion
	 */
	int getLength() {
		return a.size();
	}

	/**
	 * It loads an input int array into the internal Integer list
	 */
	void init(int[] values) throws Exception {
		a = new ArrayList<Integer>(values.length);
        for (int value : values) {
            a.add(Integer.valueOf(value));
        }
	}

	/**
	 * This method invokes sort routine on selected sorter
	 */
	void sort(int lo, int hi) throws Exception {
		sorter.sort(a, lo, hi);
	}

	/**
	 * This method asserts single values
	 */
	void checkValue(int idx, int v) {
		assertEquals("@" + idx, v, a.get(idx).intValue());
	}
}