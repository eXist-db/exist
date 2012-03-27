/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery;

import java.util.List;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * An XQuery 3.0 inline function expression.
 * 
 * @author wolf
 *
 */
public class InlineFunction extends AbstractExpression {

	private UserDefinedFunction function;
	
	public InlineFunction(XQueryContext context, UserDefinedFunction function) {
		super(context);
		this.function = function;
	}

	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		AnalyzeContextInfo info = new AnalyzeContextInfo(contextInfo);
		info.addFlag(SINGLE_STEP_EXECUTION);
		
		// local variable context is known within inline function:
		List<Variable> closureVars = context.getLocalStack();
		function.setClosureVariables(closureVars);
		
		function.analyze(info);
	}

	@Override
	public void dump(ExpressionDumper dumper) {
		dumper.display("function");
		function.dump(dumper);
	}

	/**
	 * Wraps a function call around the function and returns a
	 * reference to it. Make sure local variables in the context
	 * are visible.
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		// local variable context is known within inline function
		List<Variable> closureVars = context.getLocalStack();
		function.setClosureVariables(closureVars);
		
		FunctionCall call = new FunctionCall(context, function);
		function.setCaller(call);
		return new FunctionReference(call);
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION_REFERENCE;
	}

}
