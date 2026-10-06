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

import java.util.stream.Stream;
import org.exist.util.sorters.ComparatorChecker.SortOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
public class SortComparatorTest {

    public static Stream<Arguments> data() {
        final List<Arguments> parameters = new ArrayList<>();
        for (final SortingAlgorithmTester s : SortingAlgorithmTester.allSorters()) {
            final String name = s.getClass().getSimpleName() + ": " + PlainArrayChecker.class.getSimpleName();
            parameters.add(Arguments.of(name, new PlainArrayChecker(s)));
        }

        return parameters.stream();
    }

    private final Random rnd = new Random();
    public String sortTestName;
    public ComparatorChecker checker;

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to the checker, whose implementations assert internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void comparatorAscending(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.ASCENDING);
			checker.check(SortOrder.ASCENDING);
		}
	}

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to the checker, whose implementations assert internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void comparatorDescending(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.DESCENDING);
			checker.check(SortOrder.DESCENDING);
		}
	}

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to a helper that asserts internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void badComparatorUnstable(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
		for (int i = 0; i < 10; i++) {
			checker.init(getRandomIntArray(100));
			checker.sort(SortOrder.UNSTABLE);
		}
	}

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to a helper that asserts internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void badComparatorRandom(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
		checker.init(getRandomIntArray(100));
		checker.sort(SortOrder.RANDOM);
	}

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to the checker, whose implementations assert internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void sortSubsection1asc(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
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

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to the checker, whose implementations assert internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void sortSubsection2asc(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
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

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to the checker, whose implementations assert internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void sortSubsection1desc(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
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

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertion is delegated to the checker, whose implementations assert internally
    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void sortSubsection2desc(String sortTestName, ComparatorChecker checker) throws Exception {
        initSortComparatorTest(sortTestName, checker);
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

    public void initSortComparatorTest(String sortTestName, ComparatorChecker checker) {
        this.sortTestName = sortTestName;
        this.checker = checker;
    }

}
