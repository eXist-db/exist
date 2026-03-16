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
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FunOnFunctions extends BasicFunction {

	public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("function-lookup", Function.BUILTIN_FUNCTION_NS),
            "Returns a reference to the function having a given name and arity, if there is one," +
            " the empty sequence otherwise",
            new SequenceType[] {
                new FunctionParameterSequenceType("name", Type.QNAME, Cardinality.EXACTLY_ONE, "Qualified name of the function"),
                new FunctionParameterSequenceType("arity", Type.INTEGER, Cardinality.EXACTLY_ONE, "The arity (number of arguments) of the function")
            },
            new FunctionReturnSequenceType(Type.FUNCTION, Cardinality.ZERO_OR_ONE, "The function if found, empty sequence otherwise")),
        new FunctionSignature(
            new QName("function-name", Function.BUILTIN_FUNCTION_NS),
            "Returns the name of the function identified by a function item.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function item")
            },
            new FunctionReturnSequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE,
            		"The name of the function or the empty sequence if $function is an anonymous function.")),
		new FunctionSignature(
            new QName("function-arity", Function.BUILTIN_FUNCTION_NS),
            "Returns the arity of the function identified by a function item.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function item")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE,
            		"The arity of the function.")),
        new FunctionSignature(
            new QName("function-annotations", Function.BUILTIN_FUNCTION_NS),
            "Returns the annotations of the function identified by a function item, " +
            "as a sequence of single-entry maps.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function item")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                    "A sequence of single-entry maps, each associating an annotation name (xs:QName) with its value(s).")),
        new FunctionSignature(
            new QName("function-identity", Function.BUILTIN_FUNCTION_NS),
            "Returns a string that uniquely identifies the function item within the current execution scope.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function item")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                    "A string uniquely identifying the function."))
    };
	
	public FunOnFunctions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);
		if (getContext().getXQueryVersion()<30) {
			throw new XPathException(this, ErrorCodes.EXXQDY0003, "Function '" + 
					getSignature().getName() + "' is only supported for xquery version \"3.0\" and later.");
		}
	}
	
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		try {
			if (isCalledAs("function-lookup")) {
				final QName fname = ((QNameValue) args[0].itemAt(0)).getQName();
				final int arity = ((IntegerValue)args[1].itemAt(0)).getInt();
				
			    FunctionCall call;
                try {
                    call = NamedFunctionReference.lookupFunction(this, context, fname, arity);
                } catch (final XPathException e) {
                    if (e.getErrorCode() == ErrorCodes.XPST0017) {
                        // return empty sequence for all "function not found" related errors
                        return Sequence.EMPTY_SEQUENCE;
                    }
                    throw e;
                }
			    return call == null ? Sequence.EMPTY_SEQUENCE : new FunctionReference(this, call);
			} else if (isCalledAs("function-name")) {
				final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
				final QName qname = ref.getSignature().getName();
				if (qname == null || qname == InlineFunction.INLINE_FUNCTION_QNAME)
					{return Sequence.EMPTY_SEQUENCE;}
				else
					{return new QNameValue(this, context, qname);}
			} else if (isCalledAs("function-arity")) {
				final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
				return new IntegerValue(this, ref.getSignature().getArgumentCount());
			} else if (isCalledAs("function-annotations")) {
				return evalFunctionAnnotations(args);
			} else {
				// isCalledAs("function-identity")
				return evalFunctionIdentity(args);
			}
		} catch (final Exception e) {
			if (e instanceof XPathException)
				{throw (XPathException)e;}
			else
				{throw new XPathException(this, ErrorCodes.XPST0017, e.getMessage());}
		}
	}

	private Sequence evalFunctionAnnotations(final Sequence[] args) throws XPathException {
		final FunctionReference ref = (FunctionReference) args[0].itemAt(0);

		// Try to find annotations from various paths through the function reference
		Annotation[] annotations = ref.getSignature().getAnnotations();

		// Fallback: check the underlying UserDefinedFunction's signature
		if ((annotations == null || annotations.length == 0) && ref.getCall() != null) {
			final Function func = ref.getCall().getFunction();
			if (func != null) {
				annotations = func.getSignature().getAnnotations();
			}
		}

		if (annotations == null || annotations.length == 0) {
			return Sequence.EMPTY_SEQUENCE;
		}

		return buildAnnotationMaps(annotations);
	}

	private Sequence buildAnnotationMaps(final Annotation[] annotations) throws XPathException {
		final ValueSequence result = new ValueSequence(annotations.length);
		for (final Annotation annot : annotations) {
			final QName annotName = annot.getName();
			final QNameValue qnameKey = new QNameValue(this, context, annotName);

			// Build the value sequence from annotation literal values
			final LiteralValue[] literals = annot.getValue();
			final Sequence annotValues;
			if (literals == null || literals.length == 0) {
				annotValues = Sequence.EMPTY_SEQUENCE;
			} else {
				final ValueSequence vals = new ValueSequence(literals.length);
				for (final LiteralValue lv : literals) {
					vals.add((AtomicValue) lv.getValue());
				}
				annotValues = vals;
			}

			// Create a single-entry map: { annotName: values }
			final MapType map = new MapType(this, context);
			map.add(qnameKey, annotValues);
			result.add(map);
		}
		return result;
	}

	private Sequence evalFunctionIdentity(final Sequence[] args) throws XPathException {
		final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
		final FunctionSignature sig = ref.getSignature();
		final QName qname = sig.getName();

		final String identity;
		if (qname != null && qname != InlineFunction.INLINE_FUNCTION_QNAME) {
			// Named function: use namespace + local name + arity for stable identity
			identity = "Q{" + (qname.getNamespaceURI() != null ? qname.getNamespaceURI() : "") +
					"}" + qname.getLocalPart() + "#" + sig.getArgumentCount();
		} else {
			// Anonymous function / inline / partial application: use object identity
			identity = "anon#" + System.identityHashCode(ref.getCall());
		}
		return new StringValue(this, identity);
	}

	public static FunctionCall lookupFunction(final Expression parent, final QName qname, final int arity) {
	    // check if the function is from a module 
	    final Module[] modules = parent.getContext().getModules(qname.getNamespaceURI());
	    try {
			UserDefinedFunction func = null;
			if (modules == null || modules.length == 0) {
			    func = parent.getContext().resolveFunction(qname, arity);
			} else {
				for (final Module module : modules) {
					func = ((ExternalModule)module).getFunction(qname, arity, parent.getContext());

					if (func != null) {
						if (module.isInternalModule()) {
							throw new XPathException(parent, ErrorCodes.XPST0017, "Cannot create a reference to an internal Java function");
						}

						break;
					}
				}
			}
			if (func == null) {
				return null;
			}
			final FunctionCall funcCall = new FunctionCall(parent.getContext(), func);
			funcCall.setLocation(parent.getLine(), parent.getColumn());
			return funcCall;
		} catch (final XPathException e) {
			// function not found: return empty sequence
			return null;
		}
	}
}
