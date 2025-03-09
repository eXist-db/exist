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

/**
 * Test case to check methods that use comparators.
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

public abstract class ComparatorChecker extends SortMethodChecker {

	enum SortOrder {
		ASCENDING, DESCENDING, UNSTABLE, RANDOM
	}

    ComparatorChecker(SortingAlgorithmTester sorter) {
		super(sorter);
	}

	abstract void check(SortOrder sortOrder, int lo, int hi) throws Exception;

	abstract void sort(SortOrder sortOrder, int lo, int hi) throws Exception;

	void sort(SortOrder sortOrder) throws Exception {
		sort(sortOrder, 0, getLength() - 1);
	}

	void check(SortOrder sortOrder) throws Exception {
		check(sortOrder, 0, getLength() - 1);
	}
}
