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
package org.exist.indexing.range;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.exist.xquery.regex.RegexUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Decides whether an XPath regular expression can be evaluated by Lucene's {@code RegExp} with
 * XPath semantics, and translates it when it can.
 *
 * <p>Lucene's {@code RegExp} is a different dialect from XPath's. Probed against each other on the
 * constructs users actually write, they disagree on: {@code \d} (Lucene is ASCII-only), {@code \w}
 * (Lucene includes {@code _} and excludes non-ASCII letters), {@code .} (Lucene matches a newline;
 * XPath does not), character-class subtraction (Lucene has no such syntax and misreads it), and
 * {@code \p{..}} (Lucene throws {@code IllegalArgumentException}). Lucene also treats a backslash
 * before any character as escaping that character, so XPath's {@code \n}, {@code \r} and
 * {@code \t} would become the letters. The XQuery 4.0 additions -- {@code \b}, {@code \B} and the
 * named lookarounds such as {@code (*positive_lookahead:...)} -- have no Lucene equivalent.</p>
 *
 * <p>Case-insensitive matching differs too. XPath's case variants come from Unicode case mappings;
 * Lucene's folding agrees with them for every ASCII letter except {@code i} and {@code I}, whose
 * variants in XPath also include U+0130 and U+0131 (the Turkish dotted capital and dotless small
 * i). For non-ASCII letters the two are not guaranteed to agree at all.</p>
 *
 * <p>The rule is therefore conservative: a pattern is translatable only if every construct in it
 * means the same thing in both dialects. Anything else goes to the fallback expression, which is
 * {@code fn:matches} itself -- slower, and right. The one construct that is rewritten rather than
 * rejected is {@code .}, which becomes {@code [^\n\r]} so that the common {@code ^a.b$} stays on
 * the index. Under the {@code i} flag, {@code i} and {@code I} are rewritten as well, to include
 * U+0130 and U+0131.</p>
 *
 * <p>Anchors are handled as before: Lucene's {@code RegexpQuery} matches whole terms, so
 * {@code ^X} becomes {@code X.*}, {@code X$} becomes {@code .*X}, and {@code ^X$} becomes
 * {@code X}. A pattern with neither anchor is never sent to the index.</p>
 *
 * @see org.apache.lucene.search.RegexpQuery
 */
public final class XPathToLuceneRegexTranslator {

    /**
     * Escapes whose meaning differs between the two dialects, or which Lucene does not support:
     * back-references, XML name classes and their complements, Unicode properties and blocks,
     * the Perl-style classes, and the whitespace escapes Lucene would read as letters.
     */
    private static final String UNTRANSLATABLE_ESCAPES = "123456789icICpPdDwWsSnrt";

    /** What XPath's {@code .} matches, spelled for Lucene: any character except LF and CR. */
    private static final String XPATH_ANY_CHARACTER = "[^\n\r]";

    /**
     * The case variants of ASCII letters that XPath's case-insensitive matching includes and Lucene's ASCII folding
     * misses: U+0130 (capital I with dot above) and U+0131 (small dotless i) for i, U+212A (Kelvin sign) for k, and
     * U+017F (long s) for s.
     */
    private static String extraCaseVariants(final int c) {
        return switch (Character.toLowerCase(c)) {
            case 'i' -> "\u0130\u0131";
            case 'k' -> "\u212A";
            case 's' -> "\u017F";
            default -> "";
        };
    }

    private XPathToLuceneRegexTranslator() {
    }

    /**
     * Whether a case-sensitive pattern can be served by Lucene with XPath semantics.
     *
     * @param xpathPattern the pattern
     * @return true if {@link #translate} yields an equivalent Lucene pattern
     */
    public static boolean isTranslatable(@Nullable final String xpathPattern) {
        return isTranslatable(xpathPattern, false);
    }

    /**
     * Whether a pattern can be served by Lucene with XPath semantics.
     *
     * @param xpathPattern the pattern
     * @param caseInsensitive whether the {@code i} flag is in effect; a case-insensitive pattern
     *   containing a non-ASCII letter is not translatable, since XPath's and Lucene's case variants
     *   are not guaranteed to agree for those
     * @return true if {@link #translate} yields an equivalent Lucene pattern
     */
    public static boolean isTranslatable(@Nullable final String xpathPattern, final boolean caseInsensitive) {
        if (xpathPattern == null || xpathPattern.isEmpty()) {
            return false;
        }
        if (!xpathPattern.startsWith("^") && !xpathPattern.endsWith("$")) {
            return false;
        }
        return !RegexUtil.needsXQuery40JavaRegex(xpathPattern)
                && !containsUntranslatableConstruct(xpathPattern, caseInsensitive);
    }

