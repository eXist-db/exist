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

import java.util.ArrayDeque;
import java.util.List;

import org.exist.dom.QName;
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

	public final static QName INLINE_FUNCTION_QNAME = QName.EMPTY_QNAME;
	
	private UserDefinedFunction function;
	private ArrayDeque<FunctionCall> calls = new ArrayDeque<>();
	
    private AnalyzeContextInfo cachedContextInfo;

    public InlineFunction(XQueryContext context, UserDefinedFunction function) {
		super(context);
		this.function = function;
	}

	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {

        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        cachedContextInfo.addFlag(SINGLE_STEP_EXECUTION);
        cachedContextInfo.setParent(this);
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
		final List<ClosureVariable> closureVars = context.getLocalStack();
		
		final FunctionCall call = new FunctionCall(context, function);
		call.getFunction().setClosureVariables(closureVars);
		call.setLocation(function.getLine(), function.getColumn());
		call.analyze(new AnalyzeContextInfo(cachedContextInfo));

		// push the created function call to the stack so we can clear
        // it after execution
		calls.push(call);

		return new FunctionReference(this, call);
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION;
	}

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        calls.clear();
        function.resetState(postOptimization);
    }
}