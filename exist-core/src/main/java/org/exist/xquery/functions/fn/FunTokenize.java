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

    private final static FunctionParameterSequenceType FS_TOKENIZE_PARAM_INPUT = optParam("value", Type.STRING, "The input string");
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

                    // XQ4: '!' flag — XPath mode (allows empty matches, etc.)
                    final boolean hasXPathFlag = flagsStr.contains("!");
                    if (hasXPathFlag) {
                        flagsStr = flagsStr.replace("!", "");
                    }

                    // XQ4: 'c' flag — strip regex comments (handled by Saxon's 'x' flag)
                    final boolean hasCommentFlag = flagsStr.indexOf('c') >= 0;
                    if (hasCommentFlag) {
                        flagsStr = flagsStr.replace("c", "");
                    }
                    final int flags = parseFlags(this, flagsStr);

                    final boolean isXQuery40 = context.getXQueryVersion() >= 40;

                    String rawPattern = args[1].itemAt(0).getStringValue();
                    if (hasCommentFlag) {
                        rawPattern = FunReplace.stripRegexComments(rawPattern);
                    }

                    // XQ4: translate (*positive_lookahead:...) etc. to Java regex
                    if (isXQuery40 && org.exist.xquery.regex.RegexUtil.hasXPath4Lookaround(rawPattern)) {
                        rawPattern = org.exist.xquery.regex.RegexUtil.translateXPath4Lookaround(rawPattern);
                    }

                    // Pre-validate: reject constructs not valid in XPath regex
                    if (!hasLiteral(flags)) {
                        org.exist.xquery.regex.RegexUtil.validateXPathRegex(this, rawPattern, isXQuery40);
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
                        final boolean canMatchEmpty = pat.matcher("").matches();
                        final boolean allowEmptyMatch = hasXPathFlag || context.getXQueryVersion() >= 40;

                        if (canMatchEmpty && !allowEmptyMatch) {
                            throw new XPathException(this, ErrorCodes.FORX0003,
                                    "Regular expression matches zero-length string");
                        }

                        if (canMatchEmpty) {
                            // XQ4: empty-matching regex allowed — tokenize between each character
                            result = tokenizeEmptyMatch(string, pat);
                        } else {
                            final String[] tokens = pat.split(string, -1);
                            result = new ValueSequence();
                            // XQ4 spec: zero-width matches at the start/end of input do not
                            // produce leading/trailing empty tokens (e.g. \b or lookarounds).
                            int startIdx = 0;
                            int endIdx = tokens.length;
                            if (tokens.length > 0 && !string.isEmpty()) {
                                if (tokens[0].isEmpty()) {
                                    final java.util.regex.Matcher first = pat.matcher(string);
                                    if (first.find() && first.start() == 0 && first.end() == 0) {
                                        startIdx = 1;
                                    }
                                }
                                if (endIdx > startIdx && tokens[endIdx - 1].isEmpty()) {
                                    final java.util.regex.Matcher tail = pat.matcher(string);
                                    int lastStart = -1, lastEnd = -1;
                                    while (tail.find()) { lastStart = tail.start(); lastEnd = tail.end(); }
                                    if (lastStart == lastEnd && lastEnd == string.length()) {
                                        endIdx--;
                                    }
                                }
                            }
                            for (int i = startIdx; i < endIdx; i++) {
                                result.add(new StringValue(this, tokens[i]));
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
     * Per spec: zero-length matches at the start or end of the input string
     * do not produce leading/trailing empty tokens.
     */
    private Sequence tokenizeEmptyMatch(final String input, final Pattern pat) throws XPathException {
        final ValueSequence result = new ValueSequence();
        final java.util.regex.Matcher matcher = pat.matcher(input);
        matcher.useTransparentBounds(true);
        int lastEnd = 0;
        // Tracks whether the most recent consuming (non-empty) match ended at
        // input.length(); only such a match should produce a trailing empty token.
        boolean lastConsumingEndedAtInput = false;
        while (matcher.find()) {
            final boolean isEmpty = matcher.start() == matcher.end();

            // Skip zero-length match at end of string (do not produce trailing empty for it)
            if (isEmpty && matcher.start() >= input.length()) {
                break;
            }

            // XQ4 spec: skip leading empty token from a zero-length match at position 0
            if (!(isEmpty && matcher.start() == 0)) {
                result.add(new StringValue(this, input.substring(lastEnd, matcher.start())));
            }
            lastEnd = matcher.end();
            if (!isEmpty) {
                lastConsumingEndedAtInput = (lastEnd == input.length());
            }

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
        // Trailing token rules:
        //   - If unconsumed characters remain, emit them as the final token
        //   - If pattern never matched anything, return the original input
        //   - If the last consuming match ended right at input.length(),
        //     emit a trailing empty
        if (lastEnd < input.length()) {
            result.add(new StringValue(this, input.substring(lastEnd)));
        } else if (lastEnd == 0 && result.isEmpty()) {
            result.add(new StringValue(this, input));
        } else if (lastConsumingEndedAtInput) {
            result.add(StringValue.EMPTY_STRING);
        }
        return result;
    }

}
