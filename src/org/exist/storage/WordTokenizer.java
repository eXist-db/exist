/* eXist xml document repository and xpath implementation
 * Copyright (C) 2001,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.storage;

import org.exist.util.*;

public class WordTokenizer {

    char[] data;
    int pos = 0;
    StringBuffer token = new StringBuffer();
    boolean indexNumbers = false, stem = false;
    PorterStemmer stemmer = null;
	
    public WordTokenizer(boolean indexNumbers, boolean stem) {
        this.indexNumbers = indexNumbers;
	this.stem = stem;
	if(stem) stemmer = new PorterStemmer();
    }
    
    public WordTokenizer(String text) {
        this(false, false);
        setText(text);
    }
    
    public void setText(String text) {
        token = new StringBuffer();
        pos = 0;
        data = new char[text.length()];
        text.getChars(0, text.length(), data, 0);
    }
    
    public String nextToken() {
        String next = null;
        while(pos < data.length) {
	    if(Character.isLetter(data[pos])) {
		if(stem)
		    stemmer.add(Character.toLowerCase(data[pos]));
		else
		    token.append(Character.toLowerCase(data[pos]));
	    } else if(Character.isDigit(data[pos])) {
		if(stem)
		    stemmer.add(data[pos]);
		else
		    token.append(data[pos]);
	    } else if(data[pos] == '*') {
		if(stem) 
		    stemmer.add('%'); 
		else
		    token.append('%');
	    } else {
		if(stem) {
		    stemmer.stem();
		    next = stemmer.toString();
		    stemmer.reset();
		} else {
		    next = token.toString();
		    token = new StringBuffer();
		}
                if(next.length() > 0)
                    return next;
	    }
            pos++;
        }
	if(stem) {
	    stemmer.stem();
	    next = stemmer.toString();
	    stemmer.reset();
	} else if(token != null) {
	    next = token.toString();
	    token = null;
	}
	if(next != null && next.length() > 0)
	    return next;
        return null;
    }
}
