package org.exist.util.hashtable;

import java.util.Iterator;

public class Int2ObjectHashMapTest extends
		AbstractHashtableTest<Int2ObjectHashMap, Integer, Object> {

	protected Int2ObjectHashMap newT() {
		return new Int2ObjectHashMap();
	}

	protected void clearT() {
		map.clear();
	}

	protected int simpleGet(int k) {
		return (Integer) map.get(k);
	}

	protected void simplePut(int k, int v) {
		map.put(k, v);
	}

	protected boolean simpleContainsKey(int k) {
		return map.containsKey(keyEquiv(k));
	}

	@SuppressWarnings("unchecked")
	protected Iterator<Integer> simpleKeyIterator() {
		return (Iterator<Integer>) map.iterator();
	}

	@SuppressWarnings("unchecked")
	protected Iterator<Object> simpleValueIterator() {
		return (Iterator<Object>) map.valueIterator();
	}

	protected Object simpleGet(Integer k) {
		return map.get(k);
	}

	protected void simplePut(Integer k, Object v) {
		map.put(k, v);
	}

	protected void simpleRemove(Integer k) {
		map.remove(k);
	}

	protected Object valEquiv(int a) {
		return Integer.valueOf(a);
	}

	protected int valEquiv(Object v) {
		return (Integer) v;
	}

	protected Integer keyEquiv(int k) {
		return k;
	}

	protected Integer keyEquiv_newObject(int k) {
		return Integer.valueOf(k);
	}

	protected int keyEquiv(Integer k) {
		return k;
	}
}
