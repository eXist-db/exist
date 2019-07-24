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

import org.exist.dom.persistent.NodeProxy;

/**
 * check sort(NodeProxy)
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

class NodeProxyChecker extends SortMethodChecker {
	NodeProxyChecker(SortingAlgorithmTester sorter) {
		super(sorter);
	}

	NodeProxy[] a;

	int getLength() {
		return a.length;
	}

	/**
	 * It asserts the ascending ordering of a NodeProxy array
	 */
	void check(int lo, int hi) {
		for (int i = lo; i < hi; i++) {
			assertTrue(((SortTestNodeProxy) a[i]).val <= ((SortTestNodeProxy) a[i + 1]).val);
		}
	}

	/**
	 * It loads an input int array into the internal NodeProxy one
	 */
	void init(int[] values) throws Exception {
		a = new NodeProxy[values.length];
		for (int i = 0; i < values.length; i++) {
			a[i] = new SortTestNodeProxy(-rnd.nextInt(1000), values[i]);
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
		assertEquals("@" + idx, v, ((SortTestNodeProxy) a[idx]).val);
	}
}