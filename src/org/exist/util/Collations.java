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

import org.apache.log4j.Logger;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * Utility methods dealing with collations.
 * 
 * @author wolf
 */
public class Collations {

    private final static Logger logger = Logger.getLogger(Collations.class);

    /**
     * The default unicode codepoint collation URI as defined by the XQuery
     * spec.
     */
    //public final static String CODEPOINT = "http://www.w3.org/2004/07/xpath-functions/collation/codepoint";
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
     * 
     * The original code is from saxon (@linkplain http://saxon.sf.net).
     * 
     * @param uri
     * @throws XPathException
     */
    public final static Collator getCollationFromURI(XQueryContext context,
            String uri)
        throws XPathException {
        if (uri.startsWith(EXIST_COLLATION_URI) || uri.startsWith("?")) {
            URI u = null;
            try {
                u = new URI(uri);
            } catch (URISyntaxException e) {
                return null;
            }
            String query = u.getQuery();
            String strength = null;
            /*
             * Check if the db broker is configured to be case insensitive. If
             * yes, we assume "primary" strength unless the user specified
             * something different.
             * 
             * TODO: bad idea: using primary strength as default also ignores
             * German Umlaute.
             */
            // if(!context.getBroker().isCaseSensitive())
            // strength = "primary";
            if (query == null) {
                return getCollationFromParams(null, strength, null);
            } else {
                String lang = null;
                String decomposition = null;
                StringTokenizer queryTokenizer = new StringTokenizer(query,
                        ";&");
                while (queryTokenizer.hasMoreElements()) {
                    String param = queryTokenizer.nextToken();
                    int eq = param.indexOf('=');
                    if (eq > 0) {
                        String kw = param.substring(0, eq);
                        String val = param.substring(eq + 1);
                        if (kw.equals("lang")) {
                            lang = val;
                        } else if (kw.equals("strength")) {
                            strength = val;
                        } else if (kw.equals("decomposition")) {
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
                Class collatorClass = Class.forName(uri);
                if (!Collator.class.isAssignableFrom(collatorClass)) {
                    logger.error("The specified collator class is not a subclass of java.text.Collator");
                    throw new XPathException(
                            "The specified collator class is not a subclass of java.text.Collator");
                }
                return (Collator) collatorClass.newInstance();
            } catch (Exception e) {
                logger.error("err:XQST0038: The specified collator class " + uri
                        + " could not be found");
                throw new XPathException("err:XQST0038: The specified collator class " + uri
                        + " could not be found", e);
            }
        } else if (CODEPOINT.equals(uri)) {
        	return null;
        } else {
            logger.error("err:XQST0038: Unknown collation : '" + uri + "'");
            throw new XPathException("err:XQST0038: Unknown collation : '" + uri + "'");
        }
    }

    public final static boolean equals(Collator collator, String s1, String s2) {
        if (collator == null)
            return s1.equals(s2);
        else
            return collator.equals(s1, s2);
    }

    public final static int compare(Collator collator, String s1, String s2) {
        if (collator == null)
            return s1==null ? (s2==null ? 0 : -1)  : s1.compareTo(s2);
        else
            return collator.compare(s1, s2);
    }

    public final static boolean startsWith(Collator collator, String s1, String s2) {
        if (collator == null)
            return s1.startsWith(s2);
        else {
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
            return collationStartsWith(i1, i2);
        }
    }

    public final static boolean endsWith(Collator collator, String s1, String s2) {
        if (collator == null)
            return s1.endsWith(s2);
        else {
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
            return collationContains(i1, i2, null, true);
        }
    }

    public final static boolean contains(Collator collator, String s1, String s2) {
        if (collator == null)
            return s1.indexOf(s2) != Constants.STRING_NOT_FOUND;
        else {
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
            return collationContains(i1, i2, null, false);
        }
    }

    public final static int indexOf(Collator collator, String s1, String s2) {
        if (collator == null)
            return s1.indexOf(s2);
        else {
            final int offsets[] = new int[2];
            final RuleBasedCollator rbc = (RuleBasedCollator) collator;
            final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
            final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);           
            if (collationContains(i1, i2, offsets, false))
                return offsets[0];
            else
                return Constants.STRING_NOT_FOUND;
        }
    }

    private final static boolean collationStartsWith(CollationElementIterator s0, 
            CollationElementIterator s1) {
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

    private final static boolean collationContains(CollationElementIterator s0,
            CollationElementIterator s1, int[] offsets, boolean matchAtEnd) {
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
            int start = s0.getOffset();
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
                        offsets[0] = start-1;
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
     * @param lang
     * @param strength
     * @param decomposition
     * @return The collator
     */
    private static Collator getCollationFromParams(String lang,
            String strength, String decomposition)
        throws XPathException {
        Collator collator = null;
        if (lang == null) {
            collator = Collator.getInstance();
        } else if (lang.equals("sme-SE")) {
            // Collation rules contained in a String object.
            // Codes for the representation of names of languages:
            // http://www.loc.gov/standards/iso639-2/englangn.html
            // UTF-8 characters from:
            // http://chouette.info/entities/table-utf8.php
            String Samisk = "< a,A< \u00E1,\u00C1< b,B< c,C"
                    + "< \u010d,\u010c< d,D< \u0111,\u0110< e,E"
                    + "< f,F< g,G< h,H< i,I< j,J< k,K< l,L< m,M"
                    + "< n,N< \u014b,\u014a< o,O< p,P< r,R< s,S"
                    + "< \u0161,\u0160< t,T< \u0167,\u0166< u,U"
                    + "< v,V< z,Z< \u017e,\u017d";
            try {
                collator = new RuleBasedCollator(Samisk);
            } catch (ParseException pe) {
                return null;
            }
        } else {
            Locale locale = getLocale(lang);
            collator = Collator.getInstance(locale);
        }

        if (strength != null) {
            if ("primary".equals(strength))
                collator.setStrength(Collator.PRIMARY);
            else if ("secondary".equals(strength))
                collator.setStrength(Collator.SECONDARY);
            else if ("tertiary".equals(strength))
                collator.setStrength(Collator.TERTIARY);
            else if (strength.length() == 0 || "identical".equals(strength))
                // the default setting
                collator.setStrength(Collator.IDENTICAL);
            else {
                logger.error("Collation strength should be either 'primary', 'secondary', 'tertiary' or 'identical");
                throw new XPathException(
                        "Collation strength should be either 'primary', 'secondary', 'tertiary' or 'identical");

            }

        }

        if (decomposition != null) {
            if ("none".equals(decomposition))
                collator.setDecomposition(Collator.NO_DECOMPOSITION);
            else if ("full".equals(decomposition))
                collator.setDecomposition(Collator.FULL_DECOMPOSITION);
            else if (decomposition.length() == 0
                    || "standard".equals(decomposition))
                // the default setting
                collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
            else {
                logger.error("Collation decomposition should be either 'none', 'full' or 'standard");
                throw new XPathException(
                        "Collation decomposition should be either 'none', 'full' or 'standard");
            }

        }

        return collator;
    }

    /**
     * @param lang
     * @return The locale
     */
    private static Locale getLocale(String lang) {
        int dashPos = lang.indexOf('-');
        if (dashPos == Constants.STRING_NOT_FOUND)
            return new Locale(lang);
        else
            return new Locale(lang.substring(0, dashPos), lang.substring(dashPos + 1));
    }

}
