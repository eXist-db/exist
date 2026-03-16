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

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * fn:collation() — Returns the default collation URI.
 * fn:collation-available($uri) — Returns true if the collation is supported.
 */
public class FnCollation extends BasicFunction {

    public static final FunctionSignature FN_COLLATION = new FunctionSignature(
            new QName("collation", Function.BUILTIN_FUNCTION_NS),
            "Returns the URI of the default collation.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                    "The default collation URI"));

    public static final FunctionSignature FN_COLLATION_AVAILABLE = new FunctionSignature(
            new QName("collation-available", Function.BUILTIN_FUNCTION_NS),
            "Returns true if the specified collation is supported.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("uri", Type.STRING,
                            Cardinality.EXACTLY_ONE, "The collation URI")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    "true if the collation is supported"));

    public FnCollation(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("collation")) {
            final String defaultCollation = context.getDefaultCollation();
            return new StringValue(this, defaultCollation != null ? defaultCollation
                    : org.exist.util.Collations.UNICODE_CODEPOINT_COLLATION_URI);
        } else {
            // collation-available
            final String uri = args[0].getStringValue();
            try {
                context.getCollator(uri);
                return BooleanValue.TRUE;
            } catch (final XPathException e) {
                return BooleanValue.FALSE;
            }
        }
    }
}
