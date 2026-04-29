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

import java.util.ArrayList;
import java.util.List;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.source.Source;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;

import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

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
            throw new XPathException(ast, ErrorCodes.XPST0081, "Invalid qname " +  ast.getText() + ". " + xpe.getMessage());
        }
        // XQ4 (PR2200): unprefixed function calls prefer no-namespace
        // user-declared functions over the default function namespace (fn:).
        // If a same-name user fn is already declared in no-namespace, switch.
        // Otherwise, when no fn: built-in matches the call (forward reference
        // territory), still switch to no-namespace so a later user declaration
        // can resolve via the forward-reference path.
        if (context.getXQueryVersion() >= 40
                && !ast.getText().contains(":")
                && Namespaces.XPATH_FUNCTIONS_NS.equals(qname.getNamespaceURI())) {
            final QName noNsName = new QName(ast.getText(), "");
            final UserDefinedFunction noNsFunc = context.resolveFunction(noNsName, params.size());
            if (noNsFunc != null) {
                qname = noNsName;
            } else if (!hasInternalOrUserFnFunction(context, qname, params.size())) {
                qname = noNsName;
            }
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
        final Expression arg;
        if (params.size() == 1) {
            arg = params.getFirst();
        } else if (params.isEmpty() && context.getXQueryVersion() >= 31) {
            // XQ4 focus constructor: xs:type() uses context item as argument
            arg = new ContextItemExpression(context);
            ((ContextItemExpression) arg).setLocation(ast.getLine(), ast.getColumn());
        } else {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Wrong number of arguments for constructor function");
        }
        final int code;
        try {
            code = Type.getType(qname);
        } catch (final XPathException e) {
            // Unknown type name in xs: namespace → XPST0017 (no such function)
            throw new XPathException(ast.getLine(), ast.getColumn(),
                ErrorCodes.XPST0017, "Unknown constructor function: " + qname.getStringValue());
        }
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

    private static Function functionCall(final XQueryContext context,
            final XQueryAST ast, final List<Expression> params, final QName qname) throws XPathException {
        Function fn = null;
        final String uri = qname.getNamespaceURI();
        final Module[] modules = context.getModules(uri);
        if (modules != null) {
            // Function might belongs to a module
            for (int i = 0; i < modules.length; i++) {
                final Module module = modules[i];
                final boolean throwOnNotFound = i == modules.length - 1;
                if (module.isInternalModule()) {
                    // Function is from an Internal Module
                    fn = getInternalModuleFunction(context, ast, params, qname, module, throwOnNotFound);
                } else {
                    // Function is from an imported XQuery module
                    fn = getXQueryModuleFunction(context, ast, params, qname, module, throwOnNotFound);
                }

                if (fn != null) {
                    break;
                }
            }
        }

        if (fn == null) {
            // Function is a user-defined XQuery function in the same module as the caller
            fn = getUserDefinedFunction(context, ast, params, qname);
        }

        return fn;
    }

    /**
     * Gets a Java function from an Java XQuery Extension Module
     *
     * @param throwOnNotFound true to throw an XPST0017 if the functions is not found, false to just return null
     */
    private static @Nullable Function getInternalModuleFunction(final XQueryContext context,
            final XQueryAST ast, final List<Expression> params, QName qname, Module module,
            final boolean throwOnNotFound) throws XPathException {
        //For internal modules: create a new function instance from the class
        final boolean hasKeywordArgs = hasKeywordArguments(params);
        FunctionDef def = null;
        List<Expression> effectiveParams = params;

        // When keyword args are present, skip the initial arity-based lookup because
        // params.size() may not match the correct overload. Instead, resolve keyword
        // args against all signatures (largest arity first) to find the right one.
        if (hasKeywordArgs) {
            final List<FunctionSignature> funcs = ((InternalModule) module).getFunctionsByName(qname);
            // Sort by arity descending — keyword args typically target the largest overload
            funcs.sort((a, b) -> b.getArgumentCount() - a.getArgumentCount());
            for (final FunctionSignature sig : funcs) {
                final List<Expression> resolved = resolveKeywordArguments(context, params, sig, ast);
                if (resolved != null) {
                    def = ((InternalModule) module).getFunctionDef(qname, sig.getArgumentCount());
                    if (def != null) {
                        effectiveParams = resolved;
                        break;
                    }
                }
            }
        }

        if (def == null && !hasKeywordArgs) {
            def = ((InternalModule) module).getFunctionDef(qname, params.size());
        }
        //TODO: rethink: xsl namespace function should search xpath one too
        if (def == null && Namespaces.XSL_NS.equals(qname.getNamespaceURI())) {
            //Search xpath namespace
            final Module[] _modules_ = context.getModules(Namespaces.XPATH_FUNCTIONS_NS);
            if (isNotEmpty(_modules_)) {
                // there can be only one!
                for (final Module _module_ : _modules_) {
                    if (_module_ != null) {
                        final QName _qname_ = new QName(qname.getLocalPart(), Namespaces.XPATH_FUNCTIONS_NS, qname.getPrefix());
                        def = ((InternalModule) _module_).getFunctionDef(qname, params.size());
                        if (def != null) {
                            module = _module_;
                            qname = _qname_;
                            break;
                        }
                    }
                }
            }
        }
        if (def == null) {
            final List<FunctionSignature> funcs = ((InternalModule) module).getFunctionsByName(qname);
            if (funcs.isEmpty()) {
                if (throwOnNotFound) {
                    throw new XPathException(ast.getLine(), ast.getColumn(),
                            ErrorCodes.XPST0017, "Function " + qname.getStringValue() + "() " +
                            " is not defined in module namespace: " + qname.getNamespaceURI());
                } else {
                    return null;
                }
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
        if (context.getConfiguration() != null &&
                (Boolean) context.getConfiguration().getProperty(PROPERTY_DISABLE_DEPRECATED_FUNCTIONS) &&
                def.getSignature().isDeprecated()) {
            throw new XPathException(ast.getLine(), ast.getColumn(), ErrorCodes.XPST0017,
                    "Access to deprecated functions is not allowed. Call to '" + qname.getStringValue() + "()' denied. " + def.getSignature().getDeprecated());
        }
        final Function fn = Function.createFunction(context, ast, module, def);
        if (hasKeywordArgs && effectiveParams == params) {
            // No prior keyword-arg resolution succeeded; try once more against def's signature
            final List<Expression> resolved = resolveKeywordArguments(context, params, def.getSignature(), ast);
            fn.setArguments(resolved != null ? resolved : params);
        } else {
            fn.setArguments(effectiveParams);
        }
        fn.setASTNode(ast);
        return new InternalFunctionCall(fn);
    }

    /**
     * Gets an user defined function from the XQuery
     */
    private static FunctionCall getUserDefinedFunction(XQueryContext context, XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        final FunctionCall fc;
        final boolean hasKeywordArgs = hasKeywordArguments(params);

        // Count positional arguments to determine resolution arity
        int positionalCount = params.size();
        if (hasKeywordArgs) {
            positionalCount = 0;
            for (final Expression param : params) {
                if (param instanceof KeywordArgumentExpression) {
                    break;
                }
                positionalCount++;
            }
        }

        UserDefinedFunction func = context.resolveFunction(qname, params.size());

        // If keyword args and no exact match, try resolving with positional count
        if (func == null && hasKeywordArgs && positionalCount != params.size()) {
            func = context.resolveFunction(qname, positionalCount);
        }

        if (func != null) {
            fc = new FunctionCall(context, func);
            fc.setLocation(ast.getLine(), ast.getColumn());
            if (hasKeywordArgs) {
                final List<Expression> resolved = resolveKeywordArguments(context, params, func.getSignature(), ast);
                if (resolved == null) {
                    // For user-defined functions there is exactly one signature per
                    // QName+arity, so a null return means an unmatchable keyword
                    // argument or a missing required parameter — surface as XPST0017.
                    throw new XPathException(ast.getLine(), ast.getColumn(),
                            ErrorCodes.XPST0017,
                            "Keyword arguments do not match the signature of "
                                    + qname.toURIQualifiedName() + '#' + func.getSignature().getArgumentCount());
                }
                fc.setArguments(resolved);
            } else {
                fc.setArguments(params);
            }
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
     *
     * @param throwOnNotFound true to throw an XPST0017 if the functions is not found, false to just return null
     */
    private static FunctionCall getXQueryModuleFunction(final XQueryContext context,
            final XQueryAST ast, final List<Expression> params, final QName qname, final Module module, final boolean throwOnNotFound) throws XPathException {
        final FunctionCall fc;
        final UserDefinedFunction func = ((ExternalModule) module).getFunction(qname, params.size(), context);
        if (func == null) {
            // check if the module has been compiled already
            if (module.isReady()) {
                final StringBuilder msg = new StringBuilder("Function ")
                        .append(qname.getStringValue()).append('#').append(params.size())
                        .append(" is not defined in namespace '").append(qname.getNamespaceURI()).append('\'');
                if (module instanceof ExternalModule) {
                    final Source moduleSource = ((ExternalModule) module).getSource();
                    msg.append(" for module: ").append(moduleSource.pathOrShortIdentifier());
                }
                if (throwOnNotFound) {
                    throw new XPathException(ast.getLine(), ast.getColumn(),
                            ErrorCodes.XPST0017, msg.toString());
                } else {
                    return null;
                }

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
		final SequenceType[] newParamArray = newParamTypes.toArray(new SequenceType[0]);
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

    /**
     * Check if any parameter is a keyword argument.
     */
    private static boolean hasKeywordArguments(final List<Expression> params) {
        for (final Expression param : params) {
            if (param instanceof KeywordArgumentExpression) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve keyword arguments to positional arguments using the function signature.
     *
     * Keyword arguments (name := value) are matched to the corresponding parameter
     * position in the function signature. Positional arguments must come before
     * keyword arguments. Gaps between positional and keyword arguments are filled
     * with empty sequence expressions for optional parameters. Returns null if
     * resolution fails.
     */
    private static @Nullable List<Expression> resolveKeywordArguments(
            final XQueryContext context,
            final List<Expression> params, final FunctionSignature signature,
            final XQueryAST ast) throws XPathException {
        final SequenceType[] argTypes = signature.getArgumentTypes();
        if (argTypes == null) {
            return null;
        }

        // Find where keyword arguments start
        int firstKeyword = -1;
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i) instanceof KeywordArgumentExpression) {
                firstKeyword = i;
                break;
            }
        }
        if (firstKeyword < 0) {
            return params; // no keyword args
        }

        // Build the resolved argument list
        final List<Expression> resolved = new ArrayList<>(argTypes.length);

        // Copy positional arguments
        for (int i = 0; i < firstKeyword; i++) {
            resolved.add(params.get(i));
        }

        // Fill remaining positions with nulls (to be filled by keyword args)
        for (int i = firstKeyword; i < argTypes.length; i++) {
            resolved.add(null);
        }

        // Match keyword arguments to parameter positions
        for (int i = firstKeyword; i < params.size(); i++) {
            final Expression param = params.get(i);
            if (!(param instanceof KeywordArgumentExpression)) {
                throw new XPathException(ast.getLine(), ast.getColumn(),
                        ErrorCodes.XPST0003,
                        "Positional arguments must not follow keyword arguments");
            }
            final KeywordArgumentExpression kwArg = (KeywordArgumentExpression) param;
            final String kwName = kwArg.getKeywordName();
            final String kwClark = normalizeQNameToClark(context, kwName);

            // Find matching parameter by name. Compare in Clark notation so
            // {prefix:local, Q{ns}local, plain local} all match a parameter that
            // resolves to the same expanded QName. Search ALL positions, not
            // just those at/after the first keyword, so that supplying the same
            // parameter both positionally and by keyword is caught (XPST0017).
            int matchPos = -1;
            for (int j = 0; j < argTypes.length; j++) {
                if (argTypes[j] instanceof FunctionParameterSequenceType) {
                    final String paramName = ((FunctionParameterSequenceType) argTypes[j])
                            .getAttributeName();
                    final String paramClark = normalizeQNameToClark(context, paramName);
                    if (kwClark != null && kwClark.equals(paramClark)) {
                        matchPos = j;
                        break;
                    }
                }
            }

            if (matchPos < 0) {
                return null; // no matching parameter found — signature mismatch
            }
            if (resolved.get(matchPos) != null) {
                // XQ4 (PR197): supplying the same parameter twice — whether by two
                // keyword args or one positional + one keyword — is XPST0017.
                throw new XPathException(ast.getLine(), ast.getColumn(),
                        ErrorCodes.XPST0017,
                        "Parameter '" + kwName + "' supplied more than once in call");
            }
            resolved.set(matchPos, kwArg.getArgument());
        }

        // Fill gaps: parameters with default values get them substituted in.
        // A parameter without a default is required; if the call did not supply
        // it (positionally or by keyword), the signature does not match — return
        // null so the caller can report XPST0017 or try another overload.
        for (int i = 0; i < resolved.size(); i++) {
            if (resolved.get(i) == null) {
                if (argTypes[i] instanceof FunctionParameterSequenceType) {
                    final FunctionParameterSequenceType pst =
                            (FunctionParameterSequenceType) argTypes[i];
                    if (pst.hasDefaultValue()) {
                        resolved.set(i, pst.getDefaultValue());
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }

        return resolved;
    }

    /**
     * True if the namespace's modules (Internal or XQuery) declare any function
     * matching {@code qname}, regardless of arity. Used by the XQ4 PR2200
     * unprefixed-call resolver to decide whether an unmatched call should be
     * deferred to no-namespace forward-reference resolution.
     */
    private static boolean hasInternalOrUserFnFunction(final XQueryContext context, final QName qname, final int arity) {
        final Module[] modules = context.getModules(qname.getNamespaceURI());
        if (modules != null) {
            for (final Module module : modules) {
                if (module instanceof InternalModule) {
                    if (((InternalModule) module).getFunctionDef(qname, arity) != null) {
                        return true;
                    }
                    if (!((InternalModule) module).getFunctionsByName(qname).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Resolve a (possibly prefixed) QName-shaped string to Clark notation
     * {@code {namespace}local}. Plain NCNames map to {@code {}local}; EQName
     * inputs ({@code Q{uri}local}) and Clark inputs are returned in Clark form.
     * Returns {@code null} only when the input is {@code null}; an unresolvable
     * prefix falls through to the raw input so error messages stay readable.
     */
    private static String normalizeQNameToClark(final XQueryContext context, final String name) {
        if (name == null) {
            return null;
        }
        if (name.length() > 0 && name.charAt(0) == '{') {
            return name;
        }
        if (name.length() > 1 && name.charAt(0) == 'Q' && name.charAt(1) == '{') {
            // EQName: Q{uri}local — strip the leading 'Q'.
            return name.substring(1);
        }
        final int colonIdx = name.indexOf(':');
        if (colonIdx < 0) {
            return "{}" + name;
        }
        final String prefix = name.substring(0, colonIdx);
        final String local = name.substring(colonIdx + 1);
        final String uri = context.getURIForPrefix(prefix);
        if (uri == null) {
            return name;
        }
        return "{" + uri + "}" + local;
    }
}
