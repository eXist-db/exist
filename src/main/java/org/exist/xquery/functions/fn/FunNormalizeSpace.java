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

import java.util.StringTokenizer;

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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class FunNormalizeSpace extends Function {

    private static final String FUNCTION_DESCRIPTION_0_PARAM =

            "Returns the calculated string value of the context item with whitespace normalized by stripping leading ";
    protected static final String FUNCTION_DESCRIPTION_1_PARAM =
            "Returns the value of $arg with whitespace normalized by stripping leading ";
    private static final String FUNCTION_DESCRIPTION_COMMON_1 =
            "and trailing whitespace and replacing sequences of one or more than one " +
                    "whitespace character with a single space, #x20.\n\n" +
                    "The whitespace characters are defined in the metasymbol S (Production 3) " +
                    "of [Extensible Markup Language (XML) 1.0 Recommendation (Third Edition)].\n\n" +
                    "Note:\n\n" +
                    "The definition of the metasymbol S (Production 3), is " +
                    "unchanged in [Extensible Markup Language (XML) 1.1 Recommendation].\n\n";

    private static final String FUNCTION_DESCRIPTION_1_PARAM_1 =
            "If the value of $arg is the empty sequence, returns the zero-length string.\n\n";
    private static final String FUNCTION_DESCRIPTION_COMMON_2 =
            "If no argument is supplied, $arg defaults to the string value (calculated " +
                    "using fn:string()) of the context item (.). If no argument is supplied or " +
                    "if the argument is the context item and the context item is undefined an " +
                    "error is raised: [err:XPDY0002].";

    private static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the normalized text");

    public static final FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("normalize-space", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION_0_PARAM + FUNCTION_DESCRIPTION_COMMON_1 +
                            FUNCTION_DESCRIPTION_COMMON_2,
                    new SequenceType[0],
                    RETURN_TYPE
            ),
            new FunctionSignature(
                    new QName("normalize-space", Function.BUILTIN_FUNCTION_NS),
                    FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_COMMON_1 +
                            FUNCTION_DESCRIPTION_1_PARAM_1 + FUNCTION_DESCRIPTION_COMMON_2,
                    new SequenceType[]{new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to normalize")},
                    RETURN_TYPE
            )
    };

    public FunNormalizeSpace(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public int returnsType() {
        return Type.STRING;
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

        String value = null;
        if (getSignature().getArgumentCount() == 0) {
            if (contextSequence == null) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");
            }
            value = !contextSequence.isEmpty() ? contextSequence.itemAt(0).getStringValue() : "";
        } else {
            final Sequence seq = getArgument(0).eval(contextSequence);
            if (seq == null) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");
            }
            if (!seq.isEmpty()) {
                value = seq.getStringValue();
            }
        }

        final Sequence result;
        if (value == null) {
            result = StringValue.EMPTY_STRING;
        } else {
            result = new StringValue(normalize(value));
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    protected static String normalize(final String str) {
        final StringBuilder buf = new StringBuilder();
        if (!str.isEmpty()) {
            final StringTokenizer tok = new StringTokenizer(str);
            while (tok.hasMoreTokens()) {
                buf.append(tok.nextToken());
                if (tok.hasMoreTokens()) {
                    buf.append(' ');
                }
            }
        }
        return buf.toString();
    }
}
