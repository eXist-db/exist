package org.exist.util;

import java.util.Iterator;

public class LongLinkedList {

	public final static class ListItem implements Comparable {
		
		public long l;
		ListItem next = null;
		ListItem prev = null;
        
		public ListItem( long l ) {
			this.l = l;
		}
        
        public long getValue() {
            return l;
        }
        
		/**
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
			if(!(o instanceof ListItem))
                throw new IllegalArgumentException();
            final long ol = ((ListItem)o).l;
            if(ol == l)
                return 0;
            else if(l > ol)
                return 1;
            else
                return -1;
		}

	}
	
	protected ListItem first = null;
	protected ListItem last = null;
	protected int count = 0;
	
	public LongLinkedList() {
	}
	
	public void add( long l ) {
		if(first == null) {
			first = new ListItem( l );
			last = first;
		} else {
			ListItem next = new ListItem( l );
			last.next = next;
            next.prev = last;
			last = next;
		}
		++count;
	}
	
    public int getSize() {
        return count;
    }
    
    public boolean contains(long l) {
    	ListItem next = first;
    	while( next != null ) {
    		if(next.l == l)
    			return true;
    		next = next.next;
    	}
    	return false;
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
