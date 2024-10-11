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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

@Deprecated
public class FunEquals extends CollatingFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("equals", Function.BUILTIN_FUNCTION_NS),
            "Returns an xs:boolean indicating whether or not the value of " +
            "$source-string  equals the collation units in the value of " +
            "$substring, according to the default collation. " +
            "This function is similar to the '=' expression, except that " +
            "it uses the default collation for comparisons.",
            new SequenceType[] {
                new FunctionParameterSequenceType("source-string", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The source-string"),
                new FunctionParameterSequenceType("substring", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The substring")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                "true() if $source-string equals $substring, false() otherwise")
        ),
        new FunctionSignature(
            new QName("equals", Function.BUILTIN_FUNCTION_NS),
            "Returns an xs:boolean indicating whether or not the value of " +
            "$source-string equals the  collation units in the value of " +
            "$substring, according to the collation that is specified in " +
            "$collation-uri. This function is similar to the '=' expression, " +
            "except that it uses the specified collation for comparisons." +
            THIRD_REL_COLLATION_ARG_EXAMPLE,
            new SequenceType[] {
                new FunctionParameterSequenceType("source-string", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The source-string"),
                new FunctionParameterSequenceType("substring", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The substring"),
                new FunctionParameterSequenceType("collation-uri", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The collation URI")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                "true() if $source-string equals $substring, false() otherwise"))
    };

    public FunEquals(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public int returnsType() {
        return Type.BOOLEAN;
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
        final String s1 = getArgument(0).eval(contextSequence, contextItem).getStringValue();
        final String s2 = getArgument(1).eval(contextSequence, contextItem).getStringValue();
        if (s1.isEmpty() || s2.isEmpty()) {
            if (s1.isEmpty() && s2.isEmpty()) {
                result = BooleanValue.TRUE;
            } else {
                result = Sequence.EMPTY_SEQUENCE;
            }
        } else {
            Collator collator = getCollator(contextSequence, contextItem, 3);
            // Make sure we have a collator!
            if (collator == null) {
                //TODO : raise exception ? -pb
                collator = Collations.getCollationFromURI("?strength=identical", contextItem != null ? contextItem.getExpression() : null, ErrorCodes.FOCH0002);
            }
            if (Collations.equals(collator, s1, s2)) {
                result = BooleanValue.TRUE;
            } else {
                result = BooleanValue.FALSE;
            }
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        return result;
    }
}
