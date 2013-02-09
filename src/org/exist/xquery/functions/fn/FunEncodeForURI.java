/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2009 The eXist Project
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
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 *
 * @author perig
 *
 */
public class FunEncodeForURI extends Function {

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("encode-for-uri", Function.BUILTIN_FUNCTION_NS),
            "Escapes reserved characters in $uri-part by replacing it " +
                "with its percent-encoded form as described in [RFC 3986]. " +
                "If $uri-part is the empty sequence, returns the zero-length string.",
            new SequenceType[] {
                new FunctionParameterSequenceType("uri-part", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The URI part to encode")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                "the URI part with reserved characters percent encoded"));

    public FunEncodeForURI(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

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
        final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
        if (seq.isEmpty()) {
            //If $uri-part is the empty sequence, returns the zero-length string.
            result = StringValue.EMPTY_STRING;
        } else {
            String value; 
            value = URIUtils.encodeForURI(seq.getStringValue());
            result = new StringValue(value);
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        return result;
    }
}
