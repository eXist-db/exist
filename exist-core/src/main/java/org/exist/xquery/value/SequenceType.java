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

import io.lacuna.bifurcan.IEntry;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Represents an XQuery SequenceType and provides methods to check
 * sequences and items against this type.
 *
 * @author wolf
 */
public class SequenceType {

    private int primaryType = Type.ITEM;
    private Cardinality cardinality = Cardinality.EXACTLY_ONE;
    private QName nodeName = null;
    private SequenceType[] functionParamTypes = null;
    private SequenceType functionReturnType = null;

    public SequenceType() {
    }

    /**
     * Construct a new SequenceType using the specified
     * primary type and cardinality constants.
     *
     * @param primaryType one of the constants defined in {@link Type}
     * @param cardinality one of the constants defined in {@link Cardinality}
     */
    public SequenceType(final int primaryType, final Cardinality cardinality) {
        this.primaryType = primaryType;
        this.cardinality = cardinality;
    }

    /**
     * Construct a new SequenceType using the specified
     * primary type and cardinality.
     *
     * @param primaryType one of the constants defined in {@link Type}
     * @param cardinality the cardinality integer value
     *                    
     * @deprecated Use {@link #SequenceType(int, Cardinality)}
     */
    @Deprecated
    public SequenceType(final int primaryType, final int cardinality) {
        this.primaryType = primaryType;
        this.cardinality = Cardinality.fromInt(cardinality);
    }

    /**
     * Returns the primary type as one of the
     * constants defined in {@link Type}.
     *
     * @return the primary type as one of the constants defined in {@link Type}
     */
    public int getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(int type) {
        this.primaryType = type;
    }

    /**
     * Returns the expected cardinality. See the constants
     * defined in {@link Cardinality}.
     *
     * @return expected cardinality, one of {@link Cardinality}
     */
    public Cardinality getCardinality() {
        return cardinality;
    }

