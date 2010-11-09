package org.exist.util.hashtable;

import java.util.Iterator;

public class Object2LongHashMapTest extends
		AbstractHashtableTest<Object2LongHashMap, Object, Long> {

	protected Object2LongHashMap newT() {
		return new Object2LongHashMap();
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
		return k;
	}

	protected Integer keyEquiv_newObject(int k) {
		return Integer.valueOf(k);
	}

	protected int keyEquiv(Object k) {
		return (Integer) k;
	}
	

}
