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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.sf.saxon.Configuration;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.StringValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place XQuery regular expressions are compiled.
 *
 * <p>Saxon's native engine is the authority on XPath 3.1 regular-expression semantics. eXist used
 * to compile with it on some paths and with {@code java.util.regex} -- via Saxon's translator --
 * on others, and the two disagree on ordinary constructs: {@code \s}, {@code \d}, {@code \w},
 * {@code $}, {@code .}, character-class subtraction, {@code \i}/{@code \c} and block escapes.
 * Class subtraction is the worst case, because Java has no such syntax and reads
 * {@code [a-z-[aeiou]]} as a union, so the answer is wrong rather than an error. Routing every
 * path through this class is what makes {@code fn:matches} give one answer.</p>
 *
 * <p><strong>Flags.</strong> XPath 3.1 defines {@code s m i x q}; anything else is FORX0001.
 * Saxon adds options after a {@code ;}, of which eXist admits exactly one: {@code ;j} selects
 * Java's regular-expression engine, with Java's syntax and semantics -- {@code \b} works, class
 * subtraction does not. It is an explicit, documented opt-out of XPath semantics, which is a
 * different thing from the silent fallback to Java that used to happen whenever Saxon rejected a
 * pattern, and which turned FORX0002 into {@code false()}. Any other option after the semicolon
 * is FORX0001, so a typo cannot quietly become a no-op.</p>
 *
 * <p><strong>Caching.</strong> A compiled {@link RegularExpression} is immutable and safe to
 * share between threads, and compiling one is far more expensive than matching with it. They are
 * cached here by pattern and flags, as {@code PatternFactory} already does for Java patterns; a
 * call inside a FLWOR loop compiles once rather than once per item.</p>
 */
public final class SaxonRegex {

    private static final Logger LOG = LogManager.getLogger(SaxonRegex.class);

    /** The XPath 3.1 host-language identifier Saxon expects. */
    private static final String HOST_LANGUAGE = "XP31";

    /** The Saxon-specific options admitted after a semicolon in the flags string. */
    private static final String ADMITTED_EXTENSION_FLAGS = "j";

    private static final String XPATH_FLAGS = "smixq";

    private static final UnicodeString EMPTY = StringView.of("");

    private static final Cache<String, RegularExpression> CACHE = Caffeine.newBuilder()
            .maximumSize(1_000)
            .build();

    private SaxonRegex() {
    }

    /**
     * Checks a flags string and returns it unchanged.
     *
     * @param context the calling expression, for error reporting
     * @param flags the value of the {@code $flags} argument, or null if absent
     * @return the flags, never null
     * @throws XPathException FORX0001 if a flag is not one of {@code smixq}, or if anything other
     *   than {@code j} follows a semicolon
     */
    public static String validateFlags(final Expression context, @Nullable final String flags) throws XPathException {
        if (flags == null || flags.isEmpty()) {
            return "";
        }
        final int semicolon = flags.indexOf(';');
        validateXPathFlags(context, semicolon < 0 ? flags : flags.substring(0, semicolon));
        if (semicolon >= 0) {
            validateExtensionFlags(context, flags.substring(semicolon + 1));
        }
        return flags;
    }

    /** The part before any semicolon: each character must be one of {@code smixq}. */
    private static void validateXPathFlags(final Expression context, final String standard) throws XPathException {
        for (int i = 0; i < standard.length(); i++) {
            final char ch = standard.charAt(i);
            if (XPATH_FLAGS.indexOf(ch) < 0) {
                throw new XPathException(context, ErrorCodes.FORX0001,
                        "Invalid regular expression flag: " + ch, new StringValue(String.valueOf(ch)));
            }
        }
    }

    /** The part after the semicolon: exactly {@code j}, nothing else. */
    private static void validateExtensionFlags(final Expression context, final String extension) throws XPathException {
        if (!ADMITTED_EXTENSION_FLAGS.equals(extension)) {
            throw new XPathException(context, ErrorCodes.FORX0001,
                    "Invalid regular expression flag after ';': '" + extension
                            + "'. Only ';j' (use the Java regular expression engine) is supported.",
                    new StringValue(extension));
        }
    }

    /**
     * True if the flags select Java's engine via {@code ;j}.
     *
     * @param flags a flags string that has passed {@link #validateFlags}
     * @return whether Java semantics were requested
     */
    public static boolean usesJavaEngine(final String flags) {
        return flags.endsWith(";" + ADMITTED_EXTENSION_FLAGS);
    }

