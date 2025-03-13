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

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class NamedFunctionReference extends AbstractExpression {

	private QName qname;
	private int arity;

	private FunctionCall resolvedFunction = null;
	
	public NamedFunctionReference(XQueryContext context, QName qname, int arity) {
		super(context);
		this.qname = qname;
		this.arity = arity;
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		resolvedFunction = lookupFunction(this, context, qname, arity);
    	contextInfo.addFlag(SINGLE_STEP_EXECUTION);
    	resolvedFunction.analyze(contextInfo);
	}

	public static FunctionCall lookupFunction(Expression self, XQueryContext context, QName funcName, int arity) throws XPathException {
		if (Function.BUILTIN_FUNCTION_NS.equals(funcName.getNamespaceURI())
				&& "concat".equals(funcName.getLocalPart())
				&& arity < 2) {
			throw new XPathException(self, ErrorCodes.XPST0017, "No such function; fn:concat requires at least two arguments");
		}

		final XQueryAST ast = new XQueryAST();
		ast.setLine(self.getLine());
		ast.setColumn(self.getColumn());
		final List<Expression> args = new ArrayList<>(arity);
		for (int i = 0; i < arity; i++) {
			args.add(new Function.Placeholder(context));
		}
		final Expression fun = FunctionFactory.createFunction(context, funcName, ast, null, args, false);
        switch (fun) {
            case null -> throw new XPathException(self, ErrorCodes.XPST0017, "Function not found: " + funcName);
            case FunctionCall functionCall -> {
                if (functionCall.getFunction() == null) {
                    throw new XPathException(self, ErrorCodes.XPST0017, "Function not found: " + funcName);
                }
                // clear line and column as it will be misleading. should be set later to point
                // to the location from where the function is called.
                fun.setLocation(-1, -1);
                return functionCall;
            }
            case Function function -> {
                final InternalFunctionCall funcCall;
                if (fun instanceof InternalFunctionCall) {
                    funcCall = (InternalFunctionCall) fun;
                } else {
                    funcCall = new InternalFunctionCall((Function) fun);
                }
                funcCall.setLocation(-1, -1);
                return FunctionFactory.wrap(context, funcCall);
            }
            case CastExpression castExpression -> {
                final InternalFunctionCall funcCall = new InternalFunctionCall(castExpression.toFunction());
                funcCall.setLocation(-1, -1);
                return FunctionFactory.wrap(context, funcCall);
            }
            default ->
                    throw new XPathException(self, ErrorCodes.XPST0017, "Named function reference should point to a function; found: " +
                            fun.getClass().getName());
        }
    }
	
	@Override
	public void dump(ExpressionDumper dumper) {
		dumper.display(qname);
		dumper.display('#');
		dumper.display(Integer.toString(arity));
	}

	@Override
	public String toString() {
		return qname.toString() + '#' + arity;
	}

	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		return new FunctionReference(this, resolvedFunction);
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION;
	}
	
	@Override
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
    	if (resolvedFunction != null)
    		{resolvedFunction.resetState(postOptimization);}
	}
}
