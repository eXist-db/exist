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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.Replace;
import net.sf.saxon.regex.RegularExpression;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.regex.RegexUtil.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunReplace extends BasicFunction {

	private static final QName FS_REPLACE_NAME = new QName("replace", Function.BUILTIN_FUNCTION_NS);

	private static final String FS_REPLACE_DESCRIPTION =
        "The function returns the xs:string that is obtained by replacing each non-overlapping substring " +
        "of $input that matches the given $pattern with an occurrence of the $replacement string.\n\n" + 
        "The $flags argument is interpreted in the same manner as for the fn:matches() function.\n\n" +
        "Calling the four argument version with the $flags argument set to a " +
        "zero-length string gives the same effect as using the three argument version.\n\n" +
        "If $input is the empty sequence, it is interpreted as the zero-length string.\n\nIf two overlapping " +
        "substrings of $input both match the $pattern, then only the first one (that is, the one whose first " +
        "character comes first in the $input string) is replaced.\n\nWithin the $replacement string, a variable " +
        "$N may be used to refer to the substring captured by the Nth parenthesized sub-expression in the " +
        "regular expression. For each match of the pattern, these variables are assigned the value of the " +
        "content matched by the relevant sub-expression, and the modified replacement string is then " +
        "substituted for the characters in $input that matched the pattern. $0 refers to the substring " +
        "captured by the regular expression as a whole.\n\nMore specifically, the rules are as follows, " +
        "where S is the number of parenthesized sub-expressions in the regular expression, and N is the " +
        "decimal number formed by taking all the digits that consecutively follow the $ character:\n\n" +
        "1.  If N=0, then the variable is replaced by the substring matched by the regular expression as a whole.\n\n" +
        "2.  If 1<=N<=S, then the variable is replaced by the substring captured by the Nth parenthesized " +
        "sub-expression. If the Nth parenthesized sub-expression was not matched, then the variable " +
        "is replaced by the zero-length string.\n\n" +
        "3.  If S<N<=9, then the variable is replaced by the zero-length string.\n\n" +
        "4.  Otherwise (if N>S and N>9), the last digit of N is taken to be a literal character to be " +
        "included \"as is\" in the replacement string, and the rules are reapplied using the number N " +
        "formed by stripping off this last digit.";

	private static final FunctionParameterSequenceType FS_TOKENIZE_PARAM_INPUT = optParam("input", Type.STRING, "The input string");
	private static final FunctionParameterSequenceType FS_TOKENIZE_PARAM_PATTERN = param("pattern", Type.STRING, "The pattern to match");
	private static final FunctionParameterSequenceType FS_TOKENIZE_PARAM_REPLACEMENT =
			new FunctionParameterSequenceType("replacement", Type.ITEM, Cardinality.ZERO_OR_ONE,
					"The replacement string, function, or empty sequence");

	static final FunctionSignature [] FS_REPLACE = functionSignatures(
			FS_REPLACE_NAME,
			FS_REPLACE_DESCRIPTION,
			returns(Type.STRING, "the altered string"),
			arities(
					arity(
							FS_TOKENIZE_PARAM_INPUT,
							FS_TOKENIZE_PARAM_PATTERN,
							FS_TOKENIZE_PARAM_REPLACEMENT
					),
					arity(
							FS_TOKENIZE_PARAM_INPUT,
							FS_TOKENIZE_PARAM_PATTERN,
							FS_TOKENIZE_PARAM_REPLACEMENT,
							param("flags", Type.STRING, Cardinality.ZERO_OR_ONE, "The flags")
					)
			)
	);

	public FunReplace(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
		final Sequence result;
		final Sequence stringArg = args[0];
		if (stringArg.isEmpty()) {
			result = StringValue.EMPTY_STRING;
		} else {
			String flags;
			if (args.length == 4 && !args[3].isEmpty()) {
				flags =	args[3].itemAt(0).getStringValue();
			} else {
				flags = "";
			}

			// XQ4: 'c' flag — strip regex comments (#...#) before compilation
			// When 'q' (literal) flag is present, 'c' is ignored
			final boolean hasCommentFlag = flags.indexOf('c') >= 0 && flags.indexOf('q') < 0;
			if (flags.indexOf('c') >= 0) {
				flags = flags.replace("c", "");
			}

    		final String string = stringArg.getStringValue();
    		String pattern = args[1].itemAt(0).getStringValue();

			if (hasCommentFlag) {
				pattern = stripRegexComments(pattern);
			}

			// XQ4: 3rd arg can be empty sequence (treated as empty string) or a function
			final Sequence replacementArg = args[2];
			final boolean isFunctionReplacement = !replacementArg.isEmpty()
					&& Type.subTypeOf(replacementArg.itemAt(0).getType(), Type.FUNCTION);
			final String replace;
			if (isFunctionReplacement) {
				replace = null; // handled below
			} else if (replacementArg.isEmpty()) {
				replace = "";
			} else {
				replace = replacementArg.itemAt(0).getStringValue();
			}

			final Configuration config = context.getBroker().getBrokerPool().getSaxonConfiguration();
			final List<String> warnings = new ArrayList<>(1);

			try {
				final RegularExpression regularExpression = config.compileRegularExpression(pattern, flags, "XP30", warnings);
				final boolean canMatchEmpty = regularExpression.matches("");

				if (isFunctionReplacement) {
					result = evalFunctionReplacement(string, pattern, flags,
							(FunctionReference) replacementArg.itemAt(0));
				} else if (canMatchEmpty) {
					// XQ4: empty-matching regex allowed — use Java regex fallback
					// since Saxon's replace() doesn't handle empty matches well
					result = evalEmptyMatchReplace(string, pattern, replace, flags);
				} else {
					if (!hasLiteral(flags)) {
						final String msg = Replace.checkReplacement(replace);
						if (msg != null) {
							throw new XPathException(this, ErrorCodes.FORX0004, msg);
						}
					}
					final CharSequence res = regularExpression.replace(string, replace);
					result = new StringValue(this, res.toString());
				}

			} catch (final net.sf.saxon.trans.XPathException e) {
				switch (e.getErrorCodeLocalPart()) {
					case "FORX0001" -> throw new XPathException(this, ErrorCodes.FORX0001, e.getMessage());
					case "FORX0002" -> throw new XPathException(this, ErrorCodes.FORX0002, e.getMessage());
					case "FORX0003" -> throw new XPathException(this, ErrorCodes.FORX0003, e.getMessage());
					case "FORX0004" -> throw new XPathException(this, ErrorCodes.FORX0004, e.getMessage());
					default -> throw new XPathException(this, ErrorCodes.ERROR, e.getMessage());
				}
			}
        }

        return result;
	}

	/**
	 * XQ4: Handle replacement when the regex can match the empty string.
	 * Uses Java regex with XPath-to-Java translation for proper empty-match handling.
	 */
	private Sequence evalEmptyMatchReplace(final String input, final String pattern,
			final String replace, final String flags) throws XPathException {
		final String javaPattern = org.exist.xquery.regex.RegexUtil.translateRegexp(
				this, pattern, hasIgnoreWhitespace(flags), hasCaseInsensitive(flags));
		final int javaFlags = parseFlags(this, flags);
		final Pattern compiled = Pattern.compile(javaPattern, javaFlags);
		final Matcher matcher = compiled.matcher(input);

		final StringBuilder sb = new StringBuilder();
		int lastEnd = 0;
		while (matcher.find()) {
			sb.append(input, lastEnd, matcher.start());

			// Apply XPath-style replacement ($0, $1, etc.)
			sb.append(applyXPathReplacement(replace, matcher));

			lastEnd = matcher.end();

			// Advance past empty match to prevent infinite loop
			if (matcher.start() == matcher.end()) {
				if (lastEnd < input.length()) {
					sb.append(input.charAt(lastEnd));
					lastEnd++;
					matcher.region(lastEnd, input.length());
				} else {
					break;
				}
			}
		}
		sb.append(input, lastEnd, input.length());
		return new StringValue(this, sb.toString());
	}

	/**
	 * Apply XPath-style replacement string ($0, $1, etc.) using a Java Matcher.
	 */
	private static String applyXPathReplacement(final String replacement, final Matcher matcher) {
		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < replacement.length(); i++) {
			final char ch = replacement.charAt(i);
			if (ch == '$' && i + 1 < replacement.length()) {
				i++;
				int groupNum = 0;
				boolean hasDigit = false;
				while (i < replacement.length() && Character.isDigit(replacement.charAt(i))) {
					groupNum = groupNum * 10 + (replacement.charAt(i) - '0');
					hasDigit = true;
					i++;
				}
				i--; // back up one
				if (hasDigit && groupNum <= matcher.groupCount()) {
					final String g = matcher.group(groupNum);
					if (g != null) {
						result.append(g);
					}
				} else if (hasDigit) {
					// Group doesn't exist, output empty
				}
			} else if (ch == '\\' && i + 1 < replacement.length()) {
				i++;
				result.append(replacement.charAt(i));
			} else {
				result.append(ch);
			}
		}
		return result.toString();
	}

	/**
	 * XQ4: Evaluate fn:replace with a function replacement parameter.
	 * The function receives (match, groups*) and returns the replacement string.
	 */
	private Sequence evalFunctionReplacement(final String input, final String pattern,
			final String flags, final FunctionReference func) throws XPathException {
		// Use Java regex for function replacement since Saxon's replace() only accepts strings
		final String javaPattern = org.exist.xquery.regex.RegexUtil.translateRegexp(
				this, pattern, hasIgnoreWhitespace(flags), hasCaseInsensitive(flags));
		int javaFlags = parseFlags(this, flags);
		final Pattern compiled = Pattern.compile(javaPattern, javaFlags);
		final Matcher matcher = compiled.matcher(input);

		final StringBuilder sb = new StringBuilder();
		int lastEnd = 0;
		while (matcher.find()) {
			sb.append(input, lastEnd, matcher.start());

			// Build arguments: (match, group1, group2, ...)
			final int groupCount = matcher.groupCount();
			final Sequence[] funcArgs = new Sequence[2];
			funcArgs[0] = new StringValue(this, matcher.group());
			final ValueSequence groups = new ValueSequence(groupCount);
			for (int i = 1; i <= groupCount; i++) {
				final String g = matcher.group(i);
				groups.add(g != null ? new StringValue(this, g) : StringValue.EMPTY_STRING);
			}
			funcArgs[1] = groups;

			final Sequence replacement = func.evalFunction(null, null, funcArgs);
			if (!replacement.isEmpty()) {
				sb.append(replacement.getStringValue());
			}

			lastEnd = matcher.end();

			// Prevent infinite loop on empty match
			if (matcher.start() == matcher.end()) {
				if (lastEnd < input.length()) {
					sb.append(input.charAt(lastEnd));
					lastEnd++;
					// Reset matcher position
					matcher.region(lastEnd, input.length());
				} else {
					break;
				}
			}
		}
		sb.append(input, lastEnd, input.length());
		return new StringValue(this, sb.toString());
	}

	/**
	 * XQ4: Strip regex comments (c flag).
	 * Removes text between # markers: #comment# becomes empty.
	 * A # at end of pattern (no closing #) is treated as end-of-line comment.
	 * Escaped \# is preserved.
	 */
	static String stripRegexComments(final String pattern) {
		final StringBuilder result = new StringBuilder(pattern.length());
		boolean inComment = false;
		boolean inCharClass = false;
		for (int i = 0; i < pattern.length(); i++) {
			final char ch = pattern.charAt(i);
			if (ch == '\\' && i + 1 < pattern.length()) {
				final char next = pattern.charAt(i + 1);
				if (!inComment) {
					if (next == '#') {
						// \# in c-flag mode is a literal # — output just #
						result.append('#');
					} else {
						result.append(ch);
						result.append(next);
					}
				}
				i++; // skip escaped character
			} else if (inCharClass) {
				// Inside [...] character class, # is literal
				if (ch == ']') {
					inCharClass = false;
				}
				if (!inComment) {
					result.append(ch);
				}
			} else if (ch == '[' && !inComment) {
				inCharClass = true;
				result.append(ch);
			} else if (ch == '#' && !inCharClass) {
				inComment = !inComment;
			} else if (!inComment) {
				result.append(ch);
			}
		}
		return result.toString();
	}

	private static boolean hasCaseInsensitive(final String flags) {
		return flags.contains("i");
	}

	private static boolean hasIgnoreWhitespace(final String flags) {
		return flags.contains("x");
	}
}
