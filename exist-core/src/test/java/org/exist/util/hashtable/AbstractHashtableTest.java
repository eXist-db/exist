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
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;

import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
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

	@Test
	public void zeroValues() throws Exception {
		assertFalse("empty collection should have no values",
				simpleValueIterator().hasNext());
	}

    @Test
	public void getNothing() throws Exception {
		assertNull("empty collection should have no values",
				simpleGet(keyEquiv(12345)));
	}

    @Test
	public void valuePut() throws Exception {
		simplePut(keyEquiv(12345), valEquiv(54321));
		assertEquals(simpleGet(keyEquiv(12345)), valEquiv(54321));
	}

    @Test
	public void valueMultiplePut() {
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
