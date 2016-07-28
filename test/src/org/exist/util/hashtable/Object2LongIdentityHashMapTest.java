package org.exist.util.hashtable;

import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;

public class Object2LongIdentityHashMapTest extends AbstractHashtableTest<Object2LongIdentityHashMap, Object, Long> {

	private final java.util.HashMap<Integer, Integer> canonicalObject = new java.util.HashMap<>();

	@Override
	protected Object2LongIdentityHashMap newT() {
		return new Object2LongIdentityHashMap();
	}

	@Override
	protected Iterator<?> simpleKeyIterator() {
		return map.iterator();
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Iterator<? extends Long> simpleValueIterator() {
		return map.valueIterator();
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Long simpleGet(final Object k) {
		final long foo = map.get(k);
		return foo == -1 ? null : foo;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void simplePut(final Object k, final Long v) {
		map.put(k, v);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void simpleRemove(final Object k) {
		map.remove(k);
	}

	@SuppressWarnings("unchecked")
	protected boolean simpleContainsKey(final int k) {
		return map.containsKey(keyEquiv(k));
	}

	@Override
	protected Long valEquiv(final int v) {
		return (long) v;
	}

	@Override
	protected int valEquiv(final Long v) {
		return v.intValue();
	}

	@Override
	protected Object keyEquiv(final int k) {
		if(!canonicalObject.containsKey(k)) {
			canonicalObject.put(k,k);
		}
		return canonicalObject.get(k);
	}

	@Override
	protected int keyEquiv(final Object k) {
		return (Integer) k;
	}

	@Override
	protected Integer keyEquiv_newObject(final int k) {
		return new Integer(k);
	}

	/**
	 * We override this, because the identity hash map specifically behaves differently
	 * with respect to equality.
	 */
	@Test
	public void putDuplicates() {
		for (int i = 0; i < 10; i++)
			for (int j = 0; j < 10; j++) {
				simpleAdd(keyEquiv_newObject(j));
			}

		final int[] test = new int[10];
		for (final Iterator<?> ki = simpleKeyIterator(); ki.hasNext();) {
			final Object k = ki.next();
			final int kk = keyEquiv(k);
			test[kk]++;
		}

		for (int i = 0; i < 10; i++) {
			assertEquals("key " + i + " appeared 10 times", 10, test[i]);
		}
	}
}
