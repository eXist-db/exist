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

import java.lang.reflect.Constructor;
import java.util.List;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.dom.QName;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.util.Messages;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * Abstract base class for all built-in and user-defined functions.
 *
 * Built-in functions just extend this class. A new function instance
 * will be created for each function call. Subclasses <b>have</b> to
 * provide a function signature to the constructor.
 *
 * User-defined functions extend class {@link org.exist.xquery.UserDefinedFunction},
 * which is again a subclass of Function. They will not be called directly, but through a
 * {@link org.exist.xquery.FunctionCall} object, which checks the type and cardinality of
 * all arguments and takes care that the current execution context is saved properly.
 *
 * @author wolf
 */
public abstract class Function extends PathExpr {

    // Declare it in Namespaces instead? /ljo
    public final static String BUILTIN_FUNCTION_NS =
            "http://www.w3.org/2005/xpath-functions";

    /**
     * The module that declared the function.
     */
    protected Module parentModule;

    /**
     * The signature of the function.
     */
    private FunctionSignature mySignature;

    /**
     * The parent expression from which this function is called.
     */
    private Expression parent;

    /**
     * Flag to indicate if argument types are statically checked.
     * This is set to true by default (meaning: no further checks needed).
     * Method {@link #setArguments(java.util.List)} will set it to false
     * (unless overwritten), thus enforcing a check.
     */
    protected boolean argumentsChecked = true;

    /**
     * Internal constructor. Subclasses should <b>always</b> call this and
     * pass the current context and their function signature.
     *
     * @param context the xquery context.
     * @param signature the function signature.
     */
    protected Function(final XQueryContext context, final FunctionSignature signature) {
        super(context);
        this.mySignature = signature;
    }
    /**
     * Returns the module to which this function belongs.
     *
     * @return the parent module or null.
     */
    protected Module getParentModule() {
        return parentModule;
    }

    @Override
    public int returnsType() {
        if (mySignature == null) {
            return Type.ITEM;
        } // Type is not known yet
        if (mySignature.getReturnType() == null) {
            throw new IllegalArgumentException("Return type for function " +
                    mySignature.getName() + " is not defined");
        }
        return mySignature.getReturnType().getPrimaryType();
    }

    @Override
    public Cardinality getCardinality() {
        if (mySignature.getReturnType() == null) {
            throw new IllegalArgumentException("Return type for function " +
                    mySignature.getName() + " is not defined");
        }
        return mySignature.getReturnType().getCardinality();
    }

    /**
     * Create a built-in function from the specified class.
     *
     * @param context the xquery context
     * @param ast the ast node
     * @param module the module from whence the function came
     * @param def the function definition
     *
     * @return the created function or null if the class could not be initialized.
     *
     * @throws XPathException if the function could not be created
     */
    public static Function createFunction(final XQueryContext context, final XQueryAST ast,
            final Module module, final FunctionDef def) throws XPathException {
        if (def == null) {
            throw new XPathException(ast.getLine(), ast.getColumn(), "Class for function is null");
        }
        final Class<? extends Function> fclazz = def.getImplementingClass();
        if (fclazz == null) {
            throw new XPathException(ast.getLine(), ast.getColumn(), "Class for function is null");
        }

        try {

            Function function = null;
            try {
                // attempt for a constructor that takes 1 argument
                final Constructor<? extends Function> cstr1 = fclazz.getConstructor(XQueryContext.class);
                function = cstr1.newInstance(context);
            } catch (final NoSuchMethodException nsme1) {
                try {
                    // attempt for a constructor that takes 2 arguments
                    final Constructor<? extends Function> cstr2 = fclazz.getConstructor(XQueryContext.class, FunctionSignature.class);
                    function = cstr2.newInstance(context, def.getSignature());
                } catch (final NoSuchMethodException nsme2) {
                    throw new XPathException(ast.getLine(), ast.getColumn(), "Constructor not found");
                }
            }

            function.setParentModule(module);
            function.setLocation(ast.getLine(), ast.getColumn());

            return function;

        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            LOG.debug(e.getMessage(), e);
            throw new XPathException(ast.getLine(), ast.getColumn(),
                    "Function implementation class " + fclazz.getName() + " not found", e);
        }
    }

