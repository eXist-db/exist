/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id: TryCatchExpression.java 13700 2011-01-30 13:34:44Z dizzzz $
 */
package org.exist.xquery;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
                
import org.apache.log4j.Logger;

import org.exist.Namespaces;
import org.exist.dom.QName;

import org.exist.security.xacml.XACMLSource;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.ErrorCodes.JavaErrorCode;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.*;

/**
 * XQuery 1.1+ try {...} catch{...} expression.
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @author Leif-Jöran Olsson <ljo@exist-db.org>
 * @author Dannes Wessels <dannes@exist-db.org>
 */
public class TryCatchExpression extends AbstractExpression {

    protected static final Logger LOG = Logger.getLogger(TryCatchExpression.class);

    private final Expression tryTargetExpr;
    private final List<CatchClause> catchClauses = new ArrayList<CatchClause>();

    /**
     *  Constructor.
     * 
     * @param context   Xquery context
     * @param tryTargetExpr Expression to be evaluated
     */
    public TryCatchExpression(XQueryContext context, Expression tryTargetExpr) {
        super(context);
        this.tryTargetExpr = tryTargetExpr;
    }

    /**
     * Receive catch-clause data from parser.
     *
     * TODO: List<String> must be changed to List<QName>
     */
    public void addCatchClause(List<String> catchErrorList, List<QName> catchVars, Expression catchExpr) {
        catchClauses.add( new CatchClause(catchErrorList, catchVars, catchExpr) );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
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

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getCardinality()
     */
    @Override
    public int getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setFlags(contextInfo.getFlags() & (~IN_PREDICATE));
        contextInfo.setParent(this);
        tryTargetExpr.analyze(contextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {


        context.expressionStart(this);

        if(getContext().getXQueryVersion()<30){
            throw new XPathException(this, ErrorCodes.EXXQDY0003, "The try-catch expression is supported for xquery version \"3.0\" and later.");
        }

        try {
            // Evaluate 'try' expression
            Sequence tryTargetSeq = tryTargetExpr.eval(contextSequence, contextItem);
            return tryTargetSeq;

        } catch (Throwable throwable) { 

            ErrorCode errorCode = null;
            XPathException xpe = null;

            // fn:error throws an XPathException
            if(throwable instanceof XPathException){
                // Get errorcode from nicely thrown xpathexception
                xpe = (XPathException)throwable;
                errorCode = xpe.getErrorCode();

                // if no errorcode is found, reconstruct by parsing the error text.
                if (errorCode == null) {
                    errorCode = extractErrorCode(xpe);
                } else if (errorCode == ErrorCodes.ERROR) {
                    errorCode = extractErrorCode(xpe);
                }

            } else {
                // Get errorcode from all other errors and exceptions
                errorCode = new JavaErrorCode(throwable);
            }

            // We need the qname in the end
            QName errorCodeQname = errorCode.getErrorQName();

            // Exception in thrown, catch expression will be evaluated.
            // catchvars (CatchErrorCode (, CatchErrorDesc (, CatchErrorVal)?)? )
            // need to be retrieved as variables
            Sequence catchResultSeq = null;
            LocalVariable mark0 = context.markLocalVariables(false); // DWES: what does this do?
            
            // Register new namespace
            // DWES: 
            // when declaring "fn:error( fn:QName('http://www.w3.org/2005/xqt-errors', 'err:FOER0000') )"
            // An Exception is thrown: err:XQST0033 It is a static error if a module contains multiple bindings for the same namespace prefix.
            // DWES: should I use popLocalVariables
            context.declareInScopeNamespace(Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX, Namespaces.W3C_XQUERY_XPATH_ERROR_NS);
            context.declareInScopeNamespace(Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX, Namespaces.EXIST_XQUERY_XPATH_ERROR_NS);
            
            //context.declareInScopeNamespace(null, null);

            try {
                // flag used to escape loop when errorcode has matched
                boolean errorMatched = false;

                // Iterate on all catch clauses
                for (CatchClause catchClause : catchClauses) {
                    
                    if (isErrorInList(errorCodeQname, catchClause.getCatchErrorList()) && !errorMatched) {

                        errorMatched = true;

                        // Get catch variables
                        LocalVariable mark1 = context.markLocalVariables(false); // DWES: what does this do?
                        
                        try {  
                            // Add std errors
                            addErrCode(errorCodeQname);                          
                            addErrDescription(xpe, errorCode);
                            addErrValue(xpe); 
                            addErrModule(xpe); 
                            addErrLineNumber(xpe); 
                            addErrColumnNumber(xpe); 
                            addErrAdditional(xpe); 
                            addFunctionTrace(xpe);
                            addJavaTrace(xpe);

                            // Evaluate catch expression
                            catchResultSeq = ((Expression) catchClause.getCatchExpr()).eval(contextSequence, contextItem);
                            
                            
                        } finally {
                            context.popLocalVariables(mark1, catchResultSeq);
                        }

                    } else {
                        // if in the end nothing is set, rethrow after loop
                    }
                } // for catch clauses

                // If an error hasn't been catched, throw new one
                if (!errorMatched) {
                    LOG.error(throwable);
                    throw new XPathException(throwable);
                }


            } finally {
                context.popLocalVariables(mark0, catchResultSeq);
            }

            return catchResultSeq;

        } finally {
            context.expressionEnd(this);
        }
    }


    // err:additional	item()*	
    // Implementation-defined. This variable must be bound so that a query 
    // can reference it without raising an error. The purpose of this 
    // variable is to allow implementations to provide any additional 
    // information that might be useful.
    private void addErrAdditional(XPathException xpe) throws XPathException {
        String additional = null;
        
        QName q_additional = new QName("additional", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable err_additional = new LocalVariable( q_additional);
        err_additional.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE));
        if(additional == null){
            err_additional.setValue(Sequence.EMPTY_SEQUENCE);
        } else {
            err_additional.setValue(new StringValue("to do"));
        } 
        context.declareVariableBinding(err_additional);
    }

    // err:column-number	xs:integer?	
    // The column number within the stylesheet module of the instruction 
    // where the error occurred, or an empty sequence if the information 
    // is not available. The value may be approximate.
    private void addErrColumnNumber(XPathException xpe) throws XPathException {

        Integer column_nr = null ; 
        if (xpe != null) {
            column_nr=xpe.getColumn();
        } 
        
        QName q_column_nr = new QName("column-number", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable err_column_nr = new LocalVariable( q_column_nr);
        err_column_nr.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE));
        if(column_nr == null){
            err_column_nr.setValue(Sequence.EMPTY_SEQUENCE);
        } else {
            err_column_nr.setValue(new IntegerValue(column_nr));
        } 
        context.declareVariableBinding(err_column_nr);
    }

