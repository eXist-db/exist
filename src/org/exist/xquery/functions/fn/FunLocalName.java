/* eXist Open Source Native XML Database
 * Copyright (C) 2000-2009,  the eXist team
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
 * $Id$
 */

package org.exist.xquery.functions.fn;

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
 * Built-in function fn:local-name().
 */
public class FunLocalName extends Function {

    private static final String FUNCTION_DESCRIPTION =
            "Returns the local part of the name of $arg as an xs:string that " +
                    "will either be the zero-length string or will have the lexical form of an xs:NCName.\n\n" +
                    "If the argument is omitted, it defaults to the context item (.). " +
                    "The behavior of the function if the argument is omitted is exactly " +
                    "the same as if the context item had been passed as the argument.\n\n" +
                    "The following errors may be raised: if the context item is undefined " +
                    "[err:XPDY0002]XP; if the context item is not a node [err:XPTY0004]XP.\n\n" +
                    "If the argument is supplied and is the empty sequence, the function " +
                    "returns the zero-length string.\n\n" +
                    "If the target node has no name (that is, if it is a document node, a " +
                    "comment, or a text node), the function returns the zero-length string.\n\n" +
                    "Otherwise, the value returned will be the local part of the expanded-QName " +
                    "of the target node (as determined by the dm:node-name accessor in Section " +
                    "5.11 node-name AccessorDM. This will be an xs:string whose lexical form is an xs:NCName.";


    public static final FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("local-name", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION,
                    null,
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the local name")
            ),
            new FunctionSignature(
                    new QName("local-name", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("arg", Type.NODE, Cardinality.ZERO_OR_ONE, "The node to retrieve the local name from")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the local name")
            )
    };

    public FunLocalName(final XQueryContext context, final FunctionSignature signature) {
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

        final Item item;
        // check if the node is passed as an argument or should be taken from
        // the context sequence
        if (getArgumentCount() > 0) {
            final Sequence seq = getArgument(0).eval(contextSequence);
            if (!seq.isEmpty()) {
                item = seq.itemAt(0);
            } else {
                item = null;
            }
        } else {
            if (contextSequence == null) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");
            }
            item = contextSequence.itemAt(0);
        }

        final Sequence result;
        if (item == null) {
            result = StringValue.EMPTY_STRING;
        } else {
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "item is not a node; got '" + Type.getTypeName(item.getType()) + "'");
            }

            //TODO : how to improve performance ?
            final Node n = ((NodeValue) item).getNode();
            final String localName = n.getLocalName();
            if (localName != null) {
                result = new StringValue(localName);
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
