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

import org.exist.dom.persistent.NodeProxy;

/**
 * Mock NodeProxy.
 *
 * This work was undertaken as part of the development of the taxonomic
 * repository at http://biodiversity.org.au . See <A
 * href="ghw-at-anbg.gov.au">Greg&nbsp;Whitbread</A> for further details.
 * 
 * @author pmurray@bigpond.com
 * @author pmurray@anbg.gov.au
 * @author https://sourceforge.net/users/paulmurray
 * @author http://www.users.bigpond.com/pmurray
 * @see NodeProxy
 * 
 */
class SortTestNodeProxy extends NodeProxy {
	final int val;

	public SortTestNodeProxy(int id, int val) {
		super(null, new SortTestNodeId(id));
		this.val = val;
	}

	public int compareTo(Object o) {
		if (val < 0)
			throw new IllegalStateException(
					"Sort ought not be looking at the value");
		if(!(o instanceof SortTestNodeProxy))
			throw new IllegalStateException("Test implementation limitation hit");
			
		return val - ((SortTestNodeProxy)o).val;
	}

	public SortTestNodeId getNodeId() {
		return (SortTestNodeId) super.getNodeId();
	}

}