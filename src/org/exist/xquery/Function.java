
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
package org.exist.xquery;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Abstract base class for all built-in and user-defined functions.
 * 
 * Built-in functions just extend this class. A new function instance
 * will be created for each function call. Subclasses <b>have</b> to
 * provide a function signature to the constructor.
 * 
 * User-defined functions extend class {@link org.exist.xpath.UserDefinedFunction},
 * which is again a subclass of Function. They will not be called directly, but through a
 * {@link org.exist.xpath.FunctionCall} object, which checks the type and cardinality of
 * all arguments and takes care that the current execution context is saved properly.
 * 
 * @author wolf
 */
public abstract class Function extends PathExpr {

	/** 
	 * XQuery/XPath 2.0 function namespace.
	 */
	public final static String BUILTIN_FUNCTION_NS =
		"http://www.w3.org/2003/05/xpath-functions";
	
	/**
	 * Namespace for the built-in xmldb functions.
	 */
	public final static String XMLDB_FUNCTION_NS =
		"http://exist-db.org/xquery/xmldb";

	/**
	 * Namespace for the built-in utility functions.
	 */
	public final static String UTIL_FUNCTION_NS =
		"http://exist-db.org/xquery/util";
	
	public final static String REQUEST_FUNCTION_NS =
		"http://exist-db.org/xquery/request";
		
	// The signature of the function.	
	protected FunctionSignature mySignature;
	
	// The parent expression from which this function is called.
	private Expression parent;
	
	private XQueryAST astNode = null;
	
	/**
	 * Internal constructor. Subclasses should <b>always</b> call this and
	 * pass the current context and their function signature.
	 * 
	 * @param context
	 * @param signature
	 */
	protected Function(XQueryContext context, FunctionSignature signature) {
		super(context);
		this.mySignature = signature;
	}

	protected Function(XQueryContext context) {
		super(context);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#returnsType()
	 */
	public int returnsType() {
		if(mySignature.getReturnType() == null)
			throw new IllegalArgumentException("Return type for function " + mySignature.getName() +
					" is not defined");
		return mySignature.getReturnType().getPrimaryType();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		if(mySignature.getReturnType() == null)
			throw new IllegalArgumentException("Return type for function " + mySignature.getName() +
					" is not defined");
		return mySignature.getReturnType().getCardinality();
	}
	
	/**
	 * Create a built-in function from the specified class.
	 * 
	 * @param context
	 * @param fclass
	 * @return the created function or null if the class could not be initialized.
	 */
	public static Function createFunction(
		XQueryContext context,
		XQueryAST ast,
		Class fclass) throws XPathException {
		try {
			if (fclass == null)
				throw new XPathException(ast, "class for function is null");
			Class constructorArgs[] = { XQueryContext.class };
			Constructor construct = fclass.getConstructor(constructorArgs);
			if (construct == null)
				throw new XPathException(ast, "constructor not found");
			Object initArgs[] = { context };
			Object obj = construct.newInstance(initArgs);
			if (obj instanceof Function) {
				((Function)obj).setASTNode(ast);
				return (Function) obj;
			} else
				throw new XPathException(ast, "function object does not implement interface function");
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
			throw new XPathException(ast, "function " + fclass.getName() + " not found");
		}
	}

	/**
	 * Set the parent expression of this function, i.e. the
	 * expression from which the function is called.
	 * 
	 * @param parent
	 */
	public void setParent(Expression parent) {
		this.parent = parent;
	}
	
	/**
	 * Returns the expression from which this function
	 * gets called.
	 * 
	 * @return
	 */
	public Expression getParent() {
		return parent;
	}
	
	/**
	 * Set the (static) arguments for this function from a list of expressions.
	 * 
	 * This will also check the type and cardinality of the
	 * passed argument expressions.
	 * 
	 * @param arguments
	 * @throws XPathException
	 */
	public void setArguments(List arguments) throws XPathException {
		SequenceType[] argumentTypes = mySignature.getArgumentTypes();
		if ((!mySignature.isOverloaded())
			&& arguments.size() != mySignature.getArgumentCount())
			throw new XPathException(getASTNode(),
				"number of arguments to function "
					+ getName()
					+ " doesn't match function signature (expected "
					+ mySignature.getArgumentCount()
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

	/**
	 * Statically check an argument against the sequence type specified in
	 * the signature.
	 * 
	 * @param expr
	 * @param type
	 * @return
	 * @throws XPathException
	 */
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
				throw new XPathException(astNode, "Argument " + expr.pprint() + " is empty. An " +
						"empty argument is not allowed here.");
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
		if (Type.subTypeOf(type.getPrimaryType(), Type.ATOMIC)) {
			if(!Type.subTypeOf(returnType, Type.ATOMIC))
				expr = new Atomize(context, expr);
			if (!(type.getPrimaryType() == Type.ATOMIC))
				expr =
					new UntypedValueCheck(context, type.getPrimaryType(), expr);
			returnType = expr.returnsType();
		}

		if (!Type.subTypeOf(returnType, type.getPrimaryType())) {
			if ((!Type.subTypeOf(type.getPrimaryType(), returnType)) && returnType != Type.ITEM)
				throw new XPathException(
					astNode,
					"Supplied argument " + expr.pprint() + " doesn't match required type: required: "
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

	public abstract Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException;

	public Sequence[] getArguments(Sequence contextSequence, Item contextItem) throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		final int argCount = getArgumentCount();
		Sequence[] args = new Sequence[argCount];
		for(int i = 0; i < argCount; i++) {
			args[i] = getArgument(i).eval(contextSequence, contextItem);
		}
		return args;
	}
	
	/**
	 * Get an argument expression by its position in the
	 * argument list.
	 * 
	 * @param pos
	 * @return
	 */
	public Expression getArgument(int pos) {
		return getExpression(pos);
	}

	/**
	 * Get the number of arguments passed to this function.
	 * 
	 * @return
	 */
	public int getArgumentCount() {
		return steps.size();
	}

	/**
	 * Return the name of this function.
	 * 
	 * @return
	 */
	public QName getName() {
		return mySignature.getName();
	}

	/**
	 * Get the signature of this function.
	 * 
	 * @return
	 */
	public FunctionSignature getSignature() {
		return mySignature;
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
	
	public void setASTNode(XQueryAST ast) {
		this.astNode = ast;
	}
	
	public XQueryAST getASTNode() {
		return astNode;
	}
}
