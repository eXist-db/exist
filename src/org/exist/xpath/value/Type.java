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
import it.unimi.dsi.fastutil.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.Object2IntRBTreeMap;

import org.exist.dom.QName;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;

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

	public final static int JAVA_OBJECT = 100;

	private final static Int2IntRBTreeMap typeHierarchy =
		new Int2IntRBTreeMap();

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
		defineSubType(ATOMIC, QNAME);
		defineSubType(ATOMIC, NUMBER);
		defineSubType(ATOMIC, UNTYPED_ATOMIC);
		defineSubType(ATOMIC, JAVA_OBJECT);

		defineSubType(NUMBER, DECIMAL);
		defineSubType(NUMBER, FLOAT);
		defineSubType(NUMBER, DOUBLE);

		defineSubType(DECIMAL, INTEGER);
	}

	private final static Int2ObjectOpenHashMap typeNames =
		new Int2ObjectOpenHashMap(100);
	private final static Object2IntRBTreeMap typeCodes =
		new Object2IntRBTreeMap();

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
		defineBuiltInType(STRING, "xs:string");
		defineBuiltInType(QNAME, "xs:QName");
	}

	public final static void defineBuiltInType(int type, String name) {
		typeNames.put(type, name);
		typeCodes.put(name, type);
	}

	public final static String getTypeName(int type) {
		return (String) typeNames.get(type);
	}

	public final static int getType(String name) throws XPathException {
		int code = typeCodes.getInt(name);
		if (code == typeCodes.defaultReturnValue())
			throw new XPathException("Type: " + name + " is not defined");
		return code;
	}

	public final static int getType(QName qname) throws XPathException {
		String uri = qname.getNamespaceURI();
		if (uri.equals(StaticContext.SCHEMA_NS))
			return getType("xs:" + qname.getLocalName());
		else if (uri.equals(StaticContext.XPATH_DATATYPES_NS))
			return getType("xs:" + qname.getLocalName());
		else
			return getType(qname.getLocalName());
	}

	public final static void defineSubType(int supertype, int subtype) {
		typeHierarchy.put(subtype, supertype);
	}

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
		return subTypeOf(typeHierarchy.get(subtype), supertype);
	}

	public final static int getSuperType(int subtype) {
		if (subtype == ITEM)
			return ITEM;
		return typeHierarchy.get(subtype);
	}
}