    public void setCardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
    }

    public QName getNodeName() {
        return nodeName;
    }

    public void setNodeName(QName qname) {
        this.nodeName = qname;
    }

    /**
     * Get the function parameter types for typed function tests.
     * Only set when primaryType is FUNCTION, MAP_ITEM, or ARRAY_ITEM
     * and a specific function signature was given (not function(*)).
     *
     * @return the parameter types, or null if not a typed function test
     */
    public SequenceType[] getFunctionParamTypes() {
        return functionParamTypes;
    }

    public void setFunctionParamTypes(final SequenceType[] paramTypes) {
        this.functionParamTypes = paramTypes;
    }

    /**
     * Get the function return type for typed function tests.
     * Only set when primaryType is FUNCTION, MAP_ITEM, or ARRAY_ITEM
     * and a specific function signature was given (not function(*)).
     *
     * @return the return type, or null if not a typed function test
     */
    public SequenceType getFunctionReturnType() {
        return functionReturnType;
    }

    public void setFunctionReturnType(final SequenceType returnType) {
        this.functionReturnType = returnType;
    }

    /**
     * Check the specified sequence against this SequenceType.
     *
     * @param seq sequence to check
     * @throws XPathException if check fails for one item in the sequence
     * @return true, if all items of the sequence have the same type as or a subtype of primaryType
     */
    public boolean checkType(Sequence seq) throws XPathException {
        // Choice (union) types: every item must match at least one alternative.
        // Node-name constraints, record types, and structurally-typed maps/arrays
        // also require per-item validation rather than a primary-type subtype check
        // (e.g. record(y as xs:decimal) has primaryType RECORD, but a value of type
        // map(*) — a parent of RECORD — would erroneously pass a primaryType check).
        final boolean structural = isRecordType() || (functionParamTypes != null
                && (primaryType == Type.MAP_ITEM || primaryType == Type.ARRAY_ITEM));
        if (isChoiceType() || nodeName != null || structural) {
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                if (!checkType(next)) {
                    return false;
                }
            }
            return true;
        }
        return Type.subTypeOf(seq.getItemType(), primaryType);
    }

    /**
     * Check a single item against this SequenceType.
     *
     * @param item the item to check
     * @return true, if item is a subtype of primaryType
     */
    public boolean checkType(final Item item) {
        int type = item.getType();
        if (type == Type.NODE) {
            final Node realNode = ((NodeValue) item).getNode();
            type = realNode.getNodeType();
        }
        if (!Type.subTypeOf(type, primaryType)) {
            return false;
        }

        // For typed map(K, V) tests against a map item: validate every entry's key and value
        if (primaryType == Type.MAP_ITEM && functionParamTypes != null
                && item instanceof AbstractMapType map) {
            if (!checkMapType(map)) {
                return false;
            }
        // For typed array(T) tests against an array item: validate every member
        } else if (primaryType == Type.ARRAY_ITEM && functionParamTypes != null
                && item instanceof ArrayType array) {
            if (!checkArrayType(array)) {
                return false;
            }
        // For function-type tests against a function item (including the function-shape
        // of map/array items, e.g. map matching function(xs:anyAtomicType) as item()*)
        } else if (Type.subTypeOf(primaryType, Type.FUNCTION) && item instanceof FunctionReference) {
            if (!checkFunctionType((FunctionReference) item)) {
                return false;
            }
        }

        if (nodeName == null) {
            return true;
        }
        //TODO : how to improve performance ?
        final QName realName = getRealName(item);

        if (realName == null) {
            return false;
        }
        if (nodeName.getNamespaceURI() != null &&
                !nodeName.getNamespaceURI().equals(realName.getNamespaceURI())) {
            return false;
        }
        if (nodeName.getLocalPart() != null) {
            return nodeName.getLocalPart().equals(realName.getLocalPart());
        }
        return true;
    }

    /**
     * Check if a function reference matches the required function type.
     * Per the XQuery spec, function types are checked as follows:
     * - The function's arity must match the number of parameter types
     * - The function's return type must be a subtype of the required return type (covariant)
     * - Each required parameter type must be a subtype of the function's parameter type (contravariant)
     *
     * @param funcRef the function reference to check
     * @return true if the function matches the required function type
     */
    private boolean checkFunctionType(final FunctionReference funcRef) {
        // Map and array items are also functions: a map(K, V) is an instance of
        // function(xs:anyAtomicType) as V*; an array(T) is function(xs:integer) as T.
        // We treat them specially so the synthetic accessor signature does not
        // dictate parameter/return shape.
        if (funcRef instanceof AbstractMapType map) {
            return checkMapAsFunction(map);
        }
        if (funcRef instanceof ArrayType array) {
            return checkArrayAsFunction(array);
        }

        final FunctionSignature sig = funcRef.getSignature();

        // Check arity: if we have typed parameter info, check against it
        if (functionParamTypes != null) {
            if (sig.getArgumentCount() != functionParamTypes.length) {
                return false;
            }
        }

        // Check return type: function's return type must be a subtype of required return type (covariant)
        if (functionReturnType != null && sig.getReturnType() != null) {
            if (!isSubtypeOf(sig.getReturnType(), functionReturnType)) {
                return false;
            }
        }

        // Contravariant parameter check: each required param type must be a subtype
        // of the function's actual param type. This catches subsumption mismatches
        // like `function(map(*)) ⊑ function(map(K,V))` (false: map(*) is broader).
        if (functionParamTypes != null) {
            final SequenceType[] actualParams = sig.getArgumentTypes();
            if (actualParams != null) {
                for (int i = 0; i < functionParamTypes.length; i++) {
                    if (!isSubtypeOf(functionParamTypes[i], actualParams[i])) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Structural subtype relation between two sequence types.
     * {@code sub} is a subtype of {@code sup} when every value matching
     * {@code sub} also matches {@code sup}.
     * <p>
     * Handles cardinality, item-type subtyping, and the structural cases
     * for {@code map(K, V)}, {@code array(T)}, and {@code function(...) as ...}.
     */
    private static boolean isSubtypeOf(final SequenceType sub, final SequenceType sup) {
        // Cardinality: every cardinality permitted by sub must also be permitted by sup.
        if (!cardinalitySubsumes(sup.cardinality, sub.cardinality)) {
            return false;
        }
        // EMPTY_SEQUENCE on the sub side has no item type to compare; the cardinality
        // check above is sufficient.
        if (sub.cardinality == Cardinality.EMPTY_SEQUENCE) {
            return true;
        }
        // Item type subsumption (covariant in the simple case)
        if (!Type.subTypeOf(sub.primaryType, sup.primaryType)) {
            return false;
        }
        // Element/attribute kind tests with a name: sup-name=null is wildcard;
        // sup-name set requires sub to have the same name. element(*) is NOT
        // a subtype of element(a).
        if ((sup.primaryType == Type.ELEMENT || sup.primaryType == Type.ATTRIBUTE)
                && sup.nodeName != null) {
            if (sub.nodeName == null) {
                return false;
            }
            if (!sup.nodeName.equals(sub.nodeName)) {
                return false;
            }
        }
        // record(...) ⊑ record(...): structural compatibility on declared fields.
        // Every required field of sup must be present (and non-optional, unless
        // sup permits extension) in sub, with sub's type ⊑ sup's type. sub may
        // not declare extra fields beyond sup's declared set unless sup is
        // extensible.
        if (sup.primaryType == Type.RECORD && sup.recordType != null) {
            if (sub.primaryType != Type.RECORD || sub.recordType == null) {
                return false;
            }
            return isRecordSubtype(sub.recordType, sup.recordType);
        }
        // map(K, V) is a subtype of map(K2, V2) only when K ⊑ K2 AND V ⊑ V2.
        // Records also flow through here (RECORD ⊑ MAP_ITEM): a record without
        // declared field types acts as map(xs:string, item()*).
        if (sup.primaryType == Type.MAP_ITEM && sup.functionParamTypes != null) {
            if (!Type.subTypeOf(sub.primaryType, Type.MAP_ITEM)) {
                return false;
            }
            if (sup.functionParamTypes.length != 2) {
                return false;
            }
            final SequenceType subKey, subVal;
            if (sub.functionParamTypes != null && sub.functionParamTypes.length == 2) {
                subKey = sub.functionParamTypes[0];
                subVal = sub.functionParamTypes[1];
            } else {
                subKey = new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE);
                subVal = new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);
            }
            return isSubtypeOf(subKey, sup.functionParamTypes[0])
                    && isSubtypeOf(subVal, sup.functionParamTypes[1]);
        }
        // array(T) ⊑ array(T2) iff T ⊑ T2; array(*) is not a subtype of typed arrays.
        if (sup.primaryType == Type.ARRAY_ITEM && sup.functionParamTypes != null) {
            if (sub.primaryType != Type.ARRAY_ITEM || sub.functionParamTypes == null) {
                return false;
            }
            if (sub.functionParamTypes.length != 1 || sup.functionParamTypes.length != 1) {
                return false;
            }
            return isSubtypeOf(sub.functionParamTypes[0], sup.functionParamTypes[0]);
        }
        // Typed function: arity matches; return is covariant; params are contravariant.
        // Maps and arrays act as their function-shape: per XQuery 4.0 PR1501/PR2050,
        // a map's key parameter is xs:anyAtomicType (atomic-coercion at lookup) and
        // its return is the value type with cardinality widened to permit empty
        // (lookup miss). An array becomes function(xs:integer) as T where T is the
        // declared element type.
        if (Type.subTypeOf(sup.primaryType, Type.FUNCTION) && sup.functionParamTypes != null) {
            if (!Type.subTypeOf(sub.primaryType, Type.FUNCTION)) {
                return false;
            }
            final SequenceType[] subParams;
            final SequenceType subReturn;
            if (Type.subTypeOf(sub.primaryType, Type.MAP_ITEM)) {
                subParams = new SequenceType[]{
                        new SequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE)};
                final SequenceType valueType;
                if (sub.functionParamTypes != null && sub.functionParamTypes.length == 2) {
                    valueType = sub.functionParamTypes[1];
                } else {
                    valueType = new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);
                }
                final Cardinality returnCard;
                if (valueType.cardinality == Cardinality.EXACTLY_ONE
                        || valueType.cardinality == Cardinality.ZERO_OR_ONE) {
                    returnCard = Cardinality.ZERO_OR_ONE;
                } else {
                    returnCard = Cardinality.ZERO_OR_MORE;
                }
                subReturn = new SequenceType(valueType.primaryType, returnCard);
            } else if (sub.primaryType == Type.ARRAY_ITEM) {
                subParams = new SequenceType[]{
                        new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)};
                if (sub.functionParamTypes != null && sub.functionParamTypes.length == 1) {
                    subReturn = sub.functionParamTypes[0];
                } else {
                    subReturn = new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);
                }
            } else {
                if (sub.functionParamTypes == null) {
                    return false;
                }
                subParams = sub.functionParamTypes;
                subReturn = sub.functionReturnType;
            }
            if (subParams.length != sup.functionParamTypes.length) {
                return false;
            }
            for (int i = 0; i < subParams.length; i++) {
                // Contravariant: sup's param must be ⊑ sub's param
                if (!isSubtypeOf(sup.functionParamTypes[i], subParams[i])) {
                    return false;
                }
            }
            if (sup.functionReturnType != null) {
                if (subReturn == null) {
                    return false;
                }
                // Covariant: sub's return must be ⊑ sup's return
                if (!isSubtypeOf(subReturn, sup.functionReturnType)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * record-vs-record subsumption per XQuery 4.0 Records.
     */
    private static boolean isRecordSubtype(final RecordType subRec, final RecordType supRec) {
        final java.util.List<RecordType.FieldDeclaration> subFields = subRec.getFieldDeclarations();
        final java.util.List<RecordType.FieldDeclaration> supFields = supRec.getFieldDeclarations();
        final java.util.Map<String, RecordType.FieldDeclaration> subByName = new java.util.HashMap<>();
        for (final RecordType.FieldDeclaration f : subFields) {
            subByName.put(f.getName(), f);
        }
        final java.util.Set<String> supNames = new java.util.HashSet<>();
        for (final RecordType.FieldDeclaration supField : supFields) {
            supNames.add(supField.getName());
            final RecordType.FieldDeclaration subField = subByName.get(supField.getName());
            if (subField == null) {
                if (!supField.isOptional()) {
                    return false;
                }
                continue;
            }
            if (!supField.isOptional() && subField.isOptional()) {
                return false;
            }
            if (supField.getType() != null) {
                final SequenceType supType = supField.getType();
                final SequenceType subType = subField.getType() != null
                        ? subField.getType()
                        : new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);
                if (!isSubtypeOf(subType, supType)) {
                    return false;
                }
            }
        }
        if (!supRec.isExtensible()) {
            for (final RecordType.FieldDeclaration f : subFields) {
                if (!supNames.contains(f.getName())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * True when {@code outer} permits every cardinality permitted by {@code inner}
     * (e.g. {@code *} subsumes {@code +}, {@code ?}, and {@code 1}).
     */
    private static boolean cardinalitySubsumes(final Cardinality outer, final Cardinality inner) {
        if (outer == inner) {
            return true;
        }
        // Empty inner is only subsumed by something that allows zero
        if (inner == Cardinality.EMPTY_SEQUENCE) {
            return !outer.atLeastOne();
        }
        if (inner == Cardinality.EXACTLY_ONE) {
            return outer == Cardinality.ZERO_OR_ONE
                    || outer == Cardinality.ONE_OR_MORE
                    || outer == Cardinality.ZERO_OR_MORE;
        }
        if (inner == Cardinality.ZERO_OR_ONE) {
            return outer == Cardinality.ZERO_OR_MORE;
        }
        if (inner == Cardinality.ONE_OR_MORE) {
            return outer == Cardinality.ZERO_OR_MORE;
        }
        return false;
    }

    /**
     * Validate a map item against a typed {@code map(K, V)} test.
     * Every entry's key must satisfy K (an item type with cardinality EXACTLY_ONE
     * by construction in the grammar), and every entry's value must satisfy V.
     */
    private boolean checkMapType(final AbstractMapType map) {
        if (functionParamTypes.length != 2) {
            return false;
        }
        final SequenceType keyType = functionParamTypes[0];
        final SequenceType valueType = functionParamTypes[1];
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            if (!keyType.checkType((Item) entry.key())) {
                return false;
            }
            if (!matchesSequence(valueType, entry.value())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate an array item against a typed {@code array(T)} test.
     * Every member sequence must satisfy T.
     */
    private boolean checkArrayType(final ArrayType array) {
        if (functionParamTypes.length != 1) {
            return false;
        }
        final SequenceType memberType = functionParamTypes[0];
        for (final Sequence member : array.toArray()) {
            if (!matchesSequence(memberType, member)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Treat a map as a function {@code function(xs:anyAtomicType) as V*}. The map
     * matches a function-type test if (a) the required parameter type is a subtype
     * of {@code xs:anyAtomicType} (contravariant), and (b) every value in the map
     * conforms to the required return type (an empty map matches vacuously).
     */
    private boolean checkMapAsFunction(final AbstractMapType map) {
        if (functionParamTypes != null) {
            if (functionParamTypes.length != 1) {
                return false;
            }
            // Required param type must be a subtype of xs:anyAtomicType (contravariant:
            // the function-shape's parameter is xs:anyAtomicType; required must be ⊑ that)
            if (!Type.subTypeOf(functionParamTypes[0].getPrimaryType(), Type.ANY_ATOMIC_TYPE)) {
                return false;
            }
        }
        if (functionReturnType != null) {
            // A map applied to a missing key returns the empty sequence, so the
            // required return cardinality must permit zero results — otherwise the
            // map cannot fulfil the contract for arbitrary inputs (qt3tests #28).
            if (functionReturnType.cardinality.atLeastOne()) {
                return false;
            }
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                if (!matchesSequence(functionReturnType, entry.value())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Treat an array as a function {@code function(xs:integer) as T}. The array
     * matches a function-type test if (a) the required parameter type is a subtype
     * of {@code xs:integer} (contravariant), and (b) every member conforms to the
     * required return type (an empty array matches vacuously).
     */
    private boolean checkArrayAsFunction(final ArrayType array) {
        if (functionParamTypes != null) {
            if (functionParamTypes.length != 1) {
                return false;
            }
            if (!Type.subTypeOf(functionParamTypes[0].getPrimaryType(), Type.INTEGER)) {
                return false;
            }
        }
        if (functionReturnType != null) {
            for (final Sequence member : array.toArray()) {
                if (!matchesSequence(functionReturnType, member)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check whether a value sequence satisfies a sequence type's item type and cardinality.
     */
    private static boolean matchesSequence(final SequenceType type, final Sequence value) {
        try {
            type.checkCardinality(value);
        } catch (final XPathException e) {
            return false;
        }
        // An empty sequence has no item type to validate against; only its
        // cardinality matters (handled above). Item-type tests on empty
        // sequences would otherwise fail because getItemType() returns
        // Type.EMPTY_SEQUENCE, which is not a subtype of any concrete type.
        if (value.isEmpty()) {
            return true;
        }
        try {
            return type.checkType(value);
        } catch (final XPathException e) {
            return false;
        }
    }

    private static QName getRealName(final Item item) {
        final NodeValue nvItem = (NodeValue) item;
        if (item.getType() != Type.DOCUMENT) {
            // get the name of the element/attribute
            return nvItem.getQName();
        }
        // it's a document... we need to get the document element's name
        final Document doc;
        if (nvItem instanceof Document) {
            doc = (Document) nvItem;
        } else {
            doc = nvItem.getOwnerDocument();
        }
        if (doc == null) {
            return null;
        }
        final Element elem = doc.getDocumentElement();
        if (elem == null) {
            return null;
        }
        return new QName(elem.getLocalName(), elem.getNamespaceURI());
    }

    /**
     * Check the given type against the primary type
     * declared in this SequenceType.
     *
     * @param type one of the constants defined in {@link Type}
     * @throws XPathException if subtype check fails
     */
    public void checkType(int type) throws XPathException {
        if (type == Type.EMPTY_SEQUENCE || type == Type.ITEM) {
            return;
        }

        // Although xs:anyURI is not a subtype of xs:string, both types are compatible
        if (type == Type.ANY_URI && primaryType == Type.STRING) {
            return;
        }

        if (!Type.subTypeOf(type, primaryType)) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                    "Type error: expected type: " + Type.getTypeName(primaryType) + "; got: " + Type.getTypeName(type));
        }
    }

    /**
     * Check if the given sequence has the cardinality required
     * by this sequence type.
     *
     * @param seq the sequence to check
     * @throws XPathException if cardinality does not match
     */
    public void checkCardinality(Sequence seq) throws XPathException {
        if (!seq.isEmpty() && cardinality == Cardinality.EMPTY_SEQUENCE) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004, "Empty sequence expected; got " + seq.getItemCount());
        }
        if (seq.isEmpty() && cardinality.atLeastOne()) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004, "Empty sequence is not allowed here");
        } else if (seq.hasMany() && cardinality.atMostOne()) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004, "Sequence with more than one item is not allowed here");
        }
    }

    /**
     * Used to serialize SequenceTypes, when building stack traces, for example.
     *
     * @return The serialized SequenceType
     */
    @Override
    public String toString() {
        if (cardinality == Cardinality.EMPTY_SEQUENCE) {
            return cardinality.toXQueryCardinalityString();
        }

        final String str;
        if (primaryType == Type.DOCUMENT && nodeName != null) {
            str = "document-node(" + nodeName.getStringValue() + ")";
        } else if (primaryType == Type.ELEMENT && nodeName != null) {
            str = "element(" + nodeName.getStringValue() + ")";
        } else if (primaryType == Type.MAP_ITEM) {
            str = functionParamTypes != null && functionParamTypes.length == 2
                    ? "map(" + functionParamTypes[0] + ", " + functionParamTypes[1] + ")"
                    : "map(*)";
        } else if (primaryType == Type.ARRAY_ITEM) {
            str = functionParamTypes != null && functionParamTypes.length == 1
                    ? "array(" + functionParamTypes[0] + ")"
                    : "array(*)";
        } else if (primaryType == Type.FUNCTION) {
            if (functionParamTypes != null && functionReturnType != null) {
                final StringBuilder sb = new StringBuilder("function(");
                for (int i = 0; i < functionParamTypes.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(functionParamTypes[i]);
                }
                sb.append(") as ").append(functionReturnType);
                str = sb.toString();
            } else {
                str = "function(*)";
            }
        } else {
            str = Type.getTypeName(primaryType);
        }

        return str + cardinality.toXQueryCardinalityString();
    }

}
