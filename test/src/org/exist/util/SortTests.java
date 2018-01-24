/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2015 The eXist Project
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
package org.exist.util;

import com.googlecode.junittoolbox.ParallelSuite;
import org.exist.util.sorters.SortComparatorTest;
import org.exist.util.sorters.SortTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Perform comprehensive testing of the eXist sort algorithms.
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

@RunWith(ParallelSuite.class)
@Suite.SuiteClasses({
		SortTest.class,
		SortComparatorTest.class,
})
public class SortTests {
}
