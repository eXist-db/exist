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

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import java.math.RoundingMode;

/**
 * Base class for rounding mode functions
 *
 * Implements the eval function which knows how to round,
 * but defers to the subclass for the {@link RoundingMode} to use.
 */
abstract class FunRoundBase extends BasicFunction {

    public FunRoundBase(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public int returnsType() {
        return Type.NUMBER;
    }

    abstract protected RoundingMode getFunctionRoundingMode(NumericValue value);

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Item item = args[0].itemAt(0);
        final NumericValue value;
        if (item instanceof NumericValue) {
            value = (NumericValue) item;
        } else {
            value = (NumericValue) item.convertTo(Type.NUMBER);
        }

        final RoundingMode roundingMode = getFunctionRoundingMode(value);

        if (args.length > 1) {
            final Item precisionItem = args[1].itemAt(0);
            if (precisionItem instanceof IntegerValue) {
                final IntegerValue precision = (IntegerValue) precisionItem;
                return value.round(precision, roundingMode);
            }
        }

        return value.round(IntegerValue.ZERO, roundingMode);
    }

}
