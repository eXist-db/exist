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

import java.util.Comparator;
import java.util.TreeSet;

import com.ibm.icu.text.Collator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.ValueComparison;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:distinct-values standard library function.
 * 
 * @author wolf
 * @author perig
 */
public class FunDistinctValues extends CollatingFunction {

    protected static final Logger logger = LogManager.getLogger(FunDistinctValues.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("distinct-values", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
            "Returns a sequence where duplicate values of $atomic-values, " +
            "based on value equality, have been deleted.",
            new SequenceType[] {
                new FunctionParameterSequenceType("atomic-values", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_MORE, "The atomic values")
            },
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE,
                "the distinct values sequence")
        ),
        new FunctionSignature(
            new QName("distinct-values", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
            "Returns a sequence where duplicate values of $atomic-values, " +
            "based on value equality specified by collation $collation-uri, " + 
            "have been deleted.",
            new SequenceType[] { 
                new FunctionParameterSequenceType("atomic-values", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_MORE, "The atomic values"),
                new FunctionParameterSequenceType("collation-uri", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The collation URI")
            },
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE,
                "the distinct values sequence")
        )
    };

    public FunDistinctValues(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#returnsType()
     */
    public int returnsType() {
        return Type.ANY_ATOMIC_TYPE;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
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
        final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
        final Collator collator = getCollator(contextSequence, contextItem, 2);		
        final TreeSet<AtomicValue> set = new TreeSet<>(new ValueComparator(collator));
        final ValueSequence result = new ValueSequence();
        Item item;
        AtomicValue value;
        boolean hasAlreadyNaN = false;
        for (final SequenceIterator i = seq.iterate(); i.hasNext();) {
            item = i.nextItem();
            value = item.atomize();
            if (!set.contains(value)) {
                if (Type.subTypeOfUnion(value.getType(), Type.NUMERIC)) {
                    if (((NumericValue)value).isNaN()) {
                        //although NaN does not equal itself, if $arg 
                        //contains multiple NaN values a single NaN is returned.
                        if (!hasAlreadyNaN) {
                            set.add(value);
                            result.add(value);	
                            hasAlreadyNaN = true;
                        }
                    } else {
                        set.add(value);
                        result.add(value);
                    }
                } else {
                    set.add(value);
                    result.add(value);
                }
            }
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
        return result;
    }

    public final static class ValueComparator implements Comparator<AtomicValue> {
        Collator collator;

        public ValueComparator(Collator collator) {
            this.collator = collator;
        }

        /* (non-Javadoc)
         * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
         */
        public int compare(AtomicValue o1, AtomicValue o2) {
            try {
                if (ValueComparison.compareAtomic(collator, o1, o2,
                        StringTruncationOperator.NONE, Comparison.EQ))
                    {return Constants.EQUAL;}
                else if (ValueComparison.compareAtomic(collator, o1, o2,
                        StringTruncationOperator.NONE, Comparison.LT))
                    {return Constants.INFERIOR;}
                else if (ValueComparison.compareAtomic(collator, o1, o2,
                        StringTruncationOperator.NONE, Comparison.GT))
                    {return Constants.SUPERIOR;}
                //Fallback
                else
                    {return o1.compareTo(collator, o2);}
            } catch (final XPathException e) {
                //Values that cannot be compared, i.e. the eq operator
                //is not defined for their types, are considered to be distinct
                return Constants.INFERIOR;
            }
        }
    }
}
