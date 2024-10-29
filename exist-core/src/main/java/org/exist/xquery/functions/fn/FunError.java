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
package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunError extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(FunError.class);

    public final static FunctionSignature[] signature = {
        new FunctionSignature(
            new QName("error", Function.BUILTIN_FUNCTION_NS),
            "Indicates that an irrecoverable error has occurred. " +
            "The script will terminate immediately with an exception using " +
            "the default qname, 'http://www.w3.org/2004/07/xqt-errors#err:FOER0000', " +
            "and the default error message, 'An error has been raised by the query'.",
            null,
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        ),
        new FunctionSignature(
            new QName("error", Function.BUILTIN_FUNCTION_NS),
            "Indicates that an irrecoverable error has occurred. " +
            "The script will terminate immediately with an exception using " +
            "$qname and the default message, 'An error has been raised by the query'.",
            new SequenceType[] {
                new FunctionParameterSequenceType("qname", Type.QNAME,
                    Cardinality.ZERO_OR_ONE, "The qname")
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        ),
        new FunctionSignature(
            new QName("error", Function.BUILTIN_FUNCTION_NS),
            "Indicates that an irrecoverable error has occurred. " +
            "The script will terminate immediately with an exception using " +
            "$qname and $message.",
            new SequenceType[] {
                new FunctionParameterSequenceType("qname", Type.QNAME,
                    Cardinality.ZERO_OR_ONE, "The qname"),
                new FunctionParameterSequenceType("message", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The message")
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
        new FunctionSignature(
            new QName("error", Function.BUILTIN_FUNCTION_NS),
            "Indicates that an irrecoverable error has occurred. " +
            "The script will terminate immediately with an exception using " +
            "$qname and $message with $error-object appended.",
            new SequenceType[] {
                new FunctionParameterSequenceType("qname", Type.QNAME,
                    Cardinality.ZERO_OR_ONE, "The qname"),
                new FunctionParameterSequenceType("message", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The message"),
                new FunctionParameterSequenceType("error-object", Type.ITEM,
                    Cardinality.ZERO_OR_MORE, "The error object")
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
    };

    public FunError(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public final static ErrorCode DEFAULT_ERROR = ErrorCodes.FOER0000;
    public static final String DEFAULT_DESCRIPTION = "An error has been raised by the query";

    @Override
    public int returnsType() {
        return Type.EMPTY_SEQUENCE;
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        // Define default values
        ErrorCode errorCode = DEFAULT_ERROR;
        String errorDesc = DEFAULT_DESCRIPTION;
        Sequence errorVal = Sequence.EMPTY_SEQUENCE;
        // Enter if one or more parameters are supplied
        if (args.length > 0) {
            // If there are 2 arguments or more supplied
            // use 2nd argument for error description
            if (args.length > 1) {
                errorDesc = args[1].getStringValue();
            }
            // If first argument is not empty, get qname from argument
            // and construct error code
            if (!args[0].isEmpty()) {
                QName errorQName = ((QNameValue) args[0].itemAt(0)).getQName();
                String prefix = errorQName.getPrefix();
                if (prefix==null){
                    final String ns = errorQName.getNamespaceURI();
                    prefix = getContext().getPrefixForURI(ns);
                    errorQName = new QName(errorQName.getLocalPart(), errorQName.getNamespaceURI(), prefix);
                }
                errorCode = new ErrorCode(errorQName, errorDesc);
            }
            // If there is a third argument, use it.
            if (args.length == 3) {
                errorVal = args[2];
            }
        }

        if (LOG.isTraceEnabled()) {
            logger.trace("{}: {}", errorDesc, errorCode.toString());
        }

        throw new XPathException(this, errorCode, errorDesc, errorVal);
    }
}
