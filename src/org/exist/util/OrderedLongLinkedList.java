package org.exist.util;

import java.util.Iterator;

/**
 * OrderedLongLinkedList.java
 * 
 * @author Wolfgang Meier
 */
public class OrderedLongLinkedList extends LongLinkedList {

    
	/**
	 * Constructor for OrderedLongLinkedList.
	 */
	public OrderedLongLinkedList() {
		super();
	}

	/**
	 * @see org.exist.util.LongLinkedList#add(long)
	 */
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
                    return;
                if(cmp > 0) {
                    newItem.prev = prev;
                    newItem.next = prev.next;
                    if(prev == last)
                        last = newItem;
                    else
                        newItem.next.prev = newItem;
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

    public static void main(String[] args) {
        OrderedLongLinkedList list = new OrderedLongLinkedList();
        list.add(7);
        list.add(44);
        list.add(4);
        list.add(-43);
        list.add(60);
        list.add(-122);
        list.add(1);
        System.out.println("size: " + list.getSize());
        for(Iterator i = list.iterator(); i.hasNext(); ) {
            final OrderedLongLinkedList.ListItem item =
                (OrderedLongLinkedList.ListItem)i.next();
            System.out.println(item.l);
        }
    }
}
