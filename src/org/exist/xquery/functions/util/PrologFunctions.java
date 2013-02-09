/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Team
 *
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.Option;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class PrologFunctions extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(PrologFunctions.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("import-module", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically imports an XQuery module into the current context. The parameters have the same " +
			"meaning as in an 'import module ...' expression in the query prolog.",
			new SequenceType[] {
				new FunctionParameterSequenceType("module-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The namespace URI of the module"),
				new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The prefix to be assigned to the namespace"),
				new FunctionParameterSequenceType("location", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The location of the module")
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
		new FunctionSignature(
			new QName("declare-namespace", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically declares a namespace/prefix mapping for the current context.",
			new SequenceType[] {
				new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.EXACTLY_ONE, "The prefix to be assigned to the namespace"),
				new FunctionParameterSequenceType("namespace-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The namespace URI")
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
		new FunctionSignature(
			new QName("declare-option", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically declares a serialization option as with 'declare option'.",
			new SequenceType[] {
				new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization option name"),
				new FunctionParameterSequenceType("option", Type.STRING, Cardinality.EXACTLY_ONE, "The serialization option value")
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
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
		final String location = args[2].getStringValue();
		final Module module = context.importModule(uri, prefix, location);

		context.getRootContext().resolveForwardReferences();

		if( !module.isInternalModule() ) {
        	((ExternalModule)module).getRootExpression().analyze( new AnalyzeContextInfo() );
        }
		
//		context.getRootContext().analyzeAndOptimizeIfModulesChanged((PathExpr) context.getRootExpression());
	}
	
	private void declareOption(Sequence[] args) throws XPathException {
		final String qname = args[0].getStringValue();
		final String options = args[1].getStringValue();
		context.addDynamicOption(qname, options);
	}

        private Sequence getOption(Sequence[] args) throws XPathException {
            final String qnameString = args[0].getStringValue();
            final QName qname = QName.parse(context, qnameString, context.getDefaultFunctionNamespace());
            final Option option = context.getOption(qname);

            if(option != null) {
                return new StringValue(option.getContents());
            } else {
                return Sequence.EMPTY_SEQUENCE;
            }
        }
}
