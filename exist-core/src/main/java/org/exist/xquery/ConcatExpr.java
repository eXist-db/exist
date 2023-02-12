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

import org.exist.xquery.util.Error;
import org.exist.xquery.value.*;

public class ConcatExpr extends PathExpr {

	public ConcatExpr(XQueryContext context) {
		super(context);
	}

	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if(getContext().getXQueryVersion() < 30){
            throw new XPathException(this, ErrorCodes.EXXQDY0003,
                    "string concatenation operator is not available before XQuery 3.0");
        }
		super.analyze(contextInfo);
	}
	
	@Override
	public void add(PathExpr pathExpr) {
		Expression expr = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, pathExpr,
            new Error(Error.FUNC_PARAM_CARDINALITY));
        if (!Type.subTypeOf(expr.returnsType(), Type.ANY_ATOMIC_TYPE))
            {expr = new Atomize(context, expr);}
		super.add(expr);
	}
	
	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
		
		final StringBuilder concat = new StringBuilder();
		for(final Expression step : steps) {
            final Sequence seq = step.eval(contextSequence, contextItem);
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.FUNCTION))
                    {throw new XPathException(this, ErrorCodes.FOTY0013, "Got a function item as operand in string concatenation");}
                concat.append(item.getStringValue());
            }
		}
		final StringValue result = new StringValue(this, concat.toString());
		
		if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
		
		return result;
	}
	
	@Override
	public int returnsType() {
		return Type.STRING;
	}
	
	@Override
	public Cardinality getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}

	@Override
	public Expression simplify() {
		return this;
	}
}
