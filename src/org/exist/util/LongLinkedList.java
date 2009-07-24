package org.exist.util;

import java.util.Iterator;

import org.exist.xquery.Constants;

public class LongLinkedList {

	public static class ListItem implements Comparable {
		
		public long l;
		
		public ListItem next = null;
		public ListItem prev = null;
        
		public ListItem() {
		}
		
		public ListItem( long l ) {
			this.l = l;
		}
        
        public long getValue() {
            return l;
        }
     
     	public ListItem getNext() {
     		return next;
     	}  
     	
		/**
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
            final long ol = ((ListItem)o).l;
            if(ol == l)
                return Constants.EQUAL;
            else if(l < ol)
                return Constants.INFERIOR;
            else
                return Constants.SUPERIOR;
		}

	}
	
	protected ListItem first = null;
	protected ListItem last = null;
	protected int count = 0;
	
	public LongLinkedList() {
	}
	
	public void add( long l ) {
		if(first == null) {
			first = createListItem( l );
			last = first;
		} else {
			ListItem next = createListItem( l );
			last.next = next;
            next.prev = last;
			last = next;
		}
		++count;
	}
	
    public int getSize() {
        return count;
    }
    
    public ListItem getFirst() {
    	return first;
    }
    
    public ListItem removeFirst() {
    	ListItem temp = first;
    	first = first.next;
    	if(first != null)
    		first.prev = null;
    	return temp;
    }
    
    public long getLast() {
    	if(last != null)
    		return last.l;
    	else
    		return -1;
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
    
	public String toString() {
		StringBuilder buf = new StringBuilder();
		ListItem next = first;
		while( next != null ) {
			buf.append(next.l).append(' ');
			next = next.next;
		}
		return buf.toString();
	}
	
    public Iterator iterator() {
        return new LongLinkedListIterator();
    }
    
    protected ListItem createListItem(long l) {
    	return new ListItem(l);
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
