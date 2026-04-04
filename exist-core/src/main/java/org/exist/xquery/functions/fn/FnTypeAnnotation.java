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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.AbstractExpression;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.ValueSequence;

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements XQuery 4.0 fn:atomic-type-annotation and fn:node-type-annotation.
 *
 * Returns a schema-type-record (map) with function-valued entries: base-type, primitive-type,
 * matches, constructor. The entire ancestor chain is pre-computed to avoid issues with
 * nested function evaluation contexts.
 */
public class FnTypeAnnotation extends BasicFunction {

    private static final String XS_NS = "http://www.w3.org/2001/XMLSchema";

    /** List types: type → item type mapping */
    private static final Map<Integer, Integer> LIST_TYPES = Map.of(
            Type.NMTOKENS, Type.NMTOKEN,
            Type.IDREFS, Type.IDREF,
            Type.ENTITIES, Type.ENTITY
    );

    /** Union types: type → member types mapping */
    private static final Map<Integer, int[]> UNION_TYPES = Map.of(
            Type.NUMERIC, new int[]{Type.DOUBLE, Type.FLOAT, Type.DECIMAL}
    );

    public static final FunctionSignature FN_ATOMIC_TYPE_ANNOTATION = new FunctionSignature(
            new QName("atomic-type-annotation", Function.BUILTIN_FUNCTION_NS),
            "Returns a record describing the type annotation of an atomic value.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The atomic value to inspect")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "schema-type-record"));

    public static final FunctionSignature FN_NODE_TYPE_ANNOTATION = new FunctionSignature(
            new QName("node-type-annotation", Function.BUILTIN_FUNCTION_NS),
            "Returns a record describing the type annotation of an element or attribute node.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE, "The element or attribute node to inspect")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "schema-type-record"));

    public FnTypeAnnotation(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Item item = args[0].itemAt(0);

        final int type;
        final boolean isSimple;
        if (isCalledAs("atomic-type-annotation")) {
            type = item.getType();
            isSimple = true;
        } else {
            // Non-schema-aware: elements → xs:untyped, attributes → xs:untypedAtomic
            if (item.getType() == Type.ATTRIBUTE) {
                type = Type.UNTYPED_ATOMIC;
                isSimple = true;
            } else {
                type = Type.UNTYPED;
                isSimple = false;
            }
        }

        // Pre-compute the full ancestor chain bottom-up
        return buildRecordChain(type, isSimple);
    }

    /**
     * Build the complete record chain from the given type up to xs:anyType.
     * Each record's base-type function returns the pre-built parent record.
     *
     * Uses the XML Schema type hierarchy (not the XPath item() hierarchy)
     * since type annotations follow the schema derivation chain.
     */
    /**
     * Build a full schema-type-record for a type, used by both
     * fn:atomic-type-annotation and fn:schema-type.
     */
    MapType buildSchemaTypeRecord(final int type, final boolean isSimple) throws XPathException {
        return buildRecordChain(type, isSimple);
    }

    private MapType buildRecordChain(final int type, final boolean isSimple) throws XPathException {
        // Collect ancestor chain using XML Schema hierarchy
        final List<int[]> chain = new ArrayList<>();
        chain.add(new int[]{type, isSimple ? 1 : 0});

        int current = type;
        while (current != Type.ANY_TYPE) {
            final int parent = schemaBaseType(current);
            if (parent == current) {
                break;
            }
            final boolean parentSimple = isSchemaSimpleType(parent);
            chain.add(new int[]{parent, parentSimple ? 1 : 0});
            current = parent;
        }

        // Build records top-down (from anyType to target type)
        // so each record can reference its pre-built parent record
        MapType parentRecord = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            final int t = chain.get(i)[0];
            final boolean simple = chain.get(i)[1] == 1;
            parentRecord = buildSingleRecord(t, simple, parentRecord, type);
        }

        return parentRecord;
    }

    /**
     * Get the XML Schema base type for a given type.
     * Unlike Type.getSuperType which follows the XPath item() hierarchy,
     * this follows the XML Schema derivation chain:
     * anyAtomicType → anySimpleType → anyType (not item()).
     */
    private static int schemaBaseType(final int type) {
        switch (type) {
            case Type.ANY_TYPE:
                return Type.ANY_TYPE; // root
            case Type.UNTYPED:
                return Type.ANY_TYPE;
            case Type.ANY_SIMPLE_TYPE:
                return Type.ANY_TYPE;
            case Type.ANY_ATOMIC_TYPE:
                return Type.ANY_SIMPLE_TYPE; // key fix: not item()
            case Type.UNTYPED_ATOMIC:
                return Type.ANY_ATOMIC_TYPE;
            default:
                // List types (NMTOKENS, IDREFS, ENTITIES) derive from anySimpleType
                if (LIST_TYPES.containsKey(type)) {
                    return Type.ANY_SIMPLE_TYPE;
                }
                // Union types (NUMERIC, ERROR) derive from anySimpleType
                if (UNION_TYPES.containsKey(type) || type == Type.ERROR) {
                    return Type.ANY_SIMPLE_TYPE;
                }
                // For other types, use the normal hierarchy but redirect through schema chain
                final int parent = Type.getSuperType(type);
                // If parent is ITEM, redirect to ANY_ATOMIC_TYPE (for atomic types) or ANY_TYPE
                if (parent == Type.ITEM) {
                    return Type.ANY_ATOMIC_TYPE;
                }
                // NUMERIC subtypes (DOUBLE, FLOAT, DECIMAL) — in schema they derive from
                // anyAtomicType, not from xs:numeric (which is a union, not a base type)
                if (parent == Type.NUMERIC) {
                    return Type.ANY_ATOMIC_TYPE;
                }
                return parent;
        }
    }

    /**
     * Determine if a type is "simple" in the XML Schema sense.
     */
    private static boolean isSchemaSimpleType(final int type) {
        if (type == Type.ANY_TYPE || type == Type.UNTYPED) {
            return false;
        }
        if (type == Type.ANY_SIMPLE_TYPE || type == Type.ANY_ATOMIC_TYPE || type == Type.UNTYPED_ATOMIC) {
            return true;
        }
        // List and union types are simple types
        if (LIST_TYPES.containsKey(type) || UNION_TYPES.containsKey(type) || type == Type.ERROR) {
            return true;
        }
        // Walk up to see if we eventually reach ANY_ATOMIC_TYPE or ANY_SIMPLE_TYPE
        int current = type;
        for (int i = 0; i < 20; i++) { // safety limit
            final int parent = Type.getSuperType(current);
            if (parent == current) break;
            if (parent == Type.ANY_ATOMIC_TYPE || parent == Type.ANY_SIMPLE_TYPE) {
                return true;
            }
            if (parent == Type.ITEM || parent == Type.ANY_TYPE) {
                return false;
            }
            current = parent;
        }
        return false;
    }

    /**
     * Build a single schema-type-record for a given type,
     * with base-type returning the pre-built parent record.
     *
     * @param type         the eXist type constant
     * @param isSimple     whether this is a simple type
     * @param parentRecord pre-built parent record (or null for root)
     * @param leafType     the original leaf type (for primitive-type calculation)
     */
    private MapType buildSingleRecord(final int type, final boolean isSimple,
                                       final MapType parentRecord, final int leafType) throws XPathException {
        final MapType result = new MapType(this, context);

        // name: xs:QName
        final QName typeName = typeToQName(type);
        if (typeName != null) {
            result.add(new StringValue(this, "name"),
                    new QNameValue(this, context, typeName));
        }

        // is-simple: xs:boolean
        result.add(new StringValue(this, "is-simple"), BooleanValue.valueOf(isSimple));

        // variety: depends on the kind of type
        final String variety = determineVariety(type, isSimple);
        if (variety != null) {
            result.add(new StringValue(this, "variety"), new StringValue(this, variety));
        }

        // base-type: function() as schema-type-record?
        final Sequence baseTypeResult = parentRecord != null ? parentRecord : Sequence.EMPTY_SEQUENCE;
        result.add(new StringValue(this, "base-type"), makeConstantFunction("base-type-" + type, 0, baseTypeResult));

        // members: for list and union types, a function returning member type annotations
        if (LIST_TYPES.containsKey(type)) {
            // List type: members returns annotation for the item type
            final int itemType = LIST_TYPES.get(type);
            final MapType itemTypeRecord = buildRecordChain(itemType, true);
            result.add(new StringValue(this, "members"),
                    makeConstantFunction("members-" + type, 0, itemTypeRecord));
        } else if (UNION_TYPES.containsKey(type)) {
            // Union type: members returns annotations for all member types
            final int[] memberTypes = UNION_TYPES.get(type);
            final ValueSequence memberRecords = new ValueSequence(memberTypes.length);
            for (final int memberType : memberTypes) {
                memberRecords.add(buildRecordChain(memberType, true));
            }
            result.add(new StringValue(this, "members"),
                    makeConstantFunction("members-" + type, 0, memberRecords));
        }

        // For atomic types: add primitive-type, matches, constructor
        if (isSimple && isAtomicOrAtomicSubtype(type)) {
            // primitive-type: find the primitive ancestor type and build its record
            final int primitiveType = findPrimitiveType(type);
            if (primitiveType != type) {
                result.add(new StringValue(this, "primitive-type"),
                        makeConstantFunction("primitive-type-" + type, 0, buildPrimitiveRecord(primitiveType)));
            } else {
                // This IS the primitive type — return self
                result.add(new StringValue(this, "primitive-type"),
                        makeConstantFunction("primitive-type-" + type, 0, result));
            }
        }

        // matches: for atomic types and union types that have an atomic() representation
        if (isSimple && (isAtomicOrAtomicSubtype(type) || UNION_TYPES.containsKey(type))) {
            result.add(new StringValue(this, "matches"), makeMatchesFunction(type));
        }

        // constructor: for atomic types (except xs:QName and xs:NOTATION) and list/union types
        if (isSimple && type != Type.ANY_SIMPLE_TYPE && type != Type.ANY_ATOMIC_TYPE) {
            if (type != Type.QNAME && type != Type.NOTATION && type != Type.ERROR) {
                result.add(new StringValue(this, "constructor"), makeConstructorFunction(type));
            }
        }

        return result;
    }

    /**
     * Determine the variety of a type for the schema-type-record.
     * Returns null for xs:anySimpleType (which has no variety per spec).
     */
    private static String determineVariety(final int type, final boolean isSimple) {
        if (type == Type.ANY_SIMPLE_TYPE) {
            return null; // xs:anySimpleType has no variety
        }
        if (LIST_TYPES.containsKey(type)) {
            return "list";
        }
        if (UNION_TYPES.containsKey(type)) {
            return "union";
        }
        if (!isSimple) {
            return "mixed"; // xs:anyType, xs:untyped
        }
        return "atomic";
    }

    /**
     * Check if a type is xs:anyAtomicType or a subtype of it.
     * Excludes list types, union types, and non-simple types.
     */
    private static boolean isAtomicOrAtomicSubtype(final int type) {
        if (type == Type.ANY_ATOMIC_TYPE || type == Type.UNTYPED_ATOMIC) {
            return true;
        }
        if (LIST_TYPES.containsKey(type) || UNION_TYPES.containsKey(type)
                || type == Type.ERROR || type == Type.ANY_SIMPLE_TYPE
                || type == Type.ANY_TYPE || type == Type.UNTYPED) {
            return false;
        }
        // Walk up to check if we reach ANY_ATOMIC_TYPE
        int current = type;
        for (int i = 0; i < 20; i++) {
            final int parent = Type.getSuperType(current);
            if (parent == current) break;
            if (parent == Type.ANY_ATOMIC_TYPE) return true;
            if (parent == Type.ITEM || parent == Type.ANY_TYPE || parent == Type.ANY_SIMPLE_TYPE) return false;
            current = parent;
        }
        return false;
    }

    /**
     * Build a minimal schema-type-record for the primitive type.
     */
    private MapType buildPrimitiveRecord(final int type) throws XPathException {
        // Primitive types have base-type = anyAtomicType
        // Build a simple chain: primitiveType → anyAtomicType → anySimpleType → anyType
        return buildRecordChain(type, true);
    }

    /**
     * Create a zero-arg function that returns a constant sequence.
     */
    private FunctionReference makeConstantFunction(final String name, final int arity, final Sequence value) throws XPathException {
        final QName fnName = new QName(name, XMLConstants.NULL_NS_URI);
        final FunctionSignature sig = new FunctionSignature(fnName, new SequenceType[0],
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result"));
        final UserDefinedFunction func = new UserDefinedFunction(context, sig);
        func.setFunctionBody(new ConstantExpression(context, value));
        final FunctionCall call = new FunctionCall(context, func);
        call.setLocation(getLine(), getColumn());
        return new FunctionReference(this, call);
    }

    private FunctionReference makeMatchesFunction(final int type) throws XPathException {
        final QName fnName = new QName("matches-" + type, XMLConstants.NULL_NS_URI);
        final QName paramName = new QName("value", XMLConstants.NULL_NS_URI);
        final FunctionSignature sig = new FunctionSignature(fnName,
                new SequenceType[] {
                        new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.EXACTLY_ONE, "value to test")
                },
                new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if value matches"));
        final UserDefinedFunction func = new UserDefinedFunction(context, sig);
        func.addVariable(paramName);
        func.setFunctionBody(new MatchesExpression(context, type, paramName));
        final FunctionCall call = new FunctionCall(context, func);
        call.setLocation(getLine(), getColumn());
        return new FunctionReference(this, call);
    }

    private FunctionReference makeConstructorFunction(final int type) throws XPathException {
        final QName fnName = new QName("constructor-" + type, XMLConstants.NULL_NS_URI);
        final QName paramName = new QName("value", XMLConstants.NULL_NS_URI);
        final FunctionSignature sig = new FunctionSignature(fnName,
                new SequenceType[] {
                        new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.EXACTLY_ONE, "value to cast")
                },
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.EXACTLY_ONE, "cast value"));
        final UserDefinedFunction func = new UserDefinedFunction(context, sig);
        func.addVariable(paramName);
        func.setFunctionBody(new ConstructorExpression(context, type, paramName));
        final FunctionCall call = new FunctionCall(context, func);
        call.setLocation(getLine(), getColumn());
        return new FunctionReference(this, call);
    }

    private static QName typeToQName(final int type) {
        final String name = Type.getTypeName(type);
        if (name == null) {
            return null;
        }
        final String local;
        if (name.startsWith("xs:")) {
            local = name.substring(3);
        } else {
            local = name;
        }
        return new QName(local, XS_NS, "xs");
    }

    private static int findPrimitiveType(final int type) {
        if (type == Type.ANY_ATOMIC_TYPE || type == Type.ANY_SIMPLE_TYPE || type == Type.ANY_TYPE) {
            return type;
        }
        int current = type;
        while (true) {
            final int parent = Type.getSuperType(current);
            if (parent == Type.ANY_ATOMIC_TYPE || parent == current) {
                return current;
            }
            current = parent;
        }
    }


    // === Inner expression classes ===

    private static class ConstantExpression extends AbstractExpression {
        private final Sequence value;

        ConstantExpression(final XQueryContext context, final Sequence value) {
            super(context);
            this.value = value;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) {
            return value;
        }

        @Override
        public int returnsType() { return Type.ITEM; }
        @Override
        public void analyze(final org.exist.xquery.AnalyzeContextInfo contextInfo) {}
        @Override
        public void dump(final org.exist.xquery.util.ExpressionDumper dumper) { dumper.display("constant"); }
        @Override
        public String toString() { return "constant"; }
    }

    private static class MatchesExpression extends AbstractExpression {
        private final int targetType;
        private final QName paramName;

        MatchesExpression(final XQueryContext context, final int targetType, final QName paramName) {
            super(context);
            this.targetType = targetType;
            this.paramName = paramName;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence value = context.resolveVariable(paramName).getValue();
            if (value.isEmpty()) {
                return BooleanValue.FALSE;
            }
            final Item item = value.itemAt(0);
            return BooleanValue.valueOf(Type.subTypeOf(item.getType(), targetType));
        }

        @Override
        public int returnsType() { return Type.BOOLEAN; }
        @Override
        public void analyze(final org.exist.xquery.AnalyzeContextInfo contextInfo) {}
        @Override
        public void dump(final org.exist.xquery.util.ExpressionDumper dumper) { dumper.display("matches()"); }
        @Override
        public String toString() { return "matches()"; }
    }

    private static class ConstructorExpression extends AbstractExpression {
        private final int targetType;
        private final QName paramName;

        ConstructorExpression(final XQueryContext context, final int targetType, final QName paramName) {
            super(context);
            this.targetType = targetType;
            this.paramName = paramName;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence value = context.resolveVariable(paramName).getValue();
            if (value.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final Item item = value.itemAt(0);
            return item.convertTo(targetType);
        }

        @Override
        public int returnsType() { return targetType; }
        @Override
        public void analyze(final org.exist.xquery.AnalyzeContextInfo contextInfo) {}
        @Override
        public void dump(final org.exist.xquery.util.ExpressionDumper dumper) { dumper.display("constructor()"); }
        @Override
        public String toString() { return "constructor()"; }
    }
}
