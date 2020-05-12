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

import javax.annotation.Nullable;

/**
 * Defines all built-in types and their relations.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Type {

    public static final int NODE = 7;
    public final static int ELEMENT = 1;
    public final static int ATTRIBUTE = 2;
    public final static int TEXT = 3;
    public final static int PROCESSING_INSTRUCTION = 4;
    public final static int COMMENT = 5;
    public final static int DOCUMENT = 6;
    public final static int NAMESPACE = 500;
    public final static int CDATA_SECTION = 501;
    public final static int EMPTY_SEQUENCE = 10;
    public final static int ITEM = 11;
    public final static int ANY_TYPE = 12;
    public final static int ANY_SIMPLE_TYPE = 13;
    public final static int UNTYPED = 14;
    public final static int ANY_ATOMIC_TYPE = 20;
    public final static int UNTYPED_ATOMIC = 21;
    public final static int STRING = 22;
    public final static int BOOLEAN = 23;
    public final static int QNAME = 24;
    public final static int ANY_URI = 25;
    public final static int BASE64_BINARY = 26;
    public final static int HEX_BINARY = 27;
    public final static int NOTATION = 28;
    public final static int NUMERIC = 30;
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
    public final static int G_YEAR = 56;
    public final static int G_MONTH = 57;
    public final static int G_DAY = 58;
    public final static int G_YEAR_MONTH = 59;
    public final static int G_MONTH_DAY = 71;
    public final static int DATE_TIME_STAMP = 72;
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
    public final static int FUNCTION = 101;
    public final static int MAP_ITEM = 102;
    public final static int ARRAY_ITEM = 103;
    private final static Logger LOG = LogManager.getLogger(Type.class);

    private static int NO_SUCH_VALUE = -99;

    private final static int[] superTypes = new int[512];
    private final static Int2ObjectOpenHashMap<String[]> typeNames = new Int2ObjectOpenHashMap<>(64, Hash.FAST_LOAD_FACTOR);
    private final static Object2IntOpenHashMap<String> typeCodes = new Object2IntOpenHashMap<>(100, Hash.FAST_LOAD_FACTOR);
    static {
        typeCodes.defaultReturnValue(NO_SUCH_VALUE);
    }
    private final static Int2ObjectMap<IntArraySet> unionTypes = new Int2ObjectArrayMap<>(1);
    private final static Int2IntOpenHashMap primitiveTypes = new Int2IntOpenHashMap(44, Hash.FAST_LOAD_FACTOR);
    static {
        primitiveTypes.defaultReturnValue(NO_SUCH_VALUE);
    }

    static {
        // ANY types
        defineSubType(ANY_TYPE, ANY_SIMPLE_TYPE);
        defineSubType(ANY_TYPE, UNTYPED);

        // ANY_SIMPLE types
        defineSubType(ANY_SIMPLE_TYPE, ANY_ATOMIC_TYPE);
        defineSubType(ANY_SIMPLE_TYPE, NUMERIC);

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
        defineSubType(NODE, CDATA_SECTION);  // TODO(AR) this doesn't appear in the XDM 3.1
        defineSubType(NODE, COMMENT);
        defineSubType(NODE, DOCUMENT);
        defineSubType(NODE, ELEMENT);
        defineSubType(NODE, NAMESPACE);
        defineSubType(NODE, PROCESSING_INSTRUCTION);
        defineSubType(NODE, TEXT);
    }

    static {
        defineBuiltInType(NODE, "node()");
        defineBuiltInType(ITEM, "item()");
        defineBuiltInType(EMPTY_SEQUENCE, "empty-sequence()", "empty()");                                // keep `empty()` for backward compatibility

        defineBuiltInType(ELEMENT, "element()");
        defineBuiltInType(DOCUMENT, "document-node()");
        defineBuiltInType(ATTRIBUTE, "attribute()");
        defineBuiltInType(TEXT, "text()");
        defineBuiltInType(PROCESSING_INSTRUCTION, "processing-instruction()");
        defineBuiltInType(COMMENT, "comment()");
        defineBuiltInType(NAMESPACE, "namespace()");
        defineBuiltInType(CDATA_SECTION, "cdata-section()");

        defineBuiltInType(JAVA_OBJECT, "object");
        defineBuiltInType(FUNCTION, "function(*)", "function");
        defineBuiltInType(MAP_ITEM, "map(*)", "map");                                                // keep `map` for backward compatibility
        defineBuiltInType(ARRAY_ITEM, "array(*)","array");
        defineBuiltInType(NUMERIC, "xs:numeric", "numeric");                                     // keep `numeric` for backward compatibility

        defineBuiltInType(ANY_TYPE, "xs:anyType");
        defineBuiltInType(ANY_SIMPLE_TYPE, "xs:anySimpleType");
        defineBuiltInType(UNTYPED, "xs:untyped");

        defineBuiltInType(ANY_ATOMIC_TYPE, "xs:anyAtomicType", "xdt:anyAtomicType");                     // keep `xdt:anyAtomicType` for backward compatibility

        defineBuiltInType(UNTYPED_ATOMIC, "xs:untypedAtomic", "xdt:untypedAtomic");             // keep `xdt:untypedAtomic` for backward compatibility

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
        defineBuiltInType(NOTATION, "xs:NOTATION");

        defineBuiltInType(DATE_TIME_STAMP, "xs:dateTimeStamp");
        defineBuiltInType(DATE_TIME, "xs:dateTime");
        defineBuiltInType(DATE, "xs:date");
        defineBuiltInType(TIME, "xs:time");
        defineBuiltInType(DURATION, "xs:duration");
        defineBuiltInType(G_YEAR, "xs:gYear");
        defineBuiltInType(G_MONTH, "xs:gMonth");
        defineBuiltInType(G_DAY, "xs:gDay");
        defineBuiltInType(G_YEAR_MONTH, "xs:gYearMonth");
        defineBuiltInType(G_MONTH_DAY, "xs:gMonthDay");

        defineBuiltInType(YEAR_MONTH_DURATION, "xs:yearMonthDuration", "xdt:yearMonthDuration");    // keep `xdt:yearMonthDuration` for backward compatibility
        defineBuiltInType(DAY_TIME_DURATION, "xs:dayTimeDuration", "xdt:dayTimeDuration");          // keep `xdt:dayTimeDuration` for backward compatibility

        defineBuiltInType(NORMALIZED_STRING, "xs:normalizedString");
        defineBuiltInType(TOKEN, "xs:token");
        defineBuiltInType(LANGUAGE, "xs:language");
        defineBuiltInType(NMTOKEN, "xs:NMTOKEN");
        defineBuiltInType(NAME, "xs:Name");
        defineBuiltInType(NCNAME, "xs:NCName");
        defineBuiltInType(ID, "xs:ID");
        defineBuiltInType(IDREF, "xs:IDREF");
        defineBuiltInType(ENTITY, "xs:ENTITY");

        // reduce any unused space
        typeNames.trim();
        typeCodes.trim();
    }

    static {
        defineUnionType(NUMERIC, new int[]{ DECIMAL, FLOAT, DOUBLE });
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
     */
    public static int getSuperType(final int subtype) {
        if (subtype == ITEM || subtype == NODE) {
            return ITEM;
        } else if (subtype == ANY_TYPE) {
            return subtype;
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

        //TODO : optimize by swapping the arguments based on their numeric values ?
        //Processing lower value first *should* reduce the size of the Set
        //Collect type1's super-types
        final IntSet t1 = new IntOpenHashSet(Hash.DEFAULT_INITIAL_SIZE, Hash.VERY_FAST_LOAD_FACTOR);
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
}
