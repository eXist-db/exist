
/*
 *  eXist Native XML Database
 *  Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.xpath.functions;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.AtomicToString;
import org.exist.xpath.Atomize;
import org.exist.xpath.Cardinality;
import org.exist.xpath.CastExpression;
import org.exist.xpath.Dependency;
import org.exist.xpath.DynamicCardinalityCheck;
import org.exist.xpath.DynamicTypeCheck;
import org.exist.xpath.Expression;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.UntypedValueCheck;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public abstract class Function extends PathExpr {

	public final static String BUILTIN_FUNCTION_NS =
		"http://www.w3.org/2003/05/xpath-functions";

	private FunctionSignature signature;

	public Function(StaticContext context, FunctionSignature signature) {
		super(context);
		this.signature = signature;
	}

	public Function(StaticContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#returnsType()
	 */
	public int returnsType() {
		return signature.getReturnType().getPrimaryType();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return signature.getReturnType().getCardinality();
	}

	public static Function createFunction(
		StaticContext context,
		String clazzName) {
		try {
			if (clazzName == null)
				throw new RuntimeException("insufficient arguments");
			Class constructorArgs[] = { StaticContext.class };
			Class fclass = Class.forName(clazzName);
			if (fclass == null)
				throw new RuntimeException("class not found");
			Constructor construct = fclass.getConstructor(constructorArgs);
			if (construct == null)
				throw new RuntimeException("constructor not found");
			Object initArgs[] = { context };
			Object obj = construct.newInstance(initArgs);
			if (obj instanceof Function)
				return (Function) obj;
			else
				throw new RuntimeException("function object does not implement interface function");
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			e.printStackTrace();
			throw new RuntimeException("function " + clazzName + " not found");
		}
	}

	public void setArguments(List arguments) throws XPathException {
		SequenceType[] argumentTypes = signature.getArgumentTypes();
		if ((!signature.isOverloaded())
			&& arguments.size() != signature.getArgumentCount())
			throw new XPathException(
				"number of arguments to function "
					+ getName()
					+ " doesn't match function signature (expected "
					+ signature.getArgumentCount()
					+ ", got "
					+ arguments.size()
					+ ')');
		Expression next;
		SequenceType argType = null;
		for (int i = 0; i < arguments.size(); i++) {
			if (argumentTypes != null && i < argumentTypes.length)
				argType = argumentTypes[i];
			next = checkArgument((Expression) arguments.get(i), argType);
			steps.add(next);
		}
	}

	protected Expression checkArgument(Expression expr, SequenceType type)
		throws XPathException {
		if (type == null)
			return expr;
		// check cardinality if expected cardinality is not zero or more
		boolean cardinalityMatches =
			type.getCardinality() == Cardinality.ZERO_OR_MORE;
		if (!cardinalityMatches) {
			cardinalityMatches =
				(expr.getCardinality() | type.getCardinality())
					== type.getCardinality();
			if ((!cardinalityMatches)
				&& expr.getCardinality() == Cardinality.ZERO
				&& (type.getCardinality() & Cardinality.ZERO) == 0)
				throw new XPathException("Function does not allow an empty argument");
		}

		// check return type if both types are not Type.ITEM
		int returnType = expr.returnsType();
		boolean typeMatches = type.getPrimaryType() == Type.ITEM;
		typeMatches = Type.subTypeOf(returnType, type.getPrimaryType());

		if (typeMatches && cardinalityMatches)
			return expr;

		if (context.isBackwardsCompatible()) {
			if (Type.subTypeOf(type.getPrimaryType(), Type.STRING)) {
				if (!Type.subTypeOf(returnType, Type.ATOMIC)) {
					expr = new Atomize(context, expr);
					returnType = Type.ATOMIC;
				}
				expr = new AtomicToString(context, expr);
				returnType = Type.STRING;
			} else if (
				type.getPrimaryType() == Type.NUMBER
					|| Type.subTypeOf(type.getPrimaryType(), Type.DOUBLE)) {
				if (!Type.subTypeOf(returnType, Type.ATOMIC)) {
					expr = new Atomize(context, expr);
					returnType = Type.ATOMIC;
				}
				expr =
					new UntypedValueCheck(context, type.getPrimaryType(), expr);
				returnType = type.getPrimaryType();
			}
		}

		// if the required type is an atomic type, convert the argument to an atomic 
		if (Type.subTypeOf(type.getPrimaryType(), Type.ATOMIC)
			&& (!Type.subTypeOf(returnType, Type.ATOMIC))) {
			expr = new Atomize(context, expr);
			if (!(type.getPrimaryType() == Type.ATOMIC))
				expr =
					new UntypedValueCheck(context, type.getPrimaryType(), expr);
			returnType = expr.returnsType();
		}

		if (!Type.subTypeOf(returnType, type.getPrimaryType())) {
			if (returnType != Type.ITEM)
				throw new XPathException(
					"Supplied argument doesn't match required type: required: "
						+ type.toString()
						+ "; got: "
						+ Type.getTypeName(returnType)
						+ Cardinality.display(expr.getCardinality()));
		}
		if (!typeMatches)
			expr = new DynamicTypeCheck(context, type.getPrimaryType(), expr);
		if (!cardinalityMatches)
			expr =
				new DynamicCardinalityCheck(
					context,
					type.getCardinality(),
					expr);
		return expr;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#preselect(org.exist.dom.DocumentSet, org.exist.xpath.StaticContext)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return super.preselect(in_docs);
	}

	public abstract Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException;

	public Expression getArgument(int pos) {
		return getExpression(pos);
	}

	public int getArgumentCount() {
		return steps.size();
	}

	public QName getName() {
		return signature.getName();
	}

	public FunctionSignature getSignature() {
		return signature;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_ITEM | Dependency.CONTEXT_SET;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(getName());
		buf.append('(');
		for (Iterator i = steps.iterator(); i.hasNext();) {
			Expression e = (Expression) i.next();
			buf.append(e.pprint());
			buf.append(',');
		}
		buf.deleteCharAt(buf.length() - 1);
		buf.append(')');
		return buf.toString();
	}
}
