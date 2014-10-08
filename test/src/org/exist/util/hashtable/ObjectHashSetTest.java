package org.exist.util.hashtable;

import java.util.Iterator;

import org.exist.dom.QName;

public class ObjectHashSetTest extends AbstractHashSetTest<ObjectHashSet, Object> {
	
	protected ObjectHashSet newT() {
		return new ObjectHashSet();
	}

	protected Object keyEquiv(int k) {
		return k;
	}

	protected boolean simpleContainsKey(int k) {
		return map.contains(keyEquiv(k));
	}

	protected int keyEquiv(Object k) {
		return (Integer)k;
	}

	protected Integer keyEquiv_newObject(int k) {
		return Integer.valueOf(k);
	}

	protected void simpleAdd(Object k) {
		map.add(k);
	}

	protected void simpleRemove(Object k) {
		map.remove(k);
	}

	@SuppressWarnings("unchecked")
	protected Iterator<? extends Object> simpleKeyIterator() {
		return map.iterator();
	}
}
