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
package org.exist.xquery.functions.fn;

import org.exist.dom.INode;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;


/**
 * Implements the fn:node-name library function.
 *
 * @author wolf
 */
public class FunNodeName extends Function {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("node-name", Function.BUILTIN_FUNCTION_NS),
                    "Returns an expanded-QName for node kinds that can have names. For other kinds " +
                            "of nodes it returns the empty sequence. If the context item is the empty sequence, the " +
                            "empty sequence is returned.",
                    new SequenceType[0],
                    new FunctionReturnSequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE, "the expanded QName")),

            new FunctionSignature(
                    new QName("node-name", Function.BUILTIN_FUNCTION_NS),
                    "Returns an expanded-QName for node kinds that can have names. For other kinds " +
                            "of nodes it returns the empty sequence. If $arg is the empty sequence, the " +
                            "empty sequence is returned.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("arg", Type.NODE, Cardinality.ZERO_OR_ONE, "The input node")
                    },
                    new FunctionReturnSequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE, "the expanded QName"))
    };

    public FunNodeName(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        //If we have one argument, we take it into account
        final Sequence seq;
        if (getSignature().getArgumentCount() > 0) {
            seq = getArgument(0).eval(contextSequence, contextItem);

        } else {
            //Otherwise, we take the context sequence and we iterate over it
            seq = contextSequence;
        }

        if (seq == null) {
            throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");
        }

        final Sequence result;
        if (seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            final Item item = seq.itemAt(0);
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "item is not a node; got '" + Type.getTypeName(item.getType()) + "'");
            }

            //TODO : how to improve performance ?
            final Node n = ((NodeValue) item).getNode();
            //Returns an expanded-QName for node kinds that can have names.
            if (n instanceof INode) {
                final QName qn = ((INode) n).getQName();
                if (qn.equals(QName.EMPTY_QNAME)) {
                    result = Sequence.EMPTY_SEQUENCE;
                } else {
                    result = new QNameValue(context, qn);
                }
                //For other kinds of nodes it returns the empty sequence.
            } else {
                result = Sequence.EMPTY_SEQUENCE;
            }
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }
}
