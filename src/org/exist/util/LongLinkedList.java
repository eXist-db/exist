package org.exist.util;

import java.util.Iterator;

public class LongLinkedList {

	public final static class ListItem {
		
		long l;
		ListItem next = null;
		
		public ListItem( long l ) {
			this.l = l;
		}
        
        public long getValue() {
            return l;
        }
	}
	
	private ListItem first = null;
	private ListItem last = null;
	private int count = 0;
	
	public LongLinkedList() {
	}
	
	public void add( long l ) {
		if(first == null) {
			first = new ListItem( l );
			last = first;
		} else {
			ListItem next = new ListItem( l );
			last.next = next;
			last = next;
		}
		++count;
	}
	
    public int getSize() {
        return count;
    }
    
	public long[] getData() {
		long[] data = new long[count];
		ListItem next = first;
		int i = 0;
		while( next != null ) {
			data[i++] = next.l;
			next = next.next;
		}
		return data;
    }
    
    public Iterator iterator() {
        return new LongLinkedListIterator();
    }
    
    private final class LongLinkedListIterator implements Iterator {
        
        private ListItem next = first;
        
        public boolean hasNext() {
            return next != null;
        }
        
        public Object next() {
            Object temp = next;
            next = next.next;
            return temp;
        }
        
        public void remove() {
            throw new RuntimeException("not implemented");
        }
    }
}
