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
package org.exist.numbering;

import com.googlecode.junittoolbox.ParallelRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class DLNTest {

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

    @Test
    public void singleId() {
        Random rand = new Random();
        TestItem items[] = new TestItem[ITEMS_TO_TEST];
        for (int i = 0; i < ITEMS_TO_TEST; i++) {
            int next = rand.nextInt(5000000);
            DLN dln = new DLN();
            dln.setLevelId(0, next);
            items[i] = new TestItem(next, dln);
        }

        Arrays.sort(items);

        for (int i = 0; i < ITEMS_TO_TEST; i++) {
            assertEquals("Item: " + i, items[i].id, ((DLN)items[i].dln).getLevelId(0));
            if (i + 1 < ITEMS_TO_TEST)
                assertTrue(items[i].id <= items[i + 1].id);
            if (i > 0)
                assertTrue(items[i].id >= items[i - 1].id);
        }
    }

    @Test
    public void sort() {
        Random rand = new Random();
        DLN items[] = new DLN[ITEMS_TO_TEST];
        for (int i = 0; i < ITEMS_TO_TEST; i++) {
            int next = rand.nextInt(5000000);
            DLN dln = new DLN();
            dln.setLevelId(0, next);
            items[i] = dln;
        }
        Arrays.sort(items);
    }

    @Test
    public void create() {
        DLN dln = new DLN();
        for (int i = 1; i < 500000; i++) {
            dln.incrementLevelId();
        }
        assertEquals(500000, dln.getLevelId(0));
    }

    @Test
    public void levelIds() {
        DLN dln = new DLN("1.33.56.2.98.1.27");
        assertEquals("1.33.56.2.98.1.27", dln.toString());
        
        dln = new DLN("1.56.4.33.30.11.9.40.3.2");
        assertEquals("1.56.4.33.30.11.9.40.3.2", dln.toString());
        assertEquals(10, dln.getLevelCount(0));
        
        dln = new DLN("1.8000656.40.3.2");
        assertEquals("1.8000656.40.3.2", dln.toString());
        assertEquals(5, dln.getLevelCount(0));
        
        dln = new DLN("1.1");
        assertEquals("1.1", dln.toString());
        assertEquals(2, dln.getLevelCount(0));
        dln.incrementLevelId();
        assertEquals("1.2", dln.toString());
        assertEquals(2, dln.getLevelCount(0));
        assertEquals("1", dln.getParentId().toString());
        
        dln = new DLN("1");
        assertEquals("1", dln.toString());
        assertEquals(1, dln.getLevelCount(0));
        assertSame(NodeId.DOCUMENT_NODE, dln.getParentId());
        
        dln = new DLN("1.72");
        assertEquals("1.72", dln.toString());
        
        dln = new DLN("1.7.3/1.34");
        assertEquals("1.7.3/1.34", dln.toString());
        assertEquals(4, dln.getLevelCount(0));
        
        dln = new DLN("1.7.3.1/34");
        assertEquals("1.7.3.1/34", dln.toString());
        assertEquals(4, dln.getLevelCount(0));
        dln.incrementLevelId();
        assertEquals("1.7.3.1/35", dln.toString());
        assertEquals(4, dln.getLevelCount(0));
        
        dln = new DLN("1.2.1/2/3");
        assertEquals(3, dln.getSubLevelCount(dln.lastLevelOffset()));
        
        dln = new DLN("1/2/3");
        assertEquals(3, dln.getSubLevelCount(dln.lastLevelOffset()));

        int[] id0 = new int[] { 1, 33, 56, 2, 98, 1, 27 };
        dln = new DLN();
        for (int i = 0; i < id0.length; i++) {
            if (i > 0)
                dln.addLevelId(1, false);
            for (int j = 1; j < id0[i]; j++) dln.incrementLevelId();
        }
        assertEquals("1.33.56.2.98.1.27", dln.toString());
        assertEquals(7, dln.getLevelCount(0));
    }

    @Test
    public void relations() {
    	DLN root = new DLN("1.3");
    	DLN descendant = new DLN("1.3.1");

    	assertTrue(descendant.isDescendantOf(root));

    	assertTrue(descendant.isChildOf(root));

    	assertTrue(root.equals(descendant.getParentId()));
    	
    	descendant = new DLN("1.3.2.5.6");
    	assertTrue(descendant.isDescendantOf(root));

    	assertFalse(descendant.isChildOf(root));

    	assertTrue(descendant.isDescendantOrSelfOf(root));
    	
    	descendant = new DLN("1.4");
    	assertFalse(descendant.isDescendantOf(root));
    	
    	descendant = new DLN("1.3");
    	assertFalse(descendant.isDescendantOf(root));

    	assertTrue(descendant.isDescendantOrSelfOf(root));
    	
    	root = new DLN("1.3.2.5.6");
    	descendant = new DLN("1.3.2.5.6.7777");
    	assertTrue(descendant.isDescendantOf(root));

    	assertTrue(descendant.isChildOf(root));

    	assertTrue(root.equals(descendant.getParentId()));
    	
    	descendant = new DLN("1.3.2.5.6.7777.1");
    	assertTrue(descendant.isDescendantOf(root));

    	assertFalse(descendant.isChildOf(root));
    	
    	root = new DLN("1.3.1");
    	descendant = new DLN("1.3.2");
    	assertFalse(descendant.isDescendantOf(root));
    	
    	root = new DLN("1.6.6.66");
    	descendant = new DLN("1.6.6.65.1");
    	assertFalse(descendant.isChildOf(root));
    	
    	descendant = new DLN("1.6.6.66");
    	assertFalse(descendant.isChildOf(root));
    	
    	root = new DLN("1.3.1/1");
    	descendant = new DLN("1.3.1/1.1");
    	assertTrue(descendant.isChildOf(root));
    	
    	descendant = (DLN) root.newChild();
    	assertEquals("1.3.1/1.1", descendant.toString());
    	descendant.incrementLevelId();
    	assertEquals("1.3.1/1.2", descendant.toString());

    	assertTrue(root.equals(descendant.getParentId()));
    	
    	descendant = new DLN("1.3.1/1.2.2");
    	assertFalse(descendant.isChildOf(root));

    	assertTrue(descendant.isDescendantOf(root));
    	
    	NodeId left = new DLN("1.3.1");
    	NodeId dln = new DLN("1.3.1/1");
    	NodeId right = new DLN("1.3.2");
    	
    	assertTrue(dln.compareTo(right) < 0);
    	assertTrue(dln.compareTo(left) > 0);
    	assertTrue(left.compareTo(dln) < 0);
    	assertTrue(right.compareTo(dln) > 0);
    	assertTrue(left.compareTo(right) < 0);

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
    }

    @Test
    public void insertion() {
        DLN left = new DLN("1.1"); 
        DLN right = (DLN) left.insertNode(null);
        assertEquals("1.2", right.toString());
        
        DLN dln = (DLN) left.insertNode(right);
        assertEquals("1.1/1", dln.toString());
        
        left = dln;
        dln = (DLN) left.insertNode(right);
        assertEquals("1.1/2", dln.toString());
        
        right = dln;
        dln = (DLN) left.insertNode(right);
        assertEquals("1.1/1/1", dln.toString());
        
        right = new DLN("1.1/1");
        left = new DLN("1.1");
        dln = (DLN) left.insertNode(right);
        assertEquals("1.1/0/35", dln.toString());
        
        right = dln;
        dln = (DLN) left.insertNode(right);
        assertEquals("1.1/0/34", dln.toString());
    }
}