    private void setParentModule(final Module parentModule) {
        this.parentModule = parentModule;
    }

    /**
     * Set the parent expression of this function, i.e. the
     * expression from which the function is called.
     *
     * @param parent the parent expression
     */
    public void setParent(final Expression parent) {
        this.parent = parent;
    }

    /**
     * Returns the expression from which this function gets called.
     *
     * @return the parent expression
     */
    @Override
    public Expression getParent() {
        return parent;
    }

    /**
     * Set the (static) arguments for this function from a list of expressions.
     *
     * This will also trigger a check on the type and cardinality of the
     * passed argument expressions. By default, the method sets the
     * argumentsChecked property to false, thus triggering the analyze method to
     * perform a type check.
     *
     * Classes overwriting this method are typically optimized functions and will
     * handle type checks for arguments themselves.
     *
     * @param arguments the function arguments.
     *
     * @throws XPathException if an error occurs setting the arguments
     */
    public void setArguments(final List<Expression> arguments) throws XPathException {
        if ((!mySignature.isVariadic()) && arguments.size() != mySignature.getArgumentCount()) {
            throw new XPathException(this, ErrorCodes.XPST0017,
                    "Number of arguments of function " + getName() + " doesn't match function signature (expected " +
                            mySignature.getArgumentCount() + ", got " + arguments.size() + ')');
        }
        steps.clear();

        for (final Expression argument : arguments) {
            steps.add(argument.simplify());
        }

        argumentsChecked = false;
    }

    /**
     * Check the function arguments.
     *
     * @param argument the argument
     * @param argType the type of the argument if known, or null
     * @param argContextInfo the context info from analyzing the argument
     * @param argPosition the position of the argument
     *
     * @throws XPathException if an error occurs when checking the arguments.
     */
    protected void checkArgument(final Expression argument, @Nullable final SequenceType argType,
            final AnalyzeContextInfo argContextInfo, final int argPosition) throws XPathException {
        final Expression next = checkArgumentType(argument, argType, argContextInfo, argPosition);
        steps.set(argPosition - 1, next);
    }

    /**
     * Statically check an argument against the sequence type specified in
     * the signature.
     *
     * @param argument the argument
     * @param argType the type of the argument if known, or null
     * @param argContextInfo the context info from analyzing the argument
     * @param argPosition the position of the argument
     *
     * @return The passed expression
     *
     * @throws XPathException if an error occurs whilst checking the argument
     */
    private Expression checkArgumentType(Expression argument, @Nullable final SequenceType argType,
            final AnalyzeContextInfo argContextInfo, final int argPosition) throws XPathException {
        if (argType == null || argument instanceof Placeholder) {
            return argument;
        }

        // check cardinality if expected cardinality is not zero or more
        final boolean cardinalityMatches = checkArgumentTypeCardinality(argument, argType, argPosition);

        argument = new DynamicCardinalityCheck(context, argType.getCardinality(), argument,
                new Error(Error.FUNC_PARAM_CARDINALITY, argPosition, mySignature));
        // check return type if both types are not Type.ITEM
        int returnType = argument.returnsType();
        if (returnType == Type.ANY_TYPE || returnType == Type.EMPTY_SEQUENCE) {
            returnType = Type.ITEM;
        }
        final boolean typeMatches = Type.subTypeOf(returnType, argType.getPrimaryType());
        if (typeMatches && cardinalityMatches) {
            if (argType.getNodeName() != null) {
                argument = new DynamicNameCheck(context,
                        new NameTest(argType.getPrimaryType(), argType.getNodeName()), argument);
            }
            return argument;
        }

        //Loose argument check : we may move this, or a part hereof, to UntypedValueCheck
        if (context.isBackwardsCompatible()) {
            final Tuple2<Expression, Integer> looseCheckResult = looseCheckArgumentType(argument, argType, argContextInfo, argPosition, returnType);
            argument = looseCheckResult._1;
            returnType = looseCheckResult._2;

        //Strict argument check : we may move this, or a part hereof, to UntypedValueCheck
        } else {
            final Tuple2<Expression, Integer> strictCheckResult = strictCheckArgumentType(argument, argType, argContextInfo, argPosition, returnType);
            argument = strictCheckResult._1;
            returnType = strictCheckResult._2;
        }

        if (returnType != Type.ITEM && !Type.subTypeOf(returnType, argType.getPrimaryType())) {
            if (!(Type.subTypeOf(argType.getPrimaryType(), returnType) ||
                    //Because () is seen as a node
                    (argType.getCardinality().isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE) && returnType == Type.NODE))) {
                LOG.debug(ExpressionDumper.dump(argument));
                throw new XPathException(this, ErrorCodes.XPTY0004, Messages.getMessage(Error.FUNC_PARAM_TYPE_STATIC,
                        String.valueOf(argPosition), mySignature, argType.toString(), Type.getTypeName(returnType)));
            }
        }

