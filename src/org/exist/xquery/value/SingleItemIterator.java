/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

public class SingleItemIterator implements SequenceIterator {

	boolean more = true;
	Item item;

	public SingleItemIterator(Item item) {
		this.item = item;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.SequenceIterator#hasNext()
	 */
	public boolean hasNext() {
		return more;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.SequenceIterator#nextItem()
	 */
	public Item nextItem() {
		if (!more)
			{return null;}
		more = false;
		return item;
	}

}
