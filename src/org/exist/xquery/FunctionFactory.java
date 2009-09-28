/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import java.util.Iterator;
import java.util.List;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.functions.ExtNear;
import org.exist.xquery.functions.ExtPhrase;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Type;

public class FunctionFactory {

	public static final String ENABLE_JAVA_BINDING_ATTRIBUTE = "enable-java-binding";
	public static final String PROPERTY_ENABLE_JAVA_BINDING = "xquery.enable-java-binding";
	public static final String DISABLE_DEPRECATED_FUNCTIONS_ATTRIBUTE = "disable-deprecated-functions";
	public static final String PROPERTY_DISABLE_DEPRECATED_FUNCTIONS = "xquery.disable-deprecated-functions";	
	public static final boolean DISABLE_DEPRECATED_FUNCTIONS_BY_DEFAULT = false;
	
	/**
	 * Create a function call. 
	 * 
	 * This method handles all calls to built-in or user-defined
	 * functions. It also deals with constructor functions and
	 * optimizes some function calls like starts-with, ends-with or
	 * contains. 
	 */
	public static Expression createFunction(XQueryContext context, XQueryAST ast, PathExpr parent, List params)
		throws XPathException {
		QName qname = null;
		try {
			qname = QName.parse(context, ast.getText(), context.getDefaultFunctionNamespace());
		} catch(XPathException e) {
            e.setLocation(ast.getLine(), ast.getColumn());
			throw e;
		}
		String local = qname.getLocalName();
		String uri = qname.getNamespaceURI();
		Expression step = null;
		if(uri.equals(Function.BUILTIN_FUNCTION_NS)) {
			//TODO : move to text:near()
			// near(node-set, string)
			if (local.equals("near")) {
				if (params.size() < 2)
					throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017: Function near() requires two arguments");
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast.getLine(), ast.getColumn(), "Second argument to near is empty");
				Expression e1 = p1.getExpression(0);
				ExtNear near = new ExtNear(context);
                near.setLocation(ast.getLine(), ast.getColumn());
				near.addTerm(e1);
				near.setPath((PathExpr) params.get(0));

				if (params.size() > 2) {
				    p1 = (PathExpr) params.get(2);
				    if (p1.getLength() == 0) {
					throw new XPathException(ast.getLine(), ast.getColumn(), "Max distance argument to near is empty");
				    }
				    near.setMaxDistance(p1);
				    
				    if (params.size() == 4) {
					p1 = (PathExpr) params.get(3);
					if (p1.getLength() == 0) {
					    throw new XPathException(ast.getLine(), ast.getColumn(), "Min distance argument to near is empty");
					}
					near.setMinDistance(p1);
				    }
				}
				
				step = near;
			}
	
			// phrase(node-set, string)
            if (local.equals("phrase")) {
                if (params.size() < 2)
                    throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017: Function phrase() requires two arguments");
                PathExpr p1 = (PathExpr) params.get(1);
                if (p1.getLength() == 0)
                    throw new XPathException(ast.getLine(), ast.getColumn(), "Second argument to phrase is empty");
                Expression e1 = p1.getExpression(0);
                ExtPhrase phrase = new ExtPhrase(context);
                phrase.setLocation(ast.getLine(), ast.getColumn());
                phrase.addTerm(e1);
                phrase.setPath((PathExpr) params.get(0));                                          
                step = phrase;
            }
			
			// starts-with(node-set, string)
			if (local.equals("starts-with")) {
				if (params.size() < 2)
					throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017: Function starts-with() requires two or three arguments");
				if (params.size() > 3)
					throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017: Function starts-with() requires two or three arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast.getLine(), ast.getColumn(), "Second argument to starts-with is empty");
				GeneralComparison op = 
					new GeneralComparison(context, p0, p1, Constants.EQ, Constants.TRUNC_RIGHT);
                op.setLocation(ast.getLine(), ast.getColumn());
				//TODO : not sure for parent -pb
	            context.getProfiler().message(parent, Profiler.OPTIMIZATIONS, "OPTIMIZATION",  
	            "Rewritten start-with as a general comparison with a right truncature");				
				if (params.size() == 3)
					op.setCollation((Expression)params.get(2));
				step = op;
			}
	
