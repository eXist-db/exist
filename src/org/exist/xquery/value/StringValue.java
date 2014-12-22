/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import java.text.Collator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.util.UTF8;
import org.exist.util.XMLChar;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

public class StringValue extends AtomicValue {

	public final static StringValue EMPTY_STRING = new StringValue("");

    private final static String langRegex =
    	//http://www.w3.org/TR/xmlschema-2/#language
    	//The lexical space of language is the set of all strings that conform 
    	//to the pattern [a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})* . 
    	"[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*";
    
    	//Old definition : not sure where it comes from
        //"/(([a-z]|[A-Z])([a-z]|[A-Z])|" // ISO639Code
        //+ "([iI]-([a-z]|[A-Z])+)|"     // IanaCode
        //+ "([xX]-([a-z]|[A-Z])+))"     // UserCode
        //+ "(-([a-z]|[A-Z])+)*/";        // Subcode" 
        
    
    private final static Pattern langPattern = Pattern.compile(langRegex);
    
	protected int type = Type.STRING;

	protected String value;

    public StringValue(String string, int type) throws XPathException {
        this(string, type, true);
    }

	public StringValue(String string, int type, boolean expand) throws XPathException {
		this.type = type;
        if (expand)
		    {string = StringValue.expand(string);} //Should we have character entities
		if(type == Type.STRING)
			{this.value = string;}
		else if(type == Type.NORMALIZED_STRING)
			{this.value = normalizeWhitespace(string);} 
		else {
			this.value = collapseWhitespace(string);
			checkType();
		}
 	}

	public StringValue(String string) {
		//string = StringValue.expand(string); //Should we have character entities
		value = string;
	}

    public void append(String toAppend) {
        value += toAppend;
    }

	public StringValue expand() throws XPathException {
		value = expand(value);
		return this;
	}
	
