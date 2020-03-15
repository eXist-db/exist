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
package org.exist.util;

import java.util.Iterator;

/**
 * OrderedLongLinkedList.java
 * 
 * @author Wolfgang Meier
 */
public class OrderedLongLinkedList extends LongLinkedList {

	@Override
	public void add(long l) {
		if (first == null) {
			first = createListItem(l);
			last = first;
            count = 1;
		} else {
			ListItem newItem = createListItem(l);
			ListItem prev = last;
			while (prev != null) {
                final int cmp = newItem.compareTo(prev);
                if(cmp == 0)
                    {return;}
                if(cmp > 0) {
                    newItem.prev = prev;
                    newItem.next = prev.next;
                    if(prev == last)
                        {last = newItem;}
                    else
                        {newItem.next.prev = newItem;}
                    prev.next = newItem;
                    ++count;
                    return;
                }
                prev = prev.prev;
			}
            // insert as first item
            first.prev = newItem;
            newItem.next = first;
            first = newItem;
            ++count;
		}
	}
}
