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
 * Utility functions to have an indexed access for inserting and deletion
 * of array items.
 */
public class ArrayUtils {
    
    /**
     * Delete an integer inside an array of integers.
     * 
     * @param vals array of integers
     * @param idx index of integer to delete
     * @return the array without the deleted integer
     */
    public static int[] deleteArrayInt(int[] vals, int idx) {
        int[] newVals = new int[vals.length - 1];
        if (idx > 0)
            System.arraycopy(vals, 0, newVals, 0, idx);
        if (idx < newVals.length)
            System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
        return newVals;
    }

    /**
     * Delete an long inside an array of longs.
     * 
     * @param vals array of longs
     * @param idx index of long to delete
     * @return the array without the deleted long
     */
    public static long[] deleteArrayLong(long[] vals, int idx) {
        long[] newVals = new long[vals.length - 1];
        if (idx > 0)
            System.arraycopy(vals, 0, newVals, 0, idx);
        if (idx < newVals.length)
            System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
        return newVals;
    }

    /**
     * 
     * @param vals
     * @param idx
     * @return
     */
    public static short[] deleteArrayShort(short[] vals, int idx) {
        short[] newVals = new short[vals.length - 1];
        if (idx > 0)
            System.arraycopy(vals, 0, newVals, 0, idx);
        if (idx < newVals.length)
            System.arraycopy(vals, idx + 1, newVals, idx, newVals.length - idx);
        return newVals;
    }

    /**
     * 
     * @param vals
     * @param val
     * @param idx
     * @return
     */
    public static int[] insertArrayInt(int[] vals, int val, int idx) {
        int[] newVals = new int[vals.length + 1];
        if (idx > 0)
            System.arraycopy(vals, 0, newVals, 0, idx);
        newVals[idx] = val;
        if (idx < vals.length)
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        return newVals;
    }

    /**
     * 
     * @param vals
     * @param val
     * @param idx
     * @return
     */
    public static long[] insertArrayLong(long[] vals, long val, int idx) {
        long[] newVals = new long[vals.length + 1];
        if (idx > 0)
            System.arraycopy(vals, 0, newVals, 0, idx);
        newVals[idx] = val;
        if (idx < vals.length)
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        return newVals;
    }

    /**
     * 
     * @param vals
     * @param val
     * @param idx
     * @return
     */
    public static short[] insertArrayShort(short[] vals, short val, int idx) {
        short[] newVals = new short[vals.length + 1];
        if (idx > 0)
            System.arraycopy(vals, 0, newVals, 0, idx);
        newVals[idx] = val;
        if (idx < vals.length)
            System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);
        return newVals;
    }
}