	private void checkType() throws XPathException {
		switch(type) {
			case Type.NORMALIZED_STRING:
			case Type.TOKEN:
				return;
			case Type.LANGUAGE:
				final Matcher matcher = langPattern.matcher(value);				
				if (!matcher.matches())
					{throw new XPathException(
						"Type error: string "
						+ value
						+ " is not valid for type xs:language");}
				return;
			case Type.NAME:
				if(!QName.isQName(value))
					{throw new XPathException("Type error: string " + value + " is not a valid xs:Name");}
				return;
			case Type.NCNAME:
			case Type.ID:
			case Type.IDREF:
			case Type.ENTITY:
				if(!XMLChar.isValidNCName(value))
					{throw new XPathException("Type error: string " + value + " is not a valid " + Type.getTypeName(type));}
			case Type.NMTOKEN:
				if(!XMLChar.isValidNmtoken(value))
					{throw new XPathException("Type error: string " + value + " is not a valid xs:NMTOKEN");}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return type;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
	 */
	public String getStringValue() {
        return getStringValue(false);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
	 */
	public String getStringValue(boolean bmpCheck) {
        if (bmpCheck) {
            final StringBuilder buf = new StringBuilder(value.length());
            char ch;
            for (int i = 0; i < value.length(); i++) {
                ch = value.charAt(i);
                if (XMLChar.isSurrogate(ch)) {
                    // Compose supplemental from high and low surrogate
                    final int suppChar = XMLChar.supplemental(ch, value.charAt(++i));
                        buf.append("&#");
                        buf.append(Integer.toString(suppChar));
                        buf.append(";");
                } else {
                    buf.append(ch);
                }
            }
            
            return buf.toString();
        } else {
        return value;
        }
	}
    
	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			//TODO : should we allow these 2 type under-promotions ?
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.STRING :
				return this;
			case Type.NORMALIZED_STRING:
			case Type.TOKEN:
			case Type.LANGUAGE:
			case Type.NMTOKEN:
			case Type.NAME:
			case Type.NCNAME:
			case Type.ID:
			case Type.IDREF:
			case Type.ENTITY:
				return new StringValue(value, requiredType);
			case Type.ANY_URI :
				return new AnyURIValue(value);
			case Type.BOOLEAN :
                final String trimmed = trimWhitespace(value);
				if ("0".equals(trimmed) || "false".equals(trimmed))
					{return BooleanValue.FALSE;}
				else if ("1".equals(trimmed) || "true".equals(trimmed))
					{return BooleanValue.TRUE;}
				else
					{throw new XPathException(
						"cannot convert string '" + value + "' to boolean");}
			case Type.FLOAT :
				return new FloatValue(value); 
			case Type.DOUBLE :
			case Type.NUMBER :
				return new DoubleValue(this);
			case Type.DECIMAL :
				return new DecimalValue(value);
			case Type.INTEGER :
			case Type.NON_POSITIVE_INTEGER :
			case Type.NEGATIVE_INTEGER :
			case Type.POSITIVE_INTEGER :
			case Type.LONG :
			case Type.INT :
			case Type.SHORT :
			case Type.BYTE :
			case Type.NON_NEGATIVE_INTEGER :
			case Type.UNSIGNED_LONG :
			case Type.UNSIGNED_INT :
			case Type.UNSIGNED_SHORT :
			case Type.UNSIGNED_BYTE :
				return new IntegerValue(value, requiredType);
			case Type.BASE64_BINARY :
				return new BinaryValueFromBinaryString(new Base64BinaryValueType(), value);
            case Type.HEX_BINARY :
                return new BinaryValueFromBinaryString(new HexBinaryValueType(), value);
			case Type.DATE_TIME :
				return new DateTimeValue(value);
			case Type.TIME :
				return new TimeValue(value);
			case Type.DATE :
            	return new DateValue(value);  
			case Type.DURATION :
				return new DurationValue(value);
			case Type.YEAR_MONTH_DURATION : 
				return new YearMonthDurationValue(value);
			case Type.DAY_TIME_DURATION :	
				return new DayTimeDurationValue(value);
            case Type.GYEAR :
            	return new GYearValue(value);  
            case Type.GMONTH :
        		return new GMonthValue(value);
            case Type.GDAY :
                return new GDayValue(value);
            case Type.GYEARMONTH :
            	return new GYearMonthValue(value);  
            case Type.GMONTHDAY :
                return new GMonthDayValue(value);
			case Type.UNTYPED_ATOMIC :
				return new UntypedAtomicValue(getStringValue());
			case Type.QNAME :
				return new QNameValue(null, new QName(value));
			default :
				throw new XPathException(ErrorCodes.FORG0001, "cannot cast '" + 
						Type.getTypeName(this.getItemType()) + "(\"" + getStringValue() + "\")' to " +
						Type.getTypeName(requiredType));
		}
	}

	public int conversionPreference(Class<?> javaClass) {
		if(javaClass.isAssignableFrom(StringValue.class)) {return 0;}
		if(javaClass == String.class || javaClass == CharSequence.class) {return 1;}
		if(javaClass == Character.class || javaClass == char.class) {return 2;}
		if(javaClass == Double.class || javaClass == double.class) {return 10;}
		if(javaClass == Float.class || javaClass == float.class) {return 11;}
		if(javaClass == Long.class || javaClass == long.class) {return 12;}
		if(javaClass == Integer.class || javaClass == int.class) {return 13;}
		if(javaClass == Short.class || javaClass == short.class) {return 14;}
		if(javaClass == Byte.class || javaClass == byte.class) {return 15;}
		if(javaClass == Boolean.class || javaClass == boolean.class) {return 16;}
		if(javaClass == Object.class) {return 20;}
		
		return Integer.MAX_VALUE;
	}
	
        @Override
	public <T> T toJavaObject(final Class<T> target) throws XPathException {
		if(target.isAssignableFrom(StringValue.class)) {
			return (T)this;
                } else if(target == Object.class || target == String.class || target == CharSequence.class) {
			return (T)value;
                } else if(target == double.class || target == Double.class) {
			final DoubleValue v = (DoubleValue)convertTo(Type.DOUBLE);
			return (T)Double.valueOf(v.getValue());
		} else if(target == float.class || target == Float.class) {
			final FloatValue v = (FloatValue)convertTo(Type.FLOAT);
			return (T)Float.valueOf(v.value);
		} else if(target == long.class || target == Long.class) {
			final IntegerValue v = (IntegerValue)convertTo(Type.LONG);
			return (T)Long.valueOf(v.getInt());
		} else if(target == int.class || target == Integer.class) {
			final IntegerValue v = (IntegerValue)convertTo(Type.INT);
			return (T)Integer.valueOf(v.getInt());
		} else if(target == short.class || target == Short.class) {
			final IntegerValue v = (IntegerValue)convertTo(Type.SHORT);
			return (T)Short.valueOf((short)v.getInt());
		} else if(target == byte.class || target == Byte.class) {
			final IntegerValue v = (IntegerValue)convertTo(Type.BYTE);
			return (T)Byte.valueOf((byte)v.getInt());
		} else if(target == boolean.class || target == Boolean.class) {
			return (T)Boolean.valueOf(effectiveBooleanValue());
		} else if(target == char.class || target == Character.class) {
                    if(value.length() > 1 || value.length() == 0) {
                        throw new XPathException("cannot convert string with length = 0 or length > 1 to Java character");
                    }
                    return (T)Character.valueOf(value.charAt(0));
		}
		
		throw new XPathException("cannot convert value of type " + Type.getTypeName(type) +
			" to Java object of type " + target.getName());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
	 */
	public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
		if (other.isEmpty())
			{return false;}
		//A value of type xs:anyURI (or any type derived by restriction from xs:anyURI) 
		//can be promoted to the type xs:string. 
		//The result of this promotion is created by casting the original value to the type xs:string.
		if (Type.subTypeOf(other.getType(), Type.ANY_URI)) 
			{other = other.convertTo(Type.STRING);}
		if (Type.subTypeOf(other.getType(), Type.STRING)) {
			final int cmp = Collations.compare(collator, value, other.getStringValue());
			switch (operator) {
				case Constants.EQ :
					return cmp == 0;
				case Constants.NEQ :
					return cmp != 0;
				case Constants.LT :
					return cmp < 0;
				case Constants.LTEQ :
					return cmp <= 0;
				case Constants.GT :
					return cmp > 0;
				case Constants.GTEQ :
					return cmp >= 0;
				default :
					throw new XPathException("Type error: cannot apply operand to string value");
			}
		}
		throw new XPathException(ErrorCodes.XPTY0004, 
			"can not compare xs:string('" + value + "') with " + 
			Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "')");
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
	 */
	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(),Type.NUMBER)) {
			//No possible comparisons
			if (((NumericValue)other).isNaN())
				{return Constants.INFERIOR;}
			if (((NumericValue)other).isInfinite())
				{return Constants.INFERIOR;}
		}			
		return Collations.compare(collator, value, other.getStringValue());
	}

	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#startsWith(org.exist.xquery.value.AtomicValue)
	 */
	public boolean startsWith(Collator collator, AtomicValue other) throws XPathException {
		return Collations.startsWith(collator, value, other.getStringValue());
	}
	
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#endsWith(org.exist.xquery.value.AtomicValue)
	 */
	public boolean endsWith(Collator collator, AtomicValue other) throws XPathException {
		return Collations.endsWith(collator, value, other.getStringValue());
	}
	
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#contains(org.exist.xquery.value.AtomicValue)
	 */
	public boolean contains(Collator collator, AtomicValue other) throws XPathException {
		return Collations.indexOf(collator, value, other.getStringValue()) != Constants.STRING_NOT_FOUND;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		// If its operand is a singleton value of type xs:string, xs:anyURI, xs:untypedAtomic, 
		//or a type derived from one of these, fn:boolean returns false if the operand value has zero length; otherwise it returns true.
		return value.length() > 0;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return value;
	}

	public final static String normalizeWhitespace(CharSequence seq) {
        if (seq == null)
            {return "";}
        final StringBuilder copy = new StringBuilder(seq.length());
		char ch;
		for (int i = 0; i < seq.length(); i++) {
			ch = seq.charAt(i);
			switch (ch) {
				case '\n' :
				case '\r' :
				case '\t' :
					copy.append(' ');
					break;
				default :
					copy.append(ch);
			}
		}
		return copy.toString();
	}

	/**
	 * Collapses all sequences of adjacent whitespace chars in the input string
	 * into a single space.
	 *  
	 * @param in
	 */
	public static String collapseWhitespace(CharSequence in) {
        if (in == null)
            {return "";}
        if (in.length() == 0) {
			return in.toString();
		}
		int i = 0;
		// this method is performance critical, so first test if we need to collapse at all
		for (; i < in.length(); i++) {
		    final char c = in.charAt(i);
		    if(XMLChar.isSpace(c)) {
		        if(i + 1 < in.length() && XMLChar.isSpace(in.charAt(i + 1)))
		            {break;}
		    }
		}
		if(i == in.length())
		    // no whitespace to collapse, just return
		    {return in.toString();}
		
		// start to collapse whitespace
		final StringBuilder sb = new StringBuilder(in.length());
		sb.append(in.subSequence(0, i + 1).toString());
		boolean inWhitespace = true;
		for (; i < in.length(); i++) {
			final char c = in.charAt(i);
			if(XMLChar.isSpace(c)) {
				if (inWhitespace) {
					// remove the whitespace
				} else {
					sb.append(' ');
					inWhitespace = true;
				}
			} else {
				sb.append(c);
				inWhitespace = false;
			}
		}
		if (sb.charAt(sb.length() - 1) == ' ') {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	public final static String trimWhitespace(String in) {
		if (in == null) {
            return "";
        }
            if (in.length()==0) {
            return in;
        }
        int first = 0;
        int last = in.length() - 1;
        while (in.charAt(first) <= 0x20) {
            if (first++ >= last) {
                return "";
            }
        }
        while (in.charAt(last) <= 0x20) {
            last--;
        }
        return in.substring(first, last+1);
	}
	
	public final static String expand(CharSequence seq) throws XPathException {
        if (seq == null)
            {return "";}
        final StringBuilder buf = new StringBuilder(seq.length());
	    StringBuilder entityRef = null;
	    char ch;
	    for (int i = 0; i < seq.length(); i++) {
	        ch = seq.charAt(i);
	        switch (ch) {
	            case '&' :
	                if (entityRef == null)
	                    {entityRef = new StringBuilder();}
	                else
	                    {entityRef.setLength(0);}
	                if ((i+1)==seq.length()) {
	                    throw new XPathException(ErrorCodes.XPST0003, "Ampersands (&) must be escaped.");
	                }
	                if ((i+2)==seq.length()) {
	                    throw new XPathException(ErrorCodes.XPST0003, "Ampersands (&) must be escaped (missing ;).");
	                }
	                ch = seq.charAt(i+1);
	                if (ch!='#') {
	                    if (!Character.isLetter(ch)) {
	                        throw new XPathException(ErrorCodes.XPST0003, "Ampersands (&) must be escaped (following character was not a name start character).");
	                    }
	                    entityRef.append(ch);
	                    boolean found = false;
	                    for (int j = i + 2; j < seq.length(); j++) {
	                        ch = seq.charAt(j);
	                        if (ch != ';' && (ch=='.' || ch=='_' || ch=='-' || Character.isLetterOrDigit(ch))) {
	                            entityRef.append(ch);
	                        } else if (ch==';') {
	                            found = true;
	                            i = j;
	                            break;
	                        } else {
	                            break;
	                        }
	                    }
	                    if (found) {
	                        buf.append((char) expandEntity(entityRef.toString()));
	                    } else {
	                        throw new XPathException(ErrorCodes.XPST0003, "Invalid character ("+ch+") in entity name ("+entityRef+") or missing ;");
	                    }
                        
	                } else {
	                    entityRef.append(ch);
	                    ch = seq.charAt(i+2);
	                    boolean found = false;
	                    if (ch=='x') {
	                        entityRef.append(ch);
	                        // hex number
	                        for (int j = i + 3; j < seq.length(); j++) {
	                            ch = seq.charAt(j);
	                            if (ch != ';' && (ch=='0' || ch=='1' || ch=='2' || ch=='3' || ch=='4' || ch=='5' || ch=='6' || ch=='7' || ch=='8' || ch=='9' ||
	                                    ch=='a' || ch=='b' || ch=='c' || ch=='d' || ch=='e' || ch=='f' ||
	                                    ch=='A' || ch=='B' || ch=='C' || ch=='D' || ch=='E' || ch=='F')) {
	                                entityRef.append(ch);
	                            } else if (ch==';') {
	                                found = true;
	                                i = j;
	                                break;
	                            } else {
	                                break;
	                            }
	                        }
	                    } else {
	                        // decimal number
	                        for (int j = i + 2; j < seq.length(); j++) {
	                            ch = seq.charAt(j);
	                            if (ch != ';' && (ch=='0' || ch=='1' || ch=='2' || ch=='3' || ch=='4' || ch=='5' || ch=='6' || ch=='7' || ch=='8' || ch=='9')) {
	                                entityRef.append(ch);
	                            } else if (ch==';') {
	                                found = true;
	                                i = j;
	                                break;
	                            } else {
	                                break;
	                            }
	                        }
	                    }
	                    if (found) {
                            final int charref = expandEntity(entityRef.toString());
                            if (XMLChar.isSupplemental(charref)) {
                                buf.append(XMLChar.highSurrogate(charref));
                                buf.append(XMLChar.lowSurrogate(charref));
                            } else {
                                buf.append((char) charref);
                            }
	                    } else {
	                        throw new XPathException(ErrorCodes.XPST0003, "Invalid character in character reference ("+ch+") or missing ;");
	                    }

	                }
	                break;
	            case '\r':
	                // drop carriage returns
	                if ((i+1)!=seq.length()) {
	                    ch = seq.charAt(i+1);
	                    if (ch!='\n') {
	                        buf.append('\n');
	                    }
	                }
	                break;
	            default :
	                buf.append(ch);
	        }
	    }
	    return buf.toString();
	}

	/**
     * The method <code>expandEntity</code>
     *
     * @param buf a <code>String</code> value
     * @return an <code>int</code> value
     * @exception XPathException if an error occurs
     */
    private final static int expandEntity(String buf) throws XPathException {
		if ("amp".equals(buf))
			{return '&';}
		else if ("lt".equals(buf))
			{return '<';}
		else if ("gt".equals(buf))
			{return '>';}
		else if ("quot".equals(buf))
			{return '"';}
		else if ("apos".equals(buf))
			{return '\'';}
		else if (buf.length() > 1 && buf.charAt(0) == '#') {
            return expandCharRef(buf.substring(1));
		} else
			{throw new XPathException("Unknown entity reference: " + buf);}
	}

	/**
     * The method <code>expandCharRef</code>
     *
     * @param buf a <code>String</code> value
     * @return an <code>int</code> value
     * @exception XPathException if an error occurs
     */
    private final static int expandCharRef(String buf) throws XPathException {
		try {
            int charNumber;
			if (buf.length() > 1 && buf.charAt(0) == 'x') {
				// Hex
				charNumber = Integer.parseInt(buf.substring(1), 16);
			} else {
				charNumber = Integer.parseInt(buf);
                        }
                   if (charNumber==0) {
                      throw new XPathException(ErrorCodes.XQST0090, "Character number zero (0) is not allowed.");
                   }
                   return charNumber;
		} catch (final NumberFormatException e) {
			throw new XPathException("Unknown character reference: " + buf);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.STRING))
			{return Collations.compare(collator, value, ((StringValue) other).value) > 0 ? this : other;}
		else
			{return Collations.compare(collator, value, ((StringValue) other.convertTo(getType())).value) > 0
				? this
				: other;}
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.STRING))
			{return Collations.compare(collator, value, ((StringValue) other).value) < 0 ? this : other;}
		else
			{return Collations.compare(collator, value, ((StringValue) other.convertTo(getType())).value) < 0
				? this
				: other;}
	}

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        final AtomicValue other = (AtomicValue)o;
//        if(Type.subTypeOf(other.getType(), Type.STRING))
        if (getType() == other.getType())
            {return value.compareTo(((StringValue)other).value);}
        else
            {return getType() > other.getType() ? 1 : -1;}
    }
    
    /** Serialize for the persistant storage 
     * @param offset
     * */
    public byte[] serializeValue( int offset, boolean caseSensitive) {
        final String val = caseSensitive ? value : value.toLowerCase();
		final byte[] data = new byte[ offset + 1 + UTF8.encoded(val) ];
		data[offset] = (byte) type;	// TODO: cast to byte is not safe
		UTF8.encode(val, data, offset+1);  
		return data;
	}

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            {return true;}
        if (obj == null)
            {return false;}
        return value.equals(obj.toString());
    }
}