        if (!typeMatches && !context.isBackwardsCompatible()) {
            if (argType.getNodeName() != null) {
                argument = new DynamicNameCheck(context,
                        new NameTest(argType.getPrimaryType(), argType.getNodeName()), argument);
            } else {
                argument = new DynamicTypeCheck(context, argType.getPrimaryType(), argument);
            }
        }
        return argument;
    }

    protected boolean checkArgumentTypeCardinality(final Expression argument, @Nullable final SequenceType argType,
            final int argPosition) throws XPathException {
        boolean cardinalityMatches = argument instanceof VariableReference
                || argType.getCardinality() == Cardinality.ZERO_OR_MORE;

        if (!cardinalityMatches) {
            cardinalityMatches = argument.getCardinality().isSubCardinalityOrEqualOf(argType.getCardinality());
        }

        if (!cardinalityMatches) {
            if (argument.getCardinality() == Cardinality.EMPTY_SEQUENCE && argType.getCardinality().atLeastOne()) {
                throw new XPathException(this,  ErrorCodes.XPTY0004,
                        Messages.getMessage(Error.FUNC_EMPTY_SEQ_DISALLOWED,
                                argPosition, ExpressionDumper.dump(argument)));
            }
        }

        return cardinalityMatches;
    }

    protected Tuple2<Expression, Integer> looseCheckArgumentType(Expression argument,
            @Nullable final SequenceType argType, final AnalyzeContextInfo argContextInfo, final int argPosition,
            int returnType) {
        if (Type.subTypeOf(argType.getPrimaryType(), Type.STRING)) {
            if (!Type.subTypeOf(returnType, Type.ANY_ATOMIC_TYPE)) {
                argument = new Atomize(context, argument);
            }
            argument = new AtomicToString(context, argument);
            returnType = Type.STRING;
        } else if (Type.subTypeOfUnion(argType.getPrimaryType(), Type.NUMERIC)) {
            if (!Type.subTypeOf(returnType, Type.ANY_ATOMIC_TYPE)) {
                argument = new Atomize(context, argument);
            }
            argument = new UntypedValueCheck(context, argType.getPrimaryType(), argument,
                    new Error(Error.FUNC_PARAM_TYPE, String.valueOf(argPosition), mySignature));
            returnType = argType.getPrimaryType();
        }
        //If the required type is an atomic type, convert the argument to an atomic
        if (Type.subTypeOf(argType.getPrimaryType(), Type.ANY_ATOMIC_TYPE)) {
            if (!Type.subTypeOf(returnType, Type.ANY_ATOMIC_TYPE)) {
                argument = new Atomize(context, argument);
            }
            if (argType.getPrimaryType() != Type.ANY_ATOMIC_TYPE) {
                argument = new UntypedValueCheck(context, argType.getPrimaryType(),
                        argument, new Error(Error.FUNC_PARAM_TYPE, String.valueOf(argPosition), mySignature));
            }
            returnType = argument.returnsType();
        }

        return Tuple(argument, returnType);
    }

    protected Tuple2<Expression, Integer> strictCheckArgumentType(Expression argument,
            @Nullable final SequenceType argType, final AnalyzeContextInfo argContextInfo, final int argPosition,
            int returnType) {

        // if the required type is an atomic type, convert the argument to an atomic
        if (Type.subTypeOf(argType.getPrimaryType(), Type.ANY_ATOMIC_TYPE)) {

            if (!Type.subTypeOf(returnType, Type.ANY_ATOMIC_TYPE)) {
                // Atomization!
                argument = new Atomize(context, argument);
            }
            argument = new UntypedValueCheck(context, argType.getPrimaryType(),
                    argument, new Error(Error.FUNC_PARAM_TYPE, String.valueOf(argPosition), mySignature));
            returnType = argument.returnsType();
        }

        return Tuple(argument, returnType);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        contextId = contextInfo.getContextId();
        contextInfo.setParent(this);

        final SequenceType[] argumentTypes = mySignature.getArgumentTypes();
        for (int i = 0; i < getArgumentCount(); i++) {
            final Expression arg = getArgument(i);

            if (arg != null) {
                // call analyze for each argument
                final AnalyzeContextInfo argContextInfo = new AnalyzeContextInfo(contextInfo);
                arg.analyze(argContextInfo);

                if (!argumentsChecked) {
                    // statically check the argument
                    SequenceType argType = null;
                    if (argumentTypes != null && i < argumentTypes.length) {
                        argType = argumentTypes[i];
                    }
                    checkArgument(arg, argType, argContextInfo, i + 1);
                }
            }
        }
        argumentsChecked = true;
    }

    public Sequence[] getArguments(Sequence contextSequence, final Item contextItem)
            throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        final int argCount = getArgumentCount();
        final Sequence[] args = new Sequence[argCount];
        for (int i = 0; i < argCount; i++) {
            args[i] = getArgument(i).eval(contextSequence, contextItem);
        }
        return args;
    }

    /**
     * Get an argument expression by its position in the
     * argument list.
     *
     * @param pos the position of the argument
     *
     * @return the expression.
     */
    public Expression getArgument(final int pos) {
        return getExpression(pos);
    }

    /**
     * Get the number of arguments passed to this function.
     *
     * @return number of arguments
     */
    public int getArgumentCount() {
        return steps.size();
    }

    @Override
    public void setPrimaryAxis(final int axis) {
    }

    /**
     * Return the name of this function.
     *
     * @return name of this function
     */
    public QName getName() {
        if (mySignature == null) {
            return null;
        }
        return mySignature.getName();
    }

    /**
     * Get the signature of this function.
     *
     * @return signature of this function
     */
    public FunctionSignature getSignature() {
        return mySignature;
    }

    /**
     * Set the signature of this function.
     *
     * @param functionSignature the signature of this function
     */
    protected void setSignature(final FunctionSignature functionSignature) {
        this.mySignature = functionSignature;
    }

    public boolean isCalledAs(final String localName) {
        return localName.equals(mySignature.getName().getLocalPart());
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_ITEM | Dependency.CONTEXT_SET;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display(getName());
        dumper.display('(');
        boolean moreThanOne = false;
        for (final Expression e : steps) {
            if (moreThanOne) {
                dumper.display(", ");
            }
            moreThanOne = true;
            e.dump(dumper);
        }
        dumper.display(')');
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        final String name = getName() != null ? getName().getStringValue() : "<UNKNOWN>";
        result.append(name);
        result.append('(');
        boolean moreThanOne = false;
        for (final Expression step : steps) {
            if (moreThanOne) {
                result.append(", ");
            }
            moreThanOne = true;
            result.append(step.toString());
        }
        result.append(')');
        return result.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitBuiltinFunction(this);
    }

    @Override
    public Expression simplify() {
        return this;
    }

    public static class Placeholder extends AbstractExpression {

        public Placeholder(final XQueryContext context) {
            super(context);
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo)
                throws XPathException {
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            dumper.display('?');
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem)
                throws XPathException {
            throw new XPathException(this, ErrorCodes.EXXQDY0001, "Internal error: function argument placeholder not expanded.");
        }

        @Override
        public int returnsType() {
            return Type.ITEM;
        }
    }
}
