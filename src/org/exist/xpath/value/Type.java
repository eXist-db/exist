/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xpath.value;

import org.exist.dom.QName;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.util.hashtable.Object2IntHashMap;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;

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
			"namespace" };

	public static final int NODE = -1;

	public final static int ELEMENT = 1;
	public final static int ATTRIBUTE = 2;
	public final static int TEXT = 3;
	public final static int PROCESSING_INSTRUCTION = 4;
	public final static int COMMENT = 5;
	public final static int DOCUMENT = 6;
	public final static int NAMESPACE = 7;

	public final static int EMPTY = 10;
	public final static int ITEM = 11;
	public final static int ANY_TYPE = 12;

	public final static int ATOMIC = 20;
	public final static int UNTYPED_ATOMIC = 21;

	public final static int STRING = 22;
	public final static int BOOLEAN = 23;
	public final static int QNAME = 24;

	public final static int NUMBER = 30;
	public final static int DECIMAL = 31;
	public final static int FLOAT = 32;
	public final static int DOUBLE = 33;
	public final static int INTEGER = 34;
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
	
	public final static int JAVA_OBJECT = 100;

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

		defineSubType(ITEM, ATOMIC);
		defineSubType(ATOMIC, STRING);
		defineSubType(ATOMIC, BOOLEAN);
		defineSubType(ATOMIC, QNAME);
		defineSubType(ATOMIC, NUMBER);
		defineSubType(ATOMIC, UNTYPED_ATOMIC);
		defineSubType(ATOMIC, JAVA_OBJECT);

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
	}

	private final static Int2ObjectHashMap typeNames = new Int2ObjectHashMap(100);
	private final static Object2IntHashMap typeCodes = new Object2IntHashMap(100);

	static {
		defineBuiltInType(NODE, "node");
		defineBuiltInType(ITEM, "item");
		defineBuiltInType(EMPTY, "empty");
		defineBuiltInType(NUMBER, "number");

		defineBuiltInType(ELEMENT, "element");
		defineBuiltInType(DOCUMENT, "document");
		defineBuiltInType(ATTRIBUTE, "attribute");
		defineBuiltInType(TEXT, "text");
		defineBuiltInType(PROCESSING_INSTRUCTION, "processing-instruction");
		defineBuiltInType(COMMENT, "comment");
		defineBuiltInType(NAMESPACE, "namespace");

		defineBuiltInType(JAVA_OBJECT, "object");

		defineBuiltInType(ANY_TYPE, "xs:anyType");
		defineBuiltInType(ATOMIC, "xdt:anyAtomicType");
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
	}

	public final static void defineBuiltInType(int type, String name) {
		typeNames.put(type, name);
		typeCodes.put(name, type);
	}

	/**
	 * Get the internal name for the built-in type.
	 * 
	 * @param type
	 * @return
	 */
	public final static String getTypeName(int type) {
		return (String) typeNames.get(type);
	}

	/**
	 * Get the type code for a type identified by its internal name.
	 * 
	 * @param name
	 * @return
	 * @throws XPathException
	 */
	public final static int getType(String name) throws XPathException {
		if (name.equals("node"))
			return NODE;
		int code = typeCodes.get(name);
		if (code == -1)
			throw new XPathException("Type: " + name + " is not defined");
		return code;
	}

	/**
	 * Get the type code for a type identified by its QName.
	 * 
	 * @param qname
	 * @return
	 * @throws XPathException
	 */
	public final static int getType(QName qname) throws XPathException {
		String uri = qname.getNamespaceURI();
		if (uri.equals(StaticContext.SCHEMA_NS))
			return getType("xs:" + qname.getLocalName());
		else if (uri.equals(StaticContext.XPATH_DATATYPES_NS))
			return getType("xs:" + qname.getLocalName());
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
	 * @return
	 */
	public final static boolean subTypeOf(int subtype, int supertype) {
		if (subtype == supertype)
			return true;
		if (supertype == ITEM)
			return true;
		if (subtype == ITEM || subtype == EMPTY)
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
	 * @return
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
	 * @return
	 */
	public static int getCommonSuperType(int type1, int type2) {
		if(type1 == type2)
			return type1;
		type1 = getSuperType(type1);
		if(type1 == type2)
			return type1;
		else
			return getCommonSuperType(type1, getSuperType(type2));
	}
}
