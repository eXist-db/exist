/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
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
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;

/**
 * xpath-library function: namespace-uri()
 */
public class FunNamespaceURI extends Function {

    private static final String FUNCTION_DESCRIPTION_0_PARAM =
            "Returns the namespace URI of the xs:QName of the context item.\n\n";
    private static final String FUNCTION_DESCRIPTION_1_PARAM =
            "Returns the namespace URI of the xs:QName of $arg.\n\n" +
                    "If the argument is omitted, it defaults to the context node (.). ";

    private static final String FUNCTION_DESCRIPTION_COMMON =
            "The behavior of the function if the argument is omitted is exactly " +
                    "the same as if the context item had been passed as the argument.\n\n" +

                    "The following errors may be raised: if the context item is undefined " +
                    "[err:XPDY0002]XP; if the context item is not a node [err:XPTY0004]XP.\n\n" +

                    "If $arg is neither an element nor an attribute node, or if it is an " +
                    "element or attribute node whose expanded-QName (as determined by the " +
                    "dm:node-name accessor in the Section 5.11 node-name AccessorDM) is " +
                    "in no namespace, then the function returns the xs:anyURI " +
                    "corresponding to the zero-length string.";

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("namespace-uri", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION_0_PARAM + FUNCTION_DESCRIPTION_COMMON,
                    new SequenceType[0],
                    new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the namespace URI"),
                    false),
            new FunctionSignature(
                    new QName("namespace-uri", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_COMMON,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("arg", Type.NODE, Cardinality.ZERO_OR_ONE, "The input node")
                    },
                    new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the namespace URI"),
                    false)
    };

    public FunNamespaceURI(final XQueryContext context, final FunctionSignature signature) {
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
            result = AnyURIValue.EMPTY_URI;
        } else {
            final Item item = seq.itemAt(0);
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node; got: " +
                        Type.getTypeName(item.getType()));
            }

            //TODO : how to improve performance ?
            final Node n = ((NodeValue) item).getNode();
            String ns = n.getNamespaceURI();
            if (ns == null) {
                ns = XMLConstants.NULL_NS_URI;
            }
            result = new AnyURIValue(ns);
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }
}
