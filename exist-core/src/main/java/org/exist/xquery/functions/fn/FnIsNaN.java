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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements fn:is-NaN (XQuery 4.0).
 *
 * Returns true if the argument is the xs:float or xs:double value NaN.
 */
public class FnIsNaN extends BasicFunction {

    public static final FunctionSignature FN_IS_NAN = new FunctionSignature(
            new QName("is-NaN", Function.BUILTIN_FUNCTION_NS),
            "Returns true if the argument is the xs:float or xs:double value NaN.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The value to test")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the value is NaN"));

    public FnIsNaN(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Item item = args[0].itemAt(0);
        final int type = item.getType();
        if (type == Type.DOUBLE) {
            return BooleanValue.valueOf(Double.isNaN(((DoubleValue) item).getValue()));
        } else if (type == Type.FLOAT) {
            return BooleanValue.valueOf(Float.isNaN(((FloatValue) item).getValue()));
        }
        return BooleanValue.FALSE;
    }
}
