/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery;

import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Represents a call to a user-defined function 
 * {@see org.exist.xpath.functions.UserDefinedFunction}.
 * 
 * FunctionCall wraps around a user-defined function. It makes sure that all function parameters
 * are checked against the signature of the function. 
 * 
 * @author wolf
 */
public class FunctionCall extends Function {

	private UserDefinedFunction functionDef;
	private Expression expression;
	
	// the name of the function. Used for forward references.
	private QName name = null;
	private List arguments = null;
	
	public FunctionCall(XQueryContext context, QName name, List arguments) {
		super(context);
		this.name = name;
		this.arguments = arguments;
	}
	
	public FunctionCall(XQueryContext context, UserDefinedFunction functionDef) {
		super(context);
		setFunction(functionDef);
	}
	
	public void setFunction(UserDefinedFunction functionDef) {
		this.functionDef = functionDef;
		this.mySignature = functionDef.getSignature();
		this.expression = functionDef;
		SequenceType returnType = functionDef.getSignature().getReturnType();
		// add return type checks
		if(returnType.getCardinality() != Cardinality.ZERO_OR_MORE)
			expression = new DynamicCardinalityCheck(context, returnType.getCardinality(), expression);
		if(Type.subTypeOf(returnType.getPrimaryType(), Type.ATOMIC))
			expression = new Atomize(context, expression);
		if(Type.subTypeOf(returnType.getPrimaryType(), Type.NUMBER))
			expression = new UntypedValueCheck(context, returnType.getPrimaryType(), expression);
		else if(returnType.getPrimaryType() != Type.ITEM)
			expression = new DynamicTypeCheck(context, returnType.getPrimaryType(), expression);
	}
	
	public void resolveForwardReference(UserDefinedFunction functionDef) throws XPathException {
		setFunction(functionDef);
		setArguments(arguments);
		arguments = null;
		name = null;
	} 
	
	public QName getQName() {
		return name;
	}
	
	/** 
	 * Evaluates all arguments, then forwards them to the user-defined function.
	 * 
	 * The return value of the user-defined function will be checked against the
	 * provided function signature.
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence[] seq = new Sequence[getArgumentCount()];
		for(int i = 0; i < getArgumentCount(); i++) {
			seq[i] = getArgument(i).eval(contextSequence, contextItem);
//			System.out.println("found " + seq[i].getLength() + " for " + getArgument(i).pprint());
		}
		functionDef.setArguments(seq);
		
		context.pushLocalContext(true);
		try {
			Sequence returnSeq = expression.eval(contextSequence, contextItem);
			return returnSeq;
		} catch(XPathException e) {
			if(e.getASTNode() == null)
				e.setASTNode(getASTNode());
			throw e;
		} finally {
			context.popLocalContext();
		}
	}

	 /* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#resetState()
	 */
	public void resetState() {
		functionDef.resetState();
	}
}
