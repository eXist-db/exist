/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.util.ByteConversion;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 */
public class ValueIndexFactory {

	public final static AtomicValue deserialize(byte[] data, int start, int len) throws EXistException {
		int type = data[start + 2];
		if (Type.subTypeOf(type, Type.STRING)) {
			String s = new String(data, start + 3, len - 3);
			return new StringValue(s);
		} else if (Type.subTypeOf(type, Type.INTEGER) ) {
			return new IntegerValue(ByteConversion.byteToLong(data, start + 3) ^ 0x8000000000000000L);
		} else if (type == Type.BOOLEAN) {
			return new BooleanValue(data[start + 3] == 1);
		} else if (type == Type.FLOAT) {
			int bits = ByteConversion.byteToInt(data, start + 3) ^ 0x80000000;
			float f = Float.intBitsToFloat(bits);
			return new FloatValue(f);
		} else if (type == Type.DOUBLE) {
			long bits = ByteConversion.byteToLong(data, start + 3) ^ 0x8000000000000000L;
			double d = Double.longBitsToDouble(bits);
			return new DoubleValue(d);
		} else
			throw new EXistException("Unknown data type for range index key: " + 
					Type.getTypeName(type));
	}
}
