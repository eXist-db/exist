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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
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

import javax.annotation.Nullable;

/**
 *
 * @author perig
 *
 */
public class FunCodepointEqual extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("codepoint-equal", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
            "Returns true or false depending on whether the value of $string-1 " +
            "is equal to the value of $string-2, according to the Unicode " +
            "code point collation.",
            new SequenceType[] {
                new FunctionParameterSequenceType("string-1", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The first string"),
                new FunctionParameterSequenceType("string-2", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The second string"),
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE,
                "true() if the codepoints are equal, false() otherwise"));

    public FunCodepointEqual(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT SEQUENCE", contextSequence);}
        }
        final Sequence result;
        if (args[0].isEmpty() || args[1].isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            result = new BooleanValue(this,
                    codepointEqual(args[0].itemAt(0), args[1].itemAt(0), context.getDefaultCollator())
            );

        }

        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}

        return result;
    }

    /**
     * Apply fn:codepoint-equal logic to the provided items.
     *
     * @param item1 the first Item to be compared.
     * @param item2 the second Item to be compared.
     * @param collator a collator to use for the comparison, or null to use the default collator.
     *
     * @return true if the items are codepoint equal, false otherwise.
     *
     * @throws XPathException if atomisation fails.
     */
    public static boolean codepointEqual(final Item item1, final Item item2, @Nullable final Collator collator) throws XPathException {
        return Collations.compare(collator, item1.getStringValue(), item2.getStringValue()) == Constants.EQUAL;
    }
}
