/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunCount extends Function {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("count", Function.BUILTIN_FUNCTION_NS),
                    "Returns the number of items in the argument sequence, $items.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("items", Type.ITEM,
                                    Cardinality.ZERO_OR_MORE, "The items")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ONE,
                            "The number of items in the argument sequence")
            );

    public FunCount(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public int returnsType() {
        return Type.INTEGER;
    }

    @Override
    public int getDependencies() {
        if (getArgumentCount() == 0) {
            return Dependency.CONTEXT_SET;
        }
        return getArgument(0).getDependencies();
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
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

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        final Sequence result;
        if (getArgumentCount() == 0) {
            result = IntegerValue.ZERO;
        } else {
            final Sequence seq = getArgument(0).eval(contextSequence);
            result = new IntegerValue(seq.getItemCountLong());
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }
}
