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

import org.exist.util.sorters.ComparatorChecker.SortOrder;

/**
 * TestCase - given a sort() method and an algorithm via a checker, do a variety
 * of tests that rely on the comparator methods.
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
public class SortTestComparator<CH extends ComparatorChecker>
	extends SortTestCase<CH>
{
	SortTestComparator(CH checker, String method) {
		super(checker, method);
	}

	public void testComparatorAscending() throws Exception {
		System.out.println("ComparatorAscending on "+getName());
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.ASCENDING);
			checker.check(SortOrder.ASCENDING);
		}
	}

	public void testComparatorDescending() throws Exception {
		System.out.println("ComparatorDescending on "+getName());
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.DESCENDING);
			checker.check(SortOrder.DESCENDING);
		}
	}

	public void testBadComparatorUnstable() throws Exception {
		System.out.println("BadComparatorUnstable on "+getName());
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.UNSTABLE);
		}
	}

	public void testBadComparatorRandom() throws Exception {
		System.out.println("BadComparatorRandom on "+getName());
		checker.init(getRandomIntArray(100));
		checker.sort(SortOrder.RANDOM);
	}

	public void testSortSubsection1asc() throws Exception {
		System.out.println("SortSubsection1 asc on "+getName());
		for (int i = 0; i < 1000; i += 100) {
			int[] a = new int[1000];

			for (int ii = 0; ii < 1000; ii++) {
				a[ii] = (ii >= i && ii < i + 100) ? rnd.nextInt(1000)
						: 999 - ii;
			}

			checker.init(a);
			checker.sort(SortOrder.ASCENDING, i, i + 99);
			checker.check(SortOrder.ASCENDING, i, i + 99);

			// check that the other values have not been disturbed
			for (int ii = 0; ii < i; ii++) {
				checker.checkValue(ii, 999 - ii);
			}
			for (int ii = i + 100; ii < 1000; ii++) {
				checker.checkValue(ii, 999 - ii);
			}
		}
	}

	public void testSortSubsection2asc() throws Exception {
		System.out.println("SortSubsection2 asc on "+getName());
		for (int i = 0; i < 1000; i += 100) {
			int[] a = new int[1000];

			for (int ii = 0; ii < 1000; ii++) {
				a[ii] = (ii >= i && ii < i + 100) ? rnd.nextInt(1000) : ii;
			}

			checker.init(a);
			checker.sort(SortOrder.ASCENDING, i, i + 99);
			checker.check(SortOrder.ASCENDING, i, i + 99);

			// check that the other values have not been disturbed
			for (int ii = 0; ii < i; ii++) {
				checker.checkValue(ii, ii);
			}
			for (int ii = i + 100; ii < 1000; ii++) {
				checker.checkValue(ii, ii);
			}
		}
	}

	public void testSortSubsection1desc() throws Exception {
		System.out.println("SortSubsection1 desc on "+getName());

		for (int i = 0; i < 1000; i += 100) {
			int[] a = new int[1000];

			for (int ii = 0; ii < 1000; ii++) {
				a[ii] = (ii >= i && ii < i + 100) ? rnd.nextInt(1000)
						: 999 - ii;
			}

			checker.init(a);
			checker.sort(SortOrder.DESCENDING, i, i + 99);
			checker.check(SortOrder.DESCENDING, i, i + 99);

			// check that the other values have not been disturbed
			for (int ii = 0; ii < i; ii++) {
				checker.checkValue(ii, 999 - ii);
			}
			for (int ii = i + 100; ii < 1000; ii++) {
				checker.checkValue(ii, 999 - ii);
			}

		}
	}

	public void testSortSubsection2desc() throws Exception {
		System.out.println("SortSubsection2 desc on "+getName());
		for (int i = 0; i < 1000; i += 100) {
			int[] a = new int[1000];

			for (int ii = 0; ii < 1000; ii++) {
				a[ii] = (ii >= i && ii < i + 100) ? rnd.nextInt(1000) : ii;
			}

			checker.init(a);
			checker.sort(SortOrder.DESCENDING, i, i + 99);
			checker.check(SortOrder.DESCENDING, i, i + 99);

			// check that the other values have not been disturbed
			for (int ii = 0; ii < i; ii++) {
				checker.checkValue(ii, ii);
			}
			for (int ii = i + 100; ii < 1000; ii++) {
				checker.checkValue(ii, ii);
			}

		}
	}

}
