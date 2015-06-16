package org.exist.util.hashtable;

import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;

public class Object2LongIdentityHashMapTest extends AbstractHashtableTest<Object2LongIdentityHashMap, Object, Long> {

	java.util.HashMap<Integer, Integer> canonicalObject = new java.util.HashMap<>();
	
	protected Object2LongIdentityHashMap newT() {
		return new Object2LongIdentityHashMap();
	}
	
	@SuppressWarnings("unchecked")
	protected Iterator<? extends Object> simpleKeyIterator() {
		return map.iterator();
	}

	@SuppressWarnings("unchecked")
	protected Iterator<? extends Long> simpleValueIterator() {
		return map.valueIterator();
	}

	protected Long simpleGet(Object k) {
		long foo = map.get(k);
		return foo == -1 ? null : foo;
	}

	protected void simplePut(Object k, Long v) {
		map.put(k, v);
	}

	protected void simpleRemove(Object k) {
		map.remove(k);
	}

	protected boolean simpleContainsKey(int k) {
		return map.containsKey(keyEquiv(k));
	}

	protected Long valEquiv(int v) {
		return (long) v;
	}

	protected int valEquiv(Long v) {
		return v.intValue();
	}

	protected Object keyEquiv(int k) {
		if(!canonicalObject.containsKey(k)) {
			canonicalObject.put(k,k);
		}
		return canonicalObject.get(k);
	}

	protected int keyEquiv(Object k) {
		return (Integer) k;
	}
	
	protected Integer keyEquiv_newObject(int k) {
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

		int[] test = new int[10];
		for (Iterator<? extends Object> ki = simpleKeyIterator(); ki.hasNext();) {
			Object k = ki.next();
			int kk = keyEquiv(k);
			test[kk]++;
		}

		for (int i = 0; i < 10; i++) {
			assertEquals("key " + i + " appeared 10 times", 10, test[i]);
		}
	}

	
}
