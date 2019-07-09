/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xquery;

import java.util.ArrayList;
import java.util.List;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import javax.xml.XMLConstants;

public class FunctionFactory {

    public static final String ENABLE_JAVA_BINDING_ATTRIBUTE = "enable-java-binding";
    public static final String PROPERTY_ENABLE_JAVA_BINDING = "xquery.enable-java-binding";
    public static final String DISABLE_DEPRECATED_FUNCTIONS_ATTRIBUTE = "disable-deprecated-functions";
    public static final String PROPERTY_DISABLE_DEPRECATED_FUNCTIONS = "xquery.disable-deprecated-functions";
    public static final boolean DISABLE_DEPRECATED_FUNCTIONS_BY_DEFAULT = false;

    public static Expression createFunction(XQueryContext context, XQueryAST ast, PathExpr parent, List<Expression> params) throws XPathException {
    	QName qname = null;
        try {
            qname = QName.parse(context, ast.getText(), context.getDefaultFunctionNamespace());
        } catch(final QName.IllegalQNameException xpe) {
            throw new XPathException(ErrorCodes.XPST0081, "Invalid qname " +  ast.getText() + ". " + xpe.getMessage());
        }
        return createFunction(context, qname, ast, parent, params);
    }

    public static Expression createFunction(XQueryContext context, QName qname, XQueryAST ast, PathExpr parent, List<Expression> params) throws XPathException {
        return createFunction(context, qname, ast, parent, params, true);
    }

    /**
     * Create a function call.
     *
     * This method handles all calls to built-in or user-defined
     * functions. It also deals with constructor functions and
     * optimizes some function calls like starts-with, ends-with or
     * contains.
     *
     * @param context the XQuery context
     * @param qname the name of the function
     * @param ast the AST node of the function
     * @param parent the parent expression of the function
     * @param params the parameters to the function
     * @param optimizeStrFuncs true if string functions be optimized
     *
     * @return the function expression
     *
     * @throws XPathException if an error occurs creating the function
     */
    public static Expression createFunction(XQueryContext context, QName qname, XQueryAST ast, PathExpr parent, List<Expression> params,
        boolean optimizeStrFuncs) throws XPathException {
        final String local = qname.getLocalPart();
        final String uri = qname.getNamespaceURI();
        Expression step = null;
        if (optimizeStrFuncs && (Namespaces.XPATH_FUNCTIONS_NS.equals(uri) || Namespaces.XSL_NS.equals(uri))) {
            if("starts-with".equals(local)) {
                step = startsWith(context, ast, parent, params);
            } else if("ends-with".equals(local)) {
                step = endsWith(context, ast, parent, params);
            } else if("contains".equals(local)) {
                step = contains(context, ast, parent, params);
            } else if("equals".equals(local)) {
                step = equals(context, ast, parent, params);
            }
        //Check if the namespace belongs to one of the schema namespaces.
        //If yes, the function is a constructor function
        } else if (uri.equals(Namespaces.SCHEMA_NS) ||
                uri.equals(Namespaces.XPATH_DATATYPES_NS)) {
            step = castExpression(context, ast, params, qname);
        //Check if the namespace URI starts with "java:". If yes, treat
        //the function call as a call to an arbitrary Java function.
        } else if (uri.startsWith("java:")) {
            step = javaFunctionBinding(context, ast, params, qname);
        }
        //None of the above matched: function is either a built-in function or
        //a user-defined function
        if (step == null) {
            step = functionCall(context, ast, params, qname);
        }
        return step;
    }

