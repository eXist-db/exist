/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id:
 */
package org.exist.util;

/**
 * Arrays.java enclosing_type
 * 
 * @author wolf
 *
 */
public class Arrays {

    /**
     * Compare two byte sequences
     * 
     * @param a  first byte array.
     * @param ax start offset into first byte array.
     * @param b  second byte array.
     * @param bx start offset into second byte array.
     * @param len number of bytes to compare.
     * @return boolean true if both sequences are equal.
     */
    public final static boolean equals(byte[] a, int ax, byte[] b, int bx, int len) {
        for(int i = 0; i < len; i++) {
            if(a[ax + i] != b[bx + i])
                return false;
        }
        return true;
    }
}
