/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2015 The eXist Project
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
 */
package org.exist.util.sorters;

import org.exist.util.sorters.ComparatorChecker.SortOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test case - given a sort() method and an algorithm via a checker, do a variety
 * of tests that rely on the comparator methods.
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
@RunWith(Parameterized.class)
public class SortComparatorTest {

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        final List<Object[]> parameters = new ArrayList<>();
        for (final SortingAlgorithmTester s : SortingAlgorithmTester.allSorters()) {
            final String name = s.getClass().getSimpleName() + ": " + PlainArrayChecker.class.getSimpleName();
            parameters.add(new Object[]{name, new PlainArrayChecker(s)});
        }

        return parameters;
    }

    private final Random rnd = new Random();

    @Parameter
    public String sortTestName;

    @Parameter(value = 1)
    public ComparatorChecker checker;

	@Test
	public void comparatorAscending() throws Exception {
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.ASCENDING);
			checker.check(SortOrder.ASCENDING);
		}
	}

	@Test
	public void comparatorDescending() throws Exception {
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.DESCENDING);
			checker.check(SortOrder.DESCENDING);
		}
	}

	@Test
	public void badComparatorUnstable() throws Exception {
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.UNSTABLE);
		}
	}

	@Test
	public void badComparatorRandom() throws Exception {
		checker.init(getRandomIntArray(100));
		checker.sort(SortOrder.RANDOM);
	}

	@Test
	public void sortSubsection1asc() throws Exception {
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

	@Test
	public void sortSubsection2asc() throws Exception {
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

	@Test
	public void sortSubsection1desc() throws Exception {
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

	@Test
	public void sortSubsection2desc() throws Exception {
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

    protected int[] getRandomIntArray(int sz) {
        int[] a = new int[sz];
        for (int i = 0; i < sz; i++) {
            a[i] = rnd.nextInt(1000);
        }
        return a;
    }

}
