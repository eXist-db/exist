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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
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
    private SequenceType[] functionParamTypes;
    private SequenceType functionReturnType;

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
    public boolean checkType(final Sequence seq) throws XPathException {
        if (nodeName == null) {
            return Type.subTypeOf(seq.getItemType(), primaryType);
        }

        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            if (!checkType(i.nextItem())) {
                return false;
            }
        }
        return true;
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

        // For typed function() tests, check parameter and return type compatibility.
        // MAP_ITEM and ARRAY_ITEM are subtypes of FUNCTION but use distinct typed-test
        // syntax (map(K,V), array(T)) whose parameter counts do not match the
        // underlying function signature's argument count (a map's accessor signature
        // takes 1 arg, the key; map(K,V) carries 2 type parameters). Restrict the
        // function-arity/return check to plain function() tests so map and array
        // values continue to satisfy their typed tests.
        if (primaryType == Type.FUNCTION && item instanceof FunctionReference reference
                && !checkFunctionType(reference)) {
            return false;
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
        final FunctionSignature sig = funcRef.getSignature();

        // Check arity: if we have typed parameter info, check against it
        if (functionParamTypes != null && sig.getArgumentCount() != functionParamTypes.length) {
            return false;
        }

        // Check return type: function's return type must be a subtype of required return type (covariant)
        if (functionReturnType != null && sig.getReturnType() != null) {
            final int actualReturnType = sig.getReturnType().getPrimaryType();
            final int requiredReturnType = functionReturnType.getPrimaryType();
            if (!Type.subTypeOf(actualReturnType, requiredReturnType)) {
                return false;
            }
        }

        // Check parameter types: required param types must be subtypes of function's param types (contravariant)
        // Note: for now we skip contravariant parameter checking as it requires more infrastructure
        // The return type check alone fixes the majority of subtyping test failures

        return true;
    }

    private static QName getRealName(final Item item) {
        final NodeValue nvItem = (NodeValue) item;
        if (item.getType() != Type.DOCUMENT) {
            // get the name of the element/attribute
            return nvItem.getQName();
        }
        // it's a document... we need to get the document element's name
        final Document doc;
        if (nvItem instanceof Document document) {
            doc = document;
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
            str = "map(*)";
        } else if (primaryType == Type.ARRAY_ITEM) {
            str = "array(*)";
        } else if (primaryType == Type.FUNCTION) {
            str = "function(*)";
        } else {
            str = Type.getTypeName(primaryType);
        }

        return str + cardinality.toXQueryCardinalityString();
    }

}
