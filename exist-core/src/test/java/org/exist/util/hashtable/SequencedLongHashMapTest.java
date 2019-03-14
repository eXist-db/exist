package org.exist.util.hashtable;

import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SequencedLongHashMapTest extends AbstractHashtableTest<SequencedLongHashMap, Long, Object> {
	
	protected SequencedLongHashMap newT() {
		return new SequencedLongHashMap();
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

	protected boolean simpleContainsKey(int k) {
		return map.get(keyEquiv(k))!=null;
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

	protected Long keyEquiv(int k) {
		return (long) k;
	}

	protected Long keyEquiv_newObject(int k) {
		return Long.valueOf(k);
	}

	protected int keyEquiv(Long k) {
		return k.intValue();
	}

	@SuppressWarnings("unchecked")
	protected Iterator<? extends Long> simpleKeyIterator() {
		return map.iterator();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void sequenceProperty() throws Exception {
		map.put(1, 2);
		map.put(3, 4);
		map.put(11, 12);
		map.put(5, 6);
		map.put(7, 8);
		map.put(9, 10);
		
		map.put(3, 2);
		map.put(11, 10);
		map.put(5, 4);
		
		Iterator<Long> ki = map.iterator();
		assertEquals(1, (int) (long)ki.next());
		assertEquals(7, (int) (long)ki.next());
		assertEquals(9, (int) (long)ki.next());
		assertEquals(3, (int) (long)ki.next());
		assertEquals(11, (int) (long)ki.next());
		assertEquals(5, (int) (long)ki.next());
		assertFalse(ki.hasNext());
		
		Iterator<Integer> vi = map.valueIterator();
		assertEquals(2, (int) vi.next());
		assertEquals(8, (int) vi.next());
		assertEquals(10, (int) vi.next());
		assertEquals(2, (int) vi.next());
		assertEquals(10, (int) vi.next());
		assertEquals(4, (int) vi.next());
		assertFalse(vi.hasNext());
	}

	@Test
	public void sequencedMap2() {
		long[] l = { 10, 100, 50, 250, 100, 15, 35, 250, 100, 65, 45, 50, 65, 80, 90, 70, 250, 100 };
		long[] expected = { 15, 35, 45, 50, 65, 80, 90, 70, 250, 100 };
		SequencedLongHashMap<String> table = new SequencedLongHashMap<>(100000);
		for (int i = 0; i < l.length; i++) {
			table.put(l[i], "k" + l[i]);
		}
		table.removeFirst();
		SequencedLongHashMap.Entry<String> next = table.getFirstEntry();
		int i = 0;
		while(next != null) {
			assertEquals(next.getKey(), expected[i]);
			next = next.getNext();
			i++;
		}
	}
}
