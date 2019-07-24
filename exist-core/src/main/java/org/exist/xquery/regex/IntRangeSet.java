package org.exist.xquery.regex;

import java.io.Serializable;
import java.util.Arrays;

import org.exist.util.FastStringBuffer;

/**
 * Set of int values. This implementation of IntSet uses a sorted array
 * of integer ranges.
 * 
 * Copied from Saxon-HE 9.2 package net.sf.saxon.regex.
 *
 * @author Michael Kay
 */
public class IntRangeSet implements Serializable {

    // The array of start points, which will always be sorted
    private int[] startPoints;

    // The array of end points, which will always be sorted
    private int[] endPoints;

    // The number of elements of the above two arrays that are actually in use
    private int used = 0;

    // Hashcode, evaluated lazily
    private int hashCode = -1;

    // The number of items in the set
    private int size = 0;

    /**
     *  Create an empty set
     */
    public IntRangeSet() {
        startPoints = new int[4];
        endPoints = new int[4];
        used = 0;
        size = 0;
        hashCode = -1;
    }

    /**
     * Create one IntRangeSet as a copy of another
     * @param input the IntRangeSet to be copied
     */

    public IntRangeSet(IntRangeSet input) {
        startPoints = new int[input.used];
        endPoints = new int[input.used];
        used = input.used;
        System.arraycopy(input.startPoints, 0, startPoints, 0, used);
        System.arraycopy(input.endPoints, 0, endPoints, 0, used);
        hashCode = input.hashCode;
    }

    /**
     * Create an IntRangeSet given the start points and end points of the integer ranges.
     * The two arrays must be the same length; each must be in ascending order; and the n'th end point
     * must be greater than the n'th start point, and less than the n+1'th start point, for all n.
     * @param startPoints the start points of the integer ranges
     * @param endPoints the end points of the integer ranges
     * @throws IllegalArgumentException if the two arrays are different lengths. Other error conditions
     * in the input are not currently detected.
     */

    public IntRangeSet(int[] startPoints, int[] endPoints) {
        if (startPoints.length != endPoints.length) {
            throw new IllegalArgumentException("Array lengths differ");
        }
        this.startPoints = startPoints;
        this.endPoints = endPoints;
        used = startPoints.length;
        for (int i=0; i<used; i++) {
            size += (endPoints[i] - startPoints[i] + 1);
        }
    }

