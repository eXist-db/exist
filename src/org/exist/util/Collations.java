/*
 * Created on Sep 23, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.CollationElementIterator;
import java.text.Collator;
import java.text.RuleBasedCollator;
import java.util.Locale;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * Utility methods dealing with collations.
 * 
 * @author wolf
 */
public class Collations {

	private final static Logger LOG = Logger.getLogger(Collations.class);
	
	/**
	 * The default unicode codepoint collation URI as defined by the XQuery spec.
	 */
	public final static String CODEPOINT = 
		"http://www.w3.org/2004/07/xpath-functions/collation/codepoint";
	
	/**
	 * Short string to select the default codepoint collation
	 */
	public final static String CODEPOINT_SHORT =
		"codepoint";
	
	/**
	 * The URI used to select collations in eXist.
	 */
	public final static String EXIST_COLLATION_URI =
		"http://exist-db.org/collation";
	
	/**
	 * Get a {@link Comparator} from the specified URI.
	 * 
	 * The original code is from saxon (@linkplain http://saxon.sf.net).
	 * 
	 * @param uri
	 * @return
	 * @throws XPathException
	 */
	public final static Collator getCollationFromURI(XQueryContext context, String uri) throws XPathException {
		if(uri.startsWith(EXIST_COLLATION_URI) || uri.startsWith("?")) {
			URI u = null;
			try {
				u = new URI(uri);
			} catch (URISyntaxException e) {
				return null;
			}
			String query = u.getQuery();
			String strength = null;
			/*
			 * Check if the db broker is configured to be case insensitive.
			 * If yes, we assume "primary" strength unless the user specified
			 * something different.
			 */
			if(!context.getBroker().isCaseSensitive())
				strength = "primary";
			if(query == null) {
				return getCollationFromParams(null, strength, null);
			} else {
				String lang = null;
				String decomposition = null;
				StringTokenizer queryTokenizer = new StringTokenizer(query, ";&");
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
		} else
			// unknown collation
			return null;
	}

	public final static boolean equals(Collator collator, String s1, String s2) {
		if(collator == null)
			return s1.equals(s2);
		else
			return collator.equals(s1, s2);
	}
	
	public final static int compare(Collator collator, String s1, String s2) {
		if(collator == null)
			return s1.compareTo(s2);
		else
			return collator.compare(s1, s2);
	}
	
	public final static boolean startsWith(Collator collator, String s1, String s2) {
		if(collator == null)
			return s1.startsWith(s2);
		else {
			final RuleBasedCollator rbc = (RuleBasedCollator)collator;
			final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
			final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
			return collationStartsWith(i1, i2);
		}
	}
	
	public final static boolean endsWith(Collator collator, String s1, String s2) {
		if(collator == null)
			return s1.endsWith(s2);
		else {
			final RuleBasedCollator rbc = (RuleBasedCollator)collator;
			final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
			final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
			return collationContains(i1, i2, null, true);
		}
	}
	
	public final static boolean contains(Collator collator, String s1, String s2) {
		if(collator == null)
			return s1.indexOf(s2) > -1;
		else {
			final RuleBasedCollator rbc = (RuleBasedCollator)collator;
			final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
			final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
			return collationContains(i1, i2, null, false);
		}
	}
	
	public final static int indexOf(Collator collator, String s1, String s2) {
		if(collator == null)
			return s1.indexOf(s2);
		else {
			final int offsets[] = new int[2]; 
			final RuleBasedCollator rbc = (RuleBasedCollator)collator;
			final CollationElementIterator i1 = rbc.getCollationElementIterator(s1);
			final CollationElementIterator i2 = rbc.getCollationElementIterator(s2);
			final boolean found = collationContains(i1, i2, offsets, false);
			if(found)
				return offsets[0];
			else
				return -1;
		}
	}
	
	private final static boolean collationStartsWith(CollationElementIterator s0,
			CollationElementIterator s1) {
		while (true) {
			int e1 = s1.next();
			if (e1 == -1) {
				return true;
			}
			int e0 = s0.next();
			if (e0 != e1) {
				return false;
			}
		}
	}
	 
	private final static boolean collationContains(CollationElementIterator s0, CollationElementIterator s1, 
			int[] offsets, boolean endsWith ) {
		int e1 = s1.next();
		if (e1 == -1) {
			return true;
		}
		int e0 = -1;
		while (true) {
			// scan the first string to find a matching character
			while (e0 != e1) {
				e0 = s0.next();
				if (e0 == -1) {
					// hit the end, no match
					return false;
				}
			}
			// matched first character, note the position of the possible match
			int start = s0.getOffset();
			if (collationStartsWith(s0, s1)) {
				if (endsWith) {
					if (offsets != null) {
						offsets[0] = start-1;
						offsets[1] = s0.getOffset();
					}
					return true;
				} else {
					// operation == ENDSWITH
					if (s0.next() == -1) {
						// the match is at the end
						return true;
					}
					// else ignore this match and keep looking
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
			e1 = s1.next();
			// loop round to try again
		}
	}

	/**
	 * @param lang
	 * @param strength
	 * @param decomposition
	 * @return
	 */
	private static Collator getCollationFromParams(String lang, String strength, String decomposition) 
	throws XPathException {
		Collator collator = null;
		if(lang == null) {
			collator = Collator.getInstance();
		} else {
			Locale locale = getLocale(lang);
			LOG.debug("Using locale: " + locale.toString());
			collator = Collator.getInstance(locale);
		}
		
		if(strength != null) {
			if("primary".equals(strength))
				collator.setStrength(Collator.PRIMARY);
			else if("secondary".equals(strength))
				collator.setStrength(Collator.SECONDARY);
			else if("tertiary".equals(strength))
				collator.setStrength(Collator.TERTIARY);
			else if(strength.length() == 0 || "identical".equals(strength))
				// the default setting
				collator.setStrength(Collator.IDENTICAL);
			else
				throw new XPathException("Collation strength should be either 'primary', 'secondary', 'tertiary' or 'identical");
		}
		
		if(decomposition != null) {
			if("none".equals(decomposition))
				collator.setDecomposition(Collator.NO_DECOMPOSITION);
			else if("full".equals(decomposition))
				collator.setDecomposition(Collator.FULL_DECOMPOSITION);
			else if(decomposition.length() == 0 || "standard".equals(decomposition))
				// the default setting
				collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
			else
				throw new XPathException("Collation decomposition should be either 'none', 'full' or 'standard");
		}
		
		return collator;
	}

	/**
	 * @param lang
	 * @return
	 */
	private static Locale getLocale(String lang) {
		int dash = lang.indexOf('-');
		if(dash < 0)
			return new Locale(lang);
		else
			return new Locale(lang.substring(0, dash), lang.substring(dash + 1));
	}
	
	
}
