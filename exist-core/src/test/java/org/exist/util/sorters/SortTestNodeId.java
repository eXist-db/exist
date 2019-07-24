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

import java.io.IOException;

import org.exist.numbering.NodeId;
import org.exist.storage.io.VariableByteOutputStream;

/**
 * Mock NodeId.
 *
 * This work was undertaken as part of the development of the taxonomic
 * repository at http://biodiversity.org.au . See <A
 * href="ghw-at-anbg.gov.au">Greg&nbsp;Whitbread</A> for further details.
 * 
 * @author pmurray@bigpond.com
 * @author pmurray@anbg.gov.au
 * @author https://sourceforge.net/users/paulmurray
 * @author http://www.users.bigpond.com/pmurray
 * @see NodeId
 * 
 */
class SortTestNodeId implements NodeId {
	final int i;

	SortTestNodeId(int i) {
		this.i = i;
	}

	public boolean after(NodeId arg0, boolean arg1) {
		throw new UnsupportedOperationException();
	}

	public boolean before(NodeId arg0, boolean arg1) {
		throw new UnsupportedOperationException();
	}

	public int compareTo(SortTestNodeId arg0) {
		if (i < 0)
			throw new IllegalStateException(
					"Sort ought not be looking at the nodeid");
		return i - arg0.i;
	}

	public int compareTo(NodeId arg0) {
		return compareTo((SortTestNodeId) arg0);
	}

	public int computeRelation(NodeId arg0) {
		throw new UnsupportedOperationException();
	}

	public boolean equals(NodeId arg0) {
		return i == ((SortTestNodeId) arg0).i;
	}

	public NodeId getChild(int arg0) {
		throw new UnsupportedOperationException();
	}

	public NodeId getParentId() {
		throw new UnsupportedOperationException();
	}

	public int getTreeLevel() {
		throw new UnsupportedOperationException();
	}

	public NodeId insertBefore() {
		throw new UnsupportedOperationException();
	}

	public NodeId insertNode(NodeId arg0) {
		throw new UnsupportedOperationException();
	}

	public boolean isChildOf(NodeId arg0) {
		throw new UnsupportedOperationException();
	}

	public boolean isDescendantOf(NodeId arg0) {
		throw new UnsupportedOperationException();
	}

	public boolean isDescendantOrSelfOf(NodeId arg0) {
		throw new UnsupportedOperationException();
	}

	public boolean isSiblingOf(NodeId arg0) {
		throw new UnsupportedOperationException();
	}

	public NodeId newChild() {
		throw new UnsupportedOperationException();
	}

	public NodeId nextSibling() {
		throw new UnsupportedOperationException();
	}

	public NodeId precedingSibling() {
		throw new UnsupportedOperationException();
	}

	public void serialize(byte[] arg0, int arg1) {
		throw new UnsupportedOperationException();
	}

	public int size() {
		throw new UnsupportedOperationException();
	}

	public int units() {
		throw new UnsupportedOperationException();
	}

	public void write(VariableByteOutputStream arg0) {
		throw new UnsupportedOperationException();
	}

	public NodeId write(NodeId arg0, VariableByteOutputStream arg1)
	throws IOException {
		throw new UnsupportedOperationException();
	}

	public NodeId append(NodeId other) {
		throw new UnsupportedOperationException();
	}
}