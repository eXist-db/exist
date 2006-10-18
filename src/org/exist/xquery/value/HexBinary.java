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

import org.exist.xquery.XPathException;

public class HexBinary extends BinaryValue {
    
    public HexBinary(byte[] data) {
        super(data);
    }
    
    public HexBinary(String in) throws XPathException {
        in = StringValue.trimWhitespace(in);
        if ((in.length() & 1) != 0) {
            throw new XPathException("FORG0001: A hexBinary value must contain an even " +
                    "number of characters");
        }
        data = new byte[in.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)((fromHex(in.charAt(2*i))<<4) +
                    (fromHex(in.charAt(2*i+1))));
        }
    }
    
    public int getType() {
        return Type.HEX_BINARY;
    }
    
    /**
     * Decode a single hex digit
     * @param c the hex digit
     * @return the numeric value of the hex digit
     * @throws XPathException if it isn't a hex digit
     */
    private int fromHex(char c) throws XPathException {
        int d = "0123456789ABCDEFabcdef".indexOf(c);
        if (d > 15) {
            d = d - 6;
        }
        if (d < 0) {
            throw new XPathException("FORG0001: Invalid hexadecimal digit: " + c);
        }
        return d;
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.HEX_BINARY:
                return this;
            case Type.BASE64_BINARY: 
                return new Base64Binary(data);
            case Type.UNTYPED_ATOMIC:
                //Added trim() since it looks like a new line character is added
                return new UntypedAtomicValue(getStringValue().trim());
            case Type.STRING: 
                return new StringValue(getStringValue());
            default:
                throw new XPathException("cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
            }
    }

    public String getStringValue() throws XPathException {
        String digits = "0123456789ABCDEF";
        StringBuffer sb = new StringBuffer(data.length * 2);
        for (int i=0; i<data.length; i++) {
            sb.append(digits.charAt((data[i]>>4)&0xf));
            sb.append(digits.charAt(data[i]&0xf));
        }
        return sb.toString();
    }

    public Object toJavaObject(Class target) throws XPathException {
        if(target.isAssignableFrom(HexBinary.class))
            return this;
        if (target.isArray() && target == Byte.class)
            return data;
        throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) +
            " to Java object of type " + target.getName());
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException("FORG0006: value of type " + Type.getTypeName(getType()) +
            " has no boolean value.");
    }
}
