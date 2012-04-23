package org.exist.indexing.lucene;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Token;

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
        cache = new LinkedList<AttributeSource.State>();
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
    public boolean incrementToken() throws IOException {
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
