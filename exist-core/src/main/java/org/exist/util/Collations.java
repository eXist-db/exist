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
package org.exist.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.ibm.icu.text.*;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.VersionInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.annotation.Nullable;

/**
 * Utility methods dealing with collations.
 *
 * @author wolf
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Collations {

    private final static Logger logger = LogManager.getLogger(Collations.class);

    /**
     * The default Unicode Codepoint Collation URI as defined by the XQuery
     * spec.
     */
    public final static String UNICODE_CODEPOINT_COLLATION_URI = "http://www.w3.org/2005/xpath-functions/collation/codepoint";

    /**
     * Short string to select the default codepoint collation
     */
    public final static String CODEPOINT_SHORT = "codepoint";

    /**
     * The UCA (Unicode Collation Algorithm) Codepoint URI as defined by the XQuery
     * spec.
     */
    public final static String UCA_COLLATION_URI = "http://www.w3.org/2013/collation/UCA";


    /**
     * The HTML ASCII Case-Insensitive Collation as defined by the XPath F&amp;O spec.
     */
    public final static String HTML_ASCII_CASE_INSENSITIVE_COLLATION_URI = "http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive";

    /**
     * The XQTS ASCII Case-blind Collation as defined by the XQTS 3.1.
     */
    public final static String XQTS_ASCII_CASE_BLIND_COLLATION_URI = "http://www.w3.org/2010/09/qt-fots-catalog/collation/caseblind";

    /**
     * The URI used to select collations in eXist.
     */
    public final static String EXIST_COLLATION_URI = "http://exist-db.org/collation";

    /**
     * Lazy-initialized singleton Html Ascii Case Insensitive Collator
     */
    private final static AtomicReference<Collator> htmlAsciiCaseInsensitiveCollator = new AtomicReference<>();

    /**
     * Lazy-initialized singleton XQTS Case Blind Collator
     */
    private final static AtomicReference<Collator> xqtsAsciiCaseBlindCollator = new AtomicReference<>();

    /**
     * Lazy-initialized singleton Samisk Collator
     */
    private final static AtomicReference<Collator> samiskCollator = new AtomicReference<>();

    /**
     * Get a {@link Comparator}from the specified URI.
     *
     * The original code is from saxon (@linkplain http://saxon.sf.net).
     *
     * 
     * @param uri The URI describing the collation and settings
     *
     * @return The Collator for the URI, or null.
     *
     * @throws XPathException If an error occurs whilst constructing the Collator
     */
    public static @Nullable Collator getCollationFromURI(final String uri) throws XPathException {
        return getCollationFromURI(uri, (Expression)null);
    }

    /**
     * Get a {@link Comparator}from the specified URI.
     *
     * The original code is from saxon (@linkplain http://saxon.sf.net).
     *
     *
     * @param uri The URI describing the collation and settings
     * @param errorCode the error code if the URI cannot be resolved
     *
     * @return The Collator for the URI, or null.
     *
     * @throws XPathException If an error occurs whilst constructing the Collator
     */
    public static @Nullable Collator getCollationFromURI(final String uri, final ErrorCodes.ErrorCode errorCode) throws XPathException {
        return getCollationFromURI(uri, null, errorCode);
    }

    /**
     * Get a {@link Comparator}from the specified URI.
     *
     * The original code is from saxon (@linkplain http://saxon.sf.net).
     *
     * 
     * @param uri The URI describing the collation and settings
     * @param expression The expression from which the collation derives
     *
     * @return The Collator for the URI, or null.
     *
     * @throws XPathException If an error occurs whilst constructing the Collator
     */
    public static @Nullable Collator getCollationFromURI(final String uri, @Nullable final Expression expression) throws XPathException {
        return getCollationFromURI(uri, expression, ErrorCodes.XQST0076);
    }

    /**
     * Get a {@link Comparator}from the specified URI.
     *
     * The original code is from saxon (@linkplain http://saxon.sf.net).
     *
     *
     * @param uri The URI describing the collation and settings
     * @param expression The expression from which the collation derives
     * @param errorCode the error code if the URI cannot be resolved
     *
     * @return The Collator for the URI, or null.
     *
     * @throws XPathException If an error occurs whilst constructing the Collator
     */
    public static @Nullable Collator getCollationFromURI(final String uri, @Nullable final Expression expression, final ErrorCodes.ErrorCode errorCode) throws XPathException {
        final Collator collator;

        if (uri.startsWith(EXIST_COLLATION_URI) || uri.startsWith(UCA_COLLATION_URI) || uri.startsWith("?")) {
            URI u;
            try {
                u = new URI(uri);
            } catch (final URISyntaxException e) {
                return null;
            }

            final String query = u.getQuery();
            if (query == null) {
                collator = Collator.getInstance();

            } else {

                boolean fallback = true;                // default is "yes"
                String lang = null;
                String version = null;
                String strength = null;
                String maxVariable = "punct";           // default is punct
                String alternate = "non-ignorable";     // default is non-ignorable
                boolean backwards = false;              // default is "no"
                boolean normalization = false;          // default is "no"
                boolean caseLevel = false;              // default is "no"
                String caseFirst = null;
                boolean numeric = false;                // default is "no"
                String reorder = null;
                String decomposition = null;

                final StringTokenizer queryTokenizer = new StringTokenizer(query, ";&");
                while (queryTokenizer.hasMoreElements()) {
                    final String param = queryTokenizer.nextToken();
                    final int eq = param.indexOf('=');
                    if (eq > 0) {
                        final String kw = param.substring(0, eq);
                        if (kw != null) {
                            final String val = param.substring(eq + 1);

                            switch (kw) {
                                case "fallback":
                                    fallback = "yes".equals(val);
                                    break;

                                case "lang":
                                    lang = val;
                                    break;

                                case "version":
                                    version = val;
                                    break;

                                case "strength":
                                    strength = val;
                                    break;

                                case "maxVariable":
                                    maxVariable = val;
                                    break;

                                case "alternate":
                                    alternate = val;
                                    break;

                                case "backwards":
                                    backwards = "yes".equals(val);
                                    break;

                                case "normalization":
                                    normalization = "yes".equals(val);
                                    break;

                                case "caseLevel":
                                    caseLevel = "yes".equals(val);
                                    break;

                                case "caseFirst":
                                    caseFirst = val;
                                    break;

                                case "numeric":
                                    numeric = "yes".equals(val);
                                    break;

                                case "reorder":
                                    reorder = val;
                                    break;

                                case "decomposition":
                                    decomposition = val;
                                    break;

                                default:
                                    logger.warn("Unrecognized Collation parameter: {}", kw);
                                    break;
                            }
                        }
                    }
                }

                collator = getCollationFromParams(fallback, lang, version,
                        strength, maxVariable, alternate, backwards,
                        normalization, caseLevel, caseFirst, numeric,
                        reorder, decomposition, expression);
            }
        } else if(HTML_ASCII_CASE_INSENSITIVE_COLLATION_URI.equals(uri)) {
            try {
                collator = getHtmlAsciiCaseInsensitiveCollator();
            } catch (final Exception e) {
                throw new XPathException(expression, "Unable to instantiate HTML ASCII Case Insensitive Collator: " + e.getMessage(), e);
            }
        } else if(XQTS_ASCII_CASE_BLIND_COLLATION_URI.equals(uri)) {
            try {
                collator = getXqtsAsciiCaseBlindCollator();
            } catch (final Exception e) {
                throw new XPathException(expression, "Unable to instantiate XQTS ASCII Case Blind Collator: " + e.getMessage(), e);
            }
        } else if (uri.startsWith("java:")) {
            // java class specified: this should be a subclass of
            // com.ibm.icu.text.RuleBasedCollator
            final String uriClassName = uri.substring("java:".length());
            try {
                final Class<?> collatorClass = Class.forName(uriClassName);
                if (!Collator.class.isAssignableFrom(collatorClass)) {
                    final String msg = "The specified collator class '" + collatorClass.getName() + "' is not a subclass of com.ibm.icu.text.Collator";
                    logger.error(msg);
                    throw new XPathException(expression, ErrorCodes.FOCH0002, msg);
                }
                collator = (Collator) collatorClass.newInstance();
            } catch (final Exception e) {
                final String msg = "The specified collator class " + uriClassName + " could not be found";
                logger.error(msg);
                throw new XPathException(expression, ErrorCodes.FOCH0002, msg, e);
            }
        } else if (UNICODE_CODEPOINT_COLLATION_URI.equals(uri)) {
            collator = null;
        } else {
            final String msg = "Unknown collation : '" + uri + "'";
            logger.error(msg);
            throw new XPathException(expression, errorCode, msg);
        }

        if (collator != null) {
            // make immutable and therefore thread-safe!
            collator.freeze();
        }

        return collator;
    }

    /**
     * Determines if the two strings are equal with regards to a Collation.
     *
     * @param collator The collation, or null if no collation should be used.
     * @param s1 The first string to compare against the second.
     * @param s2 The second string to compare against the first.
     *
     * @return true if the Strings are equal.
     */
    public static boolean equals(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.equals(s2);
        } else {
            return collator.equals(s1, s2);
        }
    }

    /**
     * Compares two strings with regards to a Collation.
     *
     * @param collator The collation, or null if no collation should be used.
     * @param s1 The first string to compare against the second.
     * @param s2 The second string to compare against the first.
     *
     * @return a negative integer, zero, or a positive integer if the
     *     {@code s1} is less than, equal to, or greater than {@code s2}.
     *
     * @throws UnsupportedOperationException if ICU4J does not support collation
     */
    public static int compare(@Nullable final Collator collator, final String s1,final  String s2) {
        if (collator == null) {
            return s1 == null ? (s2 == null ? 0 : -1) : s1.compareTo(s2);
        } else {
            return collator.compare(s1, s2);
        }
    }

    /**
     * Determines if one string starts with another with regards to a Collation.
     *
     * @param collator The collation, or null if no collation should be used.
     * @param s1 The first string to compare against the second.
     * @param s2 The second string to compare against the first.
     *
     * @return true if {@code s1} starts with {@code @s2}.
     *
     * @throws UnsupportedOperationException if ICU4J does not support collation
     */
    public static boolean startsWith(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.startsWith(s2);
        } else {
            if (s2.isEmpty()) {
                return true;
            } else if (s1.isEmpty()) {
                return false;
            } else {
                final SearchIterator searchIterator =
                        new StringSearch(s2, new StringCharacterIterator(s1), (RuleBasedCollator) collator);
                return searchIterator.first() == 0;
            }
        }
    }

    /**
     * Determines if one string ends with another with regards to a Collation.
     *
     * @param collator The collation, or null if no collation should be used.
     * @param s1 The first string to compare against the second.
     * @param s2 The second string to compare against the first.
     *
     * @return true if {@code s1} ends with {@code @s2}.
     *
     * @throws UnsupportedOperationException if ICU4J does not support collation
     */
    public static boolean endsWith(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.endsWith(s2);
        } else {
            if (s2.isEmpty()) {
                return true;
            } else if (s1.isEmpty()) {
                return false;
            } else {
                final SearchIterator searchIterator =
                        new StringSearch(s2, new StringCharacterIterator(s1), (RuleBasedCollator) collator);
                int lastPos = SearchIterator.DONE;
                int lastLen = 0;
                for (int pos = searchIterator.first(); pos != SearchIterator.DONE;
                     pos = searchIterator.next()) {
                    lastPos = pos;
                    lastLen = searchIterator.getMatchLength();
                }

                return lastPos > SearchIterator.DONE && lastPos + lastLen == s1.length();
            }
        }
    }

    /**
     * Determines if one string contains another with regards to a Collation.
     *
     * @param collator The collation, or null if no collation should be used.
     * @param s1 The first string to compare against the second.
     * @param s2 The second string to compare against the first.
     *
     * @return true if {@code s1} contains {@code @s2}.
     *
     * @throws UnsupportedOperationException if ICU4J does not support collation
     */
    public static boolean contains(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.contains(s2);
        } else {
            if (s2.isEmpty()) {
                return true;
            } else if (s1.isEmpty()) {
                return false;
            } else {
                final SearchIterator searchIterator =
                        new StringSearch(s2, new StringCharacterIterator(s1), (RuleBasedCollator) collator);
                return searchIterator.first() >= 0;
            }
        }
    }

    /**
     * Finds the index of one string within another string with regards to a Collation.
     *
     * @param collator The collation, or null if no collation should be used.
     * @param s1 The string to look for {@code s2} in
     * @param s2 The substring to look for in {@code s1}.
     *
     * @return the index of the first occurrence of the specified substring,
     *          or {@code -1} if there is no such occurrence.
     */
    public static int indexOf(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.indexOf(s2);
        } else {
            if (s2.isEmpty()) {
                return 0;
            } else if (s1.isEmpty()) {
                return -1;
            } else {
                final SearchIterator searchIterator =
                        new StringSearch(s2, new StringCharacterIterator(s1), (RuleBasedCollator) collator);
                return searchIterator.first();
            }
        }
    }

    /**
     * Get a Collator with the provided settings.
     *
     * @param fallback Determines whether the processor uses a fallback
     *     collation if a conformant collation is not available.
     * @param lang language code: a string in the lexical space of xs:language.
     * @param strength The collation strength as defined in UCA.
     * @param maxVariable Indicates that all characters in the specified group
     *     and earlier groups are treated as "noise" characters to be handled
     *     as defined by the alternate parameter. "space" | "punct" | "symbol".
     *     | "currency".
     * @param alternate Controls the handling of characters such as spaces and
     *     hyphens; specifically, the "noise" characters in the groups selected
     *     by the maxVariable parameter. "non-ignorable" | "shifted" |
     *     "blanked".
     * @param backwards indicates that the last accent in the string is the
     *     most significant.
     * @param normalization Indicates whether strings are converted to
     *     normalization form D.
     * @param caseLevel When used with primary strength, setting caseLevel has
     *     the effect of ignoring accents while taking account of case.
     * @param caseFirst Indicates whether upper-case precedes lower-case or
     *     vice versa.
     * @param numeric When numeric is specified, a sequence of consecutive
     *     digits is interpreted as a number, for example chap2 sorts before
     *     chap12.
     * @param reorder Determines the relative ordering of text in different
     *     scripts; for example the value digit,Grek,Latn indicates that
     *     digits precede Greek letters, which precede Latin letters.
     * @param decomposition The decomposition
     *
     * @return The collator of null if a Collator could not be retrieved
     *
     * @throws XPathException if an error occurs whilst getting the Collator
     */
    private static @Nullable Collator getCollationFromParams(
            final boolean fallback, @Nullable final String lang,
            @Nullable final String version, @Nullable final String strength,
            final String maxVariable, final String alternate,
            final boolean backwards, final boolean normalization,
            final boolean caseLevel, @Nullable final String caseFirst,
            final boolean numeric, @Nullable final String reorder,
            @Nullable final String decomposition) throws XPathException {
        return getCollationFromParams(fallback, lang, version, strength, maxVariable, alternate, backwards, normalization,
                                      caseLevel, caseFirst, numeric, reorder, decomposition, null);
    }

    /**
     * Get a Collator with the provided settings.
     *
     * @param fallback Determines whether the processor uses a fallback
     *     collation if a conformant collation is not available.
     * @param lang language code: a string in the lexical space of xs:language.
     * @param strength The collation strength as defined in UCA.
     * @param maxVariable Indicates that all characters in the specified group
     *     and earlier groups are treated as "noise" characters to be handled
     *     as defined by the alternate parameter. "space" | "punct" | "symbol".
     *     | "currency".
     * @param alternate Controls the handling of characters such as spaces and
     *     hyphens; specifically, the "noise" characters in the groups selected
     *     by the maxVariable parameter. "non-ignorable" | "shifted" |
     *     "blanked".
     * @param backwards indicates that the last accent in the string is the
     *     most significant.
     * @param normalization Indicates whether strings are converted to
     *     normalization form D.
     * @param caseLevel When used with primary strength, setting caseLevel has
     *     the effect of ignoring accents while taking account of case.
     * @param caseFirst Indicates whether upper-case precedes lower-case or
     *     vice versa.
     * @param numeric When numeric is specified, a sequence of consecutive
     *     digits is interpreted as a number, for example chap2 sorts before
     *     chap12.
     * @param reorder Determines the relative ordering of text in different
     *     scripts; for example the value digit,Grek,Latn indicates that
     *     digits precede Greek letters, which precede Latin letters.
     * @param decomposition The decomposition
     * @param expression the expression from which the collation derives
     *
     * @return The collator of null if a Collator could not be retrieved
     *
     * @throws XPathException if an error occurs whilst getting the Collator
     */
    private static @Nullable Collator getCollationFromParams(
            final boolean fallback, @Nullable final String lang,
            @Nullable final String version, @Nullable final String strength,
            final String maxVariable, final String alternate,
            final boolean backwards, final boolean normalization,
            final boolean caseLevel, @Nullable final String caseFirst,
            final boolean numeric, @Nullable final String reorder,
            @Nullable final String decomposition,
            @Nullable final Expression expression) throws XPathException {

        final Collator collator;
        if ("sme-SE".equals(lang)) {
            try {
                collator = getSamiskCollator();
            } catch (final Exception pe) {
                logger.error(pe.getMessage(), pe);
                return null;
            }
        } else {
            final ULocale locale = getLocale(lang, expression);
            collator = Collator.getInstance(locale);
        }

        if(!fallback) {
            //TODO(AR) how to disable fallback in ICU?
            logger.warn("eXist-db does not yet support disabling collation fallback");
        }

        if(version != null) {
            final VersionInfo versionInfo;
            try {
                versionInfo = VersionInfo.getInstance(version);
            } catch (final IllegalArgumentException iae) {
                logger.error(iae.getMessage(), iae);
                throw new XPathException(expression, iae.getMessage(), iae);
            }

            if(collator.getVersion().compareTo(versionInfo) < 0) {
                throw new XPathException(expression, "Requested UCA Collation version: " + version + ", however eXist-db only has ICU UCA: " + collator.getVersion().toString());
            }
        }

        if (strength != null) {
            switch(strength) {

                case "identical":
                    // the default setting
                    collator.setStrength(Collator.IDENTICAL);
                    break;

                case "1":
                case "primary":
                    collator.setStrength(Collator.PRIMARY);
                    break;

                case "2":
                case "secondary":
                    collator.setStrength(Collator.SECONDARY);
                    break;

                case "3":
                case "tertiary":
                    collator.setStrength(Collator.TERTIARY);
                    break;

                case "4":
                case "quaternary":
                    collator.setStrength(Collator.QUATERNARY);
                    break;

                default:
                    final String msg = "eXist-db only supports Collation strengths of 'identical', 'primary', 'secondary', 'tertiary' or 'quaternary', requested: " + strength;
                    logger.error(msg);
                    throw new XPathException(expression, ErrorCodes.FOCH0002, msg);

            }
        }

        if(maxVariable != null) {
            switch(maxVariable) {
                case "space":
                    collator.setMaxVariable(Collator.ReorderCodes.SPACE);
                    break;

                case "punct":
                    collator.setMaxVariable(Collator.ReorderCodes.PUNCTUATION);
                    break;

                case "symbol":
                    collator.setMaxVariable(Collator.ReorderCodes.SYMBOL);
                    break;

                case "currency":
                    collator.setMaxVariable(Collator.ReorderCodes.CURRENCY);
                    break;

                default:
                    final String msg = "eXist-db only supports Collation maxVariables of 'space', 'punct', 'symbol', or 'currency', requested: " + maxVariable;
                    logger.error(msg);
                    throw new XPathException(expression, ErrorCodes.FOCH0002, msg);
            }
        }

        if(alternate != null) {
            switch(alternate) {
                case "non-ignorable":
                    ((RuleBasedCollator)collator).setAlternateHandlingShifted(false);
                    break;

                case "shifted":
                case "blanked":
                    ((RuleBasedCollator)collator).setAlternateHandlingShifted(true);
                    break;

                default:
                    final String msg = "Collation alternate should be either 'non-ignorable', 'shifted' or 'blanked', but received: " + caseFirst;
                    logger.error(msg);
                    throw new XPathException(expression, ErrorCodes.FOCH0002, msg);
            }
        }

        if(backwards) {
            ((RuleBasedCollator)collator).setFrenchCollation(true);
        }

        if(normalization) {
            collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        } else {
            collator.setDecomposition(Collator.NO_DECOMPOSITION);
        }

        if(caseLevel && collator.getStrength() == Collator.PRIMARY) {
            ((RuleBasedCollator)collator).setCaseLevel(true);
        }

        if(caseFirst != null) {
            switch(caseFirst) {
                case "upper":
                    ((RuleBasedCollator)collator).setUpperCaseFirst(true);
                    break;

                case "lower":
                    ((RuleBasedCollator)collator).setLowerCaseFirst(true);
                    break;

                default:
                    final String msg = "Collation case first should be either 'upper' or 'lower', but received: " + caseFirst;
                    logger.error(msg);
                    throw new XPathException(expression, ErrorCodes.FOCH0002, msg);
            }
        }

        if(numeric) {
            ((RuleBasedCollator)collator).setNumericCollation(true);
        }

        if(reorder != null) {
            final String[] reorderCodes = reorder.split(",");
            final List<Integer> icuCollatorReorderCodes =
                    Arrays.stream(reorderCodes)
                    .map(Collations::toICUCollatorReorderCode)
                    .filter(i -> i > -1)
                    .collect(Collectors.toList());

            if(!icuCollatorReorderCodes.isEmpty()) {
                final int[] codes = new int[icuCollatorReorderCodes.size()];
                for(int i = 0; i < codes.length; i++) {
                    codes[i] = icuCollatorReorderCodes.get(i);
                }
                collator.setReorderCodes(codes);
            }
        }

        if (decomposition != null) {
            switch(decomposition) {
                case "none":
                    collator.setDecomposition(Collator.NO_DECOMPOSITION);
                    break;

                case "full":
                    collator.setDecomposition(Collator.FULL_DECOMPOSITION);
                    break;

                case "standard":
                case "":
                    // the default setting
                    collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
                    break;

                default:
                    final String msg = "Collation decomposition should be either 'none', 'full' or 'standard', but received: " + decomposition;
                    logger.error(msg);
                    throw new XPathException(expression, ErrorCodes.FOCH0002, msg);
            }
        }

        return collator;
    }

    private static int toICUCollatorReorderCode(final String reorderCode) {
        return switch (reorderCode.toLowerCase()) {
            case "default" -> Collator.ReorderCodes.DEFAULT;
            case "none" -> Collator.ReorderCodes.NONE;
            case "others" -> Collator.ReorderCodes.OTHERS;
            case "space" -> Collator.ReorderCodes.SPACE;
            case "first" -> Collator.ReorderCodes.FIRST;
            case "punctuation" -> Collator.ReorderCodes.PUNCTUATION;
            case "symbol" -> Collator.ReorderCodes.SYMBOL;
            case "currency" -> Collator.ReorderCodes.CURRENCY;
            case "digit" -> Collator.ReorderCodes.DIGIT;
            default -> {
                logger.warn("eXist-db does not support the collation reorderCode: {}", reorderCode);
                yield -1;
            }
        };
    }

    /**
     * Get a locale for the provided language.
     *
     * @param lang The language
     *
     * @return The locale
     */
    private static ULocale getLocale(@Nullable final String lang, @Nullable final Expression expression) throws XPathException {
        if(lang == null) {
            return ULocale.getDefault();
        } else {
            final String[] components = lang.split("-");
            return switch (components.length) {
                case 3 -> new ULocale(components[0], components[1], components[2]);
                case 2 -> new ULocale(components[0], components[1]);
                case 1 -> new ULocale(components[0]);
                default -> throw new XPathException(expression, ErrorCodes.FOCH0002, "Unrecognized lang=" + lang);
            };
        }
    }

    private static Collator getSamiskCollator() throws Exception {
        Collator collator = samiskCollator.get();
        if (collator == null) {
            // Collation rules contained in a String object.
            // Codes for the representation of names of languages:
            // http://www.loc.gov/standards/iso639-2/englangn.html
            // UTF-8 characters from:
            // http://chouette.info/entities/table-utf8.php
            samiskCollator.compareAndSet(null,
                    new RuleBasedCollator("< a,A< \u00E1,\u00C1< b,B< c,C"
                            + "< \u010d,\u010c< d,D< \u0111,\u0110< e,E"
                            + "< f,F< g,G< h,H< i,I< j,J< k,K< l,L< m,M"
                            + "< n,N< \u014b,\u014a< o,O< p,P< r,R< s,S"
                            + "< \u0161,\u0160< t,T< \u0167,\u0166< u,U"
                            + "< v,V< z,Z< \u017e,\u017d").freeze());
            collator = samiskCollator.get();
        }

        return collator;
    }

    private static Collator getHtmlAsciiCaseInsensitiveCollator() throws Exception {
        Collator collator = htmlAsciiCaseInsensitiveCollator.get();
        if (collator == null) {
            collator = new RuleBasedCollator("&a=A &b=B &c=C &d=D &e=E &f=F &g=G &h=H "
                    + "&i=I &j=J &k=K &l=L &m=M &n=N &o=O &p=P &q=Q &r=R &s=S &t=T "
                    + "&u=U &v=V &w=W &x=X &y=Y &z=Z");
            collator.setStrength(Collator.PRIMARY);
            htmlAsciiCaseInsensitiveCollator.compareAndSet(null,
                    collator.freeze());
            collator = htmlAsciiCaseInsensitiveCollator.get();
        }

        return collator;
    }

    private static Collator getXqtsAsciiCaseBlindCollator() throws Exception {
        Collator collator = xqtsAsciiCaseBlindCollator.get();
        if (collator == null) {
            collator = new RuleBasedCollator("&a=A &b=B &c=C &d=D &e=E &f=F &g=G &h=H "
                    + "&i=I &j=J &k=K &l=L &m=M &n=N &o=O &p=P &q=Q &r=R &s=S &t=T "
                    + "&u=U &v=V &w=W &x=X &y=Y &z=Z");
            collator.setStrength(Collator.PRIMARY);
            xqtsAsciiCaseBlindCollator.compareAndSet(null,
                    collator.freeze());
            collator = xqtsAsciiCaseBlindCollator.get();
        }

        return collator;
    }
}
