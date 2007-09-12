/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist Project
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

import java.util.HashSet;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.util.hashtable.Object2IntHashMap;
import org.exist.xquery.XPathException;

/**
 * Defines all built-in types and their relations.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Type {

	public final static String[] NODETYPES =
		{
			"node",
			"element",
			"attribute",
			"text",
			"processing-instruction",
			"comment",
			"document",
			"namespace",
			"cdata-section" };

	public static final int NODE = -1;

	public final static int ELEMENT = 1;
	public final static int ATTRIBUTE = 2;
	public final static int TEXT = 3;
	public final static int PROCESSING_INSTRUCTION = 4;
	public final static int COMMENT = 5;
	public final static int DOCUMENT = 6;
	public final static int NAMESPACE = 500;
	public final static int CDATA_SECTION = 501;

	public final static int EMPTY = 10;
	public final static int ITEM = 11;
	public final static int ANY_TYPE = 12;

	public final static int ATOMIC = 20;
	public final static int UNTYPED_ATOMIC = 21;

	public final static int STRING = 22;
	public final static int BOOLEAN = 23;
	public final static int QNAME = 24;
	public final static int ANY_URI = 25;
	public final static int BASE64_BINARY = 26;
    public final static int HEX_BINARY = 27;
    
	public final static int NUMBER = 30;
	public final static int INTEGER = 31;
	public final static int DECIMAL = 32;
	public final static int FLOAT = 33;
	public final static int DOUBLE = 34;
	
	public final static int NON_POSITIVE_INTEGER = 35;
	public final static int NEGATIVE_INTEGER = 36;
	public final static int LONG = 37;
	public final static int INT = 38;
	public final static int SHORT = 39;
	public final static int BYTE = 40;
	public final static int NON_NEGATIVE_INTEGER = 41;
	public final static int UNSIGNED_LONG = 42;
	public final static int UNSIGNED_INT = 43;
	public final static int UNSIGNED_SHORT = 44;
	public final static int UNSIGNED_BYTE = 45;
	public final static int POSITIVE_INTEGER = 46;
	
	public final static int DATE_TIME = 50;
	public final static int DATE = 51;
	public final static int TIME = 52;
	public final static int DURATION = 53;
	public final static int YEAR_MONTH_DURATION = 54;
	public final static int DAY_TIME_DURATION = 55;
	public final static int GYEAR = 56;
    public final static int GMONTH = 57;
    public final static int GDAY = 58;
    public final static int GYEARMONTH = 59;
    public final static int GMONTHDAY = 71;
    
	public final static int TOKEN = 60;
	public final static int NORMALIZED_STRING = 61;
	public final static int LANGUAGE = 62;
	public final static int NMTOKEN = 63;
	public final static int NAME = 64;
	public final static int NCNAME = 65;
	public final static int ID = 66;
	public final static int IDREF = 67;
	public final static int ENTITY = 68;
	
	public final static int JAVA_OBJECT = 100;
    public final static int FUNCTION_REFERENCE = 101;
	
	/**
	 * Special type constant to indicate that an item has been
	 * fulltext indexed.
	 */
	public final static int IDX_FULLTEXT = 200;
	
	private final static Int2ObjectHashMap typeHierarchy = new Int2ObjectHashMap();

	static {
		defineSubType(ITEM, NODE);
		defineSubType(NODE, ELEMENT);
		defineSubType(NODE, ATTRIBUTE);
		defineSubType(NODE, TEXT);
		defineSubType(NODE, PROCESSING_INSTRUCTION);
		defineSubType(NODE, COMMENT);
		defineSubType(NODE, DOCUMENT);
		defineSubType(NODE, NAMESPACE);
		defineSubType(NODE, CDATA_SECTION);

		defineSubType(ITEM, ATOMIC);
		defineSubType(ATOMIC, STRING);
		defineSubType(ATOMIC, BOOLEAN);
		defineSubType(ATOMIC, QNAME);
		defineSubType(ATOMIC, ANY_URI);
		defineSubType(ATOMIC, NUMBER);
		defineSubType(ATOMIC, UNTYPED_ATOMIC);
		defineSubType(ATOMIC, JAVA_OBJECT);
        defineSubType(ATOMIC, FUNCTION_REFERENCE);
		defineSubType(ATOMIC, DATE_TIME);
		defineSubType(ATOMIC, DATE);
		defineSubType(ATOMIC, TIME);
		defineSubType(ATOMIC, DURATION);
        defineSubType(ATOMIC, GYEAR);
        defineSubType(ATOMIC, GMONTH);
        defineSubType(ATOMIC, GDAY);
        defineSubType(ATOMIC, GYEARMONTH);
        defineSubType(ATOMIC, GMONTHDAY);
		defineSubType(ATOMIC, BASE64_BINARY);
        defineSubType(ATOMIC, HEX_BINARY);
        
		defineSubType(DURATION, YEAR_MONTH_DURATION);
		defineSubType(DURATION, DAY_TIME_DURATION);
		
		defineSubType(NUMBER, DECIMAL);
		defineSubType(NUMBER, FLOAT);
		defineSubType(NUMBER, DOUBLE);

		defineSubType(DECIMAL, INTEGER);
		
		defineSubType(INTEGER, NON_POSITIVE_INTEGER);
		defineSubType(NON_POSITIVE_INTEGER, NEGATIVE_INTEGER);
		
		defineSubType(INTEGER, LONG);
		defineSubType(LONG, INT);
		defineSubType(INT, SHORT);
		defineSubType(SHORT, BYTE);
		
		defineSubType(INTEGER, NON_NEGATIVE_INTEGER);
		defineSubType(NON_NEGATIVE_INTEGER, POSITIVE_INTEGER);
		
		defineSubType(NON_NEGATIVE_INTEGER, UNSIGNED_LONG);
		defineSubType(UNSIGNED_LONG, UNSIGNED_INT);
		defineSubType(UNSIGNED_INT, UNSIGNED_SHORT);
		defineSubType(UNSIGNED_SHORT, UNSIGNED_BYTE);
		
		defineSubType(STRING, NORMALIZED_STRING);
		defineSubType(NORMALIZED_STRING, TOKEN);
		defineSubType(TOKEN, LANGUAGE);
		defineSubType(TOKEN, NMTOKEN);
		defineSubType(TOKEN, NAME);
		defineSubType(NAME, NCNAME);
		defineSubType(NCNAME, ID);
		defineSubType(NCNAME, IDREF);
		defineSubType(NCNAME, ENTITY);
	}

	private final static Int2ObjectHashMap typeNames = new Int2ObjectHashMap(100);
	private final static Object2IntHashMap typeCodes = new Object2IntHashMap(100);

	static {
		//TODO : use NODETYPES above ?
		//TODO use parentheses after the nodes name  ?
		defineBuiltInType(NODE, "node()");
		defineBuiltInType(ITEM, "item()");
		defineBuiltInType(EMPTY, "empty()");		

		defineBuiltInType(ELEMENT, "element()");
		defineBuiltInType(DOCUMENT, "document()");
		defineBuiltInType(ATTRIBUTE, "attribute()");
		defineBuiltInType(TEXT, "text()");
		defineBuiltInType(PROCESSING_INSTRUCTION, "processing-instruction()");
		defineBuiltInType(COMMENT, "comment()");
		defineBuiltInType(NAMESPACE, "namespace()");
		defineBuiltInType(CDATA_SECTION, "cdata-section()");

		defineBuiltInType(JAVA_OBJECT, "object");
		defineBuiltInType(FUNCTION_REFERENCE, "function");
		defineBuiltInType(NUMBER, "numeric");
		
		defineBuiltInType(ANY_TYPE, "xs:anyType");
		//Duplicate definition : new one first
		defineBuiltInType(ATOMIC, "xs:anyAtomicType");
		defineBuiltInType(ATOMIC, "xdt:anyAtomicType");
		//Duplicate definition : new one first
		defineBuiltInType(UNTYPED_ATOMIC, "xs:untypedAtomic");
		defineBuiltInType(UNTYPED_ATOMIC, "xdt:untypedAtomic");		

		defineBuiltInType(BOOLEAN, "xs:boolean");
		defineBuiltInType(DECIMAL, "xs:decimal");
		defineBuiltInType(FLOAT, "xs:float");
		defineBuiltInType(DOUBLE, "xs:double");
		
		defineBuiltInType(INTEGER, "xs:integer");
		defineBuiltInType(NON_POSITIVE_INTEGER, "xs:nonPositiveInteger");
		defineBuiltInType(NEGATIVE_INTEGER, "xs:negativeInteger");
		defineBuiltInType(LONG, "xs:long");
		defineBuiltInType(INT, "xs:int");
		defineBuiltInType(SHORT, "xs:short");
		defineBuiltInType(BYTE, "xs:byte");
		defineBuiltInType(NON_NEGATIVE_INTEGER, "xs:nonNegativeInteger");
		defineBuiltInType(UNSIGNED_LONG, "xs:unsignedLong");
		defineBuiltInType(UNSIGNED_INT, "xs:unsignedInt");
		defineBuiltInType(UNSIGNED_SHORT, "xs:unsignedShort");
		defineBuiltInType(UNSIGNED_BYTE, "xs:unsignedByte");
		defineBuiltInType(POSITIVE_INTEGER, "xs:positiveInteger");
		
		defineBuiltInType(STRING, "xs:string");
		defineBuiltInType(QNAME, "xs:QName");
		defineBuiltInType(ANY_URI, "xs:anyURI");
		defineBuiltInType(BASE64_BINARY, "xs:base64Binary");
        defineBuiltInType(HEX_BINARY, "xs:hexBinary");
        
		defineBuiltInType(DATE_TIME, "xs:dateTime");
		defineBuiltInType(DATE, "xs:date");
		defineBuiltInType(TIME, "xs:time");
		defineBuiltInType(DURATION, "xs:duration");
        defineBuiltInType(GYEAR, "xs:gYear");
        defineBuiltInType(GMONTH, "xs:gMonth");
        defineBuiltInType(GDAY, "xs:gDay");
        defineBuiltInType(GYEARMONTH, "xs:gYearMonth");
        defineBuiltInType(GMONTHDAY, "xs:gMonthDay");
        
		//Duplicate definition : new one first
		defineBuiltInType(YEAR_MONTH_DURATION, "xs:yearMonthDuration");
		defineBuiltInType(YEAR_MONTH_DURATION, "xdt:yearMonthDuration");		
		//Duplicate definition : new one first
		defineBuiltInType(DAY_TIME_DURATION, "xs:dayTimeDuration");
		defineBuiltInType(DAY_TIME_DURATION, "xdt:dayTimeDuration");		
		
		defineBuiltInType(NORMALIZED_STRING, "xs:normalizedString");
		defineBuiltInType(TOKEN, "xs:token");
		defineBuiltInType(LANGUAGE, "xs:language");
		defineBuiltInType(NMTOKEN, "xs:NMTOKEN");
		defineBuiltInType(NAME, "xs:Name");
		defineBuiltInType(NCNAME, "xs:NCName");
		defineBuiltInType(ID, "xs:ID");
		defineBuiltInType(IDREF, "xs:IDREF");
		defineBuiltInType(ENTITY, "xs:ENTITY");
	}

	public final static void defineBuiltInType(int type, String name) {
		typeNames.put(type, name);
		typeCodes.put(name, type);
	}

	/**
	 * Get the internal name for the built-in type.
	 * 
	 * @param type
	 */
	public final static String getTypeName(int type) {
		return (String) typeNames.get(type);
	}

	/**
	 * Get the type code for a type identified by its internal name.
	 * 
	 * @param name
	 * @throws XPathException
	 */
	public final static int getType(String name) throws XPathException {
		//if (name.equals("node"))
		//	return NODE;
		int code = typeCodes.get(name);
		if (code == Object2IntHashMap.UNKNOWN_KEY)
			throw new XPathException("Type: " + name + " is not defined");
		return code;
	}

	/**
	 * Get the type code for a type identified by its QName.
	 * 
	 * @param qname
	 * @throws XPathException
	 */
	public final static int getType(QName qname) throws XPathException {
		String uri = qname.getNamespaceURI();
		if (uri.equals(Namespaces.SCHEMA_NS))
			return getType("xs:" + qname.getLocalName());
		else if (uri.equals(Namespaces.XPATH_DATATYPES_NS))
			return getType("xdt:" + qname.getLocalName());
		else
			return getType(qname.getLocalName());
	}

	/**
	 * Define supertype/subtype relation.
	 * 
	 * @param supertype
	 * @param subtype
	 */
	public final static void defineSubType(int supertype, int subtype) {
		typeHierarchy.put(subtype, new Integer(supertype));
	}

	/**
	 * Check if the given type code is a subtype of the specified supertype.
	 * 
	 * @param subtype
	 * @param supertype
	 */
	public final static boolean subTypeOf(int subtype, int supertype) {
		if (subtype == supertype)
			return true;
		//Note that it will return true even if subtype == EMPTY
		if (supertype == ITEM || supertype == ANY_TYPE)
			//maybe return subtype != EMPTY ?
			return true;
		//Note that EMPTY is *not* a sub-type of anything else than itself
		//EmptySequence has to take care of this when it checks its type
		if (subtype == ITEM || subtype == EMPTY || subtype == ANY_TYPE)
			return false;
		if (!typeHierarchy.containsKey(subtype))
			throw new IllegalArgumentException(
				"type " + subtype + " is not a valid type");
		subtype = ((Integer)typeHierarchy.get(subtype)).intValue();
		return subTypeOf(subtype, supertype);
	}

	/**
	 * Get the type code of the supertype of the specified subtype.
	 * 
	 * @param subtype
	 */
	public final static int getSuperType(int subtype) {
		if (subtype == ITEM)
			return ITEM;
		Integer i = (Integer)typeHierarchy.get(subtype);
		if(i == null) {
			System.err.println("no supertype for " + getTypeName(subtype));
			return ITEM;
		}
		return i.intValue();
	}

	/**
	 * Find a common supertype for two given type codes.
	 * 
	 * Type.ITEM is returned if no other common supertype
	 * is found.
	 *  
	 * @param type1
	 * @param type2
	 */
	public static int getCommonSuperType(int type1, int type2) {
		//Super shortcut
		if(type1 == type2)
			return type1;
		//TODO : optimize by swapping the arguments based on their numeric values ?
		//Processing lower value first *should* reduce the size of the Set
		//Collect type1's super-types
		HashSet t1 = new HashSet();
		//Don't introduce a shortcut (starting at getSuperType(type1) here
		//type2 might be a super-type of type1
		int t;
		for(t = type1; t != ITEM; t = getSuperType(t)) {
			//Shortcut
			if (t == type2)
				return t;
			t1.add(new Integer(t));
		}
		//Starting from type2's super type : the shortcut should have done its job
		for(t = getSuperType(type2); t != ITEM ; t = getSuperType(t)) {
			if (t1.contains(new Integer(t)))
				return t;
		}
		return ITEM;
	} 
}
