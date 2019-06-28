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

import java.lang.reflect.Method;
import java.util.Random;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Check one of the sort() methods, given an algorithm to use.
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
public abstract class SortMethodChecker {
	final SortingAlgorithmTester sorter;
	protected final Random rnd = new Random();

	SortMethodChecker(SortingAlgorithmTester sorter) {
		this.sorter = sorter;
	}

	abstract void checkValue(int idx, int v) throws Exception;

	public String toString() {
		return getClass().getSimpleName() + " "
			+ sorter.getClass().getSimpleName();
	}

	abstract void init(int[] values) throws Exception;

	abstract int getLength() throws Exception;

	abstract void sort(int lo, int hi) throws Exception;

	abstract void check(int lo, int hi) throws Exception;

	void sort() throws Exception {
		sort(0, getLength() - 1);
	}

	void check() throws Exception {
		check(0, getLength() - 1);
	}

	public static SortMethodChecker[] allCheckers(SortingAlgorithmTester s) {
		return new SortMethodChecker[] {
			new PlainArrayChecker(s),
			new ListChecker(s),
			new ObjectAndIntArrayChecker(s),
			new LongArrayAndObjectChecker(s),
			new NodeProxyChecker(s),
			new NodeProxyByIdChecker(s)
		};
	}

}
