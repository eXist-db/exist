package org.exist.indexing.lucene;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Token;

import java.util.List;
import java.util.LinkedList;
import java.io.IOException;

/**
 * A caching token filter which can be reset to a position marked
 * via method {@link #mark()}.
 */
public class MarkableTokenFilter extends TokenFilter {

    private List<Token> cache = null;
    private boolean isCaching = false;

    public MarkableTokenFilter(TokenStream tokenStream) {
        super(tokenStream);
    }

    public void mark() {
        isCaching = true;
        cache = new LinkedList<Token>();
    }

    @Override
    public void reset() throws IOException {
        isCaching = false;
    }

    @Override
    public Token next() throws IOException {
        return next(new Token());
    }

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
    }
}
