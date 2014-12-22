/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
 
package org.exist.xquery.value;

//TODO replace with extends Iterator<Item>
public interface SequenceIterator {

	public static final SequenceIterator EMPTY_ITERATOR = new EmptySequenceIterator();

	/**
	 * Determines if there is a next item in the sequence
	 *
	 * @return true if there is another item available, false otherwise.
	 */
	public boolean hasNext();

	/**
	 * Retrieves the next item from the Sequence
	 *
	 * @return The item, or null if there are no more items
	 */
	public Item nextItem();
}
