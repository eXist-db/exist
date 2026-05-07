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
package org.exist.indexing.lucene;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A caching token filter which can be reset to a position marked
 * via method {@link #mark()}.
 */
public class MarkableTokenFilter extends TokenFilter {

    private List<AttributeSource.State> cache;
    private Iterator<AttributeSource.State> iterator;
    private AttributeSource.State finalState;
    private boolean isCaching;

    public MarkableTokenFilter(TokenStream tokenStream) {
        super(tokenStream);
    }

    public void mark() {
        isCaching = true;
        cache = new LinkedList<>();
    }

    /**
     * Rewind to the current in-memory mark buffer.
     *
     * <p>This is distinct from {@link #reset()}, which follows Lucene's
     * TokenStream lifecycle and resets the wrapped input stream.</p>
     */
    public void rewindToMark() {
        isCaching = false;
        if (cache != null) {
            iterator = cache.iterator();
        }
    }

    @Override
    public void reset() throws IOException {
        isCaching = false;
        super.reset();
    }
    
    @Override
    public final void end() throws IOException {
        if (finalState != null) {
            restoreState(finalState);
        }
    }

    @Override
    public final boolean incrementToken() throws IOException {
        if (isCaching) {
            if (input.incrementToken()) {
                cache.add(captureState());
                return true;
            }
            input.end();
            finalState = captureState();
            return false;
        }

        if (cache == null) {
            if (input.incrementToken()) {
                return true;
            }
            input.end();
            finalState = captureState();
            return false;
        }

        if (iterator.hasNext()) {
            // Since the TokenFilter can be reset, tokens are preserved as immutable states.
            restoreState(iterator.next());
            return true;
        }

        // The cache is exhausted, return false.
        cache = null;
        return false;
    }
}
