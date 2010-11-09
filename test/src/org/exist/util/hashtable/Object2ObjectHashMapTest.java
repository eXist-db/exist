package org.exist.util.hashtable;

import java.util.Iterator;

public class Object2ObjectHashMapTest extends AbstractHashtableTest<Object2ObjectHashMap, Object, Object> {
	
	protected Object2ObjectHashMap newT() {
		return new Object2ObjectHashMap();
	}

	protected Object simpleGet(Object k) {
		return map.get(k);
	}

	protected void simplePut(Object k, Object v) {
		map.put(k,v);
	}

	protected void simpleRemove(Object k) {
		map.remove(k);
	}

	protected boolean simpleContainsKey(int k) {
		return map.get(keyEquiv(k)) != null;
	}

	@SuppressWarnings("unchecked")
	protected Iterator<? extends Object> simpleValueIterator() {
		return map.valueIterator();
	}

	protected Object valEquiv(int v) {
		return v;
	}

	protected int valEquiv(Object v) {
		return (Integer) v;
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