			// ends-with(node-set, string)
			if (local.equals("ends-with")) {
				if (params.size() < 2)
					throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017 : Function ends-with() requires two or three arguments");
				if (params.size() > 3)
					throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017 : Function ends-with() requires two or three arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast.getLine(), ast.getColumn(), "Second argument to ends-with is empty");
				GeneralComparison op =
					new GeneralComparison(context, p0, p1, Constants.EQ, Constants.TRUNC_LEFT);
				//TODO : not sure for parent -pb
	            context.getProfiler().message(parent, Profiler.OPTIMIZATIONS, "OPTIMIZATION",  
	            "Rewritten ends-with as a general comparison with a left truncature");
                op.setLocation(ast.getLine(), ast.getColumn());
				if (params.size() == 3)
					op.setCollation((Expression)params.get(2));
				step = op;
			}
	
			// contains(node-set, string)
			if (local.equals("contains")) {
				if (params.size() < 2)
					throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017: Function contains() requires two or three arguments");
				if (params.size() > 3)
					throw new XPathException(ast.getLine(), ast.getColumn(), "XPST0017: Function contains() requires two or three arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast.getLine(), ast.getColumn(), "Second argument to contains is empty");
				GeneralComparison op =
					new GeneralComparison(context, p0, p1, Constants.EQ, Constants.TRUNC_BOTH);
				//TODO : not sure for parent -pb
	            context.getProfiler().message(parent, Profiler.OPTIMIZATIONS, "OPTIMIZATION",  
	            "Rewritten contains as a general comparison with left and right truncatures");
                op.setLocation(ast.getLine(), ast.getColumn());
				if (params.size() == 3)
					op.setCollation((Expression)params.get(2));
				step = op;
			}
		// Check if the namespace belongs to one of the schema namespaces.
		// If yes, the function is a constructor function
		} else if(uri.equals(Namespaces.SCHEMA_NS) || uri.equals(Namespaces.XPATH_DATATYPES_NS)) {
			if(params.size() != 1)
				throw new XPathException(ast.getLine(), ast.getColumn(), "Wrong number of arguments for constructor function");
			PathExpr arg = (PathExpr)params.get(0);
			int code= Type.getType(qname);
			CastExpression castExpr = new CastExpression(context, arg, code, Cardinality.ZERO_OR_ONE);
            castExpr.setLocation(ast.getLine(), ast.getColumn());
			step = castExpr;
			
		// Check if the namespace URI starts with "java:". If yes, treat the function call as a call to
		// an arbitrary Java function.
		} else if(uri.startsWith("java:")) {
			
			//Only allow java binding if specified in config file <xquery enable-java-binding="yes">
			String javabinding = (String)context.broker.getConfiguration().getProperty(PROPERTY_ENABLE_JAVA_BINDING);
			if(javabinding != null)
			{
				if(javabinding.equals("yes"))
				{
					JavaCall call = new JavaCall(context, qname);
                    call.setLocation(ast.getLine(), ast.getColumn());
					call.setArguments(params);
					step = call;
				}
				else
				{
					throw new XPathException(ast.getLine(), ast.getColumn(), "Java binding is disabled in the current configuration (see conf.xml). Call to " + qname.getStringValue() + " denied.");
				}
			}
			else
			{
				throw new XPathException(ast.getLine(), ast.getColumn(), "Java binding is disabled in the current configuration (see conf.xml). Call to " + qname.getStringValue() + " denied.");
			}
		}
		
		// None of the above matched: function is either a builtin function or
		// a user-defined function 
		if (step == null) {
			Module module = context.getModule(uri);
			if(module != null) {
                // Function belongs to a module
				if(module.isInternalModule()) {
					// for internal modules: create a new function instance from the class
					FunctionDef def = ((InternalModule)module).getFunctionDef(qname, params.size());
					if (def == null) {
						List funcs = ((InternalModule)module).getFunctionsByName(qname);
						if (funcs.size() == 0)
							throw new XPathException(ast.getLine(), ast.getColumn(), "Function " + qname.getStringValue() + "() " +
								" is not defined in module namespace: " + qname.getNamespaceURI());
						else {
							StringBuilder buf = new StringBuilder();
                            buf.append("Unexpectedly received ");
                            buf.append(params.size() + " parameter(s) in call to function ");
                            buf.append("'" + qname.getStringValue() +  "()'. ");
                            buf.append("Defined function signatures are:\r\n");
							for (Iterator i = funcs.iterator(); i.hasNext(); ) {
								FunctionSignature sig = (FunctionSignature) i.next();
								buf.append(sig.toString()).append("\r\n");
							}
							throw new XPathException(ast.getLine(), ast.getColumn(), buf.toString());
						}
					}
					if (((Boolean)context.broker.getConfiguration().getProperty(PROPERTY_DISABLE_DEPRECATED_FUNCTIONS)).booleanValue()
							&& def.getSignature().isDeprecated()) {
						throw new XPathException(ast.getLine(), ast.getColumn(), "Access to deprecated functions is not allowed. Call to '"
                                + qname.getStringValue() + "()' denied. " + def.getSignature().getDeprecated());
					}
					Function func = Function.createFunction(context, ast, def );
					func.setArguments(params);
                    func.setASTNode(ast);
					step = new InternalFunctionCall(func);
				} else {
                    // function is from an imported XQuery module
					UserDefinedFunction func = ((ExternalModule)module).getFunction(qname, params.size());
					if(func == null)
						throw new XPathException(ast.getLine(), ast.getColumn(), "Function " + qname.getStringValue() +
                                "() is not defined in namespace '" + qname.getNamespaceURI() + "'");
					FunctionCall call = new FunctionCall(context, func);
					call.setArguments(params);
                    call.setLocation(ast.getLine(), ast.getColumn());
					step = call;
				}
			} else {
				UserDefinedFunction func = context.resolveFunction(qname, params.size());
				FunctionCall call;
				if(func != null) {
					call = new FunctionCall(context, func);
                    call.setLocation(ast.getLine(), ast.getColumn());
					call.setArguments(params);
				} else {
					// create a forward reference which will be resolved later
					call = new FunctionCall(context, qname, params);
                    call.setLocation(ast.getLine(), ast.getColumn());
					context.addForwardReference(call);
				}
				step = call;
			}
		}
		return step;
	}
}