    /**
     * starts-with(node-set, string)
     */
    private static GeneralComparison startsWith(XQueryContext context,
            XQueryAST ast, PathExpr parent, List<Expression> params) throws XPathException {
        if (params.size() < 2) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Function starts-with() requires two or three arguments");
        }
        if (params.size() > 3) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Function starts-with() requires two or three arguments");
        }
        final PathExpr p0 = (PathExpr) params.get(0);
        final PathExpr p1 = (PathExpr) params.get(1);
        if (p1.getLength() == 0) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
                "Second argument of starts-with() is empty");
        }
        final GeneralComparison op = new GeneralComparison(context, p0, p1,
            Comparison.EQ, StringTruncationOperator.RIGHT);
        op.setLocation(ast.getLine(), ast.getColumn());
        //TODO : not sure for parent -pb
        context.getProfiler().message(parent, Profiler.OPTIMIZATIONS,
                "OPTIMIZATION", "Rewritten start-with as a general comparison with a right truncations");
        if (params.size() == 3) {
            op.setCollation((Expression) params.get(2));
        }
        return op;
    }

    /**
     * ends-with(node-set, string)
     */
    private static GeneralComparison endsWith(XQueryContext context, XQueryAST ast,
            PathExpr parent, List<Expression> params) throws XPathException {
        if (params.size() < 2) {
            throw new XPathException(ast.getLine(), ast.getColumn(), 
        		ErrorCodes.XPST0017, "Function ends-with() requires two or three arguments");
        }
        if (params.size() > 3) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Function ends-with() requires two or three arguments");
        }
        final PathExpr p0 = (PathExpr) params.get(0);
        final PathExpr p1 = (PathExpr) params.get(1);
        if (p1.getLength() == 0) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
                "Second argument of ends-with() is empty");
        }
        final GeneralComparison op = new GeneralComparison(context, p0, p1, Comparison.EQ, StringTruncationOperator.LEFT);
        //TODO : not sure for parent -pb
        context.getProfiler().message(parent, Profiler.OPTIMIZATIONS,
            "OPTIMIZATION", "Rewritten ends-with as a general comparison with a left truncations");
        op.setLocation(ast.getLine(), ast.getColumn());
        if(params.size() == 3) {
            op.setCollation((Expression) params.get(2));
        }
        return op;
    }

    /**
     * contains(node-set, string)
     */
    private static GeneralComparison contains(XQueryContext context, XQueryAST ast,
            PathExpr parent, List<Expression> params) throws XPathException {
        if (params.size() < 2) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Function contains() requires two or three arguments");
        }
        if (params.size() > 3) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Function contains() requires two or three arguments");
        }
        final PathExpr p0 = (PathExpr) params.get(0);
        final PathExpr p1 = (PathExpr) params.get(1);
        if (p1.getLength() == 0) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
                "Second argument of contains() is empty");
        }
        final GeneralComparison op = new GeneralComparison(context, p0, p1,
            Comparison.EQ, StringTruncationOperator.BOTH);
        //TODO : not sure for parent -pb
        context.getProfiler().message(parent, Profiler.OPTIMIZATIONS,
            "OPTIMIZATION", "Rewritten contains() as a general comparison with left and right truncations");
        op.setLocation(ast.getLine(), ast.getColumn());
        if (params.size() == 3) {
            op.setCollation((Expression) params.get(2));
        }
        return op;
    }

    /**
     * equals(node-set, string)
     */
    private static GeneralComparison equals(XQueryContext context, XQueryAST ast,
            PathExpr parent, List<Expression> params) throws XPathException {
        if (params.size() < 2) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Function equals() requires two or three arguments");
        }
        if (params.size() > 3) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Function equals() requires two or three arguments");
        }
        final PathExpr p0 = (PathExpr) params.get(0);
        final PathExpr p1 = (PathExpr) params.get(1);
        if (p1.getLength() == 0) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
                "Second argument of equals() is empty");
        }
        final GeneralComparison op = new GeneralComparison(context, p0, p1,
            Comparison.EQ, StringTruncationOperator.EQUALS);
        //TODO : not sure for parent -pb
        context.getProfiler().message(parent, Profiler.OPTIMIZATIONS,
            "OPTIMIZATION", "Rewritten contains() as a general comparison with no truncations");
        op.setLocation(ast.getLine(), ast.getColumn());
        if (params.size() == 3) {
            op.setCollation((Expression) params.get(2));
        } else {
            op.setCollation(new StringValue("?strength=identical"));
        }
        return op;
    }

    private static CastExpression castExpression(XQueryContext context,
            XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        if (params.size() != 1) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Wrong number of arguments for constructor function");
        }
        final Expression arg = params.get(0);
        final int code = Type.getType(qname);
        final CastExpression castExpr = new CastExpression(context, arg, code, Cardinality.ZERO_OR_ONE);
        castExpr.setLocation(ast.getLine(), ast.getColumn());
        return castExpr;
    }

    private static JavaCall javaFunctionBinding(XQueryContext context,
            XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        //Only allow java binding if specified in config file <xquery enable-java-binding="yes">
        final String javabinding = (String) context.getBroker().getConfiguration()
            .getProperty(PROPERTY_ENABLE_JAVA_BINDING);
        if(javabinding == null || !"yes".equals(javabinding)) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
                "Java binding is disabled in the current configuration (see conf.xml)." +
                " Call to " + qname.getStringValue() + " denied.");
        }
        final JavaCall call = new JavaCall(context, qname);
        call.setLocation(ast.getLine(), ast.getColumn());
        call.setArguments(params);
        return call;
    }

    private static Function functionCall(XQueryContext context,
            XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        final Function fn;
        final String uri = qname.getNamespaceURI();
        final Module module = context.getModule(uri);
        if (module != null) {
            //Function belongs to a module
            if (module.isInternalModule()) {
                fn = getInternalModuleFunction(context, ast, params, qname, module);
            } else {
                //Function is from an imported XQuery module
                fn = getXQueryModuleFunction(context, ast, params, qname, module);
            }
        } else {
            fn = getUserDefinedFunction(context, ast, params, qname);
        }
        return fn;
    }

    /**
     * Gets a Java function from an Java XQuery Extension Module
     */
    private static Function getInternalModuleFunction(XQueryContext context,
            XQueryAST ast, List<Expression> params, QName qname, Module module) throws XPathException {
        //For internal modules: create a new function instance from the class
        FunctionDef def = ((InternalModule) module).getFunctionDef(qname, params.size());
        //TODO: rethink: xsl namespace function should search xpath one too
        if (def == null && Namespaces.XSL_NS.equals(qname.getNamespaceURI())) {
            //Search xpath namespace
            Module _module_ = context.getModule(Namespaces.XPATH_FUNCTIONS_NS);
            if(_module_ != null) {
                module = _module_;
                qname = new QName(qname.getLocalPart(), Namespaces.XPATH_FUNCTIONS_NS, qname.getPrefix());
                def = ((InternalModule) module).getFunctionDef(qname, params.size());
            }
        }
        if (def == null) {
            final List<FunctionSignature> funcs = ((InternalModule) module).getFunctionsByName(qname);
            if (funcs.isEmpty()) {
                throw new XPathException(ast.getLine(), ast.getColumn(),
            		ErrorCodes.XPST0017, "Function " + qname.getStringValue() + "() " +
                    " is not defined in module namespace: " + qname.getNamespaceURI());
            } else {
                final StringBuilder buf = new StringBuilder();
                buf.append("Unexpectedly received ");
                buf.append(params.size());
                buf.append(" parameter(s) in call to function ");
                buf.append("'");
                buf.append(qname.getStringValue());
                buf.append("()'. ");
                buf.append("Defined function signatures are:\r\n");
                for (final FunctionSignature sig : funcs) {
                    buf.append(sig.toString()).append("\r\n");
                }
                throw new XPathException(ast.getLine(), ast.getColumn(), ErrorCodes.XPST0017, buf.toString());
            }
        }
        if (((Boolean) context.getBroker().getConfiguration()
                .getProperty(PROPERTY_DISABLE_DEPRECATED_FUNCTIONS)).booleanValue() &&
                def.getSignature().isDeprecated()) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
                "Access to deprecated functions is not allowed. Call to '" + qname.getStringValue() + "()' denied. " + def.getSignature().getDeprecated());
        }
        final Function fn = Function.createFunction(context, ast, def);
        fn.setArguments(params);
        fn.setASTNode(ast);
        return new InternalFunctionCall(fn);
    }

    /**
     * Gets an user defined function from the XQuery
     */
    private static FunctionCall getUserDefinedFunction(XQueryContext context, XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        final FunctionCall fc;
        final UserDefinedFunction func = context.resolveFunction(qname, params.size());
        if (func != null) {
            fc = new FunctionCall(context, func);
            fc.setLocation(ast.getLine(), ast.getColumn());
            fc.setArguments(params);
        } else {
            //Create a forward reference which will be resolved later
            fc = new FunctionCall(context, qname, params);
            fc.setLocation(ast.getLine(), ast.getColumn());
            context.addForwardReference(fc);
        }
        return fc;
    }

    /**
     * Gets an XQuery function from an XQuery Module
     */
    private static FunctionCall getXQueryModuleFunction(XQueryContext context,
            XQueryAST ast, List<Expression> params, QName qname, Module module) throws XPathException {
        final FunctionCall fc;
        final UserDefinedFunction func = ((ExternalModule) module).getFunction(qname, params.size(), context);
        if (func == null) {
            // check if the module has been compiled already
            if (module.isReady()) {
                throw new XPathException(ast.getLine(), ast.getColumn(),
            		ErrorCodes.XPST0017, "Function " + qname.getStringValue() +
                    "() is not defined in namespace '" + qname.getNamespaceURI() + "'");
            // If not, postpone the function resolution
            // Register a forward reference with the root module, so it gets resolved
            // when the main query has been compiled.
            } else {
                fc = new FunctionCall(((ExternalModule) module).getContext(), qname, params);
                fc.setLocation(ast.getLine(), ast.getColumn());
                if(((ExternalModule) module).getContext() == context) {
                    context.addForwardReference(fc);
                } else {
                    context.getRootContext().addForwardReference(fc);
                }
            }
        } else {
            fc = new FunctionCall(context, func);
            fc.setArguments(params);
            fc.setLocation(ast.getLine(), ast.getColumn());
        }
        return fc;
    }
 
    /**
     * Wrap a function call into a user defined function.
     * This is used to handle dynamic function calls or partial
     * function applications on built in functions.
     * 
     * @param context current context
     * @param call the function call to be wrapped
     * @return a new function call referencing an inline function
     * @throws XPathException in case of a static error
     */
    public static FunctionCall wrap(XQueryContext context, Function call) throws XPathException {
		final int argCount = call.getArgumentCount();
		final QName[] variables = new QName[argCount];
		final List<Expression> innerArgs = new ArrayList<>(argCount);
		final List<Expression> wrapperArgs = new ArrayList<>(argCount);
		final FunctionSignature signature = call.getSignature();
		// the parameters of the newly created inline function:
		final List<SequenceType> newParamTypes = new ArrayList<>();
		final SequenceType[] paramTypes = signature.getArgumentTypes();
		for (int i = 0; i < argCount; i++) {
			final Expression param = call.getArgument(i);
			wrapperArgs.add(param);
			QName varName = new QName("vp" + i, XMLConstants.NULL_NS_URI);
			variables[i] = varName;
			final VariableReference ref = new VariableReference(context, varName);
			innerArgs.add(ref);
			
			// copy parameter sequence types
			// overloaded functions like concat may have an arbitrary number of arguments
			if (i < paramTypes.length)
				{newParamTypes.add(paramTypes[i]);}
			else
				// overloaded function: add last sequence type
				{newParamTypes.add(paramTypes[paramTypes.length - 1]);}
		}
		final SequenceType[] newParamArray = newParamTypes.toArray(new SequenceType[newParamTypes.size()]);
		final FunctionSignature newSignature = new FunctionSignature(signature);
        newSignature.setArgumentTypes(newParamArray);

		final UserDefinedFunction func = new UserDefinedFunction(context, newSignature);
		for (final QName varName: variables) {
			func.addVariable(varName);
		}
		
		call.setArguments(innerArgs);
		
		func.setFunctionBody(call);
		
		final FunctionCall wrappedCall = new FunctionCall(context, func);
		wrappedCall.setArguments(wrapperArgs);
		return wrappedCall;
	}
}