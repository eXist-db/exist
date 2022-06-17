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
package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.*;
import org.exist.xquery.Constants.Comparison;

import java.util.List;

/**
 * Represents a function item, i.e. a reference to a function that can be called dynamically.
 *
 * @author wolf
 */
public class FunctionReference extends AtomicValue implements AutoCloseable {

    private final static Logger LOG = LogManager.getLogger(FunctionReference.class);

    protected final FunctionCall functionCall;

    public FunctionReference(final FunctionCall functionCall) {
        this.functionCall = functionCall;
    }

    public FunctionCall getCall() {
        return functionCall;
    }

    /**
     * Get the signature of the function.
     *
     * @return signature of this function
     */
    public FunctionSignature getSignature() {
        return functionCall.getSignature();
    }

    /**
     * Calls {@link FunctionCall#analyze(AnalyzeContextInfo)}.
     *
     * @param contextInfo current context information
     * @throws XPathException in case of static error
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        functionCall.analyze(contextInfo);
        if (functionCall.getContext().optimizationsEnabled()) {
            final Optimizer optimizer = new Optimizer(functionCall.getContext());
            functionCall.accept(optimizer);
            if (optimizer.hasOptimized()) {
                functionCall.resetState(true);
                functionCall.analyze(contextInfo);
            }
        }
    }

    /**
     * Calls {@link FunctionCall#eval(Sequence)}.
     *
     * @param contextSequence the input sequence
     * @return evaluation result of the function call
     * @throws XPathException in case of dynamic error
     */
    public Sequence eval(Sequence contextSequence) throws XPathException {
        return functionCall.eval(contextSequence);
    }

    /**
     * Calls {@link FunctionCall#eval(Sequence, Item)}.
     *
     * @param contextSequence the input sequence
     * @param contextItem optional: the current context item
     * @return evaluation result of the function call
     * @throws XPathException in case of dynamic error
     */
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return functionCall.eval(contextSequence, contextItem);
    }

    /**
     * Calls {@link FunctionCall#evalFunction(Sequence, Item, Sequence[])}.
     *
     * @param contextSequence the input sequence
     * @param contextItem optional: the current context item
     * @param seq array of parameters to be passed to the function
     * @return evaluation result of the function call
     * @throws XPathException in case of dynamic error
     */
    public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq) throws XPathException {
        return functionCall.evalFunction(contextSequence, contextItem, seq);
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        functionCall.setArguments(arguments);
    }

    public void setContext(XQueryContext context) {
        functionCall.setContext(context);
    }

    @Override
    public void close() {
        resetState(false);
    }

    public void resetState(boolean postOptimization) {
//        LOG.debug("Resetting state of function item " + functionCall.getSignature().toString());
        functionCall.resetState(postOptimization);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    public int getType() {
        return Type.FUNCTION_REFERENCE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getStringValue()
     */
    public String getStringValue() throws XPathException {
        return "";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#convertTo(int)
     */
    public AtomicValue convertTo(int requiredType) throws XPathException {
        if (requiredType == Type.FUNCTION_REFERENCE) {
            return this;
        }
        throw new XPathException(ErrorCodes.FORG0001, "cannot convert function reference to " + Type.getTypeName(requiredType));
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(ErrorCodes.FORG0006, "Effective boolean value is not defined for a FunctionReference");
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other)
            throws XPathException {
        throw new XPathException("cannot compare function reference to " + Type.getTypeName(other.getType()));
    }

    public int compareTo(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("cannot compare function reference to " + Type.getTypeName(other.getType()));
    }

    public AtomicValue max(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Invalid argument to aggregate function: cannot compare function references");
    }

    public AtomicValue min(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Invalid argument to aggregate function: cannot compare function references");
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        throw new XPathException(ErrorCodes.FOTY0013, "A function item other than an array cannot be atomized");
    }

    @Override
    public String toString() {
        return "anonymous-function#" + functionCall.getArgumentCount();
    }
}
