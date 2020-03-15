/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.hashtable;

import com.googlecode.junittoolbox.ParallelRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(ParallelRunner.class)
public abstract class AbstractHashSetTest<T, K> {

	protected T map;

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

    @Before
	public final void setUp() {
		map = newT();

	}

    @After
	public final void tearDown() {
		clearT();
		map = null;
	}

	@Test
	public void zeroKeys() throws Exception {
		assertFalse("empty collection should have no keys", simpleKeyIterator()
				.hasNext());
	}

    @Test
	public void setPut() throws Exception {
		simpleAdd(keyEquiv(12345));
		assertTrue(simpleContainsKey(keyEquiv(12345)));
	}

    @Test
	public void putAndRemove() throws Exception {
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

    @Test
	public void putDuplicates() {
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
