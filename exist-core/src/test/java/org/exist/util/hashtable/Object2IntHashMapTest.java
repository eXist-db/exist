package org.exist.util.hashtable;

import java.util.Iterator;

public class Object2IntHashMapTest extends
		AbstractHashtableTest<Object2IntHashMap, Object, Integer> {

	protected Object2IntHashMap newT() {
		return new Object2IntHashMap();
	}

	protected Integer simpleGet(Object k) {
		int foo = map.get(k);
		return foo == -1 ? null : foo;
	}

	protected void simplePut(Object k, Integer v) {
		map.put(k, v);
	}

	protected void simpleRemove(Object k) {
		map.remove(k);
	}

	protected boolean simpleContainsKey(int k) {
		return map.containsKey(keyEquiv(k));
	}

	@SuppressWarnings("unchecked")
	protected Iterator<? extends Integer> simpleValueIterator() {
		return map.valueIterator();
	}

	protected Integer valEquiv(int v) {
		return v;
	}

	protected int valEquiv(Integer v) {
		return v;
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

	@SuppressWarnings("unchecked")
	protected Iterator<? extends Object> simpleKeyIterator() {
		return map.iterator();
	}

}
