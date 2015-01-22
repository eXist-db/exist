/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

import java.util.List;
import java.util.LinkedList;
import java.io.IOException;
import java.util.Iterator;
import org.apache.lucene.util.AttributeSource;

/**
 * A caching token filter which can be reset to a position marked
 * via method {@link #mark()}.
 */
public class MarkableTokenFilter extends TokenFilter {
    
    private List<AttributeSource.State> cache = null;
    private Iterator<AttributeSource.State> iterator = null; 
    private AttributeSource.State finalState;
    

    //private List<Token> cache = null;
    private boolean isCaching = false;

    public MarkableTokenFilter(TokenStream tokenStream) {
        super(tokenStream);
    }

    public void mark() {
        isCaching = true;
        cache = new LinkedList<>();
    }

    @Override
    public void reset() throws IOException {
        isCaching = false;
        
        if(cache != null) {
            iterator = cache.iterator();
        }
    }
    
    @Override
    public final void end() throws IOException {
        if(finalState != null) {
            restoreState(finalState);
        }
    }

    /*
    @Override
    public Token next(Token token) throws IOException {
        if (isCaching) {
            Token nextToken = input.next(new Token());
            cache.add(nextToken);
            return nextToken;
        } else if (cache == null) {
            return input.next(token);
        } else {
            Token nextToken = cache.remove(0);
            if (cache.isEmpty())
                cache = null;
            return nextToken;
        }
    }*/

    @Override
    public final boolean incrementToken() throws IOException {
        if (isCaching) {
            if(!input.incrementToken()) {
                input.end();
                finalState = captureState();
                return false;
            } else {
                cache.add(captureState());
                return true;
            }
        } else if (cache == null) {
            if(!input.incrementToken()) {
                input.end();
                finalState = captureState();
                return false;
            } else {
                return true;
            }
            
        } else {
            if (!iterator.hasNext()) {
	      // the cache is exhausted, return false
              cache = null;
	      return false;
	    }
	    
            // Since the TokenFilter can be reset, the tokens need to be preserved as immutable.
            restoreState(iterator.next());
            return true;
        }
    }
}
