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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.Module;
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.FunOnFunctions;
import org.exist.xquery.value.*;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Returns a sequence containing the QNames of all built-in functions
 * currently registered in the query engine.
 *
 * @author wolf
 */
public class BuiltinFunctions extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(BuiltinFunctions.class);

	public final static FunctionSignature[] signatures = {
			new FunctionSignature(
					new QName("registered-functions", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
					"Returns a sequence containing the QNames of all functions " +
							"declared in the module identified by the specified namespace URI. " +
							"An error is raised if no module is found for the specified URI.",
					new SequenceType[] { new FunctionParameterSequenceType("namespace-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The namespace URI of the function module") },
					new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the sequence of function names")),
			new FunctionSignature(
					new QName("registered-functions", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
					"Returns a sequence containing the QNames of all functions " +
							"currently known to the system, including functions in imported and built-in modules.",
					null,
					new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the sequence of function names")),
			new FunctionSignature(
					new QName("declared-variables", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
					"Returns a sequence containing the QNames of all variables " +
							"declared in the module identified by the specified namespace URI. " +
							"An error is raised if no module is found for the specified URI.",
					new SequenceType[] { new FunctionParameterSequenceType("namespace-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The namespace URI of the function module") },
					new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE, "the sequence of function names")),
			new FunctionSignature(
					new QName("list-functions", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
					"Returns a sequence of function items for each function in the current module.",
					null,
					new FunctionReturnSequenceType(Type.FUNCTION, Cardinality.ZERO_OR_MORE, "sequence of function references"),
					"Use inspect:module-functions#0 instead."),
			new FunctionSignature(
					new QName("list-functions", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
					"Returns a sequence of function items for each function in the specified module.",
					new SequenceType[] { new FunctionParameterSequenceType("namespace-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The namespace URI of the function module") },
					new FunctionReturnSequenceType(Type.FUNCTION, Cardinality.ZERO_OR_MORE, "sequence of function references"),
					"Use inspect:module-functions-by-uri#1 instead.")
	};

	public BuiltinFunctions(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence)
			throws XPathException {

		final ValueSequence resultSeq = new ValueSequence();
		if (getArgumentCount() == 1) {
			final String uri = args[0].getStringValue();

			// Get 'internal' modules
			Module[] modules = context.getModules(uri);

			// If not found, try to load Java module
			if (isEmpty(modules) && context.getRepository().isPresent()) {
				modules = new Module[] { context.getRepository().get().resolveJavaModule(uri, context) };
			}

			// There is no module after all
			if (isEmpty(modules)) {
				throw new XPathException(this, "No module found matching namespace URI: " + uri);
			}

			if (isCalledAs("declared-variables")) {
				addVariablesFromModules(resultSeq, modules);

			} else if (isCalledAs("list-functions")) {
				addFunctionRefsFromModules(resultSeq, modules);

			} else {
				addFunctionsFromModules(resultSeq, modules);
			}
		} else {
			if (isCalledAs("list-functions")) {
				addFunctionRefsFromContext(resultSeq);

			} else {
				// registered-functions
				final Iterable<Module> iterableModules = () -> context.getModules();
				final Module[] modules = StreamSupport.stream(iterableModules.spliterator(), false).toArray(Module[]::new);
				addFunctionsFromModules(resultSeq, modules);

				// Add all functions declared in the local module
				for (final Iterator<UserDefinedFunction> i = context.localFunctions(); i.hasNext(); ) {
					final UserDefinedFunction func = i.next();
					final FunctionSignature sig = func.getSignature();
					resultSeq.add(new QNameValue(this, context, sig.getName()));
				}
			}
		}
		return resultSeq;
	}

	private void addFunctionsFromModules(final ValueSequence resultSeq, final Module[] modules) {
		final Set<QName> set = new TreeSet<>();
		for (final Module module : modules) {
			final FunctionSignature[] signatures = module.listFunctions();
			// add to set to remove duplicate QName's
			for (final FunctionSignature signature : signatures) {
				final QName qname = signature.getName();
				set.add(qname);
			}
			for (final QName qname : set) {
				resultSeq.add(new QNameValue(this, context, qname));
			}
		}
	}

	private void addFunctionRefsFromModules(final ValueSequence resultSeq, final Module[] modules) throws XPathException {
		for (final Module module : modules) {
			final FunctionSignature[] signatures = module.listFunctions();
			for (final FunctionSignature signature : signatures) {
				final FunctionCall call = FunOnFunctions.lookupFunction(this, signature.getName(), signature.getArgumentCount());
				if (call != null) {
					resultSeq.add(new FunctionReference(this, call));
				}
			}
		}
	}

	private void addFunctionRefsFromContext(final ValueSequence resultSeq) throws XPathException {
		for (final Iterator<UserDefinedFunction> i = context.localFunctions(); i.hasNext(); ) {
			final UserDefinedFunction f = i.next();
			final FunctionCall call =
					FunOnFunctions.lookupFunction(this, f.getSignature().getName(), f.getSignature().getArgumentCount());
			if (call != null) {
				resultSeq.add(new FunctionReference(this, call));
			}
		}
	}

	private void addVariablesFromModules(final ValueSequence resultSeq, final Module[] modules) {
		for (final Module module : modules) {
			for (final Iterator<QName> i = module.getGlobalVariables(); i.hasNext(); ) {
				resultSeq.add(new QNameValue(this, context, i.next()));
			}
		}
	}
}
