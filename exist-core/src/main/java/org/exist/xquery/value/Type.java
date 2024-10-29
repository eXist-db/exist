/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.value;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;

import javax.annotation.Nullable;

/**
 * Defines all built-in types and their relations.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Type {

    private final static Logger LOG = LogManager.getLogger(Type.class);

    // Tombstone value used for FastUtil Int maps
    private static final int NO_SUCH_VALUE = -99;

    public final static int ITEM = 1;

    public final static int ANY_TYPE = 2;

    public final static int ANY_SIMPLE_TYPE = 3;

    /* xs:anyAtomicType and its subtypes */
    public final static int ANY_ATOMIC_TYPE = 4;
    public final static int UNTYPED_ATOMIC = 5;
    public final static int DATE_TIME = 6;
    public final static int DATE_TIME_STAMP = 7;
    public final static int DATE = 8;
    public final static int TIME = 9;
    public final static int DURATION = 10;
    public final static int YEAR_MONTH_DURATION = 11;
    public final static int DAY_TIME_DURATION = 12;
    public final static int FLOAT = 13;
    public final static int DOUBLE = 14;
    public final static int DECIMAL = 15;
    public final static int INTEGER = 16;
    public final static int NON_POSITIVE_INTEGER = 17;
    public final static int NEGATIVE_INTEGER = 18;
    public final static int LONG = 19;
    public final static int INT = 20;
    public final static int SHORT = 21;
    public final static int BYTE = 22;
    public final static int NON_NEGATIVE_INTEGER = 23;
    public final static int UNSIGNED_LONG = 24;
    public final static int UNSIGNED_INT = 25;
    public final static int UNSIGNED_SHORT = 26;
    public final static int UNSIGNED_BYTE = 27;
    public final static int POSITIVE_INTEGER = 28;
    public final static int G_YEAR_MONTH = 29;
    public final static int G_YEAR = 30;
    public final static int G_MONTH_DAY = 31;
    public final static int G_DAY = 32;
    public final static int G_MONTH = 33;
    public final static int STRING = 34;
    public final static int NORMALIZED_STRING = 35;
    public final static int TOKEN = 36;
    public final static int LANGUAGE = 37;
    public final static int NMTOKEN = 38;
    public final static int NAME = 39;
    public final static int NCNAME = 40;
    public final static int ID = 41;
    public final static int IDREF = 42;
    public final static int ENTITY = 43;
    public final static int BOOLEAN = 44;
    public final static int BASE64_BINARY = 45;
    public final static int HEX_BINARY = 46;
    public final static int ANY_URI = 47;
    public final static int QNAME = 48;
    public final static int NOTATION = 49;

    /* list types */
    public final static int NMTOKENS = 50;
    public final static int IDREFS = 51;
    public final static int ENTITIES = 52;

    /* union types */
    public final static int NUMERIC = 53;
    public final static int ERROR = 54;

    /* complex types */
    public final static int UNTYPED = 55;

    /* nodes */
    public static final int NODE = 56;
    public final static int ATTRIBUTE = 57;
    public final static int COMMENT = 58;
    public final static int DOCUMENT = 59;
    public final static int ELEMENT = 60;
    public final static int NAMESPACE = 61;
    public final static int PROCESSING_INSTRUCTION = 62;
    public final static int TEXT = 63;

    /* functions */
    public final static int FUNCTION = 64;
    public final static int ARRAY_ITEM = 65;
    public final static int MAP_ITEM = 66;

    // NOTE(AR) the types below do NOT appear in the XDM 3.1 spec - https://www.w3.org/TR/xpath-datamodel-31
    public final static int CDATA_SECTION = 67;
    public final static int JAVA_OBJECT = 68;
    public final static int EMPTY_SEQUENCE = 69;  // NOTE(AR) this types does appear in the XQ 3.1 spec - https://www.w3.org/TR/xquery-31/#id-sequencetype-syntax

    private final static int[] superTypes = new int[69];
    private final static Int2ObjectOpenHashMap<String[]> typeNames = new Int2ObjectOpenHashMap<>(69, Hash.FAST_LOAD_FACTOR);
    private final static Object2IntOpenHashMap<String> typeCodes = new Object2IntOpenHashMap<>(78, Hash.FAST_LOAD_FACTOR);
    static {
        typeCodes.defaultReturnValue(NO_SUCH_VALUE);
    }
    private final static Int2ObjectMap<IntArraySet> unionTypes = new Int2ObjectArrayMap<>(2);
    private final static Int2IntOpenHashMap primitiveTypes = new Int2IntOpenHashMap(45, Hash.FAST_LOAD_FACTOR);
    static {
        primitiveTypes.defaultReturnValue(NO_SUCH_VALUE);
    }

    static {
        // ANY types
        defineSubType(ANY_TYPE, ANY_SIMPLE_TYPE);
        defineSubType(ANY_TYPE, UNTYPED);

        // ANY_SIMPLE types
        defineSubType(ANY_SIMPLE_TYPE, ANY_ATOMIC_TYPE);
        defineSubType(ANY_SIMPLE_TYPE, IDREFS);
        defineSubType(ANY_SIMPLE_TYPE, NMTOKENS);
        defineSubType(ANY_SIMPLE_TYPE, ENTITIES);
        defineSubType(ANY_SIMPLE_TYPE, NUMERIC);
        defineSubType(ANY_SIMPLE_TYPE, ERROR);

        // ITEM sub-types
        defineSubType(ITEM, ANY_ATOMIC_TYPE);
        defineSubType(ITEM, FUNCTION);
        defineSubType(ITEM, NODE);

        // ATOMIC sub-types
        defineSubType(ANY_ATOMIC_TYPE, ANY_URI);
        defineSubType(ANY_ATOMIC_TYPE, BASE64_BINARY);
        defineSubType(ANY_ATOMIC_TYPE, BOOLEAN);
        defineSubType(ANY_ATOMIC_TYPE, DATE);
        defineSubType(ANY_ATOMIC_TYPE, DATE_TIME);
        defineSubType(ANY_ATOMIC_TYPE, DECIMAL);
        defineSubType(ANY_ATOMIC_TYPE, DOUBLE);
        defineSubType(ANY_ATOMIC_TYPE, DURATION);
        defineSubType(ANY_ATOMIC_TYPE, FLOAT);
        defineSubType(ANY_ATOMIC_TYPE, G_DAY);
        defineSubType(ANY_ATOMIC_TYPE, G_MONTH);
        defineSubType(ANY_ATOMIC_TYPE, G_MONTH_DAY);
        defineSubType(ANY_ATOMIC_TYPE, G_YEAR);
        defineSubType(ANY_ATOMIC_TYPE, G_YEAR_MONTH);
        defineSubType(ANY_ATOMIC_TYPE, HEX_BINARY);
        defineSubType(ANY_ATOMIC_TYPE, JAVA_OBJECT);
        defineSubType(ANY_ATOMIC_TYPE, NOTATION);
        defineSubType(ANY_ATOMIC_TYPE, QNAME);
        defineSubType(ANY_ATOMIC_TYPE, STRING);
        defineSubType(ANY_ATOMIC_TYPE, TIME);
        defineSubType(ANY_ATOMIC_TYPE, UNTYPED_ATOMIC);

        // DATE_TIME sub-types
        defineSubType(DATE_TIME, DATE_TIME_STAMP);

        // DURATION sub-types
        defineSubType(DURATION, DAY_TIME_DURATION);
        defineSubType(DURATION, YEAR_MONTH_DURATION);

        // DECIMAL sub-types
        defineSubType(DECIMAL, INTEGER);

        // INTEGER sub-types
        defineSubType(INTEGER, LONG);
        defineSubType(INTEGER, NON_NEGATIVE_INTEGER);
        defineSubType(INTEGER, NON_POSITIVE_INTEGER);

        // LONG sub-types
        defineSubType(LONG, INT);

        // INT sub-types
        defineSubType(INT, SHORT);

        // SHORT sub-types
        defineSubType(SHORT, BYTE);

        // NON_NEGATIVE_INTEGER sub-types
        defineSubType(NON_NEGATIVE_INTEGER, POSITIVE_INTEGER);
        defineSubType(NON_NEGATIVE_INTEGER, UNSIGNED_LONG);

        // UNSIGNED_LONG sub-types
        defineSubType(UNSIGNED_LONG, UNSIGNED_INT);

        // UNSIGNED_INT sub-types
        defineSubType(UNSIGNED_INT, UNSIGNED_SHORT);

        // UNSIGNED_SHORT sub-types
        defineSubType(UNSIGNED_SHORT, UNSIGNED_BYTE);

        // NON_POSITIVE_INTEGER sub-types
        defineSubType(NON_POSITIVE_INTEGER, NEGATIVE_INTEGER);

        // STRING sub-types
        defineSubType(STRING, NORMALIZED_STRING);

        // NORMALIZED_STRING sub-types
        defineSubType(NORMALIZED_STRING, TOKEN);

        // TOKEN sub-types
        defineSubType(TOKEN, LANGUAGE);
        defineSubType(TOKEN, NAME);
        defineSubType(TOKEN, NMTOKEN);

        // NAME sub-types
        defineSubType(NAME, NCNAME);

        // NCNAME sub-types
        defineSubType(NCNAME, ENTITY);
        defineSubType(NCNAME, ID);
        defineSubType(NCNAME, IDREF);

        // FUNCTION_REFERENCE sub-types
        defineSubType(FUNCTION, MAP_ITEM);
        defineSubType(FUNCTION, ARRAY_ITEM);

        // NODE types
        defineSubType(NODE, ATTRIBUTE);
        defineSubType(NODE, CDATA_SECTION);
        defineSubType(NODE, COMMENT);
        defineSubType(NODE, DOCUMENT);
        defineSubType(NODE, ELEMENT);
        defineSubType(NODE, NAMESPACE);
        defineSubType(NODE, PROCESSING_INSTRUCTION);
        defineSubType(NODE, TEXT);
    }

    static {
        defineBuiltInType(ITEM, "item()");
        defineBuiltInType(ANY_TYPE, "xs:anyType");
        defineBuiltInType(ANY_SIMPLE_TYPE, "xs:anySimpleType");
        defineBuiltInType(ANY_ATOMIC_TYPE, "xs:anyAtomicType", "xdt:anyAtomicType");                // keep `xdt:anyAtomicType` for backward compatibility
        defineBuiltInType(UNTYPED_ATOMIC, "xs:untypedAtomic", "xdt:untypedAtomic");                 // keep `xdt:untypedAtomic` for backward compatibility
        defineBuiltInType(DATE_TIME, "xs:dateTime");
        defineBuiltInType(DATE_TIME_STAMP, "xs:dateTimeStamp");
        defineBuiltInType(DATE, "xs:date");
        defineBuiltInType(TIME, "xs:time");
        defineBuiltInType(DURATION, "xs:duration");
        defineBuiltInType(YEAR_MONTH_DURATION, "xs:yearMonthDuration", "xdt:yearMonthDuration");    // keep `xdt:yearMonthDuration` for backward compatibility
        defineBuiltInType(DAY_TIME_DURATION, "xs:dayTimeDuration", "xdt:dayTimeDuration");          // keep `xdt:dayTimeDuration` for backward compatibility
        defineBuiltInType(FLOAT, "xs:float");
        defineBuiltInType(DOUBLE, "xs:double");
        defineBuiltInType(DECIMAL, "xs:decimal");
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
        defineBuiltInType(G_YEAR_MONTH, "xs:gYearMonth");
        defineBuiltInType(G_YEAR, "xs:gYear");
        defineBuiltInType(G_MONTH_DAY, "xs:gMonthDay");
        defineBuiltInType(G_DAY, "xs:gDay");
        defineBuiltInType(G_MONTH, "xs:gMonth");
        defineBuiltInType(STRING, "xs:string");
        defineBuiltInType(NORMALIZED_STRING, "xs:normalizedString");
        defineBuiltInType(TOKEN, "xs:token");
        defineBuiltInType(LANGUAGE, "xs:language");
        defineBuiltInType(NMTOKEN, "xs:NMTOKEN");
        defineBuiltInType(NAME, "xs:Name");
        defineBuiltInType(NCNAME, "xs:NCName");
        defineBuiltInType(ID, "xs:ID");
        defineBuiltInType(IDREF, "xs:IDREF");
        defineBuiltInType(ENTITY, "xs:ENTITY");
        defineBuiltInType(BOOLEAN, "xs:boolean");
        defineBuiltInType(BASE64_BINARY, "xs:base64Binary");
        defineBuiltInType(HEX_BINARY, "xs:hexBinary");
        defineBuiltInType(ANY_URI, "xs:anyURI");
        defineBuiltInType(QNAME, "xs:QName");
        defineBuiltInType(NOTATION, "xs:NOTATION");
        defineBuiltInType(NMTOKENS, "xs:NMTOKENS");
        defineBuiltInType(IDREFS, "xs:IDREFS");
        defineBuiltInType(ENTITIES, "xs:ENTITIES");
        defineBuiltInType(NUMERIC, "xs:numeric", "numeric");                                        // keep `numeric` for backward compatibility
        defineBuiltInType(ERROR, "xs:error");
        defineBuiltInType(UNTYPED, "xs:untyped");
        defineBuiltInType(NODE, "node()");
        defineBuiltInType(ATTRIBUTE, "attribute()");
        defineBuiltInType(COMMENT, "comment()");
        defineBuiltInType(DOCUMENT, "document-node()");
        defineBuiltInType(ELEMENT, "element()");
        defineBuiltInType(NAMESPACE, "namespace()");
        defineBuiltInType(PROCESSING_INSTRUCTION, "processing-instruction()");
        defineBuiltInType(TEXT, "text()");
        defineBuiltInType(FUNCTION, "function(*)", "function");
        defineBuiltInType(ARRAY_ITEM, "array(*)", "array");
        defineBuiltInType(MAP_ITEM, "map(*)", "map");                                               // keep `map` for backward compatibility
        defineBuiltInType(CDATA_SECTION, "cdata-section()");
        defineBuiltInType(JAVA_OBJECT, "object");
        defineBuiltInType(EMPTY_SEQUENCE, "empty-sequence()", "empty()");                           // keep `empty()` for backward compatibility

        // reduce any unused space
        typeNames.trim();
        typeCodes.trim();
    }

    static {
        defineUnionType(NUMERIC, new int[]{ DECIMAL, FLOAT, DOUBLE });
        defineUnionType(ERROR, new int[0] );
    }

    // https://www.w3.org/TR/xmlschema-2/#built-in-primitive-datatypes
    static {
        definePrimitiveType(STRING, new int[] {
                NORMALIZED_STRING,
                TOKEN,
                LANGUAGE,
                NMTOKEN,
                NAME,
                NCNAME,
                ID,
                IDREF,
                ENTITY
        });
        definePrimitiveType(BOOLEAN);
        definePrimitiveType(DECIMAL, new int[] {
                INTEGER,
                NON_POSITIVE_INTEGER,
                NEGATIVE_INTEGER,
                LONG,
                INT,
                SHORT,
                BYTE,
                NON_NEGATIVE_INTEGER,
                UNSIGNED_LONG,
                UNSIGNED_INT,
                UNSIGNED_SHORT,
                UNSIGNED_BYTE,
                POSITIVE_INTEGER
        });
        definePrimitiveType(FLOAT);
        definePrimitiveType(DOUBLE);
        definePrimitiveType(DURATION, new int[] {
                YEAR_MONTH_DURATION,
                DAY_TIME_DURATION
        });
        definePrimitiveType(DATE_TIME, new int[] {
                DATE_TIME_STAMP
        });
        definePrimitiveType(TIME);
        definePrimitiveType(DATE);
        definePrimitiveType(G_YEAR_MONTH);
        definePrimitiveType(G_YEAR);
        definePrimitiveType(G_MONTH_DAY);
        definePrimitiveType(G_DAY);
        definePrimitiveType(G_MONTH);
        definePrimitiveType(HEX_BINARY);
        definePrimitiveType(BASE64_BINARY);
        definePrimitiveType(ANY_URI);
        definePrimitiveType(QNAME);
        definePrimitiveType(NOTATION);

        // reduce any unused space
        primitiveTypes.trim();
    }

    /**
     * Define built-in type.
     *
     * @param type the type constant
     * @param name The first name is the default name, any other names are aliases.
     */
    private static void defineBuiltInType(final int type, final String... name) {
        typeNames.put(type, name);
        for (final String n : name) {
            typeCodes.put(n, type);
        }
    }

    /**
     * Define supertype/subtype relation.
     *
     * @param supertype type constant of the super type
     * @param subtype the subtype
     */
    private static void defineSubType(final int supertype, final int subtype) {
        superTypes[subtype] = supertype;
    }

    /**
     * Define a union type.
     *
     * @param unionType the union type
     * @param memberTypes the members of the union type
     */
    private static void defineUnionType(final int unionType, final int... memberTypes) {
        unionTypes.put(unionType, new IntArraySet(memberTypes));
    }

    /**
     * Define a primitive type.
     *
     * @param primitiveType the primitive type
     * @param subTypes the subtypes of the primitive type
     */
    private static void definePrimitiveType(final int primitiveType, final int... subTypes) {
        for (final int subType : subTypes) {
            primitiveTypes.put(subType, primitiveType);
        }

        // primitive type of a primitive type is itself!
        primitiveTypes.put(primitiveType, primitiveType);
    }

    /**
     * Get the internal default name for the built-in type.
     *
     * @param type the type constant
     * @return name of the type
     */
    public static @Nullable String getTypeName(final int type) {
        final String[] names = typeNames.get(type);
        if (names != null) {
            return names[0];
        }
        return null;
    }

    /**
     * Get the internal aliases for the built-in type.
     *
     * @param type the type constant
     * @return one or more alias names
     */
    public static @Nullable String[] getTypeAliases(final int type) {
        final String names[] = typeNames.get(type);
        if (names != null && names.length > 1) {
            final String aliases[] = new String[names.length - 1];
            System.arraycopy(names, 1, aliases, 0, names.length - 1);
            return aliases;
        }
        return null;
    }

    /**
     * Get the type code for a type identified by its internal name.
     *
     * @param name name of the type
     * @return type constant
     * @throws XPathException in case of dynamic error
     */
    public static int getType(final String name) throws XPathException {
        final int code = typeCodes.getInt(name);
        if (code == NO_SUCH_VALUE) {
            throw new XPathException((Expression) null, "Type: " + name + " is not defined");
        }
        return code;
    }

    /**
     * Get the type code for a type identified by its QName.
     *
     * @param qname name of the type
     * @return type constant
     * @throws XPathException in case of dynamic error
     */
    public static int getType(final QName qname) throws XPathException {
        final String uri = qname.getNamespaceURI();
        return switch (uri) {
            case Namespaces.SCHEMA_NS -> getType("xs:" + qname.getLocalPart());
            case Namespaces.XPATH_DATATYPES_NS -> getType("xdt:" + qname.getLocalPart());
            default -> getType(qname.getLocalPart());
        };
    }

    /**
     * Check if the given type code is a subtype of the specified supertype.
     *
     * @param subtype the type constant of the subtype
     * @param supertype type constant of the super type
     *
     * @return true if subtype is a sub type of supertype
     *
     * @throws IllegalArgumentException When the type is invalid
     */
    public static boolean subTypeOf(int subtype, final int supertype) {
        if (subtype == supertype) {
            return true;
        }

        if (supertype == ITEM || supertype == ANY_TYPE) {
            // Note: this will return true even if subtype == EMPTY_SEQUENCE, maybe return subtype != EMPTY_SEQUENCE ?
            return true;
        }

        // Note that EMPTY_SEQUENCE is *not* a sub-type of anything else than itself
        // EmptySequence has to take care of this when it checks its type
        if (subtype == ITEM || subtype == EMPTY_SEQUENCE || subtype == ANY_TYPE || subtype == NODE) {
            return false;
        }

        if (unionTypes.containsKey(supertype)) {
            return subTypeOfUnion(subtype, supertype);
        }
        if (unionTypes.containsKey(subtype)) {
            return unionMembersHaveSuperType(subtype, supertype);
        }

        subtype = superTypes[subtype];
        if (subtype == 0) {
            throw new IllegalArgumentException(
                    "type " + subtype + " is not a valid type");
        }
        return subTypeOf(subtype, supertype);
    }

    /**
     * Get the type code of the supertype of the specified subtype.
     *
     * @param subtype type code of the sub type
     * @return type constant for the super type
     *
     * @throws IllegalArgumentException if {@code subtype} has no defined super type
     */
    public static int getSuperType(final int subtype) {
        if (subtype == ITEM || subtype == NODE) {
            return ITEM;
        } else if (subtype == ANY_TYPE) {
            return subtype;
        }

        if (subtype >= superTypes.length) {
            // Note that EMPTY_SEQUENCE is *not* a sub-type of anything else than itself
            throw new IllegalArgumentException("Type: " + subtype + " has no super types defined");
        }

        final int supertype = superTypes[subtype];
        if (supertype == 0) {
            LOG.warn("eXist-db does not define a super-type for the sub-type {}", getTypeName(subtype), new Throwable());
            return ITEM;
        }

        return supertype;
    }

    /**
     * Find a common supertype for two given type codes.
     *
     * Type.ITEM is returned if no other common supertype
     * is found.
     *
     * @param type1 type constant for the first type
     * @param type2 type constant for the second type
     * @return common super type or {@link Type#ITEM} if none
     */
    public static int getCommonSuperType(final int type1, final int type2) {
        //Super shortcut
        if (type1 == type2) {
            return type1;
        }
        // if one of the types is empty(), return the other type: optimizer is free to choose
        // an optimization based on the more specific type.
        if (type1 == Type.EMPTY_SEQUENCE) {
            return type2;
        } else if (type2 == Type.EMPTY_SEQUENCE) {
            return type1;
        }

        if (unionTypes.containsKey(type1) && subTypeOfUnion(type2, type1)) {
            return type2;
        }
        if (unionTypes.containsKey(type2) && subTypeOfUnion(type1, type2)) {
            return type1;
        }

        //TODO : optimize by swapping the arguments based on their numeric values ?
        //Processing lower value first *should* reduce the size of the Set
        //Collect type1's super-types
        final IntSet t1 = new IntOpenHashSet(11, Hash.VERY_FAST_LOAD_FACTOR);
        //Don't introduce a shortcut (starting at getSuperType(type1) here
        //type2 might be a super-type of type1
        int t;
        for (t = type1; t != ITEM && t != ANY_TYPE; t = getSuperType(t)) {
            //Shortcut
            if (t == type2) {
                return t;
            }
            t1.add(t);
        }
        //Starting from type2's super type : the shortcut should have done its job
        for (t = getSuperType(type2); t != ITEM && t != ANY_TYPE; t = getSuperType(t)) {
            if (t1.contains(t)) {
                return t;
            }
        }
        return t;
    }

    /**
     * Determines if a union type has an other type as a member.
     *
     * @param unionType the union type
     * @param other the type to test for union membership
     *
     * @return true if the type is a member, false otherwise.
     */
    public static boolean hasMember(final int unionType, final int other) {
        final IntArraySet members = unionTypes.get(unionType);
        if (members == null) {
            return false;
        }
        return members.contains(other);
    }

    /**
     * Check if the given type is a subtype of a member of the specified union type.
     *
     * @param subtype the type constant of the subtype
     * @param unionType the union type
     *
     * @return true if subtype is a sub type of a member of the union type
     */
    public static boolean subTypeOfUnion(final int subtype, final int unionType) {
        final IntArraySet members = unionTypes.get(unionType);
        if (members == null) {
            return false;
        }

        // inherited behaviour from {@link #subTypeOf(int, int)}
        // where type is considered a subtype of itself.
        if (subtype == unionType) {
            return true;
        }

        // quick optimisation for: subtype = member
        if (members.contains(subtype)) {
            return true;
        }

        for (final int member : members) {
            if (subTypeOf(subtype, member)) {
                return true;
            }
        }
        return false;
    }

    public static boolean unionMembersHaveSuperType(final int unionType, final int supertype) {
        final IntArraySet members = unionTypes.get(unionType);
        if (members == null || members.size() == 0) {
            return false;
        }

        // inherited behaviour from {@link #subTypeOf(int, int)}
        // where type is considered a subtype of itself.
        if (supertype == unionType) {
            return true;
        }

        for (final int member : members) {
            if (!subTypeOf(member, supertype)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the primitive type for a typed atomic type.
     *
     * @param type the type to retrieve the primitive type of
     *
     * @return the primitive type
     *
     * @throws IllegalArgumentException if {@code type} has no defined primitive type
     */
    public static int primitiveTypeOf(final int type) throws IllegalArgumentException {
        final int primitiveType = primitiveTypes.get(type);
        if (primitiveType == NO_SUCH_VALUE) {
            final String typeName = getTypeName(type);
            throw new IllegalArgumentException("Primitive type is not defined for: " + (typeName != null ? typeName : type));
        }
        return primitiveType;
    }

    /**
     * Get the XDM equivalent type of a DOM Node type (i.e. {@link Node#getNodeType()}).
     *
     * @param domNodeType the DOM node type as defined in {@link Node}.
     *
     * @return the equivalent XDM type.
     *
     * @throws IllegalArgumentException if the provided argument is not a DOM Node Type.
     */
    public static int fromDomNodeType(final short domNodeType) {
        switch (domNodeType) {
            case Node.ELEMENT_NODE:
                return Type.ELEMENT;
            case Node.ATTRIBUTE_NODE:
                return Type.ATTRIBUTE;
            case Node.TEXT_NODE:
                return Type.TEXT;
            case Node.CDATA_SECTION_NODE:
                return Type.CDATA_SECTION;
            case Node.PROCESSING_INSTRUCTION_NODE:
                return Type.PROCESSING_INSTRUCTION;
            case Node.COMMENT_NODE:
                return Type.COMMENT;
            case Node.DOCUMENT_NODE:
                return Type.DOCUMENT;

            // un-mappable Node types, so just return the XDM Node type
            case Node.ENTITY_REFERENCE_NODE:
            case Node.ENTITY_NODE:
            case Node.DOCUMENT_TYPE_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
            case Node.NOTATION_NODE:
                return Type.NODE;

            // unknown
            default:
                throw new IllegalArgumentException("Unknown DOM Node type: " + domNodeType);
        }
    }
}
