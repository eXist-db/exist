/*
 *  eXist Open Source Native XML Database
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.value;

import org.exist.util.Base64Decoder;
import org.exist.util.Base64Encoder;
import org.exist.xquery.XPathException;

public class Base64Binary extends BinaryValue {

    public Base64Binary(byte[] data) {
        super(data);
    }

    public Base64Binary(String str) throws XPathException {
    	Base64Decoder dec = new Base64Decoder();
		try
		{
			dec.translate(str);
		}
		catch(IllegalArgumentException e)
		{
			throw new XPathException("cannot build " + Type.getTypeName(getType()) + " from '" + str + "'. " + e.getMessage());
		}
    	this.data = dec.getByteArray();
    }
    
    public int getType() {
        return Type.BASE64_BINARY;
    }
    
    public String getStringValue() throws XPathException
    {
        	Base64Encoder enc = new Base64Encoder();
        	enc.translate(data);
        	return new String(enc.getCharArray());
    }

    public AtomicValue convertTo(int requiredType) throws XPathException
    {
    	
    	Base64Encoder enc = new Base64Encoder();
    	
    	switch (requiredType) {
    	case Type.BASE64_BINARY: 
    		return this;
        case Type.HEX_BINARY:
            return new HexBinary(data);
    	case Type.UNTYPED_ATOMIC:
    		//Added trim() since it looks like a new line character is added
    		enc.translate(data);
    		return new UntypedAtomicValue(new String(enc.getCharArray()).trim());
    	case Type.STRING: 
			//return new StringValue(new String(data, "UTF-8"));
    		//Added trim() since it looks like a new line character is added
    		enc.translate(data);
    		return new StringValue(new String(enc.getCharArray()).trim());
    	default:
    		throw new XPathException("cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
    	}
    }
    
    public Object toJavaObject(Class target) throws XPathException {
        if(target.isAssignableFrom(Base64Binary.class))
            return this;
        if (target == byte[].class)
            return data;
        throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) +
            " to Java object of type " + target.getName());
    }
    
    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException("FORG0006: value of type " + Type.getTypeName(getType()) +
            " has no boolean value.");
    }
}