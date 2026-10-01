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


import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test case - given a sort() method and an algorithm via a checker, do a variety
 * of tests.
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
@Execution(ExecutionMode.CONCURRENT)
public class SortTest {

    public static java.util.Collection<Object[]> data() {
        final List<Object[]> parameters = new ArrayList<>();
        for (final SortingAlgorithmTester s : SortingAlgorithmTester.allSorters()) {
            for (final SortMethodChecker c : SortMethodChecker.allCheckers(s)) {
                final String name = s.getClass().getSimpleName() + ": " + c.getClass().getSimpleName();
                parameters.add(new Object[]{name, c});
            }
        }

        return parameters;
    }

	private final Random rnd = new Random();
    public String sortTestName;
    public SortMethodChecker checker;

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to a helper that asserts internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void singleElement(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);
		checker.init(getConstantIntArray(1));
		checker.sort();
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void random(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort();
			checker.check();
		}
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void constant(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);
		checker.init(getConstantIntArray(100));
		checker.sort();
		checker.check();
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void ascending(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);
		checker.init(getAscendingIntArray(100));
		checker.sort();
		checker.check();
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void descending(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);
		checker.init(getDescendingIntArray(100));
		checker.sort();
		checker.check();
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void sortSubsection1(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void sortSubsection2(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);
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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void sortSubsection3(String sortTestName, SortMethodChecker checker) throws Exception {
        initSortTest(sortTestName, checker);

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

    public void initSortTest(String sortTestName, SortMethodChecker checker) {
        this.sortTestName = sortTestName;
        this.checker = checker;
    }
}