    /**
     * Whether the range index can serve a pattern: it is {@link #isTranslatable translatable}, and
     * Lucene can build a query for the translation. A pattern can be translatable and still too
     * expensive for Lucene to determinize, e.g. {@code ^(a|b)*a(a|b){20}$}; it is valid XPath, so
     * the answer is to evaluate it with {@code fn:matches}, not to raise an error.
     *
     * @param xpathPattern the pattern
     * @param matchFlags the Lucene match flags, as passed to {@link #toQuery}
     * @return true if {@link #toQuery} will succeed and give the XPath answer
     */
    public static boolean isServable(@Nullable final String xpathPattern, final int matchFlags) {
        if (!isTranslatable(xpathPattern, isCaseInsensitive(matchFlags))) {
            return false;
        }
        try {
            automaton(translate(xpathPattern, isCaseInsensitive(matchFlags)), matchFlags);
            return true;
        } catch (final IllegalArgumentException | TooComplexToDeterminizeException e) {
            return false;
        }
    }

    /**
     * Builds the Lucene query for a translatable pattern.
     *
     * <p>Syntax flags are passed explicitly. {@code RegexpQuery(Term)} would default to
     * {@code RegExp.ALL}, under which {@code & ~ # @} and {@code <n-m>} are operators; XPath treats
     * them as literals.</p>
     *
     * @param field the index field
     * @param xpathPattern the XPath pattern, already checked with {@link #isTranslatable}
     * @param matchFlags the Lucene match flags, e.g. {@code RegExp.ASCII_CASE_INSENSITIVE}
     * @return the query
     * @throws IllegalArgumentException if Lucene cannot parse the translation
     * @throws TooComplexToDeterminizeException if Lucene cannot determinize it within its work limit
     */
    public static Query toQuery(final String field, final String xpathPattern, final int matchFlags) {
        final String pattern = translate(xpathPattern, isCaseInsensitive(matchFlags));
        return new AutomatonQuery(new Term(field, pattern), automaton(pattern, matchFlags));
    }

    /**
     * The automata of the last translated patterns, by match flags and pattern, or the exception Lucene threw for one.
     * {@link #isServable} builds the automaton that {@link #toQuery} then uses, and a lookup that is not pre-selected
     * asks for the same pattern once per node.
     */
    private static final Map<String, Object> AUTOMATA = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Object> eldest) {
            return size() > 64;
        }
    });

    /**
     * The determinized automaton of a translated pattern, as {@link RegexpQuery} builds it.
     *
     * @throws IllegalArgumentException if Lucene cannot parse the pattern
     * @throws TooComplexToDeterminizeException if Lucene cannot determinize it within its work limit
     */
    static Automaton automaton(final String lucenePattern, final int matchFlags) {
        final Object automaton = AUTOMATA.computeIfAbsent(matchFlags + ":" + lucenePattern, key -> {
            try {
                return Operations.determinize(new RegExp(lucenePattern, RegExp.NONE, matchFlags).toAutomaton(),
                        Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
            } catch (final IllegalArgumentException | TooComplexToDeterminizeException e) {
                return e;
            }
        });
        if (automaton instanceof RuntimeException e) {
            throw e;
        }
        return (Automaton) automaton;
    }

    private static boolean isCaseInsensitive(final int matchFlags) {
        return (matchFlags & RegExp.ASCII_CASE_INSENSITIVE) != 0;
    }

    /**
     * Walks the pattern once by code point, so that a letter outside the Basic Multilingual Plane
     * is seen whole rather than as two surrogates, tracking whether the position is inside a
     * character class.
     */
    private static boolean containsUntranslatableConstruct(final String pattern, final boolean caseInsensitive) {
        boolean inClass = false;
        int depth = 0;
        int i = 0;
        while (i < pattern.length()) {
            final int c = pattern.codePointAt(i);
            if (c == '\\') {
                if (isUntranslatableEscape(pattern, i)) {
                    return true;
                }
                i += 1 + Character.charCount(pattern.codePointAt(i + 1));
                continue;
            }
            if (isUntranslatableAt(pattern, i, inClass) || (caseInsensitive && isNonAsciiLetter(c))
                    || (!inClass && isMisplacedAnchorOrAlternation(pattern, i, depth))) {
                return true;
            }
            if (!inClass) {
                depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            }
            inClass = nextClassState(inClass, c);
            i += Character.charCount(c);
        }
        return false;
    }

    /** A dangling backslash, or one of the escapes the two dialects read differently. */
    private static boolean isUntranslatableEscape(final String pattern, final int backslashAt) {
        return backslashAt + 1 >= pattern.length()
                || UNTRANSLATABLE_ESCAPES.indexOf(pattern.charAt(backslashAt + 1)) >= 0;
    }

    /**
     * Inside a class, a nested {@code [} is XPath's class subtraction, which Lucene misreads.
     * Outside one, {@code (?} opens a non-capturing group, which Lucene does not have.
     */
    private static boolean isUntranslatableAt(final String pattern, final int i, final boolean inClass) {
        final char c = pattern.charAt(i);
        if (inClass) {
            return c == '[';
        }
        return c == '(' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '?';
    }

    /**
     * Outside a class, the anchor rewrite only handles a {@code ^} that starts the pattern and a {@code $} that ends
     * it, and an alternation at the top level would leave the anchors applying to one of its branches only, as in
     * {@code ^a|b$}.
     */
    private static boolean isMisplacedAnchorOrAlternation(final String pattern, final int i, final int depth) {
        final char c = pattern.charAt(i);
        return (c == '|' && depth == 0)
                || (c == '^' && i > 0)
                || (c == '$' && i < pattern.length() - 1);
    }

    private static boolean nextClassState(final boolean inClass, final int c) {
        if (inClass) {
            return c != ']';
        }
        return c == '[';
    }

    /** A letter whose case variants XPath and Lucene are not guaranteed to agree on. */
    private static boolean isNonAsciiLetter(final int c) {
        return c > 0x7F && Character.isLetter(c);
    }

    /**
     * Translate an XPath fn:matches pattern to Lucene RegExp format.
     *
     * <p>Callers are expected to have checked {@link #isTranslatable} first.</p>
     *
     * @param xpathPattern the XPath regex pattern (e.g. "^b", "b$", "^b$")
     * @return the Lucene-equivalent pattern
     */
    public static String translate(final String xpathPattern) {
        return translate(xpathPattern, false);
    }

    /**
     * Translate an XPath fn:matches pattern to Lucene RegExp format.
     *
     * <p>Callers are expected to have checked {@link #isTranslatable} first.</p>
     *
     * @param xpathPattern the XPath regex pattern (e.g. "^b", "b$", "^b$")
     * @param caseInsensitive whether the {@code i} flag is in effect
     * @return the Lucene-equivalent pattern
     */
    public static String translate(final String xpathPattern, final boolean caseInsensitive) {
        return translateAnchors(xpathPattern, body -> rewriteBody(body, caseInsensitive));
    }

    /**
     * Builds the Lucene query for a {@code range:matches} pattern, which is in Lucene's own regular-expression syntax:
     * only the anchors are translated, as Lucene matches whole terms, and Lucene's operators are on, with or without
     * the {@code i} flag.
     *
     * @param field the index field
     * @param lucenePattern the pattern, in Lucene's syntax, with XPath-style anchors
     * @param matchFlags the Lucene match flags, e.g. {@code RegExp.ASCII_CASE_INSENSITIVE}
     * @return the query
     * @throws IllegalArgumentException if Lucene cannot parse the pattern
     * @throws TooComplexToDeterminizeException if Lucene cannot determinize it within its work limit
     */
    public static RegexpQuery toLuceneSyntaxQuery(final String field, final String lucenePattern, final int matchFlags) {
        final String pattern = translateAnchors(lucenePattern, UnaryOperator.identity());
        return new RegexpQuery(new Term(field, pattern), RegExp.ALL, matchFlags, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    }

    /**
     * Lucene matches whole terms, so a pattern anchored at both ends is the body itself, one anchored at the start is
     * the body followed by anything, and one anchored at the end is anything followed by the body.
     */
    private static String translateAnchors(final String pattern, final UnaryOperator<String> body) {
        if (pattern == null || pattern.isEmpty()) {
            return pattern;
        }
        final boolean startsWithCaret = pattern.startsWith("^");
        final boolean endsWithDollar = pattern.endsWith("$");
        if (startsWithCaret && endsWithDollar) {
            // ^X$ -> X (exact match)
            if (pattern.length() == 2) {
                return "";
            }
            return body.apply(pattern.substring(1, pattern.length() - 1));
        }
        if (startsWithCaret && !pattern.contains("$")) {
            // ^X -> X.* (prefix)
            if (pattern.length() == 1) {
                return "^";
            }
            return body.apply(pattern.substring(1)) + ".*";
        }
        if (endsWithDollar && !pattern.contains("^")) {
            // X$ -> .*X (suffix)
            if (pattern.length() == 1) {
                return "$";
            }
            return ".*" + body.apply(pattern.substring(0, pattern.length() - 1));
        }
        return body.apply(pattern);
    }

    /**
     * Rewrites the constructs Lucene reads differently but can express: each unescaped {@code .}
     * outside a character class becomes {@link #XPATH_ANY_CHARACTER}, a {@code "} is escaped, and under the
     * {@code i} flag the case variants Lucene misses are added, see {@link #extraCaseVariants}. The {@code .*} the anchor
     * rewrite appends is deliberately left alone: there it stands for "anything at all", which is
     * what an unanchored end means in XPath too.
     */
    private static String rewriteBody(final String body, final boolean caseInsensitive) {
        final StringBuilder out = new StringBuilder(body.length() + 8);
        int i = 0;
        while (i < body.length()) {
            final char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                out.append(c).append(body.charAt(i + 1));
                i += 2;
            } else if (c == '[') {
                i = appendClass(body, i, caseInsensitive, out);
            } else if (c == '.') {
                out.append(XPATH_ANY_CHARACTER);
                i++;
            } else if (c == '"') {
                // Lucene reads "..." as a quoted string, whatever the syntax flags; XPath reads a quote as itself
                out.append("\\\"");
                i++;
            } else if (caseInsensitive && !extraCaseVariants(c).isEmpty()) {
                out.append('[').append(Character.toLowerCase(c)).append(Character.toUpperCase(c))
                        .append(extraCaseVariants(c)).append(']');
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Copies the character class starting at {@code start}, adding under the {@code i} flag the case variants Lucene
     * misses of each letter the class contains, as a character or within a range, see {@link #extraCaseVariants}.
     * Adding them is right for a negated class too: its complement then excludes them, as XPath's does.
     *
     * @return the index just past the class
     */
    private static int appendClass(final String body, final int start, final boolean caseInsensitive, final StringBuilder out) {
        int i = start + 1;
        final StringBuilder variants = new StringBuilder();
        int previous = -1;
        while (i < body.length() && body.charAt(i) != ']') {
            final char c = body.charAt(i);
            final int atom;
            if (c == '\\' && i + 1 < body.length()) {
                atom = body.charAt(i + 1);
                i += 2;
            } else if (c == '-' && previous >= 0 && i + 1 < body.length() && body.charAt(i + 1) != ']') {
                final int end = body.charAt(i + 1) == '\\' && i + 2 < body.length() ? body.charAt(i + 2) : body.charAt(i + 1);
                for (final char letter : new char[] {'i', 'k', 's'}) {
                    if (inRange(letter, previous, end) || inRange(Character.toUpperCase(letter), previous, end)) {
                        addVariants(variants, letter);
                    }
                }
                i += body.charAt(i + 1) == '\\' ? 3 : 2;
                previous = -1;
                continue;
            } else {
                atom = c;
                i++;
            }
            addVariants(variants, atom);
            previous = atom;
        }
        out.append(body, start, i);
        if (caseInsensitive) {
            out.append(variants);
        }
        if (i < body.length()) {
            out.append(']');
            i++;
        }
        return i;
    }

    private static void addVariants(final StringBuilder variants, final int c) {
        final String extra = extraCaseVariants(c);
        if (!extra.isEmpty() && variants.indexOf(extra) < 0) {
            variants.append(extra);
        }
    }

    private static boolean inRange(final char c, final int from, final int to) {
        return from <= c && c <= to;
    }
}
