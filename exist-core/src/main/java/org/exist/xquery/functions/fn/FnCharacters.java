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
 * Implements fn:characters (XQuery 4.0).
 *
 * Splits the supplied string into a sequence of single-character strings.
 */
public class FnCharacters extends BasicFunction {

    public static final FunctionSignature FN_CHARACTERS = new FunctionSignature(
            new QName("characters", Function.BUILTIN_FUNCTION_NS),
            "Splits the supplied string into a sequence of single-character strings.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to split")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "a sequence of single-character strings"));

    public FnCharacters(final XQueryContext context, final FunctionSignature signature) {
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
        final ValueSequence result = new ValueSequence(str.length());
        // Use codepoint iteration to handle surrogate pairs correctly
        int i = 0;
        while (i < str.length()) {
            final int codepoint = str.codePointAt(i);
            result.add(new StringValue(this, new String(Character.toChars(codepoint))));
            i += Character.charCount(codepoint);
        }
        return result;
    }
}
