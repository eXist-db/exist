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
package org.exist.xquery.regex;

import org.exist.thirdparty.net.sf.saxon.functions.regex.JDK15RegexTranslator;
import org.exist.thirdparty.net.sf.saxon.functions.regex.RegexSyntaxException;
import org.exist.thirdparty.net.sf.saxon.functions.regex.RegularExpression;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.StringValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RegexUtil {

    /**
     * Parses the flags for an XQuery Regular Expression.
     *
     * @param context The calling expression
     * @param strFlags The XQuery Regular Expression flags.
     *
     * @return The flags for a Java Regular Expression.
     * @throws XPathException in case of invalid flag
     */
    public static int parseFlags(final Expression context, @Nullable final String strFlags) throws XPathException {
        int flags = 0;
        if(strFlags != null) {
            for (int i = 0; i < strFlags.length(); i++) {
                final char ch = strFlags.charAt(i);
                switch (ch) {
                    case 'm':
                        flags |= Pattern.MULTILINE;
                        break;

                    case 'i':
                        flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                        break;

                    case 'x':
                        flags |= Pattern.COMMENTS;
                        break;

                    case 's':
                        flags |= Pattern.DOTALL;
                        break;

                    case 'q':
                        flags |= Pattern.LITERAL;
                        break;

                    default:
                        throw new XPathException(context, ErrorCodes.FORX0001, "Invalid regular expression flag: " + ch, new StringValue(String.valueOf(ch)));
                }
            }
        }
        return flags;
    }

    /**
     * Determines if the Java Regular Expression flags have the literal flag set.
     *
     * @param flags The Java Regular Expression flags
     *
     * @return true if the literal flag is set
     */
    public static boolean hasLiteral(final int flags) {
        return (flags & Pattern.LITERAL) != 0;
    }

    /**
     * Determines if the XQuery Expression flags have the literal flag set.
     *
     * @param flags The XQuery Expression flags
     *
     * @return true if the literal flag is set
     */
    public static boolean hasLiteral(final String flags) {
        return flags.contains("q");
    }

    /**
     * Determines if the Java Regular Expression flags have the case-insensitive flag set.
     *
     * @param flags The Java Regular Expression flags
     *
     * @return true if the case-insensitive flag is set
     */
    public static boolean hasCaseInsensitive(final int flags) {
        return (flags & Pattern.CASE_INSENSITIVE) != 0 || (flags & Pattern.UNICODE_CASE) != 0;
    }

    /**
     * Determines if the Java Regular Expression flags have the ignore-whitespace flag set.
     *
     * @param flags The Java Regular Expression flags
     *
     * @return true if the ignore-whitespace flag is set
     */
    public static boolean hasIgnoreWhitespace(final int flags) {
        return (flags & Pattern.COMMENTS) != 0;
    }

    /**
     * Translates the Regular Expression from XPath3 syntax to Java regex
     * syntax.
     *
     * @param context the context expression - used for error reporting
     * @param pattern a String containing a regular expression in the syntax of XPath Functions and Operators 3.0.
     * @param ignoreWhitespace true if whitespace is to be ignored ('x' flag)
     * @param caseBlind true if case is to be ignored ('i' flag)
     *
     * @return The Java Regular Expression
     *
     * @throws XPathException if the XQuery Regular Expression is invalid.
     */
    public static String translateRegexp(final Expression context, final String pattern, final boolean ignoreWhitespace, final boolean caseBlind) throws XPathException {
        // convert pattern to Java regex syntax
        try {
            final int options = RegularExpression.XML11 | RegularExpression.XPATH30;

            int flagbits = 0;
            if (ignoreWhitespace) {
                flagbits |= Pattern.COMMENTS;
            }
            if (caseBlind) {
                flagbits |= Pattern.CASE_INSENSITIVE;
            }

            final List<RegexSyntaxException> warnings = new ArrayList<>();
            return JDK15RegexTranslator.translate(pattern, options, flagbits, warnings);
        } catch (final RegexSyntaxException e) {
            // Fallback: if the pattern uses \p{Is<Block>} Unicode block names that
            // the bundled Saxon regex translator doesn't recognize, convert them to
            // Java's \p{In<Block>} syntax and try compiling directly.
            if (pattern.contains("\\p{Is") || pattern.contains("\\P{Is")) {
                final String javaPattern = convertUnicodeBlockNames(pattern);
                try {
                    int flags = Pattern.UNICODE_CHARACTER_CLASS;
                    if (ignoreWhitespace) {
                        flags |= Pattern.COMMENTS;
                    }
                    if (caseBlind) {
                        flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                    }
                    Pattern.compile(javaPattern, flags);
                    return javaPattern;
                } catch (final java.util.regex.PatternSyntaxException ignored) {
                    // fallback failed, throw original error
                }
            }
            throw new XPathException(context, ErrorCodes.FORX0002, "Conversion from XPath F&O 3.0 regular expression syntax to Java regular expression syntax failed: " + e.getMessage(), new StringValue(pattern), e);
        }
    }

    /**
     * Convert XML Schema/XPath \p{Is<Block>} and \P{Is<Block>} Unicode block
     * property escapes to Java's \p{In<Block>} and \P{In<Block>} syntax.
     */
    private static String convertUnicodeBlockNames(final String pattern) {
        return pattern
                .replaceAll("\\\\p\\{Is([^}]+)}", "\\\\p{In$1}")
                .replaceAll("\\\\P\\{Is([^}]+)}", "\\\\P{In$1}");
    }

    /**
     * Validates that a regex pattern only uses constructs allowed by the XPath
     * regular expression specification (F&amp;O 3.1, Section 5.6.1), with
     * extensions for XPath 4.0 (Section 5.6.1.1).
     *
     * <p>Saxon's XP30 regex compiler accepts many Java/Perl regex constructs
     * that are not part of the XPath regex specification. This method rejects
     * such constructs with FORX0002 before they reach the Saxon compiler.</p>
     *
     * @param context the calling expression, for error reporting
     * @param pattern the regex pattern to validate
     * @param isXQuery40 true if running in XQuery 4.0+ mode
     * @throws XPathException with FORX0002 if the pattern uses non-XPath constructs
     */
    public static void validateXPathRegex(final Expression context, final String pattern, final boolean isXQuery40) throws XPathException {
        final int len = pattern.length();
        for (int i = 0; i < len; i++) {
            final char c = pattern.charAt(i);

            if (c == '\\') {
                if (i + 1 >= len) {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: trailing backslash",
                            new StringValue(pattern));
                }
                final char next = pattern.charAt(i + 1);
                switch (next) {
                    // Valid XPath single-character escapes
                    case 'n': case 'r': case 't':
                    case '\\': case '|': case '.': case '-': case '^':
                    case '?': case '*': case '+':
                    case '{': case '}': case '(': case ')':
                    case '[': case ']': case '$':
                    // Space: not strictly in the XPath spec but Saxon allows
                    // \<space> in free-spacing mode ('x' flag) for literal space
                    case ' ':
                    // Valid XPath multi-character escape shortcuts
                    case 'd': case 'D': case 's': case 'S':
                    case 'w': case 'W': case 'i': case 'I':
                    case 'c': case 'C':
                        i++; // skip the escaped character
                        break;
                    case 'b': case 'B':
                        // Word boundaries: valid in XPath 4.0, invalid in 3.1
                        if (!isXQuery40) {
                            throw new XPathException(context, ErrorCodes.FORX0002,
                                    "Invalid regular expression: \\" + next
                                            + " is not a recognized escape sequence in XPath 3.1 regular expressions",
                                    new StringValue(pattern));
                        }
                        // Quantifier after zero-width boundary assertion is not allowed
                        if (i + 2 < len) {
                            final char q = pattern.charAt(i + 2);
                            if (q == '?' || q == '+' || q == '*' || q == '{') {
                                throw new XPathException(context, ErrorCodes.FORX0002,
                                        "Invalid regular expression: quantifier '" + q
                                                + "' after \\" + next + " boundary assertion is not allowed",
                                        new StringValue(pattern));
                            }
                        }
                        i++;
                        break;
                    case '0': case '1': case '2': case '3': case '4':
                    case '5': case '6': case '7': case '8': case '9':
                        // Back-references (\1-\9) and octal escapes (\0nn) are not
                        // part of the XPath regex specification in any version
                        throw new XPathException(context, ErrorCodes.FORX0002,
                                "Invalid regular expression: \\" + next
                                        + " (back-reference/octal escape) is not supported in XPath regular expressions",
                                new StringValue(pattern));
                    case 'p': case 'P':
                        // \p{...} or \P{...} — must be followed by {Name}
                        if (i + 2 < len && pattern.charAt(i + 2) == '{') {
                            final int close = pattern.indexOf('}', i + 3);
                            if (close < 0) {
                                throw new XPathException(context, ErrorCodes.FORX0002,
                                        "Invalid regular expression: unclosed \\p{ property escape",
                                        new StringValue(pattern));
                            }
                            i = close; // advance past the closing }
                        } else {
                            throw new XPathException(context, ErrorCodes.FORX0002,
                                    "Invalid regular expression: \\p or \\P must be followed by {Name}",
                                    new StringValue(pattern));
                        }
                        break;
                    default:
                        // Any other backslash escape is invalid in XPath regex.
                        // This catches: \x (hex), \\u (Java unicode),
                        // \A \Z \z (Java anchors),
                        // \a \e \f \v (special chars), \Q \E (literal mode),
                        // \G \k \g (named backrefs)
                        throw new XPathException(context, ErrorCodes.FORX0002,
                                "Invalid regular expression: \\" + next
                                        + " is not a recognized escape sequence in XPath regular expressions",
                                new StringValue(pattern));
                }
            } else if (c == '(' && i + 1 < len && pattern.charAt(i + 1) == '?') {
                // In XPath 3.1, only (?:...) is valid.
                // In XPath 4.0, lookaround is also valid: (?=...) (?!...) (?<=...) (?<!...)
                if (i + 2 >= len) {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: incomplete group syntax at position " + i,
                            new StringValue(pattern));
                }
                final char groupType = pattern.charAt(i + 2);
                if (groupType == ':') {
                    // (?:...) — always valid
                } else if (isXQuery40 && (groupType == '=' || groupType == '!')) {
                    // (?=...) (?!...) — valid in XPath 4.0
                } else if (isXQuery40 && groupType == '<' && i + 3 < len
                        && (pattern.charAt(i + 3) == '=' || pattern.charAt(i + 3) == '!')) {
                    // (?<=...) (?<!...) — valid in XPath 4.0
                } else {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: non-capturing group (?:...) is the only "
                                    + "permitted group syntax in XPath regular expressions; "
                                    + "found (?" + groupType + " at position " + i,
                            new StringValue(pattern));
                }
            } else if (c == '(' && i + 1 < len && pattern.charAt(i + 1) == '*') {
                // (*name:...) — XPath 4.0 named lookaround
                if (!isXQuery40) {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: (*...) named groups are not supported "
                                    + "in XPath 3.1 regular expressions",
                            new StringValue(pattern));
                }
                // Skip to the closing ':' — let translateXPath4Lookaround handle the details
            } else if (c == '[') {
                // Scan character class content, rejecting POSIX [:name:] syntax
                // and invalid escapes that aren't caught by the outer scanner.
                i = scanCharClass(context, pattern, i, len);
            } else if ((c == '*' || c == '+' || c == '?') && i + 1 < len) {
                // After a quantifier, allowed characters are '?' (lazy modifier)
                // or anything that's not itself a quantifier metacharacter.
                final char nextCh = pattern.charAt(i + 1);
                if (nextCh == '+') {
                    // Possessive quantifiers (*+, ++, ?+) are not valid in XPath regex
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: possessive quantifier "
                                    + c + "+ is not supported in XPath regular expressions",
                            new StringValue(pattern));
                }
                if (nextCh == '{' || nextCh == '*') {
                    // Doubled quantifiers like *{n,m}, +{n,m}, ?{n,m}, **, *+, +*
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: doubled quantifier "
                                    + c + nextCh + " is not allowed in XPath regular expressions",
                            new StringValue(pattern));
                }
            }
        }
        if (isXQuery40) {
            validateLookaroundConstraints(context, pattern);
        }
    }

    /**
     * Validates XPath 4.0 lookaround constructs:
     * <ul>
     *   <li>Lookbehind body must be fixed-length (no {@code *}, {@code +},
     *       {@code ?}, or unbounded {@code {n,\u00a0}} quantifiers).</li>
     *   <li>A lookaround group cannot itself be quantified.</li>
     * </ul>
     */
    private static void validateLookaroundConstraints(final Expression context, final String pattern)
            throws XPathException {
        final int len = pattern.length();
        int i = 0;
        while (i < len) {
            final boolean isLookaround;
            final boolean isLookbehind;
            final int bodyStart;
            if (i + 3 < len && pattern.charAt(i) == '(' && pattern.charAt(i + 1) == '?') {
                final char gt = pattern.charAt(i + 2);
                if (gt == '=' || gt == '!') {
                    isLookaround = true;
                    isLookbehind = false;
                    bodyStart = i + 3;
                } else if (gt == '<' && i + 4 < len
                        && (pattern.charAt(i + 3) == '=' || pattern.charAt(i + 3) == '!')) {
                    isLookaround = true;
                    isLookbehind = true;
                    bodyStart = i + 4;
                } else {
                    isLookaround = false;
                    isLookbehind = false;
                    bodyStart = -1;
                }
            } else if (i + 1 < len && pattern.charAt(i) == '(' && pattern.charAt(i + 1) == '*') {
                final int colon = pattern.indexOf(':', i + 2);
                if (colon > 0 && colon < len) {
                    final String name = pattern.substring(i + 2, colon);
                    if ("positive_lookahead".equals(name) || "negative_lookahead".equals(name)) {
                        isLookaround = true;
                        isLookbehind = false;
                        bodyStart = colon + 1;
                    } else if ("positive_lookbehind".equals(name) || "negative_lookbehind".equals(name)) {
                        isLookaround = true;
                        isLookbehind = true;
                        bodyStart = colon + 1;
                    } else {
                        isLookaround = false;
                        isLookbehind = false;
                        bodyStart = -1;
                    }
                } else {
                    isLookaround = false;
                    isLookbehind = false;
                    bodyStart = -1;
                }
            } else {
                isLookaround = false;
                isLookbehind = false;
                bodyStart = -1;
            }
            if (!isLookaround) {
                i++;
                continue;
            }
            // Find matching closing ')' for this lookaround group
            int depth = 1;
            int j = bodyStart;
            boolean bodyHasUnboundedQuantifier = false;
            while (j < len && depth > 0) {
                final char cj = pattern.charAt(j);
                if (cj == '\\') {
                    j += 2;
                    continue;
                }
                if (cj == '[') {
                    final int closeBracket = pattern.indexOf(']', j + 1);
                    if (closeBracket < 0) {
                        return; // malformed; let outer error handling take over
                    }
                    j = closeBracket + 1;
                    continue;
                }
                if (cj == '(') {
                    depth++;
                } else if (cj == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                } else if (depth == 1 && (cj == '*' || cj == '+' || cj == '?')) {
                    bodyHasUnboundedQuantifier = true;
                }
                j++;
            }
            if (depth != 0) {
                return; // unbalanced parens; outer machinery handles
            }
            if (isLookbehind && bodyHasUnboundedQuantifier) {
                throw new XPathException(context, ErrorCodes.FORX0002,
                        "Invalid regular expression: lookbehind assertion must be fixed-length "
                                + "(unbounded quantifier in body)",
                        new StringValue(pattern));
            }
            // Quantifier after the lookaround group is also invalid
            if (j + 1 < len) {
                final char after = pattern.charAt(j + 1);
                if (after == '?' || after == '+' || after == '*' || after == '{') {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: lookaround assertion cannot be quantified ('"
                                    + after + "' after closing ')')",
                            new StringValue(pattern));
                }
            }
            i = j + 1; // continue past closing ')'
        }
    }

    /**
     * Scans a character class starting at the '[' at position {@code start},
     * validating its contents and returning the position of the matching ']'.
     * Rejects POSIX-style {@code [:name:]} classes and invalid escapes inside
     * the class (e.g. backslash-x or backslash-u) that the outer scanner would
     * otherwise miss because it never enters the class body.
     */
    private static int scanCharClass(final Expression context, final String pattern,
            final int start, final int len) throws XPathException {
        int j = start + 1; // skip '['
        if (j < len && pattern.charAt(j) == '^') {
            j++;
        }
        while (j < len) {
            final char cc = pattern.charAt(j);
            if (cc == ']') {
                return j;
            }
            if (cc == '\\') {
                if (j + 1 >= len) {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: trailing backslash inside character class",
                            new StringValue(pattern));
                }
                final char ec = pattern.charAt(j + 1);
                switch (ec) {
                    case 'n': case 'r': case 't':
                    case '\\': case '|': case '.': case '-': case '^':
                    case '?': case '*': case '+':
                    case '{': case '}': case '(': case ')':
                    case '[': case ']': case '$':
                    case ' ':
                    case 'd': case 'D': case 's': case 'S':
                    case 'w': case 'W': case 'i': case 'I':
                    case 'c': case 'C':
                    case 'b': case 'B': // \b inside class is backspace, allowed
                        j += 2;
                        break;
                    case 'p': case 'P':
                        if (j + 2 < len && pattern.charAt(j + 2) == '{') {
                            final int close = pattern.indexOf('}', j + 3);
                            if (close < 0) {
                                throw new XPathException(context, ErrorCodes.FORX0002,
                                        "Invalid regular expression: unclosed \\p{ inside character class",
                                        new StringValue(pattern));
                            }
                            j = close + 1;
                        } else {
                            throw new XPathException(context, ErrorCodes.FORX0002,
                                    "Invalid regular expression: \\p or \\P must be followed by {Name}",
                                    new StringValue(pattern));
                        }
                        break;
                    default:
                        throw new XPathException(context, ErrorCodes.FORX0002,
                                "Invalid regular expression: \\" + ec
                                        + " is not a recognized escape sequence inside character class",
                                new StringValue(pattern));
                }
            } else if (cc == '[' && j + 1 < len && pattern.charAt(j + 1) == ':') {
                throw new XPathException(context, ErrorCodes.FORX0002,
                        "Invalid regular expression: POSIX character class [:...:] is not supported in XPath regular expressions",
                        new StringValue(pattern));
            } else if (cc == '[') {
                // Nested class for character class subtraction [A-[B]]
                j = scanCharClass(context, pattern, j, len) + 1;
            } else {
                j++;
            }
        }
        throw new XPathException(context, ErrorCodes.FORX0002,
                "Invalid regular expression: unclosed character class",
                new StringValue(pattern));
    }

    private static final Pattern XPATH4_LOOKAROUND = Pattern.compile(
            "\\(\\*(" +
                    "positive_lookahead|negative_lookahead|" +
                    "positive_lookbehind|negative_lookbehind" +
                    "):");

    /**
     * Translates XPath 4.0 lookaround syntax to Java regex syntax.
     *
     * <p>XPath 4.0 defines named lookaround groups:</p>
     * <ul>
     *   <li>{@code (*positive_lookahead:...)} → {@code (?=...)}</li>
     *   <li>{@code (*negative_lookahead:...)} → {@code (?!...)}</li>
     *   <li>{@code (*positive_lookbehind:...)} → {@code (?<=...)}</li>
     *   <li>{@code (*negative_lookbehind:...)} → {@code (?<!...)}</li>
     * </ul>
     *
     * @param pattern the XPath regex pattern
     * @return the pattern with any XPath 4.0 lookaround translated to Java syntax,
     *         or the original pattern if no lookaround is present
     */
    public static String translateXPath4Lookaround(final String pattern) {
        if (!pattern.contains("(*")) {
            return pattern;
        }

        final Matcher m = XPATH4_LOOKAROUND.matcher(pattern);
        if (!m.find()) {
            return pattern;
        }

        final StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            final String replacement = switch (m.group(1)) {
                case "positive_lookahead" -> "(?=";
                case "negative_lookahead" -> "(?!";
                case "positive_lookbehind" -> "(?<=";
                case "negative_lookbehind" -> "(?<!";
                default -> m.group(0); // shouldn't happen
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Checks whether a pattern contains XPath 4.0 lookaround syntax.
     *
     * @param pattern the regex pattern
     * @return true if the pattern contains (*positive_lookahead:...) or similar
     */
    public static boolean hasXPath4Lookaround(final String pattern) {
        return pattern.contains("(*") && XPATH4_LOOKAROUND.matcher(pattern).find();
    }

    /**
     * Checks whether a pattern uses XPath 4.0 regex extensions that Saxon's
     * XP30 mode cannot handle, requiring Java regex compilation instead.
     *
     * <p>This includes:</p>
     * <ul>
     *   <li>Word boundaries: {@code \b}, {@code \B}</li>
     *   <li>Java-style lookaround: {@code (?=...)}, {@code (?!...)}, {@code (?<=...)}, {@code (?<!...)}</li>
     *   <li>Named lookaround: {@code (*positive_lookahead:...)}, etc.</li>
     * </ul>
     *
     * @param pattern the regex pattern
     * @return true if the pattern needs Java regex handling for XQ4 extensions
     */
    public static boolean needsXQuery40JavaRegex(final String pattern) {
        if (pattern.contains("\\b") || pattern.contains("\\B")) {
            return true;
        }
        if (pattern.contains("(?=") || pattern.contains("(?!")
                || pattern.contains("(?<=") || pattern.contains("(?<!")) {
            return true;
        }
        return hasXPath4Lookaround(pattern);
    }
}
