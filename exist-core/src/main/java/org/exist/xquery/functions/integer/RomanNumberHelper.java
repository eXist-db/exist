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
package org.exist.xquery.functions.integer;

import java.util.TreeMap;

/**
 * Roman numerals, in support of formatting by {@link RomanIntegerPicture}
 *
 * The source code for this class is taken from the stackoverflow answer
 * https://stackoverflow.com/questions/12967896/converting-integers-to-roman-numerals-java
 * written by https://stackoverflow.com/users/1420681/ben-hur-langoni-junior
 * and is therefore used and made available in accordance with
 * https://creativecommons.org/licenses/by-sa/3.0
 * 
 */
class RomanNumberHelper {

    private static final TreeMap<Integer, String> map = new TreeMap<>();

    private RomanNumberHelper() {}

    static {

        RomanNumberHelper.map.put(1000, "M");
        RomanNumberHelper.map.put(900, "CM");
        RomanNumberHelper.map.put(500, "D");
        RomanNumberHelper.map.put(400, "CD");
        RomanNumberHelper.map.put(100, "C");
        RomanNumberHelper.map.put(90, "XC");
        RomanNumberHelper.map.put(50, "L");
        RomanNumberHelper.map.put(40, "XL");
        RomanNumberHelper.map.put(10, "X");
        RomanNumberHelper.map.put(9, "IX");
        RomanNumberHelper.map.put(5, "V");
        RomanNumberHelper.map.put(4, "IV");
        RomanNumberHelper.map.put(1, "I");

    }

    public static String toRoman(final int number) {
        final int l = RomanNumberHelper.map.floorKey(number);
        if (number == l) {
            return RomanNumberHelper.map.get(number);
        }
        return RomanNumberHelper.map.get(l) + RomanNumberHelper.toRoman(number - l);
    }

}
