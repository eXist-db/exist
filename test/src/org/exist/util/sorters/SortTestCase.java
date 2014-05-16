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


import java.util.Random;

import junit.framework.TestCase;

/**
 * TestCase - given a sort() method and an algorithm via a checker, do a variety
 * of tests.
 * <p>
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
public class SortTestCase<CH extends SortMethodChecker> extends TestCase {

	protected final Random rnd = new Random();
	protected final CH checker;
	protected final String testSuite;

	SortTestCase(CH checker, String method, String testSuite) {
		super(method);
		this.checker = checker;
		this.testSuite = testSuite;
	}

	protected int[] getRandomIntArray(int sz) {
		int[] a = new int[sz];
		for (int i = 0; i < sz; i++) {
			a[i] = rnd.nextInt(1000);
		}
		return a;
	}

	protected int[] getConstantIntArray(int sz) {
		int[] a = new int[sz];
		for (int i = 0; i < sz; i++) {
			a[i] = 0;
		}
		return a;
	}

	protected int[] getAscendingIntArray(int sz) {
		int[] a = new int[sz];
		for (int i = 0; i < sz; i++) {
			a[i] = i;
		}
		return a;
	}

	protected int[] getDescendingIntArray(int sz) {
		int[] a = new int[sz];
		for (int i = 0; i < sz; i++) {
			a[i] = sz - i - 1;
		}
		return a;
	}

	public void testSingleElement() throws Exception {
		checker.init(getConstantIntArray(1));
		checker.sort();
	}

	public void testRandom() throws Exception {
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort();
			checker.check();
		}
	}

	public void testConstant() throws Exception {
		checker.init(getConstantIntArray(100));
		checker.sort();
		checker.check();
	}

	public void testAscending() throws Exception {
		checker.init(getAscendingIntArray(100));
		checker.sort();
		checker.check();
	}

	public void testDecending() throws Exception {
		checker.init(getDescendingIntArray(100));
		checker.sort();
		checker.check();
	}

	public void testSortSubsection1() throws Exception {

		for (int i = 0; i < 1000; i += 100) {
			int[] a = new int[1000];

			for (int ii = 0; ii < 1000; ii++) {
				a[ii] = (ii >= i && ii < i + 100) ? rnd.nextInt(1000)
						: 999 - ii;
			}

			checker.init(a);
			checker.sort(i, i + 99);
			checker.check(i, i + 99);

			// check that the other values have not been disturbed
			for (int ii = 0; ii < i; ii++) {
				checker.checkValue(ii, 999 - ii);
			}
			for (int ii = i + 100; ii < 1000; ii++) {
				checker.checkValue(ii, 999 - ii);
			}

		}
	}

	public void testSortSubsection2() throws Exception {
		for (int i = 0; i < 1000; i += 100) {
			int[] a = new int[1000];

			for (int ii = 0; ii < 1000; ii++) {
				a[ii] = (ii >= i && ii < i + 100) ? rnd.nextInt(1000) : ii;
			}

			checker.init(a);
			checker.sort(i, i + 99);
			checker.check(i, i + 99);

			// check that the other values have not been disturbed
			for (int ii = 0; ii < i; ii++) {
				checker.checkValue(ii, ii);
			}
			for (int ii = i + 100; ii < 1000; ii++) {
				checker.checkValue(ii, ii);
			}

		}
	}

	public void testSortSubsection3() throws Exception {

		for (int i = 0; i < 1000; i += 100) {
			int[] a = new int[1000];

			for (int ii = 0; ii < 1000; ii++) {
				a[ii] = (ii >= i && ii < i + 100) ? rnd.nextInt(1000)
						: (ii % 7);
			}

			checker.init(a);
			checker.sort(i, i + 99);
			checker.check(i, i + 99);

			// check that the other values have not been disturbed
			for (int ii = 0; ii < i; ii++) {
				checker.checkValue(ii, ii % 7);
			}
			for (int ii = i + 100; ii < 1000; ii++) {
				checker.checkValue(ii, ii % 7);
			}

		}
	}
}