    // err:line-number	xs:integer?	
    // The line number within the stylesheet module of the instruction 
    // where the error occurred, or an empty sequence if the information 
    // is not available. The value may be approximate.
    private void addErrLineNumber(XPathException xpe) throws XPathException {

        Integer line_nr = null ; 
        if (xpe != null) {
            line_nr=xpe.getLine();
        } 
        
        QName q_line_nr = new QName("line-number", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable err_line_nr = new LocalVariable( q_line_nr);
        err_line_nr.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE));
        if(line_nr == null){
            err_line_nr.setValue(Sequence.EMPTY_SEQUENCE);
        } else {
            err_line_nr.setValue(new IntegerValue(line_nr));
        } 
        context.declareVariableBinding(err_line_nr);
    }

    // err:module	xs:string?	
    // The URI (or system ID) of the module containing the expression 
    // where the error occurred, or an empty sequence if the information 
    // is not available.
    private void addErrModule(XPathException xpe) throws XPathException {
      
        String module = null ; // to be defined where to get
        if (xpe != null) {
            XACMLSource src = xpe.getXACMLSource();
            if(src!=null){
                module=src.getKey();
            }
        } 
        
        QName q_module = new QName("module", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable err_module = new LocalVariable( q_module);
        err_module.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE));
        if(module == null){
            err_module.setValue(Sequence.EMPTY_SEQUENCE);
        } else {
            err_module.setValue(new StringValue(module));
        } 
        context.declareVariableBinding(err_module);
    }

    // err:value	item()*	
    // Value associated with the error. For an error raised by calling 
    // the error function, this is the value of the third  argument 
    // (if supplied).
    private void addErrValue(XPathException xpe) throws XPathException {

        QName q_value = new QName("value", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable err_value = new LocalVariable( q_value);
        err_value.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_MORE));                           
        
        if (xpe != null) {
            // Get errorcode from exception
            Sequence sequence = xpe.getErrorVal();
            if (sequence == null) {
                sequence = Sequence.EMPTY_SEQUENCE;
            }
            err_value.setValue(sequence);

        } else {
            // fill data from throwable object
            StringValue value = new StringValue(getStackTrace(xpe));
            err_value.setValue(value);
        }
        context.declareVariableBinding(err_value);
    }

    // err:description	xs:string?	
    // A description of the error condition; an empty sequence if no 
    // description is available (for example, if the error function 
    // was called with one argument).
    private void addErrDescription(XPathException xpe, ErrorCode errorCode) throws XPathException {

        String description = errorCode.getDescription();
        if (description == null && xpe != null)
            description = xpe.getDetailMessage();
        
        QName q_description = new QName("description", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable err_description = new LocalVariable( q_description);
        err_description.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE));
        if(description == null){
            err_description.setValue(Sequence.EMPTY_SEQUENCE);
        } else {
            err_description.setValue(new StringValue(description));
        } 
        context.declareVariableBinding(err_description);
    }

    // err:code	xs:QName	
    // The error code
    private void addErrCode(QName errorCodeQname) throws XPathException {

        String code = errorCodeQname.getStringValue();
        
        QName q_code = new QName("code", Namespaces.W3C_XQUERY_XPATH_ERROR_NS, Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable err_code = new LocalVariable( q_code);
        err_code.setSequenceType(new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE));
        err_code.setValue(new StringValue(code));
        context.declareVariableBinding(err_code);
    }
    

    /**
     *  Extract and construct errorcode from error text.
     */
    private ErrorCode extractErrorCode(XPathException xpe)  {

        // Get message from string
        String message = xpe.getMessage();

        // if the 9th position has a ":" it is probably a custom error text
        if (':' == message.charAt(8)) {

            String[] data = extractLocalName(xpe.getMessage());
            ErrorCode errorCode = new ErrorCode(new QName(data[0], "err"), data[1]);
            errorCode.getErrorQName().setPrefix("err");
            LOG.debug("Parsed string '" + xpe.getMessage() + "' for Errorcode. "
                    + "Qname='" + data[0] + "' message='" + data[1] + "'");
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

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("try {");
        dumper.startIndent();
        tryTargetExpr.dump(dumper);
        dumper.endIndent();
        for (CatchClause catchClause : catchClauses) {
            Expression catchExpr = (Expression) catchClause.getCatchExpr();
            dumper.nl().display("} catch (expr) {");
            dumper.startIndent();
            catchExpr.dump(dumper);
            dumper.nl().display("}");
            dumper.endIndent();
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("try { ");
        result.append(tryTargetExpr.toString());
        for (CatchClause catchClause : catchClauses) {
            Expression catchExpr = (Expression) catchClause.getCatchExpr();
            result.append(" } catch (expr) { ");
            result.append(catchExpr.toString());
            result.append("}");
        }
        return result.toString();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    @Override
    public int returnsType() {
        // fixme! /ljo
        return ((Expression) catchClauses.get(0).getCatchExpr()).returnsType();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        tryTargetExpr.resetState(postOptimization);
        for (CatchClause catchClause : catchClauses) {
            Expression catchExpr = (Expression) catchClause.getCatchExpr();
            catchExpr.resetState(postOptimization);
        }
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitTryCatch(this);
    }

    /**
     *  Check if error parameter matches list of error names.
     * An '*' matches immediately.
     * 
     * @return TRUE is qname is in list, or list contains '*', else FALSE,
     */
    private boolean isErrorInList(QName error, List<String> errors) {

        String qError = error.getStringValue();
        for (String lError : errors) {
            if ("*".equals(lError)) {
                return true;
            }

            if (qError.equals(lError)) {
                return true;
            }
        }
        return false;
    }

    private String[] extractLocalName(String errorText)
            throws IllegalArgumentException {
        int p = errorText.indexOf(':');
        if (p == Constants.STRING_NOT_FOUND) {
            return new String[]{null, errorText};
        }

        return new String[]{errorText.substring(0, p).trim(), errorText.substring(p + 1).trim()};
    }

    /**
     * Write stacktrace to String. 
     */
    private String getStackTrace(Throwable t){
		if (t == null)
			return null;
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();

    }

    private void addFunctionTrace(XPathException xpe) throws XPathException {
        
        QName q_value = new QName("xquery-stack-trace", Namespaces.EXIST_XQUERY_XPATH_ERROR_NS, Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable localVar = new LocalVariable( q_value);
        localVar.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_MORE));
       
	    if (xpe == null) {
			localVar.setValue(Sequence.EMPTY_SEQUENCE);
		} else { 
			List<XPathException.FunctionStackElement> callStack = xpe.getCallStack();
			if(callStack==null){
				localVar.setValue(Sequence.EMPTY_SEQUENCE);
				
			} else {
				Sequence result = new ValueSequence();
				for(XPathException.FunctionStackElement elt : callStack){
					result.add(new StringValue("at " + elt.toString()) );
				}
				localVar.setValue(result);  
			}
        }
        context.declareVariableBinding(localVar);
    }
    
    
    private void addJavaTrace(Throwable xpe) throws XPathException  {
        
        QName q_value = new QName("java-stack-trace", Namespaces.EXIST_XQUERY_XPATH_ERROR_NS, Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX);
        LocalVariable localVar = new LocalVariable( q_value);
        localVar.setSequenceType(new SequenceType(Type.QNAME, Cardinality.ZERO_OR_MORE));

		if (xpe == null) {
			localVar.setValue(Sequence.EMPTY_SEQUENCE);
		} else {
			StackTraceElement[] elements = xpe.getStackTrace();     
			if (elements == null) {
				localVar.setValue(Sequence.EMPTY_SEQUENCE);
				
			} else {
				Sequence result = new ValueSequence();
				addJavaTrace(xpe,result);
				localVar.setValue(result);                    
			}
        }
        context.declareVariableBinding(localVar);
    }
    
    // Local recursive function
    private void addJavaTrace(Throwable xpe, Sequence result) throws XPathException {

		if (xpe == null)
			return;
        StackTraceElement[] elements = xpe.getStackTrace();

        result.add(new StringValue("Caused by: " + xpe.toString()));
        for (StackTraceElement elt : elements) {
            result.add(new StringValue("at " + elt.toString()));
        }

        Throwable cause = xpe.getCause();
        if (cause != null) {
            addJavaTrace(cause, result);
        }

    }


    /**
     * Data container
     */
    public class CatchClause {

        private List<String> catchErrorList = null;
        private List<QName> catchVars = null;
        private Expression catchExpr = null;

        public CatchClause(List<String> catchErrorList, List<QName> catchVars, Expression catchExpr) {
            this.catchErrorList = catchErrorList;
            this.catchVars = catchVars;
            this.catchExpr = catchExpr;
        }

        public List<String> getCatchErrorList() {
            return catchErrorList;
        }

        public void setCatchErrorList(List<String> catchErrorList) {
            this.catchErrorList = catchErrorList;
        }

        public Expression getCatchExpr() {
            return catchExpr;
        }

        public void setCatchExpr(Expression catchExpr) {
            this.catchExpr = catchExpr;
        }

        public List<QName> getCatchVars() {
            return catchVars;
        }

        public void setCatchVars(List<QName> catchVars) {
            this.catchVars = catchVars;
        }
    }
}
