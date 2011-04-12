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

import org.exist.dom.QName;
import org.exist.xquery.ErrorCodes.EXistErrorCode;

import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.ErrorCodes.JavaErrorCode;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

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
            ErrorCode ec = new EXistErrorCode("EXXQDY0002", "The try-catch expression is supported "
                    + "for xquery version \"3.0\" and later.");
            throw new XPathException(ec, ec.getDescription(), null);
        }

        try {
            // Evaluate 'try' expression
            Sequence tryTargetSeq = tryTargetExpr.eval(contextSequence, contextItem);
            return tryTargetSeq;

        } catch (Throwable throwable) { 

            ErrorCode errorCode = null;

            if(throwable instanceof XPathException){
                // Get errorcode from nicely thrown xpathexception
                XPathException xpe = (XPathException)throwable;
                errorCode = xpe.getErrorCode();

                // if no errorcode is found, reconstruct by parsing the error text.
                if (errorCode == null) {
                    errorCode = extractErrorCode((XPathException)xpe);
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
            LocalVariable mark0 = context.markLocalVariables(false);

            try {
                // flag used to escape loop when errorcode has matched
                boolean errorMatched = false;

                // Iterate on all catch clauses
                for (CatchClause catchClause : catchClauses) {

                    if (isErrorInList(errorCodeQname, catchClause.getCatchErrorList()) && !errorMatched) {

                        errorMatched = true;

                        // Get catch variables
                        List<QName> catchVars = (List<QName>) catchClause.getCatchVars();
                        LocalVariable mark1 = context.markLocalVariables(false);
                        int varPos = 1;

                        try {
                            // catch variables
                            // "(" CatchErrorCode ("," CatchErrorDesc ("," CatchErrorVal)?)? ")"
                            for (QName catchVar : catchVars) {

                                // reset qname and prefix
                                catchVar.setPrefix(null);
                                catchVar.setNamespaceURI("");

                                LocalVariable localVar = new LocalVariable(catchVar);

                                // This should be in order of existance
                                // xs:QName, xs:string?, and item()* respectively.
                                switch (varPos) {
                                    case 1:
                                        // Error code: qname
                                        localVar.setSequenceType(new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE));
                                        QNameValue qnv = new QNameValue(context, catchVar);
                                        localVar.setValue(new StringValue(errorCode.getErrorQName().getStringValue()));
                                        break;

                                    case 2:
                                        // Error description : optional string
                                        localVar.setSequenceType(new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
                                        StringValue sv = new StringValue(errorCode.getDescription());
                                        localVar.setValue(sv);
                                        break;

                                    case 3:
                                        // Error value : optional item
                                        localVar.setSequenceType(new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
                                        if (throwable instanceof XPathException) {
                                            // Get errorcode from exception
                                            XPathException xpe = (XPathException) throwable;
                                            Sequence sequence = xpe.getErrorVal();
                                            if (sequence == null) {
                                                // TODO setting an empty sequence does not work, it does
                                                // not make the variable visible
                                                sequence = Sequence.EMPTY_SEQUENCE;
                                            }
                                            localVar.setValue(sequence);

                                        } else {
                                            // fill data from throwable object
                                            StringValue value = new StringValue(getStackTrace(throwable));
                                            localVar.setValue(value);
                                        }
                                        break;
                                }
                                context.declareVariableBinding(localVar);
                                varPos++;

                            } // Var catch variables


                            // Evaluate catch expression
                            catchResultSeq = ((Expression) catchClause.getCatchExpr()).eval(contextSequence, contextItem);

                        } finally {
                            context.popLocalVariables(mark1);
                        }

                    } else {
                        // if in the end nothing is set, rethrow
                    }
                } // for catch clauses

                // If an error hasn't been catched, throw new one
                if (!errorMatched) {
                    LOG.error(throwable);
                    throw new XPathException(throwable);
                }


            } finally {
                context.popLocalVariables(mark0);
            }

            return catchResultSeq;

        } finally {
            context.expressionEnd(this);
        }
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

    private String getStackTrace(Throwable t){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();

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
