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
package org.exist.numbering;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Random;

public class DLNTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(DLNTest.class);
    }

    private class TestItem implements Comparable<TestItem> {
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
        
        public int compareTo(TestItem other) {
            return dln.compareTo(other.dln);
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
        for (int i = 1; i < 500000; i++) {
            dln.incrementLevelId();
        }
        assertEquals(500000, dln.getLevelId(0));
        System.out.println("ID: " + dln.toBitString() + " = " + dln.getLevelId(0));
        System.out.println("------- testCreate: PASSED --------");
    }
    
    public void testLevelIds() {
        System.out.println("------ testLevelIds ------");
        DLN dln = new DLN("1.33.56.2.98.1.27");
        System.out.println("ID: " + dln.debug());
        assertEquals("1.33.56.2.98.1.27", dln.toString());
        
        dln = new DLN("1.56.4.33.30.11.9.40.3.2");
        System.out.println("ID: " + dln.debug());
        assertEquals("1.56.4.33.30.11.9.40.3.2", dln.toString());
        assertEquals(10, dln.getLevelCount(0));
        
        dln = new DLN("1.8000656.40.3.2");
        System.out.println("ID: " + dln.debug());
        assertEquals("1.8000656.40.3.2", dln.toString());
        assertEquals(5, dln.getLevelCount(0));
        
        dln = new DLN("1.1");
        System.out.println("ID: " + dln.debug());
        assertEquals("1.1", dln.toString());
        assertEquals(2, dln.getLevelCount(0));
        dln.incrementLevelId();
        System.out.println("ID after increment: " + dln.debug());
        assertEquals("1.2", dln.toString());
        assertEquals(2, dln.getLevelCount(0));
        System.out.println(((DLN)dln.getParentId()).toBitString());
        assertEquals("1", dln.getParentId().toString());
        
        dln = new DLN("1");
        System.out.println("ID: " + dln.debug());
        assertEquals("1", dln.toString());
        assertEquals(1, dln.getLevelCount(0));
        assertSame(NodeId.DOCUMENT_NODE, dln.getParentId());
        
        dln = new DLN("1.72");
        System.out.println("ID: " + dln.debug());
        assertEquals("1.72", dln.toString());
        
        dln = new DLN("1.7.3/1.34");
        System.out.println("ID: " + dln.debug());
        assertEquals("1.7.3/1.34", dln.toString());
        assertEquals(4, dln.getLevelCount(0));
        
        dln = new DLN("1.7.3.1/34");
        System.out.println("ID: " + dln.debug());
        assertEquals("1.7.3.1/34", dln.toString());
        assertEquals(4, dln.getLevelCount(0));
        dln.incrementLevelId();
        System.out.println("ID after increment: " + dln.debug());
        assertEquals("1.7.3.1/35", dln.toString());
        assertEquals(4, dln.getLevelCount(0));
        
        dln = new DLN("1.2.1/2/3");
        assertEquals(3, dln.getSubLevelCount(dln.lastLevelOffset()));
        
        dln = new DLN("1/2/3");
        assertEquals(3, dln.getSubLevelCount(dln.lastLevelOffset()));
        
        System.out.println("------- testing DLN.incrementLevelId --------");
        int[] id0 = new int[] { 1, 33, 56, 2, 98, 1, 27 };
        dln = new DLN();
        for (int i = 0; i < id0.length; i++) {
            if (i > 0)
                dln.addLevelId(1, false);
            for (int j = 1; j < id0[i]; j++) dln.incrementLevelId();
            System.out.println("ID: " + dln.debug());
        }
        System.out.println("ID: " + dln.debug());
        assertEquals("1.33.56.2.98.1.27", dln.toString());
        assertEquals(7, dln.getLevelCount(0));
        
        System.out.println("------- testLevelIds: PASSED --------");
    }
    
    public void testRelations() {
    	System.out.println("------ testLevelRelations ------");
    	DLN root = new DLN("1.3");
    	DLN descendant = new DLN("1.3.1");
    	
    	System.out.println("Testing isDescendant: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isChildOf(root));
    	
    	System.out.println("Testing getParentId: " + descendant + " -> " + root);
    	assertTrue(root.equals(descendant.getParentId()));
    	
    	descendant = new DLN("1.3.2.5.6");
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isChildOf(root));
    	
    	System.out.println("Testing isDescendantOrSelf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOrSelfOf(root));
    	
    	descendant = new DLN("1.4");
    	System.out.println("Testing isDescendant: " + descendant + " -> " + root);
    	assertFalse(descendant.isDescendantOf(root));
    	
    	descendant = new DLN("1.3");
    	System.out.println("Testing isDescendant: " + descendant + " -> " + root);
    	assertFalse(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isDescendantOrSelf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOrSelfOf(root));
    	
    	root = new DLN("1.3.2.5.6");
    	descendant = new DLN("1.3.2.5.6.7777");
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isChildOf(root));
    	
    	System.out.println("Testing getParentId: " + descendant + " -> " + root);
    	assertTrue(root.equals(descendant.getParentId()));
    	
    	descendant = new DLN("1.3.2.5.6.7777.1");
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isChildOf(root));
    	
    	root = new DLN("1.3.1");
    	descendant = new DLN("1.3.2");
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isDescendantOf(root));
    	
    	root = new DLN("1.6.6.66");
    	descendant = new DLN("1.6.6.65.1");
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isChildOf(root));
    	
    	descendant = new DLN("1.6.6.66");
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isChildOf(root));
    	
    	root = new DLN("1.3.1/1");
    	descendant = new DLN("1.3.1/1.1");
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isChildOf(root));
    	
    	descendant = (DLN) root.newChild();
    	assertEquals("1.3.1/1.1", descendant.toString());
    	descendant.incrementLevelId();
    	assertEquals("1.3.1/1.2", descendant.toString());
    	
    	System.out.println("Parent of " + descendant + " -> " + descendant.getParentId());
    	assertTrue(root.equals(descendant.getParentId()));
    	
    	descendant = new DLN("1.3.1/1.2.2");
    	System.out.println("Testing isChildOf: " + descendant + " -> " + root);
    	assertFalse(descendant.isChildOf(root));
    	
    	System.out.println("Testing isDescendantOf: " + descendant + " -> " + root);
    	assertTrue(descendant.isDescendantOf(root));
    	
    	NodeId left = new DLN("1.3.1");
    	NodeId dln = new DLN("1.3.1/1");
    	NodeId right = new DLN("1.3.2");
    	
    	assertTrue(dln.compareTo(right) < 0);
    	assertTrue(dln.compareTo(left) > 0);
    	assertTrue(left.compareTo(dln) < 0);
    	assertTrue(right.compareTo(dln) > 0);
    	assertTrue(left.compareTo(right) < 0);

        System.out.println("Testing isSiblingOf ...");
        DLN id0 = new DLN("1.1.7");
        DLN id1 = new DLN("1.1.6");
        DLN id2 = new DLN("1.1.7.1");
        DLN id3 = new DLN("1.1.7/1");

        assertTrue(id0.isSiblingOf(id1));
        assertTrue(id1.isSiblingOf(id0));
        assertFalse(id0.isSiblingOf(id2));
        assertFalse(id2.isSiblingOf(id0));
        assertTrue(id3.isSiblingOf(id0));
        assertTrue(id0.isSiblingOf(id3));
        
        System.out.println("------ testLevelRelations: PASSED ------");
    }
    
    public void testInsertion() {
        System.out.println("------ testInsertion ------");
        DLN left = new DLN("1.1"); 
        DLN right = (DLN) left.insertNode(null);
        assertEquals("1.2", right.toString());
        
        DLN dln = (DLN) left.insertNode(right);
        System.out.println(dln);
        assertEquals("1.1/1", dln.toString());
        
        left = dln;
        dln = (DLN) left.insertNode(right);
        System.out.println(dln);
        assertEquals("1.1/2", dln.toString());
        
        right = dln;
        dln = (DLN) left.insertNode(right);
        System.out.println(dln);
        assertEquals("1.1/1/1", dln.toString());
        
        right = new DLN("1.1/1");
        left = new DLN("1.1");
        dln = (DLN) left.insertNode(right);
        System.out.println(dln);
        assertEquals("1.1/0/35", dln.toString());
        
        right = dln;
        dln = (DLN) left.insertNode(right);
        System.out.println(dln);
        assertEquals("1.1/0/34", dln.toString());
        
        System.out.println("------ testInsertion: PASSED ------");
    }
}