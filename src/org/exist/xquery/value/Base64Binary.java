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

import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.util.Arrays;

import org.apache.xmlrpc.Base64;
import org.exist.xquery.XPathException;

public class Base64Binary extends BinaryValue {

    public Base64Binary(byte[] data) {
        super(data);
    }

    public Base64Binary(String str) throws XPathException {
    	try {
			this.data = Base64.decode(str.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new XPathException("cannot build UTF-8 " + Type.getTypeName(getType()) + " from '" + str + "'");
		}
    }
    
    public int getType() {
        return Type.BASE64_BINARY;
    }
    
    public String getStringValue() throws XPathException {
        try {
			return new String(Base64.encode(data), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new XPathException("cannot build UTF-8 " + Type.getTypeName(getType()) + " from " + Type.getTypeName(getType()));
		}
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
    	switch (requiredType) {
    	case Type.BASE64_BINARY: 
    		return this;
        case Type.HEX_BINARY:
            return new HexBinary(data);
    	case Type.UNTYPED_ATOMIC:
    		try {
    			//Added trim() since it looks like a new line character is added
    			return new UntypedAtomicValue(new String(Base64.encode(data), "UTF-8").trim());
			} catch (UnsupportedEncodingException e) {
				throw new XPathException("cannot convert UTF-8 " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
			}
    	case Type.STRING: 
    		try {
				//return new StringValue(new String(data, "UTF-8"));
    			//Added trim() since it looks like a new line character is added
    			return new StringValue(new String(Base64.encode(data), "UTF-8").trim());
			} catch (UnsupportedEncodingException e) {
				throw new XPathException("cannot convert UTF-8 " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
			}
    	default:
    		throw new XPathException("cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
    	}
    }
    
    public Object toJavaObject(Class target) throws XPathException {
        if(target.isAssignableFrom(Base64Binary.class))
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