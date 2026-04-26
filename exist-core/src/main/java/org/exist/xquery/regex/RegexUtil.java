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
     * regular expression specification (F&amp;O 3.1, Section 5.6.1).
     *
     * <p>Saxon's XP30 regex compiler accepts many Java/Perl regex constructs
     * that are not part of the XPath regex specification. This method rejects
     * such constructs with FORX0002 before they reach the Saxon compiler.</p>
     *
     * @param context the calling expression, for error reporting
     * @param pattern the regex pattern to validate
     * @throws XPathException with FORX0002 if the pattern uses non-XPath constructs
     */
    public static void validateXPathRegex(final Expression context, final String pattern) throws XPathException {
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
                    // Back-references (\1-\9) and octal escapes (\0nn) are not part of
                    // XPath regex but are used internally by eXist-db (e.g., test.xq).
                    // Allow them for compatibility.
                    case '0': case '1': case '2': case '3': case '4':
                    case '5': case '6': case '7': case '8': case '9':
                        i++; // skip the escaped character
                        break;
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
                        // \A \Z \z (Java anchors), \b \B (word boundary),
                        // \a \e \f \v (special chars), \Q \E (literal mode),
                        // \G \k \g (named backrefs)
                        throw new XPathException(context, ErrorCodes.FORX0002,
                                "Invalid regular expression: \\" + next
                                        + " is not a recognized escape sequence in XPath regular expressions",
                                new StringValue(pattern));
                }
            } else if (c == '(' && i + 1 < len && pattern.charAt(i + 1) == '?') {
                // Only (?:...) is valid in XPath regex.
                // Reject: (?=...) (?!...) (?<...) (?>...) (?i...) (?m...) (?s...) (?-...) (?P...) (?c...)
                if (i + 2 >= len || pattern.charAt(i + 2) != ':') {
                    throw new XPathException(context, ErrorCodes.FORX0002,
                            "Invalid regular expression: non-capturing group (?:...) is the only "
                                    + "permitted group syntax in XPath regular expressions; "
                                    + "found (?" + (i + 2 < len ? pattern.charAt(i + 2) : "")
                                    + " at position " + i,
                            new StringValue(pattern));
                }
            } else if (c == '[') {
                // Skip through character class — escape rules inside are the same,
                // so we continue scanning (backslash escapes are checked above)
                i++; // skip past '['
                // Handle negation
                if (i < len && pattern.charAt(i) == '^') {
                    i++;
                }
                // In XPath regex, ']' as first char in a class is NOT a literal
                // (unlike PCRE/Java). We don't need to skip it.
            } else if ((c == '*' || c == '+' || c == '?') && i + 1 < len
                    && pattern.charAt(i + 1) == '+') {
                // Possessive quantifiers (*+, ++, ?+) are not valid in XPath regex
                throw new XPathException(context, ErrorCodes.FORX0002,
                        "Invalid regular expression: possessive quantifier "
                                + c + "+ is not supported in XPath regular expressions",
                        new StringValue(pattern));
            }
        }
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
}
