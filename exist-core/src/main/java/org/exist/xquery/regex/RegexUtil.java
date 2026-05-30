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

import net.sf.saxon.regex.JavaRegularExpression;
import net.sf.saxon.str.StringView;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.StringValue;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RegexUtil {

    /**
     * Compiled pattern matching XPath 4.0 named lookaround group syntax,
     * e.g. {@code (*positive_lookahead:...)}, used by
     * {@link #hasXPath4Lookaround(String)} and {@link #translateXPath4Lookaround(String)}.
     */
    private static final Pattern XPATH4_LOOKAROUND = Pattern.compile(
            "\\(\\*(" +
                    "positive_lookahead|negative_lookahead|" +
                    "positive_lookbehind|negative_lookbehind" +
                    "):");

    /**
     * Single-character escapes that are valid in XPath regular expressions per
     * F&amp;O 3.1 §5.6.1. Membership lookup keeps {@link #validateBackslashEscape}
     * out of an NPath explosion from a long multi-label case arm.
     */
    private static final java.util.Set<Character> XPATH_REGEX_SIMPLE_ESCAPES = java.util.Set.of(
            'n', 'r', 't',
            '\\', '|', '.', '-', '^',
            '?', '*', '+',
            '{', '}', '(', ')',
            '[', ']', '$',
            // Space: not strictly in the XPath spec but Saxon allows
            // \<space> in free-spacing mode ('x' flag) for literal space
            ' ',
            // Valid XPath multi-character escape shortcuts
            'd', 'D', 's', 'S',
            'w', 'W', 'i', 'I',
            'c', 'C');

    /**
     * Single-character escapes that are valid inside an XPath regex character
     * class. Same set as {@link #XPATH_REGEX_SIMPLE_ESCAPES} plus {@code '\b'}
     * (which inside a class is a literal backspace, not a word boundary) and
     * {@code '\B'}.
     */
    private static final java.util.Set<Character> XPATH_REGEX_CHARCLASS_SIMPLE_ESCAPES = java.util.Set.of(
            'n', 'r', 't',
            '\\', '|', '.', '-', '^',
            '?', '*', '+',
            '{', '}', '(', ')',
            '[', ']', '$',
            ' ',
            'd', 'D', 's', 'S',
            'w', 'W', 'i', 'I',
            'c', 'C',
            // \b inside class is backspace, allowed; \B likewise
            'b', 'B');

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
        // convert pattern to Java regex syntax using Saxon's regex translator
        try {
            final StringBuilder flags = new StringBuilder();
            if (ignoreWhitespace) {
                flags.append('x');
            }
            if (caseBlind) {
                flags.append('i');
            }

            final JavaRegularExpression regex = new JavaRegularExpression(StringView.of(pattern), flags.toString());
            return regex.getJavaRegularExpression();
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw new XPathException(context, ErrorCodes.FORX0002, "Conversion from XPath F&O 3.0 regular expression syntax to Java regular expression syntax failed: " + e.getMessage(), new StringValue(pattern), e);
        }
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
        // Total capturing groups in the pattern, used as a maximum-digits cap
        // when greedily parsing back-references like \11 vs \1+'1'.
        final int totalGroups = countCapturingGroups(pattern);
        // Closed capturing groups encountered so far. Back-references must
        // refer to a group that has already CLOSED at the reference position
        // — forward references like \1(abc) are invalid.
        int closedGroupCount = 0;
        // Stack tracks whether each currently-open group is capturing.
        final java.util.Deque<Boolean> groupStack = new java.util.ArrayDeque<>();
        for (int i = 0; i < len; i++) {
            final char c = pattern.charAt(i);

            if (c == '\\') {
                i = validateBackslashEscape(context, pattern, i, len, isXQuery40, totalGroups, closedGroupCount);
            } else if (c == '(' && i + 1 < len && pattern.charAt(i + 1) == '?') {
                validateSpecialGroupOpen(context, pattern, i, len, isXQuery40);
                groupStack.push(Boolean.FALSE);
            } else if (c == '(' && i + 1 < len && pattern.charAt(i + 1) == '*') {
                // (*name:...) — XPath 4.0 named lookaround
                if (!isXQuery40) {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: (*...) named groups are not supported "
                                    + "in XPath 3.1 regular expressions",
                            new StringValue(pattern));
                }
                // Skip to the closing ':' — let translateXPath4Lookaround handle the details
                groupStack.push(Boolean.FALSE);
            } else if (c == '(') {
                // Plain capturing group
                groupStack.push(Boolean.TRUE);
            } else if (c == ')') {
                if (!groupStack.isEmpty() && Boolean.TRUE.equals(groupStack.pop())) {
                    closedGroupCount++;
                }
            } else if (c == '[') {
                // Scan character class content, rejecting POSIX [:name:] syntax
                // and invalid escapes that aren't caught by the outer scanner.
                i = scanCharClass(context, pattern, i, len);
            } else if (isXQuery40 && (c == '^' || c == '$') && i + 1 < len) {
                // XPath 4.0 tightened the regex grammar so that anchors '^' and '$'
                // are zero-width assertions that cannot themselves be quantified.
                // Saxon's XP30 mode accepts patterns like 'alp^?ha' or 'alpha$+';
                // reject them in XQuery 4.0.
                final char nextCh = pattern.charAt(i + 1);
                if (nextCh == '?' || nextCh == '+' || nextCh == '*' || nextCh == '{') {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: anchor '" + c
                                    + "' cannot be quantified by '" + nextCh + "' in XQuery 4.0",
                            new StringValue(pattern));
                }
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
     * Validate the backslash escape at position {@code i} (which points at the
     * {@code '\\'}). Returns the position of the LAST consumed character so the
     * caller's {@code for} loop's {@code i++} lands on the next unconsumed char.
     */
    private static int validateBackslashEscape(final Expression context, final String pattern,
            final int i, final int len, final boolean isXQuery40,
            final int totalGroups, final int closedGroupCount) throws XPathException {
        if (i + 1 >= len) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: trailing backslash",
                    new StringValue(pattern));
        }
        final char next = pattern.charAt(i + 1);
        return switch (classifyBackslashEscape(next)) {
            case SIMPLE -> i + 1;
            case WORD_BOUNDARY -> validateWordBoundary(context, pattern, i, len, isXQuery40, next);
            case OCTAL_ZERO -> throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: \\0 (octal escape) is not supported in XPath regular expressions",
                    new StringValue(pattern));
            case BACKREF -> validateBackReference(context, pattern, i, len, next, totalGroups, closedGroupCount);
            case CATEGORY -> validateCategoryEscape(context, pattern, i, len);
            // Any other backslash escape is invalid in XPath regex.
            // This catches: \x (hex), \\u (Java unicode),
            // \A \Z \z (Java anchors),
            // \a \e \f \v (special chars), \Q \E (literal mode),
            // \G \k \g (named backrefs)
            case UNKNOWN -> throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: \\" + next
                            + " is not a recognized escape sequence in XPath regular expressions",
                    new StringValue(pattern));
        };
    }

    /** Classification of a backslash-escape's second character. */
    private enum BackslashKind { SIMPLE, WORD_BOUNDARY, OCTAL_ZERO, BACKREF, CATEGORY, UNKNOWN }

    private static BackslashKind classifyBackslashEscape(final char next) {
        if (XPATH_REGEX_SIMPLE_ESCAPES.contains(next)) {
            return BackslashKind.SIMPLE;
        }
        if (next == 'b' || next == 'B') {
            return BackslashKind.WORD_BOUNDARY;
        }
        if (next == '0') {
            return BackslashKind.OCTAL_ZERO;
        }
        if (next >= '1' && next <= '9') {
            return BackslashKind.BACKREF;
        }
        if (next == 'p' || next == 'P') {
            return BackslashKind.CATEGORY;
        }
        return BackslashKind.UNKNOWN;
    }

    private static int validateWordBoundary(final Expression context, final String pattern,
            final int i, final int len, final boolean isXQuery40, final char next) throws XPathException {
        if (!isXQuery40) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: \\" + next
                            + " is not a recognized escape sequence in XPath 3.1 regular expressions",
                    new StringValue(pattern));
        }
        if (i + 2 < len) {
            final char q = pattern.charAt(i + 2);
            if (q == '?' || q == '+' || q == '*' || q == '{') {
                throw new XPathException(context, ErrorCodes.FORX0002,
                        "Invalid regular expression: quantifier '" + q
                                + "' after \\" + next + " boundary assertion is not allowed",
                        new StringValue(pattern));
            }
        }
        return i + 1;
    }

    private static int validateBackReference(final Expression context, final String pattern,
            final int i, final int len, final char first,
            final int totalGroups, final int closedGroupCount) throws XPathException {
        // Back-reference \N. N is parsed greedily but capped at the total
        // number of capturing groups so '\19' in a 1-group pattern is '\1'
        // + literal '9', while '\11' in an 11-group pattern is back-ref 11.
        // The chosen N must also reference a group that has already CLOSED
        // at this position; forward references and self-references are invalid.
        int j = i + 2;
        int num = first - '0';
        while (j < len && pattern.charAt(j) >= '0' && pattern.charAt(j) <= '9') {
            final int candidate = num * 10 + (pattern.charAt(j) - '0');
            if (candidate > totalGroups) {
                break;
            }
            num = candidate;
            j++;
        }
        if (num < 1 || num > closedGroupCount) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: back-reference \\" + num
                            + " refers to a capturing group that does not exist or has not been closed at this position",
                    new StringValue(pattern));
        }
        return j - 1;
    }

    private static int validateCategoryEscape(final Expression context, final String pattern,
            final int i, final int len) throws XPathException {
        if (i + 2 >= len || pattern.charAt(i + 2) != '{') {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: \\p or \\P must be followed by {Name}",
                    new StringValue(pattern));
        }
        final int close = pattern.indexOf('}', i + 3);
        if (close < 0) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: unclosed \\p{ property escape",
                    new StringValue(pattern));
        }
        return close;
    }

    /**
     * Validate the special group opening at position {@code i} (where {@code i}
     * points at {@code '('} and {@code i+1} is {@code '?'}). In XPath 3.1 only
     * {@code (?:...)} is permitted; XPath 4.0 also allows the four lookaround
     * forms {@code (?=...) (?!...) (?<=...) (?<!...)}.
     */
    private static void validateSpecialGroupOpen(final Expression context, final String pattern,
            final int i, final int len, final boolean isXQuery40) throws XPathException {
        if (i + 2 >= len) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: incomplete group syntax at position " + i,
                    new StringValue(pattern));
        }
        final char groupType = pattern.charAt(i + 2);
        if (groupType == ':') {
            return; // (?:...) — always valid
        }
        if (isXQuery40 && (groupType == '=' || groupType == '!')) {
            return; // (?=...) (?!...) — valid in XPath 4.0
        }
        if (isXQuery40 && groupType == '<' && i + 3 < len
                && (pattern.charAt(i + 3) == '=' || pattern.charAt(i + 3) == '!')) {
            return; // (?<=...) (?<!...) — valid in XPath 4.0
        }
        throw new XPathException(context, ErrorCodes.FORX0002,
                "Invalid regular expression: non-capturing group (?:...) is the only "
                        + "permitted group syntax in XPath regular expressions; "
                        + "found (?" + groupType + " at position " + i,
                new StringValue(pattern));
    }

    /**
     * Counts capturing groups in a regex pattern. A '(' opens a capturing
     * group unless followed by '?' (e.g. {@code (?:...)}, {@code (?=...)})
     * or '*' (e.g. {@code (*positive_lookahead:...)}). Escapes and character
     * classes are skipped so '(' inside them is not counted.
     */
    private static int countCapturingGroups(final String pattern) {
        final int len = pattern.length();
        int count = 0;
        int i = 0;
        while (i < len) {
            final char c = pattern.charAt(i);
            if (c == '\\') {
                i = skipBackslashForGroupCount(pattern, i, len);
            } else if (c == '[') {
                i = skipCharClassForGroupCount(pattern, i, len);
            } else {
                if (isCapturingGroupOpen(pattern, i, len)) {
                    count++;
                }
                i++;
            }
        }
        return count;
    }

    private static int skipBackslashForGroupCount(final String pattern, final int i, final int len) {
        if (i + 1 < len && (pattern.charAt(i + 1) == 'p' || pattern.charAt(i + 1) == 'P')
                && i + 2 < len && pattern.charAt(i + 2) == '{') {
            final int close = pattern.indexOf('}', i + 3);
            return close < 0 ? len : close + 1;
        }
        return i + 2;
    }

    private static int skipCharClassForGroupCount(final String pattern, final int i, final int len) {
        // Skip the character class body. Track nested '[' for subtraction
        // classes and ignore escapes within.
        int j = i + 1;
        int depth = 1;
        while (j < len && depth > 0) {
            final char cj = pattern.charAt(j);
            if (cj == '\\') {
                j += 2;
                continue;
            }
            if (cj == '[') {
                depth++;
            } else if (cj == ']') {
                depth--;
            }
            j++;
        }
        return j;
    }

    private static boolean isCapturingGroupOpen(final String pattern, final int i, final int len) {
        if (pattern.charAt(i) != '(') {
            return false;
        }
        if (i + 1 >= len) {
            return true;
        }
        final char next = pattern.charAt(i + 1);
        return next != '?' && next != '*';
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
            final Lookaround look = detectLookaroundAt(pattern, i, len);
            if (look == null) {
                i++;
                continue;
            }
            final LookaroundBody body = scanLookaroundBody(pattern, look.bodyStart, len);
            if (body == null) {
                return; // malformed body; let outer error handling take over
            }
            if (look.isLookbehind && body.hasUnboundedQuantifier) {
                throw new XPathException(context, ErrorCodes.FORX0002,
                        "Invalid regular expression: lookbehind assertion must be fixed-length "
                                + "(unbounded quantifier in body)",
                        new StringValue(pattern));
            }
            failIfQuantifierAfter(context, pattern, body.closeParen, len);
            i = body.closeParen + 1;
        }
    }

    /** Describes a lookaround group recognized at a given start position. */
    private static final class Lookaround {
        final boolean isLookbehind;
        final int bodyStart;

        Lookaround(final boolean isLookbehind, final int bodyStart) {
            this.isLookbehind = isLookbehind;
            this.bodyStart = bodyStart;
        }
    }

    /** Result of scanning a lookaround body: closing-')' index and whether an unbounded quantifier was seen. */
    private static final class LookaroundBody {
        final int closeParen;
        final boolean hasUnboundedQuantifier;

        LookaroundBody(final int closeParen, final boolean hasUnboundedQuantifier) {
            this.closeParen = closeParen;
            this.hasUnboundedQuantifier = hasUnboundedQuantifier;
        }
    }

    /**
     * Detect a lookaround group at position {@code i}, returning its body-start
     * position and direction, or {@code null} if the position does not start a
     * lookaround. Recognizes both Perl-style {@code (?=}, {@code (?!}, {@code (?<=},
     * {@code (?<!} and XPath-4-style {@code (*positive_lookahead:} family.
     */
    @Nullable
    private static Lookaround detectLookaroundAt(final String pattern, final int i, final int len) {
        if (i + 3 < len && pattern.charAt(i) == '(' && pattern.charAt(i + 1) == '?') {
            return detectPerlLookaround(pattern, i, len);
        }
        if (i + 1 < len && pattern.charAt(i) == '(' && pattern.charAt(i + 1) == '*') {
            return detectXPath4Lookaround(pattern, i, len);
        }
        return null;
    }

    @Nullable
    private static Lookaround detectPerlLookaround(final String pattern, final int i, final int len) {
        final char gt = pattern.charAt(i + 2);
        if (gt == '=' || gt == '!') {
            return new Lookaround(false, i + 3);
        }
        if (gt == '<' && i + 4 < len
                && (pattern.charAt(i + 3) == '=' || pattern.charAt(i + 3) == '!')) {
            return new Lookaround(true, i + 4);
        }
        return null;
    }

    @Nullable
    private static Lookaround detectXPath4Lookaround(final String pattern, final int i, final int len) {
        final int colon = pattern.indexOf(':', i + 2);
        if (colon <= 0 || colon >= len) {
            return null;
        }
        final String name = pattern.substring(i + 2, colon);
        if ("positive_lookahead".equals(name) || "negative_lookahead".equals(name)) {
            return new Lookaround(false, colon + 1);
        }
        if ("positive_lookbehind".equals(name) || "negative_lookbehind".equals(name)) {
            return new Lookaround(true, colon + 1);
        }
        return null;
    }

    /**
     * Walk from {@code bodyStart} to the matching closing {@code ')'} of a
     * lookaround group, tracking whether any depth-1 unbounded quantifier
     * appears in the body. Returns {@code null} on malformed input (unclosed
     * character class or unbalanced parens) so the caller can defer to outer
     * error handling.
     */
    @Nullable
    private static LookaroundBody scanLookaroundBody(final String pattern, final int bodyStart, final int len) {
        int depth = 1;
        int j = bodyStart;
        boolean hasUnbounded = false;
        while (j < len && depth > 0) {
            final char cj = pattern.charAt(j);
            if (cj == '\\') {
                j += 2;
                continue;
            }
            if (cj == '[') {
                final int closeBracket = pattern.indexOf(']', j + 1);
                if (closeBracket < 0) {
                    return null;
                }
                j = closeBracket + 1;
                continue;
            }
            if (cj == '(') {
                depth++;
            } else if (cj == ')') {
                depth--;
                if (depth == 0) {
                    return new LookaroundBody(j, hasUnbounded);
                }
            } else if (depth == 1 && (cj == '*' || cj == '+' || cj == '?')) {
                hasUnbounded = true;
            }
            j++;
        }
        return null;
    }

    /**
     * After a lookaround group closes at {@code closeParen}, the character
     * immediately following must not be a quantifier — lookarounds cannot be
     * quantified per the XPath F&amp;O regex spec.
     */
    private static void failIfQuantifierAfter(final Expression context, final String pattern,
            final int closeParen, final int len) throws XPathException {
        if (closeParen + 1 >= len) {
            return;
        }
        final char after = pattern.charAt(closeParen + 1);
        if (after == '?' || after == '+' || after == '*' || after == '{') {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: lookaround assertion cannot be quantified ('"
                            + after + "' after closing ')')",
                    new StringValue(pattern));
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
        // Track unescaped '-' immediately preceding the current position to
        // disambiguate subtraction (charClassSub) from a literal hyphen.
        // The XPath/XSD grammar requires the left side of subtraction to be
        // a non-empty (pos|neg)CharGroup, i.e. partsBeforeLastHyphen >= 1.
        int partsCount = 0;
        boolean lastWasUnescapedHyphen = false;
        int partsBeforeLastHyphen = 0;
        while (j < len) {
            final char cc = pattern.charAt(j);
            if (cc == ']') {
                if (partsCount == 0) {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: empty character class is not allowed",
                            new StringValue(pattern));
                }
                return j;
            }
            if (cc == '\\') {
                j = scanEscapeInCharClass(context, pattern, j, len);
                partsCount++;
                lastWasUnescapedHyphen = false;
                continue;
            }
            if (cc == '[') {
                return scanNestedCharClass(context, pattern, j, len, lastWasUnescapedHyphen, partsBeforeLastHyphen);
            }
            // Regular char (including literal '-')
            if (cc == '-') {
                partsBeforeLastHyphen = partsCount;
                lastWasUnescapedHyphen = true;
            } else {
                lastWasUnescapedHyphen = false;
            }
            partsCount++;
            j++;
        }
        throw new XPathException(context, ErrorCodes.FORX0002,
                "Invalid regular expression: unclosed character class",
                new StringValue(pattern));
    }

    /**
     * Handle a backslash-escape inside a character class. {@code j} points at
     * the backslash. Returns the position immediately after the escape.
     */
    private static int scanEscapeInCharClass(final Expression context, final String pattern,
            final int j, final int len) throws XPathException {
        if (j + 1 >= len) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: trailing backslash inside character class",
                    new StringValue(pattern));
        }
        final char ec = pattern.charAt(j + 1);
        if (XPATH_REGEX_CHARCLASS_SIMPLE_ESCAPES.contains(ec)) {
            return j + 2;
        }
        if (ec == 'p' || ec == 'P') {
            return scanCategoryEscape(context, pattern, j, len);
        }
        throw new XPathException(context, ErrorCodes.FORX0002,
                "Invalid regular expression: \\" + ec
                        + " is not a recognized escape sequence inside character class",
                new StringValue(pattern));
    }

    /**
     * Handle a {@code \p{Name}} or {@code \P{Name}} category escape inside a
     * character class. {@code j} points at the backslash. Returns the position
     * immediately after the closing {@code '}'}.
     */
    private static int scanCategoryEscape(final Expression context, final String pattern,
            final int j, final int len) throws XPathException {
        if (j + 2 >= len || pattern.charAt(j + 2) != '{') {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: \\p or \\P must be followed by {Name}",
                    new StringValue(pattern));
        }
        final int close = pattern.indexOf('}', j + 3);
        if (close < 0) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: unclosed \\p{ inside character class",
                    new StringValue(pattern));
        }
        return close + 1;
    }

    /**
     * Handle a nested {@code '['} inside a character class. Per the XPath/XSD
     * grammar this is only valid as the start of a subtraction class —
     * the bracket must be preceded by an unescaped {@code '-'} separator AND
     * a non-empty (pos|neg)CharGroup. Returns the position of the closing
     * {@code ']'} of the OUTER class so the caller can finish.
     */
    private static int scanNestedCharClass(final Expression context, final String pattern,
            final int j, final int len, final boolean lastWasUnescapedHyphen,
            final int partsBeforeLastHyphen) throws XPathException {
        if (j + 1 < len && pattern.charAt(j + 1) == ':') {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: POSIX character class [:...:] is not supported in XPath regular expressions",
                    new StringValue(pattern));
        }
        if (!lastWasUnescapedHyphen) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: '[' inside character class is only allowed after a '-' subtraction separator",
                    new StringValue(pattern));
        }
        if (partsBeforeLastHyphen < 1) {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: character class subtraction requires a non-empty character group before the '-' separator",
                    new StringValue(pattern));
        }
        final int innerEnd = scanCharClass(context, pattern, j, len);
        final int after = innerEnd + 1;
        if (after >= len || pattern.charAt(after) != ']') {
            throw new XPathException(context, ErrorCodes.FORX0002,
                    "Invalid regular expression: closing ']' expected after subtraction character class",
                    new StringValue(pattern));
        }
        return after;
    }

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
