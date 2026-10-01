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
package org.exist.xquery;

import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.util.ExpressionDumper;

/**
 * Abstract base class for simple functions. Subclasses should overwrite
 * method {@link #eval(Sequence[], Sequence)}.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public abstract class BasicFunction extends Function {

    public BasicFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
	public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        final int argCount = getArgumentCount();
        final Sequence[] args = new Sequence[argCount];
        for (int i = 0; i < argCount; i++) {
            try {
                args[i] = getArgument(i).eval(contextSequence, contextItem);
            } catch (final XPathException e) {
                if (e.getErrorCode() == null || e.getErrorCode() == ErrorCodes.ERROR) {
                    e.prependMessage(
                            ErrorCodes.XPTY0004,
                            "checking function parameter " + (i + 1) + " in call " + ExpressionDumper.dump(this) + ": ");
                }

                throw e;
            }
        }

        final Sequence result = eval(args, contextSequence);

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    /**
     * Process the function. All arguments are passed in the array args. The number of
     * arguments, their type and cardinality have already been checked to match
     * the function signature.
     *
     * @param args The arguments given to the function.
     * @param contextSequence The context sequence for the function or null.
     * @throws XPathException An error occurred.
     *
     * @return The result of the XPath function
     */
    public abstract Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException;

    /**
     * Validates that each argument is compatible with the declared parameter type
     * of a function reference. Useful for any function that accepts
     * callback functions (higher-order functions), whether built-in or user-defined.
     *
     * @param caller the calling expression (for error reporting)
     * @param ref    the function reference whose parameter types to check against
     * @param args   the actual arguments to validate
     * @throws XPathException with XPTY0004 if a type mismatch is detected
     */
    protected static void checkFunctionParameterTypes(final Expression caller, final FunctionReference ref, final Sequence[] args) throws XPathException {
        final SequenceType[] paramTypes = ref.getSignature().getArgumentTypes();
        if (paramTypes == null) {
            return;
        }
        for (int i = 0; i < args.length && i < paramTypes.length; i++) {
            final SequenceType expected = paramTypes[i];
            final Sequence arg = args[i];
            // Check cardinality: reject empty sequence if at least one item is required
            if (arg.isEmpty()) {
                if (!Cardinality.allowsZero(expected.getCardinality())) {
                    throw new XPathException(caller, ErrorCodes.XPTY0004,
                            "Invalid cardinality for parameter " + (i + 1) + " of higher-order function call. " +
                                    "Expected " + expected.toString() +
                                    ", got empty sequence");
                }
                continue;
            }
            final int expectedType = expected.getPrimaryType();
            // Skip type check if the declared type is item() — accepts anything
            if (expectedType == Type.ITEM) {
                continue;
            }
            if (!expected.checkType(arg)) {
                throw new XPathException(caller, ErrorCodes.XPTY0004,
                        "Invalid type for parameter " + (i + 1) + " of higher-order function call. " +
                                "Expected " + Type.getTypeName(expectedType) +
                                ", got " + Type.getTypeName(arg.getItemType()));
            }
        }
    }
}
