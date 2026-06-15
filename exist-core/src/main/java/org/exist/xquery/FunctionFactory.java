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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.source.Source;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.parser.XQueryAST;
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

    /**
     * Reserved function names per XQuery 3.1/4.0 spec.
     * These names must not be used as unprefixed function calls (XPST0003).
     */
    private static final Set<String> RESERVED_FUNCTION_NAMES = Set.of(
        "array", "attribute", "comment", "document-node", "element",
        "function", "if", "item", "map", "namespace-node", "node",
        "processing-instruction", "schema-attribute", "schema-element",
        "switch", "text", "typeswitch"
    );

    public static Expression createFunction(final XQueryContext context,final  XQueryAST ast, final PathExpr parent, final List<Expression> params) throws XPathException {
        final QName qname = getQName(context, ast);
        return createFunction(context, qname, ast, parent, params);
    }

    private static QName getQName(final XQueryContext context, final XQueryAST ast) throws XPathException {
        final String rawName = ast.getText();
        final QName qname;
        try {
            qname = QName.parse(context, rawName, context.getDefaultFunctionNamespace());
        } catch(final QName.IllegalQNameException xpe) {
            throw new XPathException(ast, ErrorCodes.XPST0081, "Invalid qname " +  rawName + ". " + xpe.getMessage());
        }

        // Check for reserved function names — unprefixed reserved names cannot be
        // used as function calls (XPST0003). Prefixed names like fn:item() are not
        // subject to the reserved name restriction (they just won't be found → XPST0017).
        if (rawName != null && !rawName.contains(":") && !rawName.contains("{")) {
            final String local = qname.getLocalPart();
            if (RESERVED_FUNCTION_NAMES.contains(local)) {
                throw new XPathException(ast.getLine(), ast.getColumn(), ErrorCodes.XPST0003,
                    "'" + local + "' is a reserved function name and cannot be used as a function call");
            }
        }
        return qname;
    }

    public static Expression createFunction(XQueryContext context, QName qname, XQueryAST ast, PathExpr parent, List<Expression> params) throws XPathException {
        return createFunction(context, qname, ast, parent, params, true);
    }

    /**
     * Make sure that partially applied functions are wrapped correctly
     * the QName is read from the given AST
     *
     * @param context the XQuery context
     * @param ast the AST node of the function
     * @param parent the parent expression of the function
     * @param params the parameters to the function
     * @param isPartial is this a partially applied function with placeholders?
     * @return either a FunctionCall or a PartialFunctionApplication
     * @throws XPathException if an error occurs creating the function
     */
    public static Expression createFunctionCall(final XQueryContext context, final XQueryAST ast, final PathExpr parent, final List<Expression> params, final boolean isPartial) throws XPathException {
        return createFunctionCall(context, getQName(context, ast), ast, parent, params, isPartial);
    }

    /**
     * Make sure that partially applied functions are wrapped correctly
     *
     * @param context the XQuery context
     * @param qname the name of the function
     * @param ast the AST node of the function
     * @param parent the parent expression of the function
     * @param params the parameters to the function
     * @param isPartial is this a partially applied function with placeholders?
     * @return either a FunctionCall or a PartialFunctionApplication
     * @throws XPathException if an error occurs creating the function
     */
    public static Expression createFunctionCall(final XQueryContext context, final QName qname, final XQueryAST ast, final PathExpr parent, final List<Expression> params, final boolean isPartial) throws XPathException {
        Expression fc = createFunction(context, qname, ast, parent, params);
        if (!isPartial) {
            return fc;
        }
        if (fc instanceof CastExpression) {
            fc = ((CastExpression) fc).toFunction();
        }
        if (!(fc instanceof FunctionCall)) {
            fc = FunctionFactory.wrap(context, (Function) fc);
        }
        return new PartialFunctionApplication(context, (FunctionCall) fc);
    }

    /**
     * Create a function call.
     * <p>
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
     * @param optimizeStringFunctions true if string functions be optimized
     *
     * @return the function expression
     *
     * @throws XPathException if an error occurs creating the function
     */
    public static Expression createFunction(XQueryContext context, QName qname, XQueryAST ast, PathExpr parent, List<Expression> params,
        boolean optimizeStringFunctions) throws XPathException {
        final String local = qname.getLocalPart();
        final String uri = qname.getNamespaceURI();

        if (optimizeStringFunctions && (Namespaces.XPATH_FUNCTIONS_NS.equals(uri) || Namespaces.XSL_NS.equals(uri))) {
            return switch (local) {
                case "starts-with" ->
                        optimizeStringFunction(context, ast, parent, params, "starts-with", StringTruncationOperator.RIGHT);
                case "ends-with" ->
                        optimizeStringFunction(context, ast, parent, params, "ends-with", StringTruncationOperator.LEFT);
                case "contains" ->
                        optimizeStringFunction(context, ast, parent, params, "contains", StringTruncationOperator.BOTH);
                case "equals" -> {
                    final GeneralComparison op = optimizeStringFunction(context, ast, parent, params, "equals", StringTruncationOperator.EQUALS);
                    if (params.size() < 3) {
                        op.setCollation(new StringValue("?strength=identical"));
                    }
                    yield op;
                }
                default -> functionCall(context, ast, params, qname);
            };
        }

        //Check if the namespace belongs to one of the schema namespaces.
        //If yes, the function is a constructor function
        if (uri.equals(Namespaces.SCHEMA_NS) || uri.equals(Namespaces.XPATH_DATATYPES_NS)) {
            return castExpression(context, ast, params, qname);
        }

        //Check if the namespace URI starts with "java:". If yes, treat
        //the function call as a call to an arbitrary Java function.
        if (uri.startsWith("java:")) {
            return javaFunctionBinding(context, ast, params, qname);
        }

        //None of the above matched: function is either a built-in function or
        //a user-defined function
        return functionCall(context, ast, params, qname);
    }

    /**
     * Optimize a function call that compares strings into a general comparison
     *
     * @param context      The XQuery context
     * @param ast          The parsed AST
     * @param parent       The parent expression
     * @param params       The list of parameters
     * @param functionName The name of the function to optimize
     * @param operator     The StringTruncationOperator to optimize with
     * @return The optimized GeneralComparison
     * @throws XPathException If the provided parameters are incorrect
     */
    private static GeneralComparison optimizeStringFunction(
            final XQueryContext context,
            final XQueryAST ast,
            final PathExpr parent,
            final List<Expression> params,
            final String functionName,
            final StringTruncationOperator operator
    ) throws XPathException {
        if (params.size() < 2 || params.size() > 3) {
            throw new XPathException(ast,
                    ErrorCodes.XPST0017, "Function " + functionName + "() requires two or three arguments");
        }

        final PathExpr p0 = (PathExpr) params.getFirst();
        final PathExpr p1 = (PathExpr) params.get(1);

        if (p1.getSubExpressionCount() == 0) {
            throw new XPathException(ast, ErrorCodes.XPTY0004, "Second argument of " + functionName + "() is empty");
        }

        final GeneralComparison op = new GeneralComparison(context, p0, p1, Comparison.EQ, operator);
        op.setLocation(ast.getLine(), ast.getColumn());
        //TODO : not sure for parent -pb
        context.getProfiler().message(parent, Profiler.OPTIMIZATIONS, "OPTIMIZATION",
                "Rewritten " + functionName + "() as a general comparison with a right truncations");

        if (params.size() == 3) {
            op.setCollation(params.get(2));
        }

        return op;
    }

    private static CastExpression castExpression(XQueryContext context,
            XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        if (params.size() != 1) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
        		ErrorCodes.XPST0017, "Wrong number of arguments for constructor function");
        }
        final Expression arg = params.getFirst();
        final int code;
        try {
            code = Type.getType(qname);
        } catch (final XPathException e) {
            // Unknown type name in xs: namespace → XPST0017 (no such function)
            throw new XPathException(ast.getLine(), ast.getColumn(),
                ErrorCodes.XPST0017, "Unknown constructor function: " + qname.getStringValue());
        }
        // No constructor function exists for xs:NOTATION, xs:anyAtomicType, or xs:anySimpleType
        // (per QT4 §4.6.3 — XPST0017 since no function with this name and arity exists)
        if (code == Type.NOTATION || code == Type.ANY_ATOMIC_TYPE || code == Type.ANY_SIMPLE_TYPE) {
            throw new XPathException(ast.getLine(), ast.getColumn(),
                ErrorCodes.XPST0017, "No constructor function exists for " + qname.getStringValue());
        }
        final CastExpression castExpr = new CastExpression(context, arg, code, Cardinality.ZERO_OR_ONE);
        castExpr.setLocation(ast.getLine(), ast.getColumn());
        return castExpr;
    }

    private static JavaCall javaFunctionBinding(XQueryContext context,
            XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        // Only allow java binding if specified in config file <xquery enable-java-binding="yes">
        final String javaBinding = (String) context.getBroker().getConfiguration()
            .getProperty(PROPERTY_ENABLE_JAVA_BINDING);
        if (!"yes".equals(javaBinding)) {
            throw new XPathException(ast.getLine(), ast.getColumn(), ErrorCodes.XPST0017,
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

        final String uri = qname.getNamespaceURI();
        final Module[] modules = context.getModules(uri);

        if (modules == null) {
            return getUserDefinedFunction(context, ast, params, qname);
        }

        // Function might belong to a module
        for (final Module module : modules) {
            final Function fn;

            if (module.isInternalModule()) {
                // Function is from an Internal Module
                fn = getInternalModuleFunction(context, ast, params, qname, module, false);
            } else {
                // Function is from an imported XQuery module
                fn = getXQueryModuleFunction(context, ast, params, qname, module, false);
            }
            // return early on first match
            if (fn != null) {
                return fn;
            }
        }

        throw new XPathException(ast.getLine(), ast.getColumn(),
                ErrorCodes.XPST0017, "Function " + qname.getStringValue() + "() " +
                " is not defined in module namespace: " + qname.getNamespaceURI());
    }

    /**
     * Get a function implemented in Java from an XQuery Extension Module
     *
     * @param throwOnNotFound true to throw an XPST0017 if the functions is not found, false to just return null
     */
    private static @Nullable Function getInternalModuleFunction(final XQueryContext context,
            final XQueryAST ast, final List<Expression> params, QName qname, Module module,
            final boolean throwOnNotFound) throws XPathException {
        // For internal modules: create a new function instance from the class
        FunctionDef def = ((InternalModule) module).getFunctionDef(qname, params.size());
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
        fn.setArguments(params);
        fn.setASTNode(ast);
        return new InternalFunctionCall(fn);
    }

    /**
     * Get a user-defined function from the XQuery context
     */
    private static FunctionCall getUserDefinedFunction(XQueryContext context, XQueryAST ast, List<Expression> params, QName qname) throws XPathException {
        final UserDefinedFunction func = context.resolveFunction(qname, params.size());

        if (func == null) {
            // Create a forward reference which will be resolved later
            final FunctionCall forwardReference = new FunctionCall(context, qname, params);
            forwardReference.setLocation(ast.getLine(), ast.getColumn());
            context.addForwardReference(forwardReference);
            return forwardReference;
        }

        final FunctionCall functionCall = new FunctionCall(context, func);
        functionCall.setLocation(ast.getLine(), ast.getColumn());
        functionCall.setArguments(params);
        return functionCall;
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
                // Check if function exists at other arities to give a better error message
                final List<FunctionSignature> otherArities = new ArrayList<>();
                final Iterator<FunctionSignature> sigs = module.getSignaturesForFunction(qname);
                while (sigs.hasNext()) {
                    otherArities.add(sigs.next());
                }

                if (!otherArities.isEmpty()) {
                    final StringBuilder msg = new StringBuilder();
                    msg.append("Unexpectedly received ").append(params.size())
                       .append(" parameter(s) in call to function '")
                       .append(qname.getStringValue()).append("()'. ");
                    msg.append("Defined function signatures are:\r\n");
                    for (final FunctionSignature sig : otherArities) {
                        msg.append(sig.toString()).append("\r\n");
                    }
                    if (throwOnNotFound) {
                        throw new XPathException(ast.getLine(), ast.getColumn(),
                                ErrorCodes.XPST0017, msg.toString());
                    } else {
                        return null;
                    }
                }

                final StringBuilder msg = new StringBuilder("Function ")
                        .append(qname.getStringValue()).append('#').append(params.size())
                        .append(" is not defined in namespace '").append(qname.getNamespaceURI()).append('\'');
                if (module instanceof ExternalModule externalModule) {
                    final Source moduleSource = externalModule.getSource();
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
                if (((ExternalModule) module).getContext() == context) {
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
     * function applications on built-in functions.
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
		// This wrapper exists to lift a built-in Function into a FunctionCall
		// so that it can be used as a function item. Per F&O 3.1 section
		// 16.1.1, the static and dynamic context of the call to
		// fn:function-lookup -- and of named function references -- forms
		// part of the closure of the returned function. When the wrapped
		// built-in is itself context-dependent (fn:position#0,
		// fn:node-name#0, fn:lang#1, fn:default-collation,
		// fn:static-base-uri, ...), the wrapper must forward that captured
		// focus into the body. Each Function subclass declares its own
		// context-dependency via Function.isContextDependent(); the default
		// is false, so non-context-dependent built-ins (fn:concat,
		// fn:string-length#1, ...) and user-defined functions do not pay
		// the propagation cost.
		func.setPropagateContextToBody(call.isContextDependent());
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
