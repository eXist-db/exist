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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * xpath-library function: name()
 */
public class FunName extends Function {

    private static final String FUNCTION_DESCRIPTION_0_PARAM =
            "Returns the name of the context item as an xs:string that is either " +
                    "the zero-length string, or has the lexical form of an xs:QName.\n\n";
    private static final String FUNCTION_DESCRIPTION_1_PARAM =
            "Returns the name of $arg as an xs:string that is either " +
                    "the zero-length string, or has the lexical form of an xs:QName.\n\n" +
                    "If the argument is omitted, it defaults to the context item (.). ";
    private static final String FUNCTION_DESCRIPTION_COMMON =
            "The behavior of the function if the argument is omitted is exactly " +
                    "the same as if the context item had been passed as the argument.\n\n" +

                    "The following errors may be raised: if the context item is undefined " +
                    "[err:XPDY0002]XP; if the context item is not a node [err:XPTY0004]XP.\n\n" +

                    "If the argument is supplied and is the empty sequence, the function " +
                    "returns the zero-length string.\n\n" +

                    "If the target node has no name (that is, if it is a document node, a comment, " +
                    "a text node, or a namespace binding having no name), the function returns " +
                    "the zero-length string.\n\n" +

                    "Otherwise, the value returned is fn:string(fn:node-name($arg)).";

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("name", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION_0_PARAM + FUNCTION_DESCRIPTION_COMMON,
                    new SequenceType[0],
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the name")
            ),
            new FunctionSignature(
                    new QName("name", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_COMMON,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("arg", Type.NODE, Cardinality.ZERO_OR_ONE, "The input node")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the name")
            )
    };

    public FunName(final XQueryContext context, final FunctionSignature signature) {
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
            result = StringValue.EMPTY_STRING;
        } else {
            final Item item = seq.itemAt(0);
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "item is not a node; got '" + Type.getTypeName(item.getType()) + "'");
            }

            //TODO : how to improve performance ?
            final Node n = ((NodeValue) item).getNode();
            if (n instanceof INode) {
                result = new StringValue(((INode) n).getQName().getStringValue());
            } else {
                result = StringValue.EMPTY_STRING;
            }
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }
}
