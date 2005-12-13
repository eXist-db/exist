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
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * @author wolf
 *
 */
public class ValueIndexFactory {

	public final static AtomicValue deserialize(byte[] data, int start, int len) throws EXistException {
		
		int type = data[start + 2];
		
		/* xs:string */
		if (Type.subTypeOf(type, Type.STRING))
		{
			String s = new String(data, start + 3, len - 3);
			return new StringValue(s);
		}
		/* xs:dateTime */
		else if(Type.subTypeOf(type, Type.DATE_TIME))
		{
			//get the dateTime back as a long
			long value = ByteConversion.byteToLong(data, start + 3); 
			
			//Create a GregorianCalendar from the long (normalized datetime as milliseconds since the Epoch)
			GregorianCalendar utccal = new GregorianCalendar();
			utccal.setTimeInMillis(value);
			
			//Create a XMLGregorianCalendar from the GregorianCalendar
			try
			{
				XMLGregorianCalendar xmlutccal = DatatypeFactory.newInstance().newXMLGregorianCalendar(utccal);
				return new DateTimeValue(xmlutccal);
			}
			catch(DatatypeConfigurationException dtce)
			{
				throw new EXistException("Could not deserialize xs:dateTime data type for range index key: " + Type.getTypeName(type) + " - " + dtce.getMessage());
			}
		}
		/* xs:integer */
		else if(Type.subTypeOf(type, Type.INTEGER))
		{
			return new IntegerValue(ByteConversion.byteToLong(data, start + 3) ^ 0x8000000000000000L);
		}
		/* xs:boolean */
		else if(type == Type.BOOLEAN)
		{
			return new BooleanValue(data[start + 3] == 1);
		}
		/* xs:float */
		else if (type == Type.FLOAT)
		{
			int bits = ByteConversion.byteToInt(data, start + 3) ^ 0x80000000;
			float f = Float.intBitsToFloat(bits);
			return new FloatValue(f);
		}
		/* xs:double */
		else if (type == Type.DOUBLE)
		{
			long bits = ByteConversion.byteToLong(data, start + 3) ^ 0x8000000000000000L;
			double d = Double.longBitsToDouble(bits);
			return new DoubleValue(d);
		}
		/* unknown! */
		else
		{
			throw new EXistException("Unknown data type for range index key: " + Type.getTypeName(type));
		}
	}
}