    public void clear() {
        startPoints = new int[4];
        endPoints = new int[4];
        used = 0;
        hashCode = -1;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(int value) {
        if (value > endPoints[used-1]) {
            return false;
        }
        if (value < startPoints[0]) {
            return false;
        }
        int i = 0;
        int j = used;
        do {
            final int mid = i + (j-i)/2;
            if (endPoints[mid] < value) {
                i = Math.max(mid, i+1);
            } else if (startPoints[mid] > value) {
                j = Math.min(mid, j-1);
            } else {
                return true;
            }
        } while (i != j);
        return false;
    }

    public boolean remove(int value) {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * Add an integer to the set
     * @param value the integer to be added
     * @return true if the integer was added, false if it was already present
     */

    public boolean add(int value) {
        hashCode = -1;
        if (used == 0) {
            ensureCapacity(1);
            startPoints[used-1] = value;
            endPoints[used-1] = value;
            size++;
            return true;
        }
        if (value > endPoints[used-1]) {
            if (value == endPoints[used-1] + 1) {
                endPoints[used-1]++;
            } else {
                ensureCapacity(used+1);
                startPoints[used-1] = value;
                endPoints[used-1] = value;
            }
            size++;
            return true;
        }
        if (value < startPoints[0]) {
            if (value == startPoints[0] - 1) {
                startPoints[0]--;
            } else {
                ensureCapacity(used+1);
                System.arraycopy(startPoints, 0, startPoints, 1, used-1);
                System.arraycopy(endPoints, 0, endPoints, 1, used-1);
                startPoints[0] = value;
                endPoints[0] = value;
            }
            size++;
            return true;
        }
        int i = 0;
        int j = used;
        do {
            final int mid = i + (j-i)/2;
            if (endPoints[mid] < value) {
                i = Math.max(mid, i+1);
            } else if (startPoints[mid] > value) {
                j = Math.min(mid, j-1);
            } else {
                return false;   // value is already present
            }
        } while (i != j);
        if (i > 0 && endPoints[i-1]+1 == value) {
            i--;
        } else if (i < used-1 && startPoints[i+1]-1 == value) {
            i++;
        }
        if (endPoints[i]+1 == value) {
            if (value == startPoints[i+1]-1) {
                // merge the two ranges
                endPoints[i] = endPoints[i+1];
                System.arraycopy(startPoints, i+2, startPoints, i+1, used-i-2);
                System.arraycopy(endPoints, i+2, endPoints, i+1, used-i-2);
                used--;
            } else {
                endPoints[i]++;
            }
            size++;
            return true;
        } else if (startPoints[i]-1 == value) {
            if (value == endPoints[i-1]+1) {
                // merge the two ranges
                endPoints[i-1] = endPoints[i];
                System.arraycopy(startPoints, i+1, startPoints, i, used-i-1);
                System.arraycopy(endPoints, i+1, endPoints, i, used-i-1);
                used--;
            } else {
                startPoints[i]--;
            }
            size++;
            return true;
        } else {
            if (value > endPoints[i]) {
                i++;
            }
            ensureCapacity(used+1);
            try {
                System.arraycopy(startPoints, i, startPoints, i+1, used-i-1);
                System.arraycopy(endPoints, i, endPoints, i+1, used-i-1);
            } catch (final Exception err) {
                err.printStackTrace();
            }
            startPoints[i] = value;
            endPoints[i] = value;
            size++;
            return true;
        }
    }

    private void ensureCapacity(int n) {
        if (startPoints.length < n) {
            int[] s = new int[startPoints.length * 2];
            int[] e = new int[startPoints.length * 2];
            System.arraycopy(startPoints, 0, s, 0, used);
            System.arraycopy(endPoints, 0, e, 0, used);
            startPoints = s;
            endPoints = e;
        }
        used = n;
    }


    /**
     * Get an iterator over the values
     *
     * @return value iterator
     */

    public IntRangeSetIterator iterator() {
        return new IntRangeSetIterator();
    }

    public String toString() {
        final FastStringBuffer sb = new FastStringBuffer(used*8);
        for (int i=0; i<used; i++) {
            sb.append(startPoints[i] + "-" + endPoints[i] + ",");
        }
        return sb.toString();
    }

    /**
     * Test whether this set has exactly the same members as another set. Note that
     * IntRangeSet values are <b>NOT</b> comparable with other implementations of IntSet
     *
     * @param other object to compare with
     * @return true if other is an IntRangeSet and has the same members
     */

    public boolean equals(Object other) {
    	if (other == null) {return false;}
        if (other instanceof IntRangeSet) {
            return used == ((IntRangeSet)other).used &&
                   Arrays.equals(startPoints, ((IntRangeSet)other).startPoints) &&
                   Arrays.equals(endPoints, ((IntRangeSet)other).endPoints) ;
        }
        return containsAll((IntRangeSet)other);
    }

    /**
     * Construct a hash key that supports the equals() test
     *
     * @return hash key
     */

    public int hashCode() {
        // Note, hashcodes are NOT the same as those used by IntHashSet and IntArraySet
        if (hashCode == -1) {
            int h = 0x836a89f1;
            for (int i=0; i<used; i++) {
                h ^= startPoints[i] + (endPoints[i]<<3);
            }
            hashCode = h;
        }
        return hashCode;
    }

    /**
     * Test if this set is a superset of another set
     *
     * @param other the subset
     * @return true if this is a superset of other
     */

    public boolean containsAll(IntRangeSet other) {
    	final IntRangeSetIterator it = other.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Add a range of integers to the set.
     * This is optimized for the case where these are all greater than any existing integer
     * in the set.
     * @param low the low end of the new range
     * @param high the high end of the new range
     */

    public void addRange(int low, int high) {
        hashCode = -1;
        if (used == 0) {
            ensureCapacity(1);
            startPoints[used-1] = low;
            endPoints[used-1] = high;
            size += (high - low + 1);
        } else if (low > endPoints[used-1]) {
            if (low == endPoints[used-1] + 1) {
                endPoints[used-1] = high;
            } else {
                ensureCapacity(used+1);
                startPoints[used-1] = low;
                endPoints[used-1] = high;
            }
            size += (high - low + 1);
        } else {
            for (int i=low; i<=high; i++) {
                add(i);
            }
        }
    }

    /**
     * Get the start points of the ranges
     *
     * @return array of start points
     */

    public int[] getStartPoints() {
        return startPoints;
    }

    /**
     * Get the end points of the ranges
     *
     * @return array of end points
     */

    public int[] getEndPoints() {
        return endPoints;
    }

    /**
     * Get the number of ranges actually in use
     *
     * @return number of ranges in use
     */

    public int getNumberOfRanges() {
        return used;
    }

    /**
     * Iterator class
     */

    private class IntRangeSetIterator implements Serializable {

        private int i = 0;
        private int current = 0;

        public IntRangeSetIterator() {
            i = -1;
            current = Integer.MIN_VALUE;
        }

        public boolean hasNext() {
            if (i<0) {
                return size > 0;
            } else {
                return current < endPoints[used-1];
            }
        }

        public int next() {
            if (i < 0) {
                i = 0;
                current = startPoints[0];
                return current;
            }
            if (current == endPoints[i]) {
                current = startPoints[++i];
                return current;
            } else {
                return ++current;
            }
        }
    }

}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
