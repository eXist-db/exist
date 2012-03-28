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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * A function item which references a partially applied function, for which a number
 * of fixed arguments were already specified but some arguments are still to be provided
 * at call time.
 * 
 * @author wolf
 *
 */
public class PartialFunctionReference extends FunctionReference {

	Sequence[] argValues;
	List<Expression> unfixedArgs = null;
	
	public PartialFunctionReference(FunctionCall fcall, Sequence[] arguments) {
		super(fcall);
		this.argValues = arguments;
	}

	/**
	 * Calls {@link FunctionCall#eval(Sequence)} after making sure all parameters (fixed and dynamic) are
	 * merged into one argument list.
	 */
	@Override
	public Sequence eval(Sequence contextSequence) throws XPathException {
		Sequence[] arguments = new Sequence[argValues.length];
		Iterator<Expression> iter = unfixedArgs.iterator();
		for (int i = 0; i < argValues.length; i++) {
			if (argValues[i] == null) {
				if (!iter.hasNext())
					throw new XPathException(functionCall, ErrorCodes.XPTY0004, "Wrong number of arguments to partially applied " +
							"function.");
				Expression nextArg = iter.next();
				arguments[i] = nextArg.eval(contextSequence);
			} else
				arguments[i] = argValues[i];
		}
		if (iter.hasNext())
			throw new XPathException(functionCall, ErrorCodes.XPTY0004, "Wrong number of arguments to partially applied " +
					"function.");
		return functionCall.evalFunction(contextSequence, null, arguments);
	}
	
	@Override
	public Sequence evalFunction(Sequence contextSequence, Item contextItem,
			Sequence[] seq) throws XPathException {
		// merge argument sequence with fixed arguments
		Sequence[] arguments = new Sequence[argValues.length];
		int j = 0;
		for (int i = 0; i < arguments.length; i++) {
			if (argValues[i] == null) {
				if (j == seq.length)
					throw new XPathException(functionCall, ErrorCodes.XPTY0004, "Wrong number of arguments to partially applied " +
							"function.");
				arguments[i] = seq[j++];
			} else {
				arguments[i] = argValues[i];
			}
		}
		return super.evalFunction(contextSequence, contextItem, arguments);
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if (unfixedArgs != null) {
			// merge arguments before calling analyze on the function
			List<Expression> updatedArgs = new ArrayList<Expression>(functionCall.getArgumentCount());
			int j = 0;
			for (int i = 0; i < functionCall.getArgumentCount(); i++) {
				Expression arg = functionCall.getArgument(i);
				if (arg instanceof Function.Placeholder) {
					if (j == unfixedArgs.size())
						throw new XPathException(functionCall, ErrorCodes.XPTY0004, "Wrong number of arguments to partially applied " +
							"function.");
					arg = unfixedArgs.get(j++);
				}
				updatedArgs.add(arg);
			}
			functionCall.setArguments(updatedArgs);
		}
		functionCall.analyze(contextInfo);
	}
	
	@Override
	public void setArguments(List<Expression> arguments) throws XPathException {
		unfixedArgs = arguments;
	}
}