    /**
     * Compiles a regular expression, or returns the cached compilation.
     *
     * @param context the calling expression, for error reporting
     * @param configuration the Saxon configuration to compile with
     * @param pattern the XPath regular expression
     * @param flags flags that have passed {@link #validateFlags}
     * @return the compiled expression
     * @throws XPathException FORX0002 if the pattern is not a valid regular expression, or
     *   FORX0001 if Saxon rejects the flags
     */
    public static RegularExpression compile(final Expression context, final Configuration configuration,
            final String pattern, final String flags) throws XPathException {
        final String key = flags + '\u0000' + pattern;
        final RegularExpression cached = CACHE.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        try {
            final List<String> warnings = new ArrayList<>(1);
            final RegularExpression compiled = configuration.compileRegularExpression(
                    StringView.of(pattern), flags, HOST_LANGUAGE, warnings);
            for (final String warning : warnings) {
                LOG.warn("Regular expression '{}': {}", pattern, warning);
            }
            CACHE.put(key, compiled);
            return compiled;
        } catch (final net.sf.saxon.trans.XPathException e) {
            throw translate(context, e, pattern);
        }
    }

    /**
     * Prepares a pattern for compilation: translates XPath 4.0 lookaround syntax when running as
     * 4.0, and checks that the pattern is valid XPath regular-expression syntax -- unless the
     * caller asked for Java syntax with {@code ;j}, or for a literal with {@code q}, in which
     * case there is nothing to check.
     *
     * @param context the calling expression, for error reporting
     * @param pattern the pattern as supplied
     * @param flags flags that have passed {@link #validateFlags}
     * @param isXQuery40 whether the query runs as XQuery 4.0 or later
     * @return the pattern to compile
     * @throws XPathException FORX0002 if the pattern uses syntax XPath does not define
     */
    public static String preparePattern(final Expression context, final String pattern, final String flags,
            final boolean isXQuery40) throws XPathException {
        final String prepared = isXQuery40 && RegexUtil.hasXPath4Lookaround(pattern)
                ? RegexUtil.translateXPath4Lookaround(pattern) : pattern;
        if (!RegexUtil.hasLiteral(flags) && !usesJavaEngine(flags)) {
            RegexUtil.validateXPathRegex(context, prepared, isXQuery40);
        }
        return prepared;
    }

    /**
     * Compiles for a given XQuery version. XPath 4.0 lookaround has no Saxon-native implementation
     * yet (Saxon 12.5 rejects it under both {@code XP31} and {@code XP40}), so in 4.0 mode a pattern
     * that needs it is compiled with {@code ;j} added -- Java's engine, requested explicitly, which
     * is a different thing from falling back to it on failure.
     *
     * @param context the calling expression, for error reporting
     * @param configuration the Saxon configuration to compile with
     * @param pattern the pattern, already translated from XPath 4.0 lookaround syntax if needed
     * @param flags flags that have passed {@link #validateFlags}
     * @param isXQuery40 whether the query runs as XQuery 4.0 or later
     * @return the compiled expression
     * @throws XPathException as for {@link #compile}
     */
    public static RegularExpression compileForXQueryVersion(final Expression context, final Configuration configuration,
            final String pattern, final String flags, final boolean isXQuery40) throws XPathException {
        final String effectiveFlags = isXQuery40 && RegexUtil.needsXQuery40JavaRegex(pattern) && !usesJavaEngine(flags)
                ? flags + ';' + ADMITTED_EXTENSION_FLAGS
                : flags;
        return compile(context, configuration, pattern, effectiveFlags);
    }

    /**
     * True if the expression matches the zero-length string -- the condition under which
     * {@code fn:replace}, {@code fn:tokenize} and {@code fn:analyze-string} raise FORX0003.
     *
     * @param regex a compiled expression
     * @return whether it matches ""
     */
    public static boolean matchesEmptyString(final RegularExpression regex) {
        return regex.matches(EMPTY);
    }

    /**
     * Maps a Saxon regular-expression error onto the corresponding eXist error code.
     *
     * @param context the calling expression
     * @param e the Saxon exception
     * @param pattern the pattern that was being compiled or applied
     * @return an exception carrying the right code
     */
    public static XPathException translate(final Expression context, final net.sf.saxon.trans.XPathException e,
            final String pattern) {
        final String code = e.getErrorCodeQName() == null ? "" : e.getErrorCodeQName().getLocalPart();
        final String message = "Invalid regular expression '" + pattern + "': " + e.getMessage();
        return switch (code) {
            case "FORX0001" -> new XPathException(context, ErrorCodes.FORX0001, message, new StringValue(pattern), e);
            case "FORX0002" -> new XPathException(context, ErrorCodes.FORX0002, message, new StringValue(pattern), e);
            case "FORX0003" -> new XPathException(context, ErrorCodes.FORX0003, message, new StringValue(pattern), e);
            case "FORX0004" -> new XPathException(context, ErrorCodes.FORX0004, message, new StringValue(pattern), e);
            default -> new XPathException(context, ErrorCodes.ERROR, message, new StringValue(pattern), e);
        };
    }
}
