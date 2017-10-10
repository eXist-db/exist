/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.CollationElementIterator;
import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.Comparator;
import java.util.Locale;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import javax.annotation.Nullable;

/**
 * Utility methods dealing with collations.
 *
 * @author wolf
 */
public class Collations {

    private final static Logger logger = LogManager.getLogger(Collations.class);

    /**
     * The default unicode codepoint collation URI as defined by the XQuery
     * spec.
     */
    public final static String CODEPOINT = "http://www.w3.org/2005/xpath-functions/collation/codepoint";

    /**
     * Short string to select the default codepoint collation
     */
    public final static String CODEPOINT_SHORT = "codepoint";

    /**
     * The URI used to select collations in eXist.
     */
    public final static String EXIST_COLLATION_URI = "http://exist-db.org/collation";

    /**
     * Get a {@link Comparator}from the specified URI.
     * <p>
     * The original code is from saxon (@linkplain http://saxon.sf.net).
     *
     * @param uri The URI describing the collation and settings
     *
     * @return The Collator for the URI, or null.
     *
     * @throws XPathException If an error occurs whilst constructing the Collator
     */
    public static @Nullable Collator getCollationFromURI(String uri) throws XPathException {
        if (uri.startsWith(EXIST_COLLATION_URI) || uri.startsWith("?")) {
            URI u;
            try {
                u = new URI(uri);
            } catch (final URISyntaxException e) {
                return null;
            }
            final String query = u.getQuery();
            if (query == null) {
                return getCollationFromParams(null, null, null);
            } else {
                String lang = null;
                String strength = null;
                String decomposition = null;
                final StringTokenizer queryTokenizer = new StringTokenizer(query,
                        ";&");
                while (queryTokenizer.hasMoreElements()) {
                    final String param = queryTokenizer.nextToken();
                    final int eq = param.indexOf('=');
                    if (eq > 0) {
                        final String kw = param.substring(0, eq);
                        String val = param.substring(eq + 1);
                        if ("lang".equals(kw)) {
                            lang = val;
                        } else if ("strength".equals(kw)) {
                            strength = val;
                        } else if ("decomposition".equals(kw)) {
                            decomposition = val;
                        }
                    }
                }
                return getCollationFromParams(lang, strength, decomposition);
            }
        } else if (uri.startsWith("java:")) {
            // java class specified: this should be a subclass of
            // java.text.RuleBasedCollator
            uri = uri.substring("java:".length());
            try {
                final Class<?> collatorClass = Class.forName(uri);
                if (!Collator.class.isAssignableFrom(collatorClass)) {
                    logger.error("The specified collator class is not a subclass of java.text.Collator");
                    throw new XPathException(
                            ErrorCodes.FOCH0002,
                            "The specified collator class is not a subclass of java.text.Collator");
                }
                return (Collator) collatorClass.newInstance();
            } catch (final Exception e) {
                logger.error("The specified collator class " + uri + " could not be found");
                throw new XPathException(
                        ErrorCodes.FOCH0002,
                        "The specified collator class " + uri + " could not be found", e);
            }
        } else if (CODEPOINT.equals(uri)) {
            return null;
        } else {
            logger.error("Unknown collation : '" + uri + "'");
            throw new XPathException(ErrorCodes.FOCH0002, "Unknown collation : '" + uri + "'");
        }
    }

