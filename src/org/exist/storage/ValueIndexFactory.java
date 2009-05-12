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

import java.io.UnsupportedEncodingException;
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 */
//TODO : rename as NativeIndexValueFactory ? -pb
public class ValueIndexFactory {
	
	private static Logger LOG = Logger.getLogger(ValueIndexFactory.class.getName());
	
	//TODO : check
	public static int OFFSET_COLLECTION_ID = 0;
	//TODO : check
	public static int OFFSET_TYPE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2
	public static int LENGTH_VALUE_TYPE = 1; //sizeof byte
	public static int OFFSET_VALUE = OFFSET_TYPE + ValueIndexFactory.LENGTH_VALUE_TYPE; //3	

	public final static Indexable deserialize(byte[] data, int start, int len) throws EXistException {
		
		int type = data[start];
		
		//TODO : improve deserialization (use static methods in the org.exist.xquery.Value package
		
		/* xs:string */
		if (Type.subTypeOf(type, Type.STRING))
		{
			String s;
			try {
				s = new String(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE),
				len - (ValueIndexFactory.LENGTH_VALUE_TYPE), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				LOG.error(e);
				throw new EXistException(e);
			}
			return new StringValue(s);
		}
		
		/* xs:dateTime */
		else if(Type.subTypeOf(type, Type.DATE_TIME))		{
			//get the dateTime back as a long
			long value = ByteConversion.byteToLong(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE));
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
		
    /* xs:date */
    else if(Type.subTypeOf(type, Type.DATE))   {
      //get the date back as a long
      long value = ByteConversion.byteToLong(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE));
      //Create a GregorianCalendar from the long (normalized datetime as milliseconds since the Epoch)
      GregorianCalendar utccal = new GregorianCalendar();
      utccal.setTimeInMillis(value);
      //Create a XMLGregorianCalendar from the GregorianCalendar
      try
      {
        XMLGregorianCalendar xmlutccal = DatatypeFactory.newInstance().newXMLGregorianCalendar(utccal);
        return new DateValue(xmlutccal);
      }
      catch(DatatypeConfigurationException dtce)
      {
        throw new EXistException("Could not deserialize xs:date data type for range index key: " + Type.getTypeName(type) + " - " + dtce.getMessage());
      }
      catch(XPathException xpe)
      {
        throw new EXistException("Could not deserialize xs:date data type for range index key: " + Type.getTypeName(type) + " - " + xpe.getMessage());
      }
    }
    
