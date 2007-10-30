package org.exist.fluent;

import java.util.NoSuchElementException;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:45:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class DatabaseEmptiesTest extends DatabaseHelper {
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
