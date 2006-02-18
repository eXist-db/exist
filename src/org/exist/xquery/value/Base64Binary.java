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

import org.apache.xmlrpc.Base64;
import org.exist.xquery.XPathException;

public class Base64Binary extends AtomicValue {

    private byte[] data;
    
    public Base64Binary(byte[] data) {
        super();
        this.data = data;
    }

    public int getType() {
        return Type.BASE64_BINARY;
    }
    
    public byte[] getBinaryData() {
        return data;
    }
    
    public String getStringValue() throws XPathException {
        return new String(Base64.encode(data));
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
    	switch (requiredType) {
    	case Type.BASE64_BINARY: 
    		return this;
    	case Type.STRING: 
    		try {
					return new StringValue(new String(data, "UTF-8"));
				} catch (UnsupportedEncodingException e) {
					return new StringValue(new String(data));
				}
    	default:
    		throw new XPathException("cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
    	}
    }

    public boolean compareTo(Collator collator, int operator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Cannot compare values of type xs:base64Binary");
    }

    public int compareTo(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Cannot compare values of type xs:base64Binary");
    }

    public AtomicValue max(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Cannot compare values of type xs:base64Binary");
    }

    public AtomicValue min(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Cannot compare values of type xs:base64Binary");
    }
    
    public int conversionPreference(Class javaClass) {
        if (javaClass.isArray() && javaClass.isInstance(Byte.class))
            return 0;
        return Integer.MAX_VALUE;
    }
    
    public Object toJavaObject(Class target) throws XPathException {
        if(target.isAssignableFrom(Base64Binary.class))
            return this;
        if (target.isArray() && target == Byte.class)
            return data;
        throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) +
            " to Java object of type " + target.getName());
    }
}