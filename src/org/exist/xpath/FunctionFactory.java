/*
 * Util.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.xpath;

import java.util.List;

import org.exist.dom.QName;
import org.exist.xpath.functions.ExtNear;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

public class FunctionFactory {

	/**
	 * Create a function call. 
	 * 
	 * This method handles all calls to built-in or user-defined
	 * functions. It also deals with constructor functions and
	 * optimizes selected function calls. 
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
		XQueryContext context,
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
					step = op;
				}
			}
		// Check if the namespace belongs to one of the schema namespaces.
		// If yes, the function is a constructor function
		} else if(uri.equals(XQueryContext.SCHEMA_NS) || uri.equals(XQueryContext.XPATH_DATATYPES_NS)) {
			if(params.size() != 1)
				throw new XPathException("Wrong number of arguments for constructor function");
			PathExpr arg = (PathExpr)params.get(0);
			int code= Type.getType(qname);
			CastExpression castExpr = new CastExpression(context, arg, code, Cardinality.EXACTLY_ONE);
			step = castExpr;
			
		// Check if the namespace URI starts with "java:". If yes, treat the function call as a call to
		// an arbitrary Java function.
		} else if(uri.startsWith("java:")) {
			JavaCall call = new JavaCall(context, qname);
			call.setArguments(params);
			call.setParent(parent);
			step = call;
		}
		
		// None of the above matched: function is either a builtin function or
		// a user-defined function 
		if (step == null) {
			Module module = context.getModule(uri);
			if(module != null) {
				if(module.isInternalModule()) {
					// for internal modules: create a new function instance from the class
					Class clazz = ((InternalModule)module).getClassForFunction(qname);
					if (clazz == null)
						throw new XPathException("function " + qname.toString() + " ( namespace-uri = " + 
							qname.getNamespaceURI() + ") is not defined");
					Function func = Function.createFunction(context, clazz);
					func.setArguments(params);
					func.setParent(parent);
					step = func;
				} else {
					UserDefinedFunction func = ((ExternalModule)module).getFunction(qname);
					if(func == null)
						throw new XPathException("function " + qname.toString() + " ( namespace-uri = " + 
							qname.getNamespaceURI() + ") is not defined");
					FunctionCall call = new FunctionCall(context, func);
					call.setArguments(params);
					step = call;
				}
			} else {
				UserDefinedFunction func = context.resolveFunction(qname);
				FunctionCall call;
				if(func != null) {
					call = new FunctionCall(context, func);
					call.setArguments(params);
				} else {
					// create a forward reference which will be resolved later
					call = new FunctionCall(context, qname, params);
					context.addForwardReference(call);
				}
				step = call;
			}
		}
		return step;
	}
}
