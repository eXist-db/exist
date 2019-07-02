/*
 *  eXist Open Source Native XML Database
 *  Copyright 2004 The eXist Team
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.util;

/**
 * Utility methods to have indexed access for insertion and deletion
 * of array items.
 * 
 * Based on original code from dbXML.
 */
public class ArrayUtils {
    
    /**
     * Delete an integer.
     * 
     * @param vals array of integers
     * @param idx index of integer to delete
     * @return the array without the deleted integer
     */
    public static int[] deleteArrayInt(int[] vals, int idx) {
        final int[] newVals = new int[vals.length - 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        if (idx < newVals.length) {
            System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
        }
        
        return newVals;
    }

    /**
     * Delete a long.
     * 
     * @param vals array of longs
     * @param idx index of long to delete
     * @return the array without the deleted long
     */
    public static long[] deleteArrayLong(long[] vals, int idx) {
        final long[] newVals = new long[vals.length - 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        if (idx < newVals.length) {
            System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
        }
        
        return newVals;
    }

    /**
     * Delete a short.
     * 
     * @param vals array of shorts
     * @param idx index of short to delete
     * @return the array without the deleted short
     */
    public static short[] deleteArrayShort(short[] vals, int idx) {
        final short[] newVals = new short[vals.length - 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        if (idx < newVals.length) {
            System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
        }
        
        return newVals;
    }

    /**
     * Insert a integer.
     * 
     * @param vals array of integers
     * @param val integer to insert
     * @param idx index of insertion
     * @return the array with added integer
     */
    public static int[] insertArrayInt(int[] vals, int val, int idx) {
        final int[] newVals = new int[vals.length + 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        newVals[idx] = val;
        if (idx < vals.length) {
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        }
        
        return newVals;
    }

    /**
     * Insert a long.
     * 
     * @param vals array of longs
     * @param val long to insert
     * @param idx index of insertion
     * @return the array with added long
     */
    public static long[] insertArrayLong(long[] vals, long val, int idx) {
        final long[] newVals = new long[vals.length + 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        newVals[idx] = val;
        if (idx < vals.length) {
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        }
        
        return newVals;
    }

    /**
     * Insert a short.
     * 
     * @param vals array of shorts
     * @param val short to insert
     * @param idx index of insertion
     * @return the array with added short
     */
    public static short[] insertArrayShort(short[] vals, short val, int idx) {
        final short[] newVals = new short[vals.length + 1];
        if (idx > 0) {
            System.arraycopy(vals, 0, newVals, 0, idx);
        }
        newVals[idx] = val;
        if (idx < vals.length) {
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        }
        
        return newVals;
    }
    
    /**
     * Searches the specified array of ints for the specified value using the
     * binary search algorithm.  The array <strong>must</strong> be sorted (as
     * by the <code>sort</code> method, above) prior to making this call.  If it
     * is not sorted, the results are undefined.  If the array contains
     * multiple elements with the specified value, there is no guarantee which
     * one will be found.
     *
     * @param a the array to be searched.
     * @param key the value to be searched for.
     * @param size the size of the array {@code a}.
     * @return index of the search key, if it is contained in the list;
     *         otherwise, <code>(-(<i>insertion point</i>) - 1)</code>.  The
     *         <i>insertion point</i> is defined as the point at which the
     *         key would be inserted into the list: the index of the first
     *         element greater than the key, or <code>list.size()</code>, if all
     *         elements in the list are less than the specified key.  Note
     *         that this guarantees that the return value will be &gt;= 0 if
     *         and only if the key is found.
     */
    public static int binarySearch(int[] a, int key, int size) {
        int low = 0;
        int high = size - 1;
        
        while (low <= high) {
            final int mid = (low + high) >> 1;
            final int midVal = a[mid];
            
            if (midVal < key)
                {low = mid + 1;}
            else if (midVal > key)
                {high = mid - 1;}
            else
                {return mid;} // key found
        }
        return -(low + 1);  // key not found.
    }
}
