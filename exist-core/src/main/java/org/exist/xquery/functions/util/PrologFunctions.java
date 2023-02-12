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
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.value.*;

public class PrologFunctions extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(PrologFunctions.class);

	public final static FunctionSignature[] signatures = {
		new FunctionSignature(
			new QName("import-module", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically imports an XQuery module into the current context. The parameters have the same " +
			"meaning as in an 'import module ...' expression in the query prolog.",
			new SequenceType[] {
				new FunctionParameterSequenceType("module-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The namespace URI of the module"),
				new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The prefix to be assigned to the namespace"),
				new FunctionParameterSequenceType("location", Type.ANY_URI, Cardinality.ZERO_OR_MORE, "The location of the module")
			},
			new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE),
				"Use fn:load-module#2 instead!"),
		new FunctionSignature(
			new QName("declare-namespace", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically declares a namespace/prefix mapping for the current context.",
			new SequenceType[] {
				new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The prefix to be assigned to the namespace"),
				new FunctionParameterSequenceType("namespace-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The namespace URI")
			},
			new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
		new FunctionSignature(
			new QName("declare-option", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically declares a serialization option as with 'declare option'.",
			new SequenceType[] {
				new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization option name"),
				new FunctionParameterSequenceType("option", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization option value")
			},
			new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
                new FunctionSignature(
                    new QName("get-option", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Gets the value of a serialization option as set with 'declare option'.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization option name")
                    },
                    new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE))
	};
	
	public PrologFunctions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		if (isCalledAs("declare-namespace")) {
                    declareNamespace(args);
                } else if (isCalledAs("declare-option")) {
                    declareOption(args);
                } else if(isCalledAs("get-option")) {
                    return getOption(args);
                } else{
                    importModule(args);
                }

		return Sequence.EMPTY_SEQUENCE;
	}

	private void declareNamespace(Sequence[] args) throws XPathException {
		context.saveState();
		
		String prefix;
		if (args[0].isEmpty())
			{prefix = "";}
		else
			{prefix = args[0].getStringValue();}
		final String uri = args[1].getStringValue();
		context.declareNamespace(prefix, uri);
	}
	
	private void importModule(Sequence[] args) throws XPathException {
		context.saveState();
		
		final String uri = args[0].getStringValue();
		final String prefix = args[1].getStringValue();
		final Sequence seq = args[2];
		final AnyURIValue[] locationHints = new AnyURIValue[seq.getItemCount()];
		for (int i = 0; i < locationHints.length; i++) {
			locationHints[i] = (AnyURIValue)seq.itemAt(i);
		}

		final Module[] modules = context.importModule(uri, prefix, locationHints);

		context.getRootContext().resolveForwardReferences();

		for (final Module module : modules) {
			if (!module.isInternalModule()) {
				// ensure variable declarations in the imported module are analyzed.
				// unlike when using a normal import statement, this is not done automatically
				((ExternalModule)module).analyzeGlobalVars();
			}
		}

//		context.getRootContext().analyzeAndOptimizeIfModulesChanged((PathExpr) context.getRootExpression());
	}
	
	private void declareOption(Sequence[] args) throws XPathException {
		final String qname = args[0].getStringValue();
		final String options = args[1].getStringValue();
		context.addDynamicOption(qname, options);
	}

	private Sequence getOption(final Sequence[] args) throws XPathException {
		final String qnameString = args[0].getStringValue();
        try {
			final QName qname = QName.parse(context, qnameString, context.getDefaultFunctionNamespace());
			final Option option = context.getOption(qname);

			if (option != null) {
				return new StringValue(this, option.getContents());
			} else {
				return Sequence.EMPTY_SEQUENCE;
			}
		} catch (final QName.IllegalQNameException e) {
			throw new XPathException(this, ErrorCodes.XPST0081, "No namespace defined for prefix " + qnameString);
		}
	}
}
