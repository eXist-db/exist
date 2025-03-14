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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

public class SequencedLongHashMapTest {

	@Test
	public void sequenceProperty() {
		final SequencedLongHashMap<Integer> map = new SequencedLongHashMap<>();

		map.put(1, 2);
		map.put(3, 4);
		map.put(11, 12);
		map.put(5, 6);
		map.put(7, 8);
		map.put(9, 10);

		map.put(3, 2);
		map.put(11, 10);
		map.put(5, 4);

		LongIterator ki = map.iterator();
		assertEquals(1, (int)ki.nextLong());
		assertEquals(7, (int)ki.nextLong());
		assertEquals(9, (int)ki.nextLong());
		assertEquals(3, (int)ki.nextLong());
		assertEquals(11, (int)ki.nextLong());
		assertEquals(5, (int)ki.nextLong());
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
		final long[] l = { 10, 100, 50, 250, 100, 15, 35, 250, 100, 65, 45, 50, 65, 80, 90, 70, 250, 100 };
		final long[] expected = { 15, 35, 45, 50, 65, 80, 90, 70, 250, 100 };

		final SequencedLongHashMap<String> map = new SequencedLongHashMap<>();

        for (long value : l) {
            map.put(value, "k" + value);
        }

		map.removeFirst();
		final Iterator<Long2ObjectMap.Entry<String>> entries = map.fastEntrySetIterator();
		int i = 0;
		while(entries.hasNext()) {
			Long2ObjectMap.Entry<String> next = entries.next();
			assertEquals(next.getLongKey(), expected[i]);
			i++;
		}
	}

	@Test
	public void zeroKeys() {
		final SequencedLongHashMap<String> map = new SequencedLongHashMap<>();
		LongIterator iterator = map.iterator();
		assertFalse("empty collection should have no keys", iterator.hasNext());
	}

	@Test
	public void getNothing() {
		final SequencedLongHashMap<Integer> map = new SequencedLongHashMap<>();
		assertNull("empty collection should have no values",
				map.get(12345));
	}

	@Test
	public void valuePut() {
		final SequencedLongHashMap<Integer> map = new SequencedLongHashMap<>();
		map.put(12345, 54321);
		assertEquals(54321, (int)map.get(12345));
	}

	@Test
	public void valueMultiplePut() {
		final SequencedLongHashMap<Integer> map = new SequencedLongHashMap<>();

		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				map.put(i + j, j);
			}
		}
		final int[] ct = new int[10];
		for (final Iterator<Integer> vi = map.valueIterator(); vi.hasNext();) {
			final Integer v = vi.next();
			final int vv = v;
			ct[vv]++;
		}
		assertEquals(10, ct[0]);
		for (int i = 1; i < 10; i++) {
			assertEquals(1, ct[i]);
		}
	}


	@Test
	public void putAndRemove() {
		final SequencedLongHashMap<Integer> map = new SequencedLongHashMap<>();

		for (int i = 0; i < 10; i++) {
			map.put(i, i);
		}
		for (int i = 0; i < 10; i+=2) {
			map.remove(i);
		}
		for (int i = 5; i < 10; i++) {
			map.put(i, i);
		}
		boolean[] test = new boolean[10];
		for (final LongIterator ki = map.iterator(); ki.hasNext();) {
			final long k = ki.nextLong();
			final int kk = (int)k;
			test[kk] = true;
		}
		for (int i = 0; i < 10; i++) {
			assertEquals(test[i], i >=5 || (i%2)==1);
		}
	}

	@Test
	public void putDuplicates() {
		final SequencedLongHashMap<Integer> map = new SequencedLongHashMap<>();

		for (int i = 0; i < 10; i++)
			for (int j = 0; j < 10; j++) {
				map.put(j, j);
			}

		boolean[] test = new boolean[10];
		for (final LongIterator ki = map.iterator(); ki.hasNext();) {
			final long k = ki.nextLong();
			final int kk = (int)k;
			assertFalse("Key " + kk + " appears only once", test[kk]);
			test[kk] = true;
		}

		for (int i = 0; i < 10; i++) {
			assertTrue("key " + i + " appeared once", test[i]);
		}
	}

	@Test
	public void expandable() {
		final SequencedLongHashMap<String> table = new SequencedLongHashMap<>(2);

		for (int i = 0; i < 8; i++) {
			table.put(i, "v" + i);
		}

		assertEquals(8, table.size());

		int count = 0;
		final Iterator<Long> iterator = table.iterator();
		while (iterator.hasNext()) {
			iterator.next();
			count++;
		}
		assertEquals(8, count);

	}
}
