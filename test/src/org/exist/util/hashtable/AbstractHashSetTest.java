package org.exist.util.hashtable;

import java.util.Iterator;
import java.util.Random;

import junit.framework.TestCase;

public abstract class AbstractHashSetTest<T, K> extends TestCase {

	protected T map;
	protected Random rnd = new Random();

	protected abstract T newT();

	protected abstract K keyEquiv(int k);

	protected abstract int keyEquiv(K k);

	/*
	 * make an equivalent of k, but always use a new object. This helps us to
	 * check that keys are compared by value, not by identity.
	 */
	protected abstract K keyEquiv_newObject(int k);

	protected abstract Iterator<? extends K> simpleKeyIterator();

	protected void clearT() {
	}

	protected abstract void simpleAdd(K k);

	protected abstract void simpleRemove(K k);

	protected boolean simpleContainsKey(K k) {
		for (Iterator<? extends K> ki = simpleKeyIterator(); ki.hasNext();) {
			if (k.equals(ki.next())) {
				return true;
			}
		}
		return false;
	}

	protected final void setUp() {
		map = newT();

	}

	protected final void tearDown() {
		clearT();
		map = null;
	}

	public void testZeroKeys() throws Exception {
		assertFalse("empty collection should have no keys", simpleKeyIterator()
				.hasNext());
	}

	public void testSetPut() throws Exception {
		simpleAdd(keyEquiv(12345));
		assertTrue(simpleContainsKey(keyEquiv(12345)));
	}

	public void testPutAndRemove() throws Exception {
		for (int i = 0; i < 10; i++) {
			simpleAdd(keyEquiv(i));
		}
		for (int i = 0; i < 10; i+=2) {
			simpleRemove(keyEquiv(i));
		}
		for (int i = 5; i < 10; i++) {
			simpleAdd(keyEquiv(i));
		}
		boolean[] test = new boolean[10];
		for (Iterator<? extends K> ki = simpleKeyIterator(); ki.hasNext();) {
			K k = ki.next();
			int kk = keyEquiv(k);
			test[kk] = true;
		}
		for (int i = 0; i < 10; i++) {
			assertEquals(test[i], i >=5 || (i%2)==1);
		}
		
	}

	public void testPutDuplicates() {
		for (int i = 0; i < 10; i++)
			for (int j = 0; j < 10; j++) {
				simpleAdd(keyEquiv_newObject(j));
			}

		boolean[] test = new boolean[10];
		for (Iterator<? extends K> ki = simpleKeyIterator(); ki.hasNext();) {
			K k = ki.next();
			int kk = keyEquiv(k);
			assertFalse("Key " + kk + " appears only once", test[kk]);
			test[kk] = true;
		}

		for (int i = 0; i < 10; i++) {
			assertTrue("key " + i + " appeared once", test[i]);
		}
	}

}