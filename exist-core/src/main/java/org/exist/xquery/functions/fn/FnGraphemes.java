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

import com.ibm.icu.text.BreakIterator;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements fn:graphemes (XQuery 4.0).
 *
 * Splits the supplied string into a sequence of strings, each containing
 * one Unicode extended grapheme cluster.
 *
 * Uses ICU4J's BreakIterator for Unicode grapheme cluster boundary detection,
 * which handles combining marks, emoji sequences, regional indicators, etc.
 */
public class FnGraphemes extends BasicFunction {

    public static final FunctionSignature FN_GRAPHEMES = new FunctionSignature(
            new QName("graphemes", Function.BUILTIN_FUNCTION_NS),
            "Splits the supplied string into a sequence of strings, each containing " +
            "one Unicode extended grapheme cluster.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE,
                            "The string to split into grapheme clusters")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
                    "a sequence of strings, each containing one grapheme cluster"));

    public FnGraphemes(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String str = args[0].getStringValue();
        if (str.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(str);

        final ValueSequence result = new ValueSequence();
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            result.add(new StringValue(this, str.substring(start, end)));
        }
        return result;
    }
}
