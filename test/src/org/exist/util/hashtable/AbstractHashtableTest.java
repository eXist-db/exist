package org.exist.util.hashtable;

import java.util.Iterator;

public abstract class AbstractHashtableTest<T, K, V> extends
		AbstractHashSetTest<T, K> {

	protected abstract void simplePut(K k, V v);

	protected abstract V simpleGet(K k);

	protected abstract V valEquiv(int v);

	protected abstract int valEquiv(V v);

	protected abstract Iterator<? extends V> simpleValueIterator();

	protected void simpleAdd(K k) {
		simplePut(k, valEquiv(keyEquiv(k) ^ 0xDEADBEEF));
	}

	protected boolean simpleContainsKey(K k) {
		return simpleGet(k) != null;
	}

	protected void simpleCheckKey(K k) {
		assertTrue("contains " + k, simpleContainsKey(k));
		assertEquals("check " + k, keyEquiv(k) ^ 0xDEADBEEF,
				valEquiv(simpleGet(k)));
	}

	public void testZeroValues() throws Exception {
		assertFalse("empty collection should have no values",
				simpleValueIterator().hasNext());
	}

	public void testGetNothing() throws Exception {
		assertNull("empty collection should have no values",
				simpleGet(keyEquiv(12345)));
	}

	public void testValuePut() throws Exception {
		simplePut(keyEquiv(12345), valEquiv(54321));
		assertEquals(simpleGet(keyEquiv(12345)), valEquiv(54321));
	}

	public void testValueMultiplePut() {
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				simplePut(keyEquiv(i + j), valEquiv(j));
			}
		}
		int[] ct = new int[10];
		for (Iterator<? extends V> vi = simpleValueIterator(); vi.hasNext();) {
			V v = vi.next();
			int vv = valEquiv(v);
			ct[vv]++;
		}
		assertEquals(ct[0], 10);
		for (int i = 1; i < 10; i++)
			assertEquals(ct[i], 1);
	}

}
