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
import org.exist.xquery.Atomize;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunData extends Function {

    public static final QName qnData = new QName("data", Function.BUILTIN_FUNCTION_NS);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            qnData,
            "Atomizes the context item, replacing all nodes in the sequence by their typed values.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE,
                Cardinality.ZERO_OR_MORE, "the atomic values of the items in $items")
        ),
        new FunctionSignature(
            qnData,
            "Atomizes the sequence $items, replacing all nodes in the sequence by their typed values.",
            new SequenceType[] {
                new FunctionParameterSequenceType("items", Type.ITEM,
                    Cardinality.ZERO_OR_MORE, "The items")
            },
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE,
                Cardinality.ZERO_OR_MORE, "the atomic values of the items in $items")
        )
    };
    public FunData(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.value.Sequence,
     * org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());
            }
        }

		final Sequence items;
        if (getArgumentCount() == 1) {
            items = Atomize.atomize(getArgument(0).eval(contextSequence, contextItem));
        } else {
            if (contextItem != null) {
                items = Atomize.atomize(contextItem.toSequence());
            } else {
                items = Sequence.EMPTY_SEQUENCE;
            }
        }

        final Sequence result;
        if (items.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else if (items.hasOne()) {
            result = items;
        } else {
            result = new ValueSequence(items);
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

		return result;
    }
}