		/* xs:integer */
		else if(Type.subTypeOf(type, Type.INTEGER))
		{
			return new IntegerValue(ByteConversion.byteToLong(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE)) ^ 0x8000000000000000L);
		}

		
		/* xs:double */
		else if (type == Type.DOUBLE)
		{
			long bits = ByteConversion.byteToLong(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE)) ^ 0x8000000000000000L;
			double d = Double.longBitsToDouble(bits);
			return new DoubleValue(d);
		}
		
		/* xs:float */
		else if (type == Type.FLOAT)
		{
			int bits = ByteConversion.byteToInt(data, start + (ValueIndexFactory.LENGTH_VALUE_TYPE)) ^ 0x80000000;
			float f = Float.intBitsToFloat(bits);
			return new FloatValue(f);
		}		
		
		/* xs:boolean */
		else if(type == Type.BOOLEAN)
		{
			return new BooleanValue(data[start + (ValueIndexFactory.LENGTH_VALUE_TYPE)] == 1);
		}		
		
		/* unknown! */
		else
		{
			throw new EXistException("Unknown data type for deserialization: " + Type.getTypeName(type));
		}
	}
	
	/*
	public final static byte[] serialize(Indexable value, short collectionId) throws EXistException {
		//TODO : refactor (only strings are case sensitive)
		return  serialize(value, collectionId, true);
	}	
	*/

	/**
	 * @ deprecated
	 * @param value
	 * @param collectionId
	 * @param caseSensitive
	 * @throws EXistException
	 */
	/*
	public final static byte[] serialize(Indexable value, short collectionId, boolean caseSensitive) 
		throws EXistException {	

		// xs:string
		if (Type.subTypeOf(value.getType(), Type.STRING))
		{			
			final String val = caseSensitive ? 
					((StringValue)value).getStringValue() : 
					((StringValue)value).getStringValue().toLowerCase();
			final byte[] data = new byte[UTF8.encoded(val) + (Collection.LENGTH_COLLECTION_ID + ValueIndexFactory.LENGTH_VALUE_TYPE)];
			ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
			data[OFFSET_TYPE] = (byte) value.getType();	// TODO: cast to byte is not safe
			UTF8.encode(val, data, OFFSET_VALUE);
			return data;
		}
		
		// xs:dateTime 
		else if(Type.subTypeOf(value.getType(), Type.DATE_TIME))		{
	    	GregorianCalendar utccal = ((AbstractDateTimeValue)value).calendar.normalize().toGregorianCalendar();	//Get the dateTime (XMLGregorianCalendar) normalized to UTC (as a GregorianCalendar)
			long millis = utccal.getTimeInMillis();			//Get the normalized dateTime as a long (milliseconds since the Epoch)
			byte[] data = new byte[(Collection.LENGTH_COLLECTION_ID + ValueIndexFactory.LENGTH_VALUE_TYPE) + 8];						//alocate a byte array for holding collectionId,Type,long (11 = (byte)short + byte + (byte)long)
			ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);	//put the collectionId in the byte array
			//TODO : should we keep the actual type, i.e. value.getType() ?
			data[OFFSET_TYPE] = (byte) Type.DATE_TIME;					//put the Type in the byte array
			ByteConversion.longToByte(millis, data, OFFSET_VALUE);			//put the long in the byte array
			return(data);			
		}
		
		// xs:integer 
		else if(Type.subTypeOf(value.getType(), Type.INTEGER))
		{
	        long l = ((IntegerValue)value).getValue() - Long.MIN_VALUE;
	        byte[] data = new byte[(Collection.LENGTH_COLLECTION_ID + ValueIndexFactory.LENGTH_VALUE_TYPE) + 8];
			ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
			data[OFFSET_TYPE] = (byte) Type.INTEGER;
			ByteConversion.longToByte(l, data, OFFSET_VALUE);
			return data;
		}
		
		// xs:double 
		else if (value.getType() == Type.DOUBLE)
		{
	        final byte[] data = new byte[(Collection.LENGTH_COLLECTION_ID + ValueIndexFactory.LENGTH_VALUE_TYPE) + 8];
	        ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
	        data[OFFSET_TYPE] = (byte) Type.DOUBLE;
	        final long bits = Double.doubleToLongBits(((DoubleValue)value).getValue()) ^ 0x8000000000000000L;
	        ByteConversion.longToByte(bits, data, OFFSET_VALUE);
	        return data;
		}
		
		// xs:float 
		else if (value.getType() == Type.FLOAT)
		{
	        final byte[] data = new byte[(Collection.LENGTH_COLLECTION_ID + ValueIndexFactory.LENGTH_VALUE_TYPE) + 4];
	        ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
	        data[OFFSET_TYPE] = (byte) Type.FLOAT;
	        final int bits = (int)(Float.floatToIntBits(((FloatValue)value).getValue()) ^ 0x80000000);
	        ByteConversion.intToByte(bits, data, OFFSET_VALUE);
	        return data;
		}
	
		// xs:boolean 
		else if(value.getType() == Type.BOOLEAN)
		{
			byte[] data = new byte[(Collection.LENGTH_COLLECTION_ID + ValueIndexFactory.LENGTH_VALUE_TYPE) + 1];
	        ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
	        data[OFFSET_TYPE] = Type.BOOLEAN;
	        data[OFFSET_VALUE] = (byte)(((BooleanValue)value).getValue() ? 1 : 0);
	        return data;
		}
		
		
		// unknown! 
		else
		{
			throw new EXistException("Unknown data type for serialization: " + Type.getTypeName(value.getType()));
		}	
	}
	*/
	
	/**
	 * @param value
	 * @param offset
	 * @throws EXistException
	 */
	public final static byte[] serialize(Indexable value, int offset) throws EXistException {
		//TODO : refactor (only strings are case sensitive)
		return  serialize(value, offset, true);
	}

	/**
	 * @deprecated
	 * @param value
	 * @param offset
	 * @param caseSensitive
	 * @throws EXistException
	 */
	public final static byte[] serialize(Indexable value, int offset, boolean caseSensitive) 
	throws EXistException {

		/* xs:string */
		if (Type.subTypeOf(value.getType(), Type.STRING))
		{			
			final String val = caseSensitive ? 
				((StringValue)value).getStringValue() : 
				((StringValue)value).getStringValue().toLowerCase();
			final byte[] data = new byte[ offset + ValueIndexFactory.LENGTH_VALUE_TYPE + UTF8.encoded(val) ];
			data[offset] = (byte) value.getType();	// TODO: cast to byte is not safe
			UTF8.encode(val, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);  
			return data;
		}
		
		/* xs:dateTime */
		else if(Type.subTypeOf(value.getType(), Type.DATE_TIME))		{
	    	GregorianCalendar utccal = ((AbstractDateTimeValue)value).calendar.normalize().toGregorianCalendar();	//Get the dateTime (XMLGregorianCalendar) normalized to UTC (as a GregorianCalendar)
			long millis = utccal.getTimeInMillis();									//Get the normalized dateTime as a long (milliseconds since the Epoch)
			final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 8];		//allocate an appropriately sized byte array for holding Type,long
			data[offset] = (byte) Type.DATE_TIME;				//put the Type in the byte array
			ByteConversion.longToByte(millis, data, offset+1);	//put the long into the byte array
			return(data);										//return the byte array
		}
		
    /* xs:date */
    else if(Type.subTypeOf(value.getType(), Type.DATE))    {
        GregorianCalendar utccal = ((AbstractDateTimeValue)value).calendar.normalize().toGregorianCalendar(); //Get the dateTime (XMLGregorianCalendar) normalized to UTC (as a GregorianCalendar)
      long millis = utccal.getTimeInMillis();                 //Get the normalized dateTime as a long (milliseconds since the Epoch)
      final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 8];   //allocate an appropriately sized byte array for holding Type,long
      data[offset] = (byte) Type.DATE;       //put the Type in the byte array
      ByteConversion.longToByte(millis, data, offset+1);  //put the long into the byte array
      return(data);                   //return the byte array
    }
    
		/* xs:integer */
		else if(Type.subTypeOf(value.getType(), Type.INTEGER))
		{
			final byte[] data = new byte[ offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 8];
			data[offset] = (byte) Type.INTEGER;
	        long l = ((IntegerValue)value).getValue() - Long.MIN_VALUE;
			ByteConversion.longToByte(l, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
			return data;
		}
		
		/* xs:double */
		else if (value.getType() == Type.DOUBLE)
		{
			final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 8];
	        data[offset] = (byte) Type.DOUBLE;
	        final long bits = Double.doubleToLongBits(((DoubleValue)value).getValue()) ^ 0x8000000000000000L;
	        ByteConversion.longToByte(bits, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
	        return data;
		}
		
		/* xs:float */
		else if (value.getType() == Type.FLOAT)
		{
			final byte[] data = new byte[offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 4];
			data[offset] = (byte) Type.FLOAT;
	        final int bits = (int)(Float.floatToIntBits(((FloatValue)value).getValue()) ^ 0x80000000);
	        ByteConversion.intToByte(bits, data, offset + ValueIndexFactory.LENGTH_VALUE_TYPE);
	        return data;
		}		
	
		/* xs:boolean */
		else if(value.getType() == Type.BOOLEAN)
		{
			final byte[] data = new byte[ offset + ValueIndexFactory.LENGTH_VALUE_TYPE + 1];
	    	data[offset] = Type.BOOLEAN;
	        data[offset + ValueIndexFactory.LENGTH_VALUE_TYPE] = (byte)(((BooleanValue)value).getValue() ? 1 : 0);
	        return data;
		}		
		
		/* unknown! */
		else
		{
			throw new EXistException("Unknown data type for serialization: " + Type.getTypeName(value.getType()));
		}	
	}

}
