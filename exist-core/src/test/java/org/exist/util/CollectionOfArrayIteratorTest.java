/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.util;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class CollectionOfArrayIteratorTest {

    @Test
    public void nullCollection() {
        final CollectionOfArrayIterator<String> it = new CollectionOfArrayIterator<>(null);
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void emptyCollection() {
        final CollectionOfArrayIterator<String> it = new CollectionOfArrayIterator<>(Collections.emptyList());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void oneEmptyArray() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[0]
        ));
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void oneArray() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5}
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void twoArrays() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5},
                new Integer[] {66,77,88,99,111}
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertTrue(it.hasNext());

        assertEquals(66, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(77, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(88, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(99, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(111, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void twoArraysOverlap() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5},
                new Integer[] {5,6,7,8,9}
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());

        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(6, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(7, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(8, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(9, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void twoArraysBothEmpty() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[0],
                new Integer[0]
        ));
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void twoArraysFirstEmpty() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[0],
                new Integer[] {6,7,8,9,10}
        ));
        assertTrue(it.hasNext());

        assertEquals(6, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(7, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(8, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(9, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(10, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void twoArraysSecondEmpty() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5},
                new Integer[0]
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void threeArrays() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5},
                new Integer[] {66,77,88,99,111},
                new Integer[] {666,777,888,999,1111}
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertTrue(it.hasNext());

        assertEquals(66, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(77, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(88, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(99, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(111, (int)it.next());
        assertTrue(it.hasNext());

        assertEquals(666, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(777, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(888, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(999, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(1111, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void threeArraysOverlap() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5},
                new Integer[] {5,6,7,8,9},
                new Integer[] {9,10,11,12,13}
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());

        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(6, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(7, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(8, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(9, (int)it.next());
        assertTrue(it.hasNext());

        assertTrue(it.hasNext());
        assertEquals(9, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(10, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(11, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(12, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(13, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void threeArraysAllEmpty() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[0],
                new Integer[0],
                new Integer[0]
        ));
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void threeArraysFirstEmpty() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[0],
                new Integer[] {66,77,88,99,111},
                new Integer[] {666,777,888,999,1111}
        ));
        assertTrue(it.hasNext());

        assertEquals(66, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(77, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(88, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(99, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(111, (int)it.next());
        assertTrue(it.hasNext());

        assertEquals(666, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(777, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(888, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(999, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(1111, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void threeArraysSecondEmpty() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5},
                new Integer[0],
                new Integer[] {666,777,888,999,1111}
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertTrue(it.hasNext());

        assertEquals(666, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(777, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(888, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(999, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(1111, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    @Test
    public void threeArraysLastEmpty() {
        final CollectionOfArrayIterator<Integer> it = new CollectionOfArrayIterator<>(listOf(
                new Integer[] {1,2,3,4,5},
                new Integer[] {66,77,88,99,111},
                new Integer[0]
        ));
        assertTrue(it.hasNext());

        assertEquals(1, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(2, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(3, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(4, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(5, (int)it.next());
        assertTrue(it.hasNext());

        assertEquals(66, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(77, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(88, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(99, (int)it.next());
        assertTrue(it.hasNext());
        assertEquals(111, (int)it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail("Expected NoSuchElementException");
        } catch (final NoSuchElementException e) {
            // no op
        }
    }

    private static <T> List<T[]> listOf(final T[]... items) {
        return Arrays.asList(items);
    }
}
