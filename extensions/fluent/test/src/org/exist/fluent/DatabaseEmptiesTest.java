package org.exist.fluent;

import java.util.NoSuchElementException;

import org.junit.Test;

import static org.junit.Assert.*;

public class DatabaseEmptiesTest extends DatabaseTestCase {
	@Test public void hasNext() {
		assertFalse(Database.EMPTY_ITERATOR.hasNext());
	}

	@SuppressWarnings("unchecked")
	@Test(expected = NoSuchElementException.class)
	public void next() {
		Database.EMPTY_ITERATOR.next();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void remove() {
		Database.EMPTY_ITERATOR.remove();
	}

	@SuppressWarnings("unchecked")
	@Test public void iterator() {
		assertSame(Database.EMPTY_ITERATOR, Database.EMPTY_ITERABLE.iterator());
	}
}
