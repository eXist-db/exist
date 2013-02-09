/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.text;

import org.exist.storage.TermMatcher;

/**
 * A fuzzy implementation of {@link org.exist.storage.TermMatcher}. It calculates
 * the  Levenshtein distance between the index and the search term.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FuzzyMatcher implements TermMatcher {
	
	private final String searchTerm;
	private final int termLength;
	private final double threshold;
	
	public FuzzyMatcher(String searchTerm, double threshold) {
		this.searchTerm = searchTerm;
		this.termLength = searchTerm.length();
		this.threshold = threshold;
	}	
	
	/* (non-Javadoc)
	 * @see org.exist.storage.TermMatcher#matches(java.lang.String)
	 */
	public boolean matches(CharSequence text) {
		if(searchTerm.equals(text))
			{return true;}
		final int textlen = text.length();
		final int dist = editDistance(text, searchTerm, textlen, termLength);
		final double distance = 1 - ((double)dist / (double)Math.min(textlen, termLength));
		return distance > threshold;
	}
	
	/**
     Finds and returns the smallest of three integers 
     */
    private static final int min(int a, int b, int c) {
        final int t = (a < b) ? a : b;
        return (t < c) ? t : c;
    }
    
    /**
     * This static array saves us from the time required to create a new array
     * everytime editDistance is called.
     */
    private int e[][] = new int[1][1];
    
    /**
     Levenshtein distance also known as edit distance is a measure of similiarity
     between two strings where the distance is measured as the number of character 
     deletions, insertions or substitutions required to transform one string to 
     the other string. 
     <p>This method takes in four parameters; two strings and their respective 
     lengths to compute the Levenshtein distance between the two strings.
     The result is returned as an integer.
     */ 
    private final int editDistance(CharSequence s, String t, int n, int m) {
        if (e.length <= n || e[0].length <= m) {
            e = new int[Math.max(e.length, n+1)][Math.max(e[0].length, m+1)];
        }
        final int d[][] = e; // matrix
        int i; // iterates through s
        int j; // iterates through t
        char s_i; // ith character of s
        
        if (n == 0) {return m;}
        if (m == 0) {return n;}
        
        // init matrix d
        for (i = 0; i <= n; i++) d[i][0] = i;
        for (j = 0; j <= m; j++) d[0][j] = j;
        
        // start computing edit distance
        for (i = 1; i <= n; i++) {
            s_i = s.charAt(i - 1);
            for (j = 1; j <= m; j++) {
                if (s_i != t.charAt(j-1))
                    {d[i][j] = min(d[i-1][j], d[i][j-1], d[i-1][j-1])+1;}
                else {d[i][j] = min(d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1]);}
            }
        }
        
        // we got the result!
        return d[n][m];
    }
}