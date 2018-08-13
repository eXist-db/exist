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
