/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.util.hashtable.test;

import java.util.Iterator;
import java.util.Random;

import junit.framework.TestCase;

import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.util.hashtable.Long2ObjectHashMap;
import org.exist.util.hashtable.Object2IntHashMap;
import org.exist.util.hashtable.Object2LongHashMap;
import org.exist.util.hashtable.Object2LongIdentityHashMap;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class HashtableTest extends TestCase {

    private int tabSize = 1000000;
	private Int2ObjectHashMap table = new Int2ObjectHashMap(tabSize);
	private int keys[] = new int[tabSize];
	private Object values[] = new Object[tabSize];

	public static void main(String[] args) {
		junit.textui.TestRunner.run(HashtableTest.class);
	}

	public void testPut() {
		Random rand = new Random(System.currentTimeMillis());
		for(int i = 0; i < tabSize; i++) {
			keys[i] = rand.nextInt(Integer.MAX_VALUE);
			values[i] = new String("a" + keys[i]);
			table.put(keys[i], values[i]);
		}
		for(int i = 0; i < tabSize; i++) {
			Object v = table.get(keys[i]);
			assertEquals( values[i], v);
		}
		int r;
		long p;
		for(int i = 0; i < tabSize / 10; i++) {
			do {
				r = rand.nextInt(tabSize - 1);
			} while(values[r] == null); 
			table.remove(keys[r]);
			//assertTrue(p > -1);
			values[r] = null;
		}
		for(int i = 0; i <tabSize; i++) {
			if(values[i] == null) {
				continue;
			}
			String v = (String)table.get(keys[i]);
			if(v == null)
				System.out.println("key " + keys[i] + " already removed?");
			else
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

}
