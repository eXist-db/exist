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
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.Namespaces;
import org.exist.dom.QName;

import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.ErrorCodes.JavaErrorCode;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * XQuery 3.0 try {...} catch{...} expression.
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:ljo@exist-db.org">Leif-Jöran Olsson</a>
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 */
public class TryCatchExpression extends AbstractExpression {

    private static final Logger LOG = LogManager.getLogger(TryCatchExpression.class);

    private static final QName QN_CODE = new QName("code", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
    private static final QName QN_DESCRIPTION = new QName("description", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
    private static final QName QN_VALUE = new QName("value", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
    private static final QName QN_MODULE = new QName("module", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
    private static final QName QN_LINE_NUM = new QName("line-number", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
    private static final QName QN_COLUMN_NUM = new QName("column-number", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
    private static final QName QN_ADDITIONAL = new QName("additional", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);

    private static final QName QN_XQUERY_STACK_TRACE = new QName("xquery-stack-trace", Namespaces.EXIST_XQUERY_XPATH_ERROR_NS, Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX);
    private static final QName QN_JAVA_STACK_TRACE = new QName("java-stack-trace", Namespaces.EXIST_XQUERY_XPATH_ERROR_NS, Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX);

    // XQuery 4.0 PR1470/PR1599: $err:stack-trace as xs:string?
    private static final QName QN_STACK_TRACE = new QName("stack-trace", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
    // XQuery 4.0 PR493: $err:map as map(xs:string, item()*)
    private static final QName QN_MAP = new QName("map", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);

    private final Expression tryTargetExpr;
    private final List<CatchClause> catchClauses = new ArrayList<>();
    private Expression finallyExpr;

    /**
     *  Constructor.
     * 
     * @param context   Xquery context
     * @param tryTargetExpr Expression to be evaluated
     */
    public TryCatchExpression(final XQueryContext context, final Expression tryTargetExpr) {
        super(context);
        this.tryTargetExpr = tryTargetExpr;
    }

    /**
     * Receive catch-clause data from parser.
     *
     * TODO: check if catchVars are still needed
     *
     * @param catchErrorList list of errors to catch
     * @param catchVars variable names for caught errors: unused (from earlier version of the spec?)
     * @param catchExpr the expression to be evaluated if error is caught
     */
    public void addCatchClause(final List<QName> catchErrorList, final List<QName> catchVars, final Expression catchExpr) {
        catchClauses.add( new CatchClause(catchErrorList, catchVars, catchExpr) );
    }

    public void setFinallyExpr(final Expression finallyExpr) {
        this.finallyExpr = finallyExpr;
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
    }

    public Expression getTryTargetExpr() {
        return tryTargetExpr;
    }

    public List<CatchClause> getCatchClauses() {
        return catchClauses;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setFlags(contextInfo.getFlags() & (~IN_PREDICATE));
            contextInfo.setParent(this);
            context.declareVariableBinding(new LocalVariable(QN_ADDITIONAL));
            context.declareVariableBinding(new LocalVariable(QN_COLUMN_NUM));
            context.declareVariableBinding(new LocalVariable(QN_LINE_NUM));
            context.declareVariableBinding(new LocalVariable(QN_CODE));
            context.declareVariableBinding(new LocalVariable(QN_DESCRIPTION));
            context.declareVariableBinding(new LocalVariable(QN_MODULE));
            context.declareVariableBinding(new LocalVariable(QN_VALUE));
            context.declareVariableBinding(new LocalVariable(QN_JAVA_STACK_TRACE));
            context.declareVariableBinding(new LocalVariable(QN_XQUERY_STACK_TRACE));
            context.declareVariableBinding(new LocalVariable(QN_STACK_TRACE));
            context.declareVariableBinding(new LocalVariable(QN_MAP));

            tryTargetExpr.analyze(contextInfo);
            for (final CatchClause catchClause : catchClauses) {
                catchClause.getCatchExpr().analyze(contextInfo);
            }
            if (finallyExpr != null) {
                finallyExpr.analyze(contextInfo);
            }
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {

        context.expressionStart(this);

        if(getContext().getXQueryVersion()<30){
            throw new XPathException(this, ErrorCodes.EXXQDY0003, "The try-catch expression is only available in xquery version \"3.0\" and later.");
        }

        Sequence result = null;
        Throwable pendingError = null;

        try {
            // Evaluate 'try' expression
            result = tryTargetExpr.eval(contextSequence, contextItem);

        } catch (final Throwable throwable) {

            // If no catch clauses (try/finally only), re-throw after finally
            if (catchClauses.isEmpty()) {
                pendingError = throwable;
            } else {

                final ErrorCode errorCode;

                // fn:error throws an XPathException
                if (throwable instanceof XPathException xpe) {
                    // Get errorcode from nicely thrown xpathexception

                    if (xpe.getErrorCode() != null) {
                        if (xpe.getErrorCode() == ErrorCodes.ERROR) {
                            errorCode = extractErrorCode(xpe);
                        } else {
                            errorCode = xpe.getErrorCode();
                        }
                    } else {
                        // if no errorcode is found, reconstruct by parsing the error text.
                        errorCode = extractErrorCode(xpe);
                    }
                } else {
                    // Get errorcode from all other errors and exceptions
                    errorCode = new JavaErrorCode(throwable);
                }

                // We need the qname in the end
                final QName errorCodeQname = errorCode.getErrorQName();

                // Exception in thrown, catch expression will be evaluated.
                // catchvars (CatchErrorCode (, CatchErrorDesc (, CatchErrorVal)?)? )
                // need to be retrieved as variables
                Sequence catchResultSeq = null;
                final LocalVariable mark0 = context.markLocalVariables(false);

                context.declareInScopeNamespace(Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX, Namespaces.W3C_XQUERY_XPATH_ERROR_NS);
                context.declareInScopeNamespace(Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX, Namespaces.EXIST_XQUERY_XPATH_ERROR_NS);

                try {
                    // flag used to escape loop when errorcode has matched
                    boolean errorMatched = false;

                    // Iterate on all catch clauses
                    for (final CatchClause catchClause : catchClauses) {

                        if (isErrorInList(errorCodeQname, catchClause.getCatchErrorList()) && !errorMatched) {

                            errorMatched = true;

                            // Get catch variables
                            final LocalVariable mark1 = context.markLocalVariables(false);

                            try {
                                // Add std errors
                                addErrCode(errorCodeQname);
                                addErrDescription(throwable, errorCode);
                                addErrValue(throwable);
                                addErrModule(throwable);
                                addErrLineNumber(throwable);
                                addErrColumnNumber(throwable);
                                addErrAdditional(throwable);
                                addFunctionTrace(throwable);
                                addJavaTrace(throwable);
                                addStackTrace(throwable);
                                addErrMap(throwable, errorCode, errorCodeQname);

                                // Evaluate catch expression
                                catchResultSeq = ((Expression) catchClause.getCatchExpr()).eval(contextSequence, contextItem);


                            } finally {
                                context.popLocalVariables(mark1, catchResultSeq);
                            }

                        } else {
                            // if in the end nothing is set, rethrow after loop
                        }
                    } // for catch clauses

                    // If an error hasn't been caught, store for re-throw after finally
                    if (!errorMatched) {
                        pendingError = throwable;
                    } else {
                        result = catchResultSeq;
                    }

                } finally {
                    context.popLocalVariables(mark0, catchResultSeq);
                }
            }
        } finally {
            // XQ4: Evaluate finally clause (always, even if try/catch succeeded or failed)
            if (finallyExpr != null) {
                try {
                    final Sequence finallyResult = finallyExpr.eval(contextSequence, contextItem);
                    // If finally produces a non-empty sequence, raise XQTY0153
                    if (finallyResult != null && !finallyResult.isEmpty()) {
                        throw new XPathException(this, ErrorCodes.XQTY0153,
                                "The finally clause must evaluate to an empty sequence, got " +
                                finallyResult.getItemCount() + " item(s)");
                    }
                } catch (final XPathException finallyError) {
                    // Finally error replaces any pending error or result
                    context.expressionEnd(this);
                    throw finallyError;
                }
            }

            // Re-throw pending error from try body (if not caught)
            if (pendingError != null) {
                context.expressionEnd(this);
                if (pendingError instanceof XPathException) {
                    throw (XPathException) pendingError;
                } else {
                    LOG.error(pendingError);
                    throw new XPathException(this, pendingError);
                }
            }

            context.expressionEnd(this);
        }

        return result;
    }


    // err:additional	item()*	
    // Implementation-defined. This variable must be bound so that a query 
    // can reference it without raising an error. The purpose of this 
    // variable is to allow implementations to provide any additional 
    // information that might be useful.
    private void addErrAdditional(final Throwable t) throws XPathException {
        final LocalVariable err_additional = new LocalVariable(QN_ADDITIONAL);
        err_additional.setSequenceType(new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE));
        err_additional.setValue(Sequence.EMPTY_SEQUENCE);

        context.declareVariableBinding(err_additional);
    }

    // err:column-number	xs:integer?	
    // The column number within the stylesheet module of the instruction 
    // where the error occurred, or an empty sequence if the information 
    // is not available. The value may be approximate.
    private void addErrColumnNumber(final Throwable t) throws XPathException {
        final LocalVariable err_column_nr = new LocalVariable(QN_COLUMN_NUM);
        err_column_nr.setSequenceType(new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

        final Sequence colNum;
        if (t != null && t instanceof XPathException) {
            colNum = new IntegerValue(this, ((XPathException)t).getColumn());
        } else {
            colNum = Sequence.EMPTY_SEQUENCE;
        }
        err_column_nr.setValue(colNum);

        context.declareVariableBinding(err_column_nr);
    }

    // err:line-number	xs:integer?	
    // The line number within the stylesheet module of the instruction 
    // where the error occurred, or an empty sequence if the information 
    // is not available. The value may be approximate.
    private void addErrLineNumber(final Throwable t) throws XPathException {
        final LocalVariable err_line_nr = new LocalVariable(QN_LINE_NUM);
        err_line_nr.setSequenceType(new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

        final Sequence lineNum;
        if (t != null && t instanceof XPathException) {
            lineNum = new IntegerValue(this, ((XPathException)t).getLine());
        } else {
            lineNum = Sequence.EMPTY_SEQUENCE;
        }
        err_line_nr.setValue(lineNum);

        context.declareVariableBinding(err_line_nr);
    }

    // err:module	xs:string?	
    // The URI (or system ID) of the module containing the expression 
    // where the error occurred, or an empty sequence if the information 
    // is not available.
    private void addErrModule(final Throwable t) throws XPathException {
        final LocalVariable err_module = new LocalVariable(QN_MODULE);
        err_module.setSequenceType(new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));

        final Sequence module;
        if (t != null && t instanceof XPathException && ((XPathException)t).getSource() != null) {
            module = new StringValue(this, ((XPathException)t).getSource().pathOrShortIdentifier());
        } else {
            module = Sequence.EMPTY_SEQUENCE;
        }
        err_module.setValue(module);

        context.declareVariableBinding(err_module);
    }

    // err:value	item()*	
    // Value associated with the error. For an error raised by calling 
    // the error function, this is the value of the third  argument 
    // (if supplied).
    private void addErrValue(final Throwable t) throws XPathException {
        final LocalVariable err_value = new LocalVariable(QN_VALUE);
        err_value.setSequenceType(new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));

        final Sequence errorValue;
        if (t != null) {
            // Get error value from exception
            if(t instanceof XPathException && ((XPathException)t).getErrorVal() != null) {
                errorValue = ((XPathException)t).getErrorVal();
            } else {
                errorValue = Sequence.EMPTY_SEQUENCE;
            }
        } else {
            // fill data from throwable object
            errorValue = null;
        }
        err_value.setValue(errorValue);

        context.declareVariableBinding(err_value);
    }

    // err:description	xs:string?	
    // A description of the error condition; an empty sequence if no 
    // description is available (for example, if the error function 
    // was called with one argument).
    private void addErrDescription(final Throwable t, final ErrorCode errorCode) throws XPathException {
        final Optional<String> errorDesc = Optional.ofNullable(errorCode.getDescription());
        final Optional<String> throwableDesc = Optional.ofNullable(t instanceof XPathException ? ((XPathException) t).getDetailMessage() : t.getMessage());
        final Expression expression = this;
        final Sequence description = errorDesc
                .<Sequence>map(
                    d -> new StringValue(expression, throwableDesc.filter(td -> !td.equals(d)).map(td -> d + (d.endsWith(".") ? " " : ". ") + td).orElse(d))
                ).orElse(
                        errorDesc.<Sequence>map(d -> new StringValue(expression, "")).orElse(Sequence.EMPTY_SEQUENCE)
                );

        final LocalVariable err_description = new LocalVariable(QN_DESCRIPTION);
        err_description.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE));
        err_description.setValue(description);
        context.declareVariableBinding(err_description);
    }

    // err:code	xs:QName	
    // The error code
    private void addErrCode(final QName errorCodeQname) throws XPathException {
        final LocalVariable err_code = new LocalVariable(QN_CODE);
        err_code.setSequenceType(new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE));
        err_code.setValue(new QNameValue(this, context, errorCodeQname));
        context.declareVariableBinding(err_code);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("try {");
        dumper.startIndent();
        tryTargetExpr.dump(dumper);
        dumper.endIndent();
        for (final CatchClause catchClause : catchClauses) {
            final Expression catchExpr = (Expression) catchClause.getCatchExpr();
            dumper.nl().display("} catch (expr) {");
            dumper.startIndent();
            catchExpr.dump(dumper);
            dumper.nl().display("}");
            dumper.endIndent();
        }
        if (finallyExpr != null) {
            dumper.nl().display("} finally {");
            dumper.startIndent();
            finallyExpr.dump(dumper);
            dumper.nl().display("}");
            dumper.endIndent();
        }
    }

    /**
     *  Extract and construct errorcode from error text.
     */
    private ErrorCode extractErrorCode(final XPathException xpe)  {

        // Get message from string
        final String message = xpe.getMessage();

        // if the 9th position has a ":" it is probably a custom error text
        if (':' == message.charAt(8)) {

            final String[] data = extractLocalName(xpe.getMessage());
            final ErrorCode errorCode = new ErrorCode(data[0], data[1]);
            LOG.debug("Parsed string '{}' for Errorcode. Qname='{}' message='{}'", xpe.getMessage(), data[0], data[1]);
            return errorCode;

        }

        // Convert xpe to Throwable
        Throwable retVal = xpe;

        // Swap with cause if present
        Throwable cause = xpe.getCause();
        if(cause != null && !(cause instanceof XPathException) ){
            retVal = cause;
        }

        // Fallback, create java error
        return new ErrorCodes.JavaErrorCode(retVal);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("try { ");
        result.append(tryTargetExpr.toString());
        for (final CatchClause catchClause : catchClauses) {
            final Expression catchExpr = (Expression) catchClause.getCatchExpr();
            result.append(" } catch (expr) { ");
            result.append(catchExpr.toString());
            result.append("}");
        }
        if (finallyExpr != null) {
            result.append(" finally { ");
            result.append(finallyExpr.toString());
            result.append("}");
        }
        return result.toString();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    @Override
    public int returnsType() {
        if (!catchClauses.isEmpty()) {
            return ((Expression) catchClauses.getFirst().getCatchExpr()).returnsType();
        }
        return tryTargetExpr.returnsType();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        tryTargetExpr.resetState(postOptimization);
        for (final CatchClause catchClause : catchClauses) {
            final Expression catchExpr = (Expression) catchClause.getCatchExpr();
            catchExpr.resetState(postOptimization);
        }
        if (finallyExpr != null) {
            finallyExpr.resetState(postOptimization);
        }
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitTryCatch(this);
    }

    /**
     *  Check if error parameter matches list of error names.
     * An '*' matches immediately.
     * 
     * @return TRUE is qname is in list, or list contains '*', else FALSE,
     */
    private boolean isErrorInList(final QName error, final List<QName> errors) {
        for (final QName lError : errors) {
            if (error.matches(lError)) {
                return true;
            }
        }
        return false;
    }

    private String[] extractLocalName(final String errorText)
            throws IllegalArgumentException {
        final int p = errorText.indexOf(':');
        if (p == Constants.STRING_NOT_FOUND) {
            return new String[]{null, errorText};
        }

        return new String[]{errorText.substring(0, p).trim(), errorText.substring(p + 1).trim()};
    }


    private void addFunctionTrace(final Throwable t) throws XPathException {
        final LocalVariable localVar = new LocalVariable(QN_XQUERY_STACK_TRACE);
        localVar.setSequenceType(new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));

        final Sequence trace;
		if(t != null && t instanceof XPathException) {
			final List<XPathException.FunctionStackElement> callStack = ((XPathException)t).getCallStack();
			if(callStack == null){
				trace = Sequence.EMPTY_SEQUENCE;
			} else {
				final Sequence result = new ValueSequence();
				for(final XPathException.FunctionStackElement elt : callStack){
					result.add(new StringValue(this, "at " + elt.toString()) );
				}
				trace = result;
			}
        } else {
            trace = Sequence.EMPTY_SEQUENCE;
        }
        localVar.setValue(trace);

        context.declareVariableBinding(localVar);
    }
    
    
    private void addJavaTrace(final Throwable t) throws XPathException  {
        final LocalVariable localVar = new LocalVariable(QN_JAVA_STACK_TRACE);
        localVar.setSequenceType(new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));

        final Sequence trace;
		if (t != null && t.getStackTrace() != null) {
            final Sequence result = new ValueSequence();
            addJavaTrace(t, result);
            trace = result;
		} else {
            trace = Sequence.EMPTY_SEQUENCE;
        }
        localVar.setValue(trace);

        context.declareVariableBinding(localVar);
    }
    
    // XQuery 4.0 PR1470/PR1599: $err:stack-trace as xs:string?
    private void addStackTrace(final Throwable t) throws XPathException {
        final LocalVariable localVar = new LocalVariable(QN_STACK_TRACE);
        localVar.setSequenceType(new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));

        final Sequence trace;
        if (t instanceof XPathException xpe && xpe.getCallStack() != null && !xpe.getCallStack().isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final XPathException.FunctionStackElement elt : xpe.getCallStack()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("at ").append(elt.toString());
            }
            trace = new StringValue(this, sb.toString());
        } else {
            trace = Sequence.EMPTY_SEQUENCE;
        }
        localVar.setValue(trace);

        context.declareVariableBinding(localVar);
    }

    // XQuery 4.0 PR493: $err:map -- map(xs:string, item()*) of all error properties
    private void addErrMap(final Throwable t, final ErrorCode errorCode, final QName errorCodeQname) throws XPathException {
        final LocalVariable localVar = new LocalVariable(QN_MAP);
        localVar.setSequenceType(new SequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE));

        final MapType errMap = new MapType(this, context);

        // code: xs:QName
        errMap.add(new StringValue(this, "code"), new QNameValue(this, context, errorCodeQname));

        // description: xs:string?
        final Optional<String> errorDesc = Optional.ofNullable(errorCode.getDescription());
        final Optional<String> throwableDesc = Optional.ofNullable(t instanceof XPathException xpe2 ? xpe2.getDetailMessage() : (t != null ? t.getMessage() : null));
        final Sequence description = errorDesc
                .<Sequence>map(d -> new StringValue(this, throwableDesc.filter(td -> !td.equals(d)).map(td -> d + (d.endsWith(".") ? " " : ". ") + td).orElse(d)))
                .orElse(Sequence.EMPTY_SEQUENCE);
        errMap.add(new StringValue(this, "description"), description);

        // value: item()*
        final Sequence errorValue;
        if (t instanceof XPathException xpe3 && xpe3.getErrorVal() != null) {
            errorValue = xpe3.getErrorVal();
        } else {
            errorValue = Sequence.EMPTY_SEQUENCE;
        }
        errMap.add(new StringValue(this, "value"), errorValue);

        // module: xs:string?
        final Sequence module;
        if (t instanceof XPathException xpe4 && xpe4.getSource() != null) {
            module = new StringValue(this, xpe4.getSource().pathOrShortIdentifier());
        } else {
            module = Sequence.EMPTY_SEQUENCE;
        }
        errMap.add(new StringValue(this, "module"), module);

        // line-number: xs:integer?
        final Sequence lineNum;
        if (t instanceof XPathException xpe5 && xpe5.getLine() > 0) {
            lineNum = new IntegerValue(this, xpe5.getLine());
        } else {
            lineNum = Sequence.EMPTY_SEQUENCE;
        }
        errMap.add(new StringValue(this, "line-number"), lineNum);

        // column-number: xs:integer?
        final Sequence colNum;
        if (t instanceof XPathException xpe6 && xpe6.getColumn() > 0) {
            colNum = new IntegerValue(this, xpe6.getColumn());
        } else {
            colNum = Sequence.EMPTY_SEQUENCE;
        }
        errMap.add(new StringValue(this, "column-number"), colNum);

        // additional: item()*
        errMap.add(new StringValue(this, "additional"), Sequence.EMPTY_SEQUENCE);

        // stack-trace: xs:string?
        final Sequence stackTrace;
        if (t instanceof XPathException xpe7 && xpe7.getCallStack() != null && !xpe7.getCallStack().isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (final XPathException.FunctionStackElement elt : xpe7.getCallStack()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("at ").append(elt.toString());
            }
            stackTrace = new StringValue(this, sb.toString());
        } else {
            stackTrace = Sequence.EMPTY_SEQUENCE;
        }
        errMap.add(new StringValue(this, "stack-trace"), stackTrace);

        localVar.setValue(errMap);
        context.declareVariableBinding(localVar);
    }

    // Local recursive function
    private void addJavaTrace(final Throwable t, final Sequence result) throws XPathException {
        final StackTraceElement[] elements = t.getStackTrace();
        result.add(new StringValue(this, "Caused by: " + t));
        for (final StackTraceElement elt : elements) {
            result.add(new StringValue(this, "at " + elt.toString()));
        }

        final Throwable cause = t.getCause();
        if (cause != null) {
            addJavaTrace(cause, result);
        }
    }


    /**
     * Data container
     *
     * TODO: catchVars is unused? Remove?
     */
    public static class CatchClause {
        private final List<QName> catchErrorList;
        private final List<QName> catchVars;
        private final Expression catchExpr;

        public CatchClause(final List<QName> catchErrorList, final List<QName> catchVars, final Expression catchExpr) {
            this.catchErrorList = catchErrorList;
            this.catchVars = catchVars;
            this.catchExpr = catchExpr;
        }

        public List<QName> getCatchErrorList() {
            return catchErrorList;
        }

        public Expression getCatchExpr() {
            return catchExpr;
        }

        public List<QName> getCatchVars() {
            return catchVars;
        }
    }
}
