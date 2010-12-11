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

import org.exist.util.io.HexOutputStream;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class HexBinaryValueType extends BinaryValueType<HexOutputStream> {

    public HexBinaryValueType() {
        super(Type.HEX_BINARY, HexOutputStream.class);
    }



    /*
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
    */
}