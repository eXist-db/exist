/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util.hashtable;

import java.util.Iterator;
import java.util.Random;

import junit.framework.TestCase;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class HashtableTest extends TestCase {

    private int tabSize = 100000;
	private Object values[] = new Object[tabSize];

	public static void main(String[] args) {
		junit.textui.TestRunner.run(HashtableTest.class);
	}

	public HashtableTest(String testname) {
		super(testname);
	}
	
	public void testPut() {
		int keys[] = new int[tabSize];
		Int2ObjectHashMap table = new Int2ObjectHashMap(tabSize);
		Random rand = new Random(System.currentTimeMillis());
		System.out.println("Generating " + tabSize + " random keys...");
		for(int i = 0; i < tabSize; i++) {
			do {
				keys[i] = rand.nextInt(Integer.MAX_VALUE);
			} while(table.get(keys[i]) != null);
			values[i] = new String("a" + keys[i]);
			table.put(keys[i], values[i]);
		}
		System.out.println("Testing get(key) ...");
		for(int i = 0; i < tabSize; i++) {
			Object v = table.get(keys[i]);
			assertEquals( values[i], v );
		}
		System.out.println("Testing remove(key) ...");
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
			String v = (String)table.get(keys[i]);
			assertNotNull("Key not found", v);
			assertEquals(values[i], v);
		}
		int c = 0;
		for(Iterator i = table.iterator(); i.hasNext(); c++) {
			Integer v = (Integer)i.next();
		}
		System.out.println(table.size() + " = " + c);
		System.out.println("maxRehash: " + table.getMaxRehash());
		assertEquals(table.size(), c);
	}

    public void testObjectHashMap() {
        String keys[] = new String[tabSize];
        long values[] = new long[tabSize];
        Object2LongHashMap table = new Object2LongHashMap(tabSize / 4);
		Random rand = new Random(System.currentTimeMillis());
		System.out.println("Generating " + tabSize + " random keys...");
		for(int i = 0; i < tabSize; i++) {
			do {
				keys[i] = "/db/" + rand.nextInt(Integer.MAX_VALUE);
			} while(table.get(keys[i]) != -1);
            values[i] = rand.nextInt(Integer.MAX_VALUE);
            table.put(keys[i], values[i]);
		}
        System.out.println("Testing get(key) ...");
		for(int i = 0; i < tabSize; i++) {
			long k = table.get(keys[i]);
			assertEquals( values[i], k );
		}
        System.out.println("Remove/add keys ...");
        for(int i = 0; i < tabSize * 10; i++) {
            int idx0 = rand.nextInt(tabSize);
            table.remove(keys[idx0]);
            int idx1 = rand.nextInt(tabSize);
            table.remove(keys[idx1]);
            table.put(keys[idx0], values[idx0]);
            table.put(keys[idx1], values[idx1]);
        }
        System.out.println("Testing get(key) ...");
		for(int i = 0; i < tabSize; i++) {
			long k = table.get(keys[i]);
			assertEquals( values[i], k );
		}
    }

    public void testSequencedMap() {
		long keys[] = new long[tabSize];
		SequencedLongHashMap table = new SequencedLongHashMap(tabSize);
		Random rand = new Random(System.currentTimeMillis());
		System.out.println("Generating " + tabSize + " random keys...");
		for(int i = 0; i < tabSize; i++) {
			do {
				keys[i] = rand.nextInt(Integer.MAX_VALUE);
			} while(table.get(keys[i]) != null);
			values[i] = new String("a" + keys[i]);
			table.put(keys[i], values[i]);
		}
        
		// check SequencedLongHashMap.get()
		for(int i = 0; i < tabSize; i++) {
			Object v = table.get(keys[i]);
			assertEquals( values[i], v);
		}
		
		// check SequencedLongHashMap.iterator()
        int c = 0;
        for(Iterator i = table.iterator(); i.hasNext(); c++) {
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
		System.out.println("Hashtable size: " + table.size());
		
		// iterate through the sequence again
		int k = 0;
		c = 0;
		for(Iterator i = table.iterator(); i.hasNext(); k++, c++) {
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
			Object v = table.get(keys[i]);
			assertEquals( values[i], v);
		}
		
        // update keys
        for (int i = 0; i < tabSize; i++) {
            Object v = table.get(keys[i]);
            values[i] = "b" + v;
            table.put(keys[i], values[i]);
        }
        
        // check SequencedLongHashMap.get()
        for(int i = 0; i < tabSize; i++) {
            Object v = table.get(keys[i]);
            assertEquals( values[i], v);
        }
        
		// check SequencedLongHashMap.iterator()
		c = 0;
		for(Iterator i = table.iterator(); i.hasNext(); c++) {
			Long v = (Long)i.next();
		}
		assertEquals(c, table.size());
		System.gc();
		
		for(int i = 0; i < values.length; i++) {
			table.removeFirst();
		}
		System.gc();
		Iterator iter = table.iterator();
		assertFalse("Hashtable should be empty", iter.hasNext());
		assertTrue(table.size() == 0);
		
		System.out.println("Hashtable size: " + table.size());
		System.out.println("maxRehash: " + table.getMaxRehash());
	}
    
    public void testSequencedMap2() {
        long[] l = { 10, 100, 50, 250, 100, 15, 35, 250, 100, 65, 45, 50, 65, 80, 90, 70, 250, 100 };
        long[] expected = { 15, 35, 45, 50, 65, 80, 90, 70, 250, 100 };
        SequencedLongHashMap table = new SequencedLongHashMap(tabSize);
        for (int i = 0; i < l.length; i++) {
            table.put(l[i], "k" + l[i]);
        }
        table.removeFirst();
        SequencedLongHashMap.Entry next = table.getFirstEntry();
        int i = 0;
        while(next != null) {
            assertEquals(next.getKey(), expected[i]);
            System.out.print(next.getKey());
            System.out.print(' ');
            next = next.getNext();
            i++;
        }
        System.out.println();
    }
}
