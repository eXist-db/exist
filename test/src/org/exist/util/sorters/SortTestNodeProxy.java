/**
 * 
 */
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

import org.exist.dom.NodeProxy;

/**
 * Mock NodeProxy.
 * <p>
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

	public int compareTo(SortTestNodeProxy o) {
		if (val < 0)
			throw new IllegalStateException(
					"Sort ought not be looking at the value");
		return val - o.val;
	};

	public int compareTo(NodeProxy o) {
		return compareTo((SortTestNodeProxy) o);
	}

	public int compareTo(Object o) {
		return compareTo((SortTestNodeProxy) o);
	};

	public SortTestNodeId getNodeId() {
		return (SortTestNodeId) super.getNodeId();
	}

}