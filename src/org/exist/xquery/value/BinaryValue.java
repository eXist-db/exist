/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import java.text.Collator;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

public abstract class BinaryValue extends AtomicValue {

    protected byte[] data;
    
    public BinaryValue() {
    }
    
    public BinaryValue(byte[] data) {
        super();
        this.data = data;
    }

    public byte[] getBinaryData() {
        return data;
    }
    
    public boolean compareTo(Collator collator, int operator, AtomicValue other)
    throws XPathException {
        if (other.getType() == Type.HEX_BINARY || other.getType() == Type.BASE64_BINARY) {
            int value = compareTo((BinaryValue)other);
            switch(operator) {
                case Constants.EQ:
                    return value == 0;
                case Constants.NEQ:
                    return value != 0;
                case Constants.GT:
                    return value > 0;
                case Constants.GTEQ:
                    return value >= 0;
                case Constants.LT:
                    return value < 0;
                case Constants.LTEQ:
                    return value <= 0;
                default:
                    throw new XPathException("Type error: cannot apply operator to numeric value");
            }
        } else
            throw new XPathException("Cannot compare value of type xs:hexBinary with " +
                    Type.getTypeName(other.getType()));
    }

    public int compareTo(Collator collator, AtomicValue other)
    throws XPathException {
        if (other.getType() == Type.HEX_BINARY || other.getType() == Type.BASE64_BINARY) {
            return compareTo((BinaryValue)other);
        } else
            throw new XPathException("Cannot compare value of type xs:hexBinary with " +
                    Type.getTypeName(other.getType()));
    }

    public AtomicValue max(Collator collator, AtomicValue other)
    throws XPathException {
        throw new XPathException("Cannot compare values of type " +
                Type.getTypeName(getType()));
    }

    public AtomicValue min(Collator collator, AtomicValue other)
    throws XPathException {
        throw new XPathException("Cannot compare values of type " +
                Type.getTypeName(getType()));
    }

    public int conversionPreference(Class javaClass) {
        if (javaClass.isArray() && javaClass.isInstance(Byte.class))
            return 0;
        return Integer.MAX_VALUE;
    }

    protected int compareTo(BinaryValue otherValue) {
        byte[] other = otherValue.data;
        int a1len = data.length;
        int a2len = other.length;

        int limit = a1len <= a2len ? a1len : a2len;
        for (int i = 0; i < limit; i++) {
            byte b1 = data[i];
            byte b2 = other[i];
            if (b1 != b2)
                return (b1 & 0xFF) - (b2 & 0xFF);
        }
        return (a1len - a2len);
    }
}