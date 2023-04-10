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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.dom.QName;
import org.exist.util.PatternFactory;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.regex.RegexUtil.*;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @see <a href="https://www.w3.org/TR/xpath-functions-31/#func-tokenize">https://www.w3.org/TR/xpath-functions-31/#func-tokenize</a>
 */
public class FunTokenize extends BasicFunction {

    private static final QName FS_TOKENIZE_NAME = new QName("tokenize", Function.BUILTIN_FUNCTION_NS);

    private final static FunctionParameterSequenceType FS_TOKENIZE_PARAM_INPUT = optParam("input", Type.STRING, "The input string");
    private final static FunctionParameterSequenceType FS_TOKENIZE_PARAM_PATTERN = param("pattern", Type.STRING, "The tokenization pattern");

    public final static FunctionSignature[] FS_TOKENIZE = functionSignatures(
            FS_TOKENIZE_NAME,
            "Breaks the input string $input into a sequence of strings, ",
            returnsOptMany(Type.STRING, "the token sequence"),
            arities(
                arity(
                    FS_TOKENIZE_PARAM_INPUT
                ),
                arity(
                    FS_TOKENIZE_PARAM_INPUT,
                    FS_TOKENIZE_PARAM_PATTERN
                ),
                arity(
                    FS_TOKENIZE_PARAM_INPUT,
                    FS_TOKENIZE_PARAM_PATTERN,
                    param("flags", Type.STRING,"The flags")
                )
            )
    );

    public FunTokenize(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence result;
        final Sequence stringArg = args[0];
        if (stringArg.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            String string = stringArg.getStringValue();
            if (string.isEmpty()) {
                result = Sequence.EMPTY_SEQUENCE;
            } else {
                final int flags;
                if (args.length == 3) {
                    flags = parseFlags(this, args[2].itemAt(0).getStringValue());
                } else {
                    flags = 0;
                }

                final String pattern;
                if (args.length == 1) {
                    pattern = " ";
                    string = FunNormalizeSpace.normalize(string);
                } else {
                    if(hasLiteral(flags)) {
                        // no need to change anything
                        pattern = args[1].itemAt(0).getStringValue();
                    } else {
                        final boolean ignoreWhitespace = hasIgnoreWhitespace(flags);
                        final boolean caseBlind = hasCaseInsensitive(flags);
                        pattern = translateRegexp(this, args[1].itemAt(0).getStringValue(), ignoreWhitespace, caseBlind);
                    }
                }

                try {
                    final Pattern pat = PatternFactory.getInstance().getPattern(pattern, flags);
                    if (pat.matcher("").matches()) {
                        throw new XPathException(this, ErrorCodes.FORX0003, "regular expression could match empty string");
                    }

                    final String[] tokens = pat.split(string, -1);
                    result = new ValueSequence();

                    for (final String token : tokens) {
                        result.add(new StringValue(token));
                    }

                } catch (final PatternSyntaxException e) {
                    throw new XPathException(this, ErrorCodes.FORX0001, "Invalid regular expression: " + e.getMessage(), new StringValue(pattern), e);
                }
            }
        }

        return result;
    }

}
