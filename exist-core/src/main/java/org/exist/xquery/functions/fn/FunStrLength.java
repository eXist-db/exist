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

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * Built-in function fn:string-length($srcval as xs:string?) as xs:integer?
 */
public class FunStrLength extends Function {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("string-length", Function.BUILTIN_FUNCTION_NS),
                    "Returns an xs:integer equal to the length in characters of the value of the context item.\n" +
                            "If the context item is undefined an error is raised. ",
                    new SequenceType[0],
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the length in characters")
            ),
            new FunctionSignature(
                    new QName("string-length", Function.BUILTIN_FUNCTION_NS),
                    "Returns an xs:integer equal to the length in characters of the value of $arg.\n" +
                            "If the value of $arg is the empty sequence, the xs:integer 0 is returned.\n" +
                            "If no argument is supplied, $arg defaults to the string value (calculated using fn:string()) of the context item (.). If no argument is supplied or if the argument is the context item and the context item is undefined an error is raised",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The input string")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the length in characters")
            )
    };

    public FunStrLength(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    protected Tuple2<Expression, Integer> strictCheckArgumentType(Expression argument,
            @Nullable final SequenceType argType, final AnalyzeContextInfo argContextInfo, final int argPosition,
            int returnType) {
        if (getArgumentCount() == 1 && (argContextInfo.getFlags() & LocationStep.DOT_TEST) == LocationStep.DOT_TEST) {
            /*
                fn:string-length has different behaviour with regards the context item...
                See https://www.biglist.com/lists/lists.mulberrytech.com/xsl-list/archives/201906/msg00021.html
                See https://github.com/eXist-db/exist/issues/2798
             */
            return Tuple(argument, returnType);
        } else {
            return super.strictCheckArgumentType(argument, argType, argContextInfo, argPosition, returnType);
        }
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


        if (getSignature().getArgumentCount() == 1) {
            contextSequence = getArgument(0).eval(contextSequence);
        }

        if (contextSequence == null) {
            throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");
        }

        final String strval = contextSequence.getStringValue();

        final Sequence result = new IntegerValue(FunStringToCodepoints.getCodePointCount(strval));

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }
}
