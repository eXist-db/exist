/*
 * Util.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.xpath;

import java.util.List;

import org.exist.dom.QName;
import org.exist.xpath.functions.ExtNear;
import org.exist.xpath.functions.UserDefinedFunction;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

public class FunctionFactory {

	/**
	 * Create a function call. This method handles special functions like 
	 * document(), collection() or near(). It also optimizes some function 
	 * calls.
	 * 
	 * @param pool
	 * @param context
	 * @param parent
	 * @param fnName
	 * @param params
	 * @return
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	public static Expression createFunction(
		StaticContext context,
		PathExpr parent,
		String fnName,
		List params)
		throws XPathException {
		QName qname = QName.parseFunction(context, fnName);
		String local = qname.getLocalName();
		String uri = qname.getNamespaceURI();
		Expression step = null;
		if(uri.equals(Function.BUILTIN_FUNCTION_NS)) {
			// near(node-set, string)
			if (local.equals("near")) {
				if (params.size() < 2)
					throw new IllegalArgumentException("Function near requires two arguments");
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new IllegalArgumentException("Second argument to near is empty");
				Expression e1 = p1.getExpression(0);
				ExtNear near = new ExtNear(context);
				near.addTerm(e1);
				near.setPath((PathExpr) params.get(0));
				if (params.size() > 2) {
					p1 = (PathExpr) params.get(2);
					if (p1.getLength() == 0)
						throw new IllegalArgumentException("Distance argument to near is empty");
					near.setDistance(p1);
				}
				step = near;
				parent.addPath(near);
			}
	
			// ends-with(node-set, string)
			if (local.equals("starts-with")) {
				if (params.size() < 2)
					throw new IllegalArgumentException("Function starts-with requires two arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new IllegalArgumentException("Second argument to starts-with is empty");
				Expression e1 = p1.getExpression(0);
				if (e1 instanceof LiteralValue && p0.returnsType() == Type.NODE) {
					LiteralValue l = (LiteralValue) e1;
					AtomicValue v =
						new StringValue(l.getValue().getStringValue() + '%');
					l.setValue(v);
					GeneralComparison op =
						new GeneralComparison(context, p0, e1, Constants.EQ);
					parent.addPath(op);
					step = op;
				}
			}
	
			// ends-with(node-set, string)
			if (local.equals("ends-with")) {
				if (params.size() < 2)
					throw new IllegalArgumentException("Function ends-with requires two arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new IllegalArgumentException("Second argument to ends-with is empty");
				Expression e1 = p1.getExpression(0);
				if (e1 instanceof LiteralValue && p0.returnsType() == Type.NODE) {
					LiteralValue l = (LiteralValue) e1;
					AtomicValue v =
						new StringValue('%' + l.getValue().getStringValue());
					l.setValue(v);
					GeneralComparison op =
						new GeneralComparison(context, p0, e1, Constants.EQ);
					parent.addPath(op);
					step = op;
				}
			}
	
			// contains(node-set, string)
			if (local.equals("contains")) {
				if (params.size() < 2)
					throw new IllegalArgumentException("Function contains requires two arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new IllegalArgumentException("Second argument to contains is empty");
				Expression e1 = p1.getExpression(0);
				if (e1 instanceof LiteralValue && p0.returnsType() == Type.NODE) {
					LiteralValue l = (LiteralValue) e1;
					AtomicValue v =
						new StringValue('%' + l.getValue().getStringValue() + '%');
					l.setValue(v);
					GeneralComparison op =
						new GeneralComparison(context, p0, e1, Constants.EQ);
					parent.addPath(op);
					step = op;
				}
			}
		}
		// None of the above matched: function is either a builtin function or
		// a user-defined function 
		if (step == null) {
			if(uri.equals(Function.BUILTIN_FUNCTION_NS) || uri.equals(Function.UTIL_FUNCTION_NS) |
				uri.equals(Function.XMLDB_FUNCTION_NS)) {
				Class clazz = context.getClassForFunction(qname);
				if (clazz == null)
					throw new XPathException("function " + qname.toString() + " ( namespace-uri = " + 
						qname.getNamespaceURI() + ") is not defined");
				Function func = Function.createFunction(context, clazz);
				func.setArguments(params);
				func.setParent(parent);
				parent.addPath(func);
				step = func;
			} else {
				UserDefinedFunction func = context.resolveFunction(qname);
				FunctionCall call = new FunctionCall(context, func);
				call.setArguments(params);
				parent.addPath(call);
				step = call;
			}
		}
		return step;
	}
}
