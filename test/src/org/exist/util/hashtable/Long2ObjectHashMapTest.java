package org.exist.util.hashtable;

import java.util.Iterator;

public class Long2ObjectHashMapTest extends
		AbstractHashtableTest<Long2ObjectHashMap, Long, Object> {

	protected Long2ObjectHashMap newT() {
		return new Long2ObjectHashMap();
	}

	protected void clearT() {
		map.clear();
	}

	protected int simpleGet(int k, int v) {
		return (Integer) map.get(k);
	}

	protected void simplePut(int k, int v) {
		map.put(k, v);
	}
	
	protected boolean simpleContainsKey(int k) {
		return map.get(keyEquiv(k))!=null;
	}


	@SuppressWarnings("unchecked")
	protected Iterator<Long> simpleKeyIterator() {
		return map.iterator();
	}

	@SuppressWarnings("unchecked")
	protected Iterator<Object> simpleValueIterator() {
		return map.valueIterator();
	}

	protected Object simpleGet(Long k) {
		return map.get(k);
	}

	protected void simplePut(Long k, Object v) {
		map.put(k, v);

	}

	protected void simpleRemove(Long k) {
		map.remove(k);
	}

	protected Object valEquiv(int v) {
		return v;
	}

	protected int valEquiv(Object v) {
		return (Integer) v;
	}

	protected Long keyEquiv(int k) {
		return (long) k;
	}

	protected Long keyEquiv_newObject(int k) {
		return Long.valueOf(k);
	}

	protected int keyEquiv(Long k) {
		return k.intValue();
	}
}
