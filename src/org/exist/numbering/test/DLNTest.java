/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
package org.exist.numbering.test;

import junit.framework.TestCase;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;

import java.util.Arrays;
import java.util.Random;

public class DLNTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(DLNTest.class);
    }

    private class TestItem implements Comparable {
        int id;
        NodeId dln;
        
        public TestItem(int id, DLN dln) {
            this.id = id;
            this.dln = dln;
        }
        
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append(id);
            buf.append(" = ");
            buf.append(dln.toString());
            return buf.toString();
        }
        
        public int compareTo(Object other) {
            return dln.compareTo(((TestItem) other).dln);
        }
    }
    
    private final static int ITEMS_TO_TEST = 10000;
    
    public void testSingleId() {
        long start = System.currentTimeMillis();
        
        System.out.println("------- testSingleId: generating " + ITEMS_TO_TEST + " random ids --------");
        Random rand = new Random();
        TestItem items[] = new TestItem[ITEMS_TO_TEST];
        for (int i = 0; i < ITEMS_TO_TEST; i++) {
            int next = rand.nextInt(5000000);
            DLN dln = new DLN();
            dln.setLevelId(0, next);
            items[i] = new TestItem(next, dln);
        }
        System.out.println("------ generation took " + (System.currentTimeMillis() - start));
        
        start = System.currentTimeMillis();
        System.out.println("------ sorting id set ------");
        Arrays.sort(items);
        System.out.println("------ sort took " + (System.currentTimeMillis() - start));
        
        System.out.println("------ testing id set ------");
        for (int i = 0; i < ITEMS_TO_TEST; i++) {
            assertEquals("Item: " + i, items[i].id, ((DLN)items[i].dln).getLevelId(0));
            if (i + 1 < ITEMS_TO_TEST)
                assertTrue(items[i].id <= items[i + 1].id);
            if (i > 0)
                assertTrue(items[i].id >= items[i - 1].id);
//            System.out.println(items[i].toBitString());
        }
        System.out.println("------- testSingleId: PASSED --------");
        Runtime rt = Runtime.getRuntime();
        System.out.println("Memory: total: " + (rt.totalMemory() / 1024) + 
                "; free: " + (rt.freeMemory() / 1024));
    }
    
    public void testSort() {
        long start = System.currentTimeMillis();
        
        System.out.println("------- testSort: generating " + ITEMS_TO_TEST + " random ids --------");
        Random rand = new Random();
        DLN items[] = new DLN[ITEMS_TO_TEST];
        for (int i = 0; i < ITEMS_TO_TEST; i++) {
            int next = rand.nextInt(5000000);
            DLN dln = new DLN();
            dln.setLevelId(0, next);
            items[i] = dln;
        }
        System.out.println("------ generation took " + (System.currentTimeMillis() - start));
        
        start = System.currentTimeMillis();
        System.out.println("------ sorting id set ------");
        Arrays.sort(items);
        System.out.println("------ sort took " + (System.currentTimeMillis() - start));
        
        System.out.println("------- testSortId: PASSED --------");
        Runtime rt = Runtime.getRuntime();
        System.out.println("Memory: total: " + (rt.totalMemory() / 1024) + 
                "; free: " + (rt.freeMemory() / 1024));
    }
    
    public void testCreate() {
        System.out.println("------ testCreate ------");
        DLN dln = new DLN();
        for (int i = 1; i < 500000; i++)
            dln.incrementLevelId();
        assertEquals(500000, dln.getLevelId(0));
        System.out.println("ID: " + dln.toBitString() + " = " + dln.getLevelId(0));
        System.out.println("------- testCreate: PASSED --------");
    }
    
    public void testLevelIds() {
        System.out.println("------ testLevelIds ------");
        int[] id0 = { 1, 33, 56, 2, 98, 1, 27 };
        int[] id1 = { 1, 56, 4, 33, 30, 11, 9, 40, 3, 2 };
        int[] id2 = { 1, 8000656, 40, 3, 2 };
        int[] id3 = { 1, 1 };
        int[] id4 = { 1, 72 };
        DLN dln = new DLN(id0);
        System.out.println("ID: " + dln.debug());
        assertEquals("1.33.56.2.98.1.27", dln.toString());
        
        dln = new DLN(id1);
        System.out.println("ID: " + dln.debug());
        assertEquals("1.56.4.33.30.11.9.40.3.2", dln.toString());
        
        dln = new DLN(id2);
        System.out.println("ID: " + dln.debug());
        assertEquals("1.8000656.40.3.2", dln.toString());

        dln = new DLN(id3);
        System.out.println("ID: " + dln.debug());
        assertEquals("1.1", dln.toString());

        dln = new DLN(id4);
        System.out.println("ID: " + dln.debug());
        assertEquals("1.72", dln.toString());

        dln = new DLN();
        for (int i = 0; i < id0.length; i++) {
            if (i > 0)
                dln.addLevelId(1);
            for (int j = 1; j < id0[i]; j++) dln.incrementLevelId();
            System.out.println("ID: " + dln.debug());
        }
        System.out.println("ID: " + dln.debug());
        assertEquals("1.33.56.2.98.1.27", dln.toString());
        
        System.out.println("------- testLevelIds: PASSED --------");
    }
    
    public void testRelations() {
    	System.out.println("------ testLevelRelations ------");
    	int[] id0 = { 1, 3 };
    	int[] id1 = { 1, 3, 1 };
    	int[] id2 = { 1, 3, 2, 5, 6 };
    	int[] id3 = { 1, 4 };
    	int[] id4 = { 1, 3, 2, 5, 6, 7777 };
    	int[] id5 = { 1, 3, 2, 5, 6, 7777, 1 };
    	
    	DLN root = new DLN(id0);
    	DLN descendant = new DLN(id1);
    	
    	System.out.println("Testing isDescendant: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	descendant = new DLN(id1);
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	descendant = new DLN(id1);
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isChildOf(root));
    	
    	descendant = new DLN(id2);
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	descendant = new DLN(id2);
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isChildOf(root));
    	
    	System.out.println("Testing isDescendantOrSelf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOrSelfOf(root));
    	
    	descendant = new DLN(id3);
    	System.out.println("Testing isDescendant: " + descendant + " -> " + root);
    	assertFalse(descendant.isDescendantOf(root));
    	
    	descendant = new DLN(id0);
    	System.out.println("Testing isDescendant: " + descendant + " -> " + root);
    	assertFalse(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isDescendantOrSelf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOrSelfOf(root));
    	
    	root = new DLN(id2);
    	descendant = new DLN(id4);
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isChildOf(root));
    	
    	descendant = new DLN(id5);
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isChildOf(root));
    	
    	System.out.println("------ testLevelRelations: PASSED ------");
    }
}