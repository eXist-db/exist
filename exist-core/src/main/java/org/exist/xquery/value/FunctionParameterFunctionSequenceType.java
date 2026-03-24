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

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * This class is used to specify the name and description of an XQuery function parameter of type function.
 */
public class FunctionParameterFunctionSequenceType extends FunctionParameterSequenceType {

    private final int arity;
    private final SequenceType[] parameters;
    private final SequenceType returnType;

    /**
     * shorthand for single, non-optional function parameters
     * @param attributeName     The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param parameterTypes    The <strong>Types</strong> of parameters the function needs to accept.
     * @param description       A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionParameterFunctionSequenceType(final String attributeName, final FunctionParameterSequenceType[] parameterTypes, final FunctionReturnSequenceType returnType, final String description) {
        this(attributeName, parameterTypes, returnType, Cardinality.EXACTLY_ONE, description);
    }

    /**
     * shorthand for functions
     * @param attributeName     The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param parameterTypes    The <strong>Types</strong> of parameters the function needs to accept.
     * @param cardinality       The <strong>Cardinality</strong> of the parameter.
     * @param description       A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionParameterFunctionSequenceType(final String attributeName, final FunctionParameterSequenceType[] parameterTypes, final FunctionReturnSequenceType returnType, final Cardinality cardinality, final String description) {
        this(attributeName, Type.FUNCTION, parameterTypes, returnType, cardinality, description);
    }

    /**
     * Constructor can be used for Type.MAP_TYPE and Type.ARRAY_TYPE as well
     *
     * @param attributeName     The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param primaryType       The <strong>Type</strong> of the parameter.
     * @param parameterTypes    The <strong>parameters</strong> the function(s) must accept.
     * @param returnType        The <strong>Type</strong> the function(s) needs to return.
     * @param cardinality       The <strong>Cardinality</strong> of the parameter.
     * @param description       A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionParameterFunctionSequenceType(final String attributeName, final int primaryType, final FunctionParameterSequenceType[] parameterTypes, final SequenceType returnType, final Cardinality cardinality, final String description) {
        super(attributeName, primaryType, cardinality, description);
        this.parameters = parameterTypes;
        this.arity = parameterTypes.length;
        this.returnType = returnType;
    }

    /**
     * Legacy constructor accepting SequenceType[] for backward compatibility with old-style signatures.
     *
     * @param attributeName     The name of the parameter in the <strong>FunctionSignature</strong>.
     * @param primaryType       The <strong>Type</strong> of the parameter.
     * @param parameterTypes    The <strong>Types</strong> of parameters the function needs to accept.
     * @param returnType        The <strong>Type</strong> the function(s) needs to return.
     * @param cardinality       The <strong>Cardinality</strong> of the parameter.
     * @param description       A description of the parameter in the <strong>FunctionSignature</strong>.
     * @see org.exist.xquery.FunctionSignature @see Type @see org.exist.xquery.Cardinality
     */
    public FunctionParameterFunctionSequenceType(final String attributeName, final int primaryType, final SequenceType[] parameterTypes, final SequenceType returnType, final Cardinality cardinality, final String description) {
        super(attributeName, primaryType, cardinality, description);
        this.parameters = parameterTypes;
        this.arity = parameterTypes.length;
        this.returnType = returnType;
    }

    /**
     * Legacy shorthand if return type is unspecified.
     *
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
        this.returnType = new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE);
    }

    @Override
    public boolean checkType(final Sequence seq) throws XPathException {
        // all functions?
        if (!Type.subTypeOf(seq.getItemType(), getPrimaryType())) {
            throw new XPathException(ErrorCodes.XPTY0004,
                    "Type error: expected type: "
                            + Type.getTypeName(getPrimaryType())
                            + "; got: "
                            + Type.getTypeName(seq.getItemType()));
        }

        // check each ref
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final FunctionReference next = (FunctionReference) i.nextItem();
            checkType(next);
        }
        return true;
    }

    public boolean checkType(final FunctionReference ref) throws XPathException {
        final FunctionSignature sig = ref.getSignature();

        // check return type
        returnTypeMatches(sig);
        // check arity of referenced function call
        arityMatches(sig);
        // check argumentTypes
        parameterTypesMatch(sig);

        return true;
    }

    private void returnTypeMatches (final FunctionSignature sig) throws XPathException {
        final int primaryReturnType = returnType.getPrimaryType();
        if (primaryReturnType == Type.ITEM) {
            return;
        }
        final int otherPrimaryReturnType = sig.getReturnType().getPrimaryType();
        if (Type.subTypeOf(otherPrimaryReturnType, primaryReturnType)) {
            return;
        }
        // ITEM is likely unspecified return type - catch error later
        if (otherPrimaryReturnType == Type.ITEM) {
            return;
        }
        // return type mismatch
        throw new XPathException(ErrorCodes.XPTY0004,
                "Type error: unexpected return type: " + Type.getTypeName(primaryReturnType)
                        + "; got: " + Type.getTypeName(otherPrimaryReturnType));
    }

    private void arityMatches (final FunctionSignature sig) throws XPathException {
        final int otherArity;

        if (sig.isVariadic()) {
            // variadic functions do return -1 as their argument count
            // but a function reference will return the exact number of
            // argument types that has to match
            // Example: a reference to concat#3 will have an argumentTypes length of 3
            otherArity = sig.getArgumentTypes().length;
        } else {
            // all non-variadic functions
            otherArity = sig.getArgumentCount();
        }

        if (arity == otherArity) {
            return;
        }
        // arity mismatch
        throw new XPathException(ErrorCodes.XPTY0004,
                "Type error: Function does not have expected arity of "
                        + arity + "; got " + otherArity);
    }

    private void parameterTypesMatch(FunctionSignature sig) throws XPathException {
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
    }

    @Override
    public String toString() {
        final int T = getPrimaryType();
        return  Type.getTypeName(T) + "(" +
                    Arrays.stream(parameters)
                            .map(SequenceType::toString)
                            .collect(Collectors.joining(",")) +
                ")" + (T == Type.FUNCTION ?  " as " + returnType.toString() : "");
    }
}
