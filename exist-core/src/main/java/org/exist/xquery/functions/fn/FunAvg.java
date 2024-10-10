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
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunAvg extends Function {

    protected static final Logger logger = LogManager.getLogger(FunAvg.class);

    private boolean gotInfinity = false;

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("avg", Function.BUILTIN_FUNCTION_NS),
            "Returns the average of the values in the input sequence $values, " +
            "that is, the sum of the values divided by the number of values.",
            new SequenceType[] {
                new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_MORE, "The values")},
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE,
                "The average of the values in the input sequence"));

    public FunAvg(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT ITEM", contextItem.toSequence());}
        }
        Sequence result;
        final Sequence inner = getArgument(0).eval(contextSequence, contextItem);
        if (inner.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            final SequenceIterator iter = inner.iterate();
            Item item = iter.nextItem();
            AtomicValue value = item.atomize();
            //Any values of type xdt:untypedAtomic are cast to xs:double
            if (value.getType() == Type.UNTYPED_ATOMIC) 
                {value = value.convertTo(Type.DOUBLE);}
            if (!(value instanceof ComputableValue sum)) {
                throw new XPathException(this, ErrorCodes.FORG0006,
                    Type.getTypeName(value.getType()) + "(" + value + ") " +
                    "can not be an operand in a sum", value);
            }
            //Set the first value
            while (iter.hasNext()) {
                item = iter.nextItem();
                value = item.atomize();
                //Any value of type xdt:untypedAtomic are cast to xs:double
                if (value.getType() == Type.UNTYPED_ATOMIC) 
                    {value = value.convertTo(Type.DOUBLE);}
                if (!(value instanceof ComputableValue)) {
                    throw new XPathException(this, ErrorCodes.FORG0006, "" +
                        Type.getTypeName(value.getType()) + "(" + value +
                        ") can not be an operand in a sum", value);
                }
                if (Type.subTypeOfUnion(value.getType(), Type.NUMERIC)) {
                    if (((NumericValue)value).isInfinite())
                        {gotInfinity = true;}
                    if (((NumericValue)value).isNaN()) {
                        sum = DoubleValue.NaN;
                        break;
                    }
                }
                try {
                    sum = (ComputableValue) sum.promote(value);
                    //Aggregate next values	
                    sum = sum.plus((ComputableValue) value);
                } catch(final XPathException e) {
                    throw new XPathException(this, ErrorCodes.FORG0006, e.getMessage());
                }
            }
            result = sum.div(new IntegerValue(this, inner.getItemCount()));
        }
        if (!gotInfinity) {
            if (Type.subTypeOfUnion(result.getItemType(), Type.NUMERIC) &&
                ((NumericValue)result).isInfinite()) {
                //Throw an overflow exception here since we get an infinity 
                //whereas is hasn't been provided by the sequence
                //TODO ? -pb
            }
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
        return result;
    }
}