    public static boolean equals(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.equals(s2);
        } else {
            return collator.equals(s1, s2);
        }
    }

    public static int compare(@Nullable final Collator collator, final String s1,final  String s2) {
        if (collator == null) {
            return s1 == null ? (s2 == null ? 0 : -1) : s1.compareTo(s2);
        } else {
            return collator.compare(s1, s2);
        }
    }

    public static boolean startsWith(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.startsWith(s2);
        } else {
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
            return collationStartsWith(i1, i2);
        }
    }

    public static boolean endsWith(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.endsWith(s2);
        } else {
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
            return collationContains(i1, i2, null, true);
        }
    }

    public static boolean contains(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.contains(s2);
        } else {
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
            return collationContains(i1, i2, null, false);
        }
    }

    public static int indexOf(@Nullable final Collator collator, final String s1, final String s2) {
        if (collator == null) {
            return s1.indexOf(s2);
        } else {
            final int offsets[] = new int[2];
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
            if (collationContains(i1, i2, offsets, false)) {
                return offsets[0];
            } else {
                return Constants.STRING_NOT_FOUND;
            }
        }
    }

    private static boolean collationStartsWith(final CollationElementIterator s0, final CollationElementIterator s1) {
        //Copied from Saxon
        while (true) {
            int e0, e1;
            do {
                e1 = s1.next();
            } while (e1 == 0);
            if (e1 == -1) {
                return true;
            }
            do {
                e0 = s0.next();
            } while (e0 == 0);
            if (e0 != e1) {
                return false;
            }
        }
        //End of copy
    }

    private static boolean collationContains(final CollationElementIterator s0,
            final CollationElementIterator s1, final int[] offsets, final boolean matchAtEnd) {
        //Copy from Saxon
        int e0, e1;
        do {
            e1 = s1.next();
        } while (e1 == 0);
        if (e1 == -1) {
            return true;
        }
        e0 = -1;
        while (true) {
            // scan the first string to find a matching character
            while (e0 != e1) {
                do {
                    e0 = s0.next();
                } while (e0 == 0);
                if (e0 == -1) {
                    // hit the end, no match
                    return false;
                }
            }
            // matched first character, note the position of the possible match
            final int start = s0.getOffset();
            if (collationStartsWith(s0, s1)) {
                if (matchAtEnd) {
                    do {
                        e0 = s0.next();
                    } while (e0 == 0);
                    if (e0 == -1) {
                        // the match is at the end
                        return true;
                    }
                    // else ignore this match and keep looking
                } else {
                    if (offsets != null) {
                        offsets[0] = start - 1;
                        offsets[1] = s0.getOffset();
                    }
                    return true;
                }
            }
            // reset the position and try again
            s0.setOffset(start);

            // workaround for a difference between JDK 1.4.0 and JDK 1.4.1
            if (s0.getOffset() != start) {
                // JDK 1.4.0 takes this path
                s0.next();
            }
            s1.reset();
            e0 = -1;
            do {
                e1 = s1.next();
            } while (e1 == 0);
            // loop round to try again
        }
        //End of copy
    }

    /**
     * Get a Collator with the provided settings.
     *
     * @param lang The language
     * @param strength The strength
     * @param decomposition The decomposition
     * @return The collator
     */
    private static Collator getCollationFromParams(@Nullable final String lang,
            @Nullable final String strength,
            @Nullable final String decomposition) throws XPathException {
        final Collator collator;
        if (lang == null) {
            collator = Collator.getInstance();
        } else if ("sme-SE".equals(lang)) {
            // Collation rules contained in a String object.
            // Codes for the representation of names of languages:
            // http://www.loc.gov/standards/iso639-2/englangn.html
            // UTF-8 characters from:
            // http://chouette.info/entities/table-utf8.php
            final String Samisk = "< a,A< \u00E1,\u00C1< b,B< c,C"
                    + "< \u010d,\u010c< d,D< \u0111,\u0110< e,E"
                    + "< f,F< g,G< h,H< i,I< j,J< k,K< l,L< m,M"
                    + "< n,N< \u014b,\u014a< o,O< p,P< r,R< s,S"
                    + "< \u0161,\u0160< t,T< \u0167,\u0166< u,U"
                    + "< v,V< z,Z< \u017e,\u017d";
            try {
                collator = new RuleBasedCollator(Samisk);
            } catch (final ParseException pe) {
                return null;
            }
        } else {
            final Locale locale = getLocale(lang);
            collator = Collator.getInstance(locale);
        }

        if (strength != null) {
            if ("primary".equals(strength)) {
                collator.setStrength(Collator.PRIMARY);
            } else if ("secondary".equals(strength)) {
                collator.setStrength(Collator.SECONDARY);
            } else if ("tertiary".equals(strength)) {
                collator.setStrength(Collator.TERTIARY);
            } else if (strength.length() == 0 || "identical".equals(strength)) {
                // the default setting
                collator.setStrength(Collator.IDENTICAL);
            } else {
                logger.error("Collation strength should be either 'primary', 'secondary', 'tertiary' or 'identical");
                throw new XPathException(
                        "Collation strength should be either 'primary', 'secondary', 'tertiary' or 'identical");
            }
        }

        if (decomposition != null) {
            if ("none".equals(decomposition)) {
                collator.setDecomposition(Collator.NO_DECOMPOSITION);
            } else if ("full".equals(decomposition)) {
                collator.setDecomposition(Collator.FULL_DECOMPOSITION);
            } else if (decomposition.length() == 0
                    || "standard".equals(decomposition))
            // the default setting
            {
                collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
            } else {
                logger.error("Collation decomposition should be either 'none', 'full' or 'standard");
                throw new XPathException(
                        "Collation decomposition should be either 'none', 'full' or 'standard");
            }
        }

        return collator;
    }

    /**
     * Get a locale for the provided language.
     *
     * @param lang The language
     *
     * @return The locale
     */
    private static Locale getLocale(final String lang) {
        final int dashPos = lang.indexOf('-');
        if (dashPos == Constants.STRING_NOT_FOUND) {
            return new Locale(lang);
        } else {
            return new Locale(lang.substring(0, dashPos), lang.substring(dashPos + 1));
        }
    }
}
