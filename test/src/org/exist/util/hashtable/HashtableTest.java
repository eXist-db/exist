/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.util.hashtable;

import com.googlecode.junittoolbox.ParallelRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
@RunWith(ParallelRunner.class)
public class HashtableTest {

    private int tabSize = 100000;
	private String values[] = new String[tabSize];

	@Test
	public void put() {
		int keys[] = new int[tabSize];
		Int2ObjectHashMap<String> table = new Int2ObjectHashMap<>(tabSize);
		Random rand = new Random(System.currentTimeMillis());
		for(int i = 0; i < tabSize; i++) {
			do {
				keys[i] = rand.nextInt(Integer.MAX_VALUE);
			} while(table.get(keys[i]) != null);
			values[i] = new String("a" + keys[i]);
			table.put(keys[i], values[i]);
		}

		for(int i = 0; i < tabSize; i++) {
			String v = table.get(keys[i]);
			assertEquals( values[i], v );
		}

		int r;
		for(int i = 0; i < tabSize / 10; i++) {
			do {
				r = rand.nextInt(tabSize - 1);
			} while(values[r] == null); 
			table.remove(keys[r]);
			values[r] = null;
		}
		for(int i = 0; i <tabSize; i++) {
			while(values[i] == null)
				i++;
			String v = table.get(keys[i]);
			assertNotNull("Key not found", v);
			assertEquals(values[i], v);
		}
		int c = 0;
		for(Iterator<?> i = table.iterator(); i.hasNext(); c++) {
			@SuppressWarnings("unused")
			Integer v = (Integer)i.next();
		}
		assertEquals(table.size(), c);
	}

	@Test
    public void objectHashMap() {
        String keys[] = new String[tabSize];
        long values[] = new long[tabSize];
        Object2LongHashMap<String> table = new Object2LongHashMap<>(tabSize / 4);
		Random rand = new Random(System.currentTimeMillis());
		for(int i = 0; i < tabSize; i++) {
			do {
				keys[i] = "/db/" + rand.nextInt(Integer.MAX_VALUE);
			} while(table.get(keys[i]) != -1);
            values[i] = rand.nextInt(Integer.MAX_VALUE);
            table.put(keys[i], values[i]);
		}

		for(int i = 0; i < tabSize; i++) {
			long k = table.get(keys[i]);
			assertEquals( values[i], k );
		}

        for(int i = 0; i < tabSize * 10; i++) {
            int idx0 = rand.nextInt(tabSize);
            table.remove(keys[idx0]);
            int idx1 = rand.nextInt(tabSize);
            table.remove(keys[idx1]);
            table.put(keys[idx0], values[idx0]);
            table.put(keys[idx1], values[idx1]);
        }

		for(int i = 0; i < tabSize; i++) {
			long k = table.get(keys[i]);
			assertEquals( values[i], k );
		}
    }

	@Test
    public void sequencedMap() {
		long keys[] = new long[tabSize];
		SequencedLongHashMap<String> table = new SequencedLongHashMap<>(tabSize);
		Random rand = new Random(System.currentTimeMillis());

		for(int i = 0; i < tabSize; i++) {
			do {
				keys[i] = rand.nextInt(Integer.MAX_VALUE);
			} while(table.get(keys[i]) != null);
			values[i] = new String("a" + keys[i]);
			table.put(keys[i], values[i]);
		}
        
		// check SequencedLongHashMap.get()
		for(int i = 0; i < tabSize; i++) {
			String v = table.get(keys[i]);
			assertEquals( values[i], v);
		}
		
		// check SequencedLongHashMap.iterator()
        int c = 0;
        for(Iterator<?> i = table.iterator(); i.hasNext(); c++) {
            Long v = (Long)i.next();
            assertEquals(keys[c], v.longValue());
        }
        assertEquals(c, table.size());
        
		// remove 1000 random items
		for(int i = 0; i < 10000; i++) {
			int k;
			do {
				k = rand.nextInt(tabSize - 1);
			} while(values[k] == null);
			table.remove(keys[k]);
			values[k] = null;
			assertNull(table.get(keys[k]));
		}
		
		// iterate through the sequence again
		int k = 0;
		c = 0;
		for(Iterator<?> i = table.iterator(); i.hasNext(); k++, c++) {
			while(values[k] == null)
				k++;
			Long v = (Long)i.next();
			assertTrue("Value has been removed and should be null", values[k] != null);
			assertEquals("Keys don't match", keys[k], v.longValue());
		}
		assertEquals("Hashtable size is incorrect", table.size(), values.length - 10000);
		assertEquals("Hashtable size is incorrect", table.size(), c);
		
		System.gc();
		
		// add some new items
		for(int i = 0; i < values.length; i++) {
			if(values[i] == null) {
				do {
					keys[i] = rand.nextInt(Integer.MAX_VALUE);
				} while(table.get(keys[i]) != null);
				values[i] = new String("a" + keys[i]);
				table.put(keys[i], values[i]);
			}
		}
        assertEquals(values.length, table.size());
        
		// check SequencedLongHashMap.get()
		for(int i = 0; i < tabSize; i++) {
			String v = table.get(keys[i]);
			assertEquals( values[i], v);
		}
		
        // update keys
        for (int i = 0; i < tabSize; i++) {
        	String v = table.get(keys[i]);
            values[i] = "b" + v;
            table.put(keys[i], values[i]);
        }
        
        // check SequencedLongHashMap.get()
        for(int i = 0; i < tabSize; i++) {
        	String v = table.get(keys[i]);
            assertEquals( values[i], v);
        }
        
		// check SequencedLongHashMap.iterator()
		c = 0;
		for(Iterator<?> i = table.iterator(); i.hasNext(); c++) {
			@SuppressWarnings("unused")
			Long v = (Long)i.next();
		}
		assertEquals(c, table.size());
		System.gc();
		
		for(int i = 0; i < values.length; i++) {
			table.removeFirst();
		}
		System.gc();
		Iterator<Long> iter = table.iterator();
		assertFalse("Hashtable should be empty", iter.hasNext());
		assertTrue(table.size() == 0);
	}

	@Test
    public void sequencedMap2() {
        long[] l = { 10, 100, 50, 250, 100, 15, 35, 250, 100, 65, 45, 50, 65, 80, 90, 70, 250, 100 };
        long[] expected = { 15, 35, 45, 50, 65, 80, 90, 70, 250, 100 };
        SequencedLongHashMap<String> table = new SequencedLongHashMap<>(tabSize);
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
