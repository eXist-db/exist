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
    private final static FunctionParameterSequenceType FS_TOKENIZE_PARAM_PATTERN =
            new FunctionParameterSequenceType("pattern", Type.STRING, Cardinality.ZERO_OR_ONE, "The tokenization pattern");

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
                    new FunctionParameterSequenceType("flags", Type.STRING, Cardinality.ZERO_OR_ONE, "The flags")
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
                // XQ4: pattern can be empty sequence — treat as 1-arg whitespace form
                final boolean useWhitespaceTokenization = args.length == 1
                        || (args.length >= 2 && args[1].isEmpty());

                if (useWhitespaceTokenization) {
                    string = FunNormalizeSpace.normalize(string);
                    if (string.isEmpty()) {
                        return Sequence.EMPTY_SEQUENCE;
                    }
                    final String[] tokens = string.split(" ");
                    result = new ValueSequence();
                    for (final String token : tokens) {
                        result.add(new StringValue(this, token));
                    }
                } else {
                    // XQ4: flags can be empty sequence
                    String flagsStr = "";
                    if (args.length == 3 && !args[2].isEmpty()) {
                        flagsStr = args[2].itemAt(0).getStringValue();
                    }

                    // XQ4: 'c' flag — strip regex comments
                    final boolean hasCommentFlag = flagsStr.indexOf('c') >= 0 && flagsStr.indexOf('q') < 0;
                    if (flagsStr.indexOf('c') >= 0) {
                        flagsStr = flagsStr.replace("c", "");
                    }
                    final int flags = parseFlags(this, flagsStr);

                    String rawPattern = args[1].itemAt(0).getStringValue();
                    if (hasCommentFlag) {
                        rawPattern = FunReplace.stripRegexComments(rawPattern);
                    }
                    final String pattern;
                    if (hasLiteral(flags)) {
                        pattern = rawPattern;
                    } else {
                        final boolean ignoreWhitespace = hasIgnoreWhitespace(flags);
                        final boolean caseBlind = hasCaseInsensitive(flags);
                        pattern = translateRegexp(this, rawPattern, ignoreWhitespace, caseBlind);
                    }

                    try {
                        final Pattern pat = PatternFactory.getInstance().getPattern(pattern, flags);

                        if (pat.matcher("").matches()) {
                            // XQ4: empty-matching regex allowed — tokenize between each character
                            result = tokenizeEmptyMatch(string, pat);
                        } else {
                            final String[] tokens = pat.split(string, -1);
                            result = new ValueSequence();
                            for (final String token : tokens) {
                                result.add(new StringValue(this, token));
                            }
                        }

                    } catch (final PatternSyntaxException e) {
                        throw new XPathException(this, ErrorCodes.FORX0001, "Invalid regular expression: " + e.getMessage(), new StringValue(this, pattern), e);
                    }
                }
            }
        }

        return result;
    }

    /**
     * XQ4: Handle tokenization when the regex matches the empty string.
     * Per spec: zero-length matches at start/end of string do not produce
     * leading/trailing empty tokens. Empty matches advance past one character.
     */
    private Sequence tokenizeEmptyMatch(final String input, final Pattern pat) throws XPathException {
        final ValueSequence result = new ValueSequence();
        final java.util.regex.Matcher matcher = pat.matcher(input);
        int lastEnd = 0;
        while (matcher.find()) {
            final boolean isEmpty = matcher.start() == matcher.end();

            // Skip zero-length match at end of string
            if (isEmpty && matcher.start() >= input.length()) {
                break;
            }

            // Add token: text from end of last match to start of this match
            result.add(new StringValue(this, input.substring(lastEnd, matcher.start())));
            lastEnd = matcher.end();

            // For empty match, advance matcher past one character to prevent infinite loop.
            // The skipped character becomes part of the next token (not consumed).
            if (isEmpty) {
                final int nextPos = lastEnd + 1;
                if (nextPos <= input.length()) {
                    matcher.region(nextPos, input.length());
                } else {
                    break;
                }
            }
        }
        // Add trailing token
        if (lastEnd <= input.length()) {
            result.add(new StringValue(this, input.substring(lastEnd)));
        }
        return result;
    }

}
