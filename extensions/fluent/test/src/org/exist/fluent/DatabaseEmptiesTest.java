package org.exist.fluent;

import java.util.NoSuchElementException;

public class DatabaseEmptiesTest extends DatabaseTestCase {
    public void testHasNext() {
        assertFalse(Database.EMPTY_ITERATOR.hasNext());
    }

    @SuppressWarnings("unchecked")
		public void testNext() {
        try {
            Database.EMPTY_ITERATOR.next();
            fail();
        } catch (NoSuchElementException e) {}
    }

    public void testRemove() {
        try {
            Database.EMPTY_ITERATOR.remove();
            fail();
        } catch (UnsupportedOperationException e) {}
    }

    @SuppressWarnings("unchecked")
		public void testIterator() {
        assertSame(Database.EMPTY_ITERATOR, Database.EMPTY_ITERABLE.iterator());
    }
}
