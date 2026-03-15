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
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements XQuery 4.0 fn:message.
 *
 * Similar to fn:trace but returns empty-sequence() instead of passing through values.
 * Outputs the input values (and optional label) to the log.
 */
public class FnMessage extends BasicFunction {

    private static final Logger LOG = LogManager.getLogger(FnMessage.class);

    public static final FunctionSignature[] FN_MESSAGE = {
            new FunctionSignature(
                    new QName("message", Function.BUILTIN_FUNCTION_NS),
                    "Outputs values to the log and returns empty sequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The values to output")
                    },
                    new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "empty sequence")),
            new FunctionSignature(
                    new QName("message", Function.BUILTIN_FUNCTION_NS),
                    "Outputs values to the log with a label and returns empty sequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The values to output"),
                            new FunctionParameterSequenceType("label", Type.STRING, Cardinality.ZERO_OR_ONE, "Optional label for the output")
                    },
                    new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "empty sequence"))
    };

    public FnMessage(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input = args[0];
        final String label = (args.length > 1 && !args[1].isEmpty()) ? args[1].getStringValue() : null;

        final String value = input.getStringValue();
        if (label != null && !label.isEmpty()) {
            LOG.info("{}: {}", label, value);
        } else {
            LOG.info("{}", value);
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
