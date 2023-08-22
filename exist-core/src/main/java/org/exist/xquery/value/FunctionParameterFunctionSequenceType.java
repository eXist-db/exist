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

import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.FunctionSignature;

/**
 * This class is used to specify the name and description of an XQuery function parameter of type function.
 */
public class FunctionParameterFunctionSequenceType extends FunctionParameterSequenceType {

    private SequenceType[] parameters = null;
    private SequenceType returnType = new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);

    /**
     * mimics isOverloaded property of FunctionSignature
     * @see org.exist.xquery.FunctionSignature
     */
    private int arity = -1;

    /**
     * @param attributeName     The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param primaryType       The <strong>Type</strong> of the parameter.
     * @param parameterTypes    The <strong>parameters</strong> the function(s) must accept.
     * @param returnType        The <strong>Type</strong> the function(s) needs to return.
     * @param cardinality       The <strong>Cardinality</strong> of the parameter.
     * @param description       A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionParameterFunctionSequenceType(final String attributeName, final int primaryType, final SequenceType[] parameterTypes, final SequenceType returnType, final Cardinality cardinality, final String description) {
        // use for Type.MAP_TYPE and Type.ARRAY_TYPE as well
        super(attributeName, primaryType, cardinality, description);
        this.parameters = parameterTypes;
        this.arity = parameterTypes.length;
        this.returnType = returnType;
    }

    /**
     * shorthand if return type is unspecified
     * @param attributeName     The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param primaryType       The <strong>Type</strong> of the parameter.
     * @param parameterTypes    The <strong>Types</strong> of parameters the function needs to accept.
     * @param cardinality       The <strong>Cardinality</strong> of the parameter.
     * @param description       A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionParameterFunctionSequenceType(final String attributeName, final int primaryType, final SequenceType[] parameterTypes, final Cardinality cardinality, final String description) {
        super(attributeName, primaryType, cardinality, description);
        this.parameters = parameterTypes;
        this.arity = parameterTypes.length;
    }

    /**
     * FIXME: is this constructor needed?
     * @param attributeName
     */
    public FunctionParameterFunctionSequenceType(final String attributeName) {
        super(attributeName);
    }

    @Override
    public boolean checkType(Sequence seq) throws XPathException {
        // all functions?
        if (!Type.subTypeOf(seq.getItemType(), getPrimaryType())) {
            throw new XPathException(ErrorCodes.XPTY0004,
                    "Type error: expected type: "
                            + Type.getTypeName(getPrimaryType())
                            + "; got: "
                            + Type.getTypeName(seq.getItemType()));
        }

        // check each ref
        FunctionReference next;
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            next = (FunctionReference) i.nextItem();
            if (!checkType(next)) {
                throw new XPathException(ErrorCodes.XPTY0004,
                        "Type error: expected type: "
                                + Type.getTypeName(getPrimaryType())
                                + "; got: "
                                + Type.getTypeName(seq.getItemType()));
            }
        }
        return true;
    }

    public boolean checkType(final FunctionReference ref) throws XPathException {
        final FunctionSignature sig = ref.getSignature();
        // check arity of referenced function call
        arityMatches(sig);

        // check return type
        returnTypeMatches(sig);

        if (arityUnspecified()) { return true; }

        // check argumentTypes
        final SequenceType[] arguments = sig.getArgumentTypes();
        for (int i = 0; i < arguments.length; i++) {
            final int argumentType = arguments[i].getPrimaryType();
            final int parameterType = parameters[i].getPrimaryType();
            // ITEM is likely unspecified return type - catch error later
            if (argumentType != Type.ITEM && !Type.subTypeOf(argumentType, parameterType)) {
                // throw
                throw new XPathException(ErrorCodes.XPTY0004,
                        "Type error: expected type: " + Type.getTypeName(parameterType)
                                + "; got: " + Type.getTypeName(argumentType));
            }
        }
        return true;
    }

    private void arityMatches (final FunctionSignature sig) throws XPathException {
        if (arityUnspecified()) { return; }
        final int otherArity;

        if (sig.isOverloaded()) {
            // concat#3 will not return the correct argument count but the number of
            // argument types matches the concrete reference with three entries
            otherArity = sig.getArgumentTypes().length;
        } else {
            // expected arity is specified and the reference is not isOverloaded
            otherArity = sig.getArgumentCount();
        }
//        final int otherArity = sig.getArgumentCount();
        if (arity == otherArity) { return; }
        // arity mismatch
        throw new XPathException(ErrorCodes.XPTY0004,
                "Type error: Function does not have expected arity of "
                        + arity + "; got " + otherArity);
    }

    private void returnTypeMatches (final FunctionSignature sig) throws XPathException {
        final int primaryReturnType = returnType.getPrimaryType();
        if (primaryReturnType == Type.ITEM) { return; }
        final int otherPrimaryReturnType = sig.getReturnType().getPrimaryType();
        if (Type.subTypeOf(otherPrimaryReturnType, primaryReturnType)) { return; }
        // ITEM is likely unspecified return type - catch error later
        if (otherPrimaryReturnType == Type.ITEM) { return; }
        // return type mismatch
        throw new XPathException(ErrorCodes.XPTY0004,
                "Type error: unexpected return type: " + Type.getTypeName(primaryReturnType)
                        + "; got: " + Type.getTypeName(otherPrimaryReturnType));
    }

    private boolean arityUnspecified () {
        return arity < 0;
    }
}
