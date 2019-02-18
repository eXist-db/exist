/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * @author wolf
 */
public class CatchFunction extends Function {

    protected static final Logger logger = LogManager.getLogger(CatchFunction.class);
    
    public final static FunctionSignature signature =
            new FunctionSignature(
                new QName("catch", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                            "This function corresponds to a try-catch statement in Java. The code block "
                            + "in $try-code-blocks will be put inside a try-catch statement. If an exception "
                            + "is thrown while executing $try-code-blocks, the function checks the name of "
                            + "the exception and calls $catch-code-blocks if it matches one of "
                            + "the fully qualified Java class names specified in $java-classnames. "
                            + "A value of \"*\" in $java-classnames will catch all java exceptions. "
                            + "Inside the catch code block, the variable $util:exception will be bound to the "
                            + "java class name of the exception, "
                            + "$util:exception-message will be bound to the message produced by the exception, "
                            + "and $util:error-code will be bound to the xs:QName error code.",

                new SequenceType[]{
                    new FunctionParameterSequenceType("java-classnames", Type.STRING, Cardinality.ONE_OR_MORE, "The list of one or more fully qualified Java class names.  An entry of '*' will catch all java exceptions."),
                    new FunctionParameterSequenceType("try-code-blocks", Type.ITEM, Cardinality.ZERO_OR_MORE, "The code blocks that will be put inside of a the try part of the try-catch statement."),
                    new FunctionParameterSequenceType("catch-code-blocks", Type.ITEM, Cardinality.ZERO_OR_MORE, "The code blocks that will be will called if the catch matches one of the $java-classnames")
                },
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results from the try-catch"),
                "Use the XQuery 3.0 try/catch expression instead."
            );

    /**
     * @param context
     */
    public CatchFunction(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {

        // Get exception classes
        final Sequence exceptionClasses = getArgument(0).eval(contextSequence, contextItem);

        Sequence result = null;
        // Try to evaluate try-code
        try {
            context.pushDocumentContext();
            final LocalVariable mark = context.markLocalVariables(false);

            try {
                // Actually execute try-code
                result = getArgument(1).eval(contextSequence, contextItem);
                return result;
            } finally {
                context.popDocumentContext();
                context.popLocalVariables(mark, result);
            }

        // Handle exception
        } catch (final Exception expException) {

            logger.debug("Caught exception in util:catch: " + expException.getMessage());
            if (!(expException instanceof XPathException)) {
                logger.warn("Exception: " + expException.getMessage(), expException);
            }

//            context.popDocumentContext();
            context.getWatchDog().reset();

            // Iterate over all exception parameters
            for (final SequenceIterator i = exceptionClasses.iterate(); i.hasNext();) {
                final Item currentItem = i.nextItem();
                try {
                    // Get value of exception argument
                    final String exClassName = currentItem.getStringValue();
                    Class<?> exClass = null;

                    // Get exception class, if available
                    if (!"*".equals(exClassName)) {
                        exClass = Class.forName(currentItem.getStringValue());
                    }

                    // If value is '*' or if class actually matches
                    if ("*".equals(exClassName) 
                            || exClass.getName().equals(expException.getClass().getName())
                            || exClass.isInstance(expException)) {

                        logger.debug("Calling exception handler to process " + expException.getClass().getName());

                        // Make exception name and message available to query
                        final UtilModule myModule =
                                (UtilModule) context.getModule(UtilModule.NAMESPACE_URI);
                        myModule.declareVariable(UtilModule.EXCEPTION_QNAME, 
                                                 new StringValue(expException.getClass().getName()));
                        myModule.declareVariable(UtilModule.EXCEPTION_MESSAGE_QNAME, 
                                                 new StringValue(expException.getMessage()));
                        final QName errorCode;
                        if(expException instanceof XPathException) {
                            errorCode = ((XPathException)expException).getErrorCode().getErrorQName();
                        } else {
                            errorCode = ErrorCodes.ERROR.getErrorQName();
                        }
                        myModule.declareVariable(UtilModule.ERROR_CODE_QNAME, new QNameValue(context, errorCode));

                        // Execute catch-code
                        return getArgument(2).eval(contextSequence, contextItem);
                    }

                } catch (final Exception e2) {
                    if (e2 instanceof XPathException) {
                        throw (XPathException) e2;

                    } else {
                        throw new XPathException(this, "Error in exception handler: " + e2.getMessage(), expException);
                    }
                }
            }

            // this type of exception is not caught: throw again
            if (expException instanceof XPathException) {
                throw (XPathException) expException;
            }
            
            throw new XPathException(this, expException);
        }
    }

    @Override
    public int returnsType() {
        return getArgument(1).returnsType();
    }

    @Override
    public int getCardinality() {
        return getArgument(1).getCardinality();
    }
}
