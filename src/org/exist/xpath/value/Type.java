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

import it.unimi.dsi.fastutil.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.Int2ObjectRBTreeMap;

public class Type {

	public final static String[] NODETYPES = {
		"node",
		"element",
		"attribute",
		"text",
		"processing-instruction",
		"comment",
		"document",
		"namespace"
	};
						   
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
	public final static int ANY_SIMPLE_TYPE = 21;
	public final static int UNTYPED_ATOMIC = 22;
	
	public final static int NUMBER = 100;
	public final static int STRING = 101;
	public final static int BOOLEAN = 102;
	public final static int DECIMAL = 200;
	public final static int FLOAT = 201;
	public final static int DOUBLE = 203;
	public final static int INTEGER = 300;
	
	public final static int JAVA_OBJECT = 400;
	
	private final static Int2IntRBTreeMap typeHierarchy = new Int2IntRBTreeMap();
	
	static {
		defineSubType(ITEM, NODE);
		defineSubType(NODE, ELEMENT);
		defineSubType(NODE, TEXT);
		defineSubType(NODE, PROCESSING_INSTRUCTION);
		defineSubType(NODE, COMMENT);
		defineSubType(NODE, DOCUMENT);
		defineSubType(NODE, NAMESPACE);
		
		defineSubType(ITEM, ATOMIC);
		defineSubType(ATOMIC, STRING);
		defineSubType(ATOMIC, BOOLEAN);
		defineSubType(ATOMIC, NUMBER);
		defineSubType(ATOMIC, UNTYPED_ATOMIC);
		defineSubType(ATOMIC, JAVA_OBJECT);
		
		defineSubType(NUMBER, DECIMAL);
		defineSubType(NUMBER, FLOAT);
		defineSubType(NUMBER, DOUBLE);
		
		defineSubType(DECIMAL, INTEGER);
	}
	
	private final static Int2ObjectRBTreeMap typeNames = new Int2ObjectRBTreeMap();
	
	static {
		defineTypeName(ELEMENT, "element");
		defineTypeName(DOCUMENT, "document");
		defineTypeName(ATTRIBUTE, "attribute");
		defineTypeName(TEXT, "text");
		defineTypeName(PROCESSING_INSTRUCTION, "processing-instruction");
		defineTypeName(COMMENT, "comment");
		defineTypeName(NAMESPACE, "namespace");
		defineTypeName(NODE, "node");
		defineTypeName(ITEM, "item");
		defineTypeName(JAVA_OBJECT, "object");
		defineTypeName(ANY_SIMPLE_TYPE, "xs:anySimpleType");
		defineTypeName(ANY_TYPE, "xs:anyType");
		defineTypeName(ATOMIC, "xs:anyAtomicType");
		defineTypeName(UNTYPED_ATOMIC, "xs:untypedAtomic");
		defineTypeName(BOOLEAN, "xs:boolean");
		defineTypeName(NUMBER, "number");
		defineTypeName(DECIMAL, "xs:decimal");
		defineTypeName(FLOAT, "xs:float");
		defineTypeName(DOUBLE, "xs:double");
		defineTypeName(INTEGER, "xs:integer");
		defineTypeName(STRING, "xs:string");
	}
	
	public final static void defineTypeName(int type, String name) {
		typeNames.put(type, name);
	}
	
	public final static String getTypeName(int type) {
		return (String)typeNames.get(type);
	}
	
	public final static void defineSubType(int supertype, int subtype) {
		typeHierarchy.put(subtype, supertype);
	}
	
	public final static boolean isNode(int type) {
		return type < 10;
	}
	
	public final static boolean isAtomic(int type) {
		return type >= 20;
	}
	
	public final static boolean subTypeOf(int subtype, int supertype) {
		if(subtype == supertype)
			return true;
		if(supertype == ITEM)
			return true;
		if(subtype == ITEM || subtype == EMPTY)
			return false;
		if(!typeHierarchy.containsKey(subtype))
			throw new IllegalArgumentException("type " + subtype + " is not a valid type");
		return subTypeOf(typeHierarchy.get(subtype), supertype);
	}
	
	public final static int getSuperType(int subtype) {
		if(subtype == ITEM)
			return ITEM;
		return typeHierarchy.get(subtype);
	}
}
