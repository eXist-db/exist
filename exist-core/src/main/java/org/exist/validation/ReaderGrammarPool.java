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
package org.exist.validation;

import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;

/**
 * A view of the shared {@link GrammarPool} for one XML reader.
 *
 * The shared pool never gives DTD grammars to a parser, because Xerces does not make the entities
 * that a cached DTD declares available to a later document. A reader whose consumer restores those
 * entities itself, see {@link DtdEntityRestorer}, enables DTD reuse here, and only then does its
 * parser see the cached DTD grammars. Every other parser that uses the same reader still parses
 * a DTD each time, as before.
 */
public class ReaderGrammarPool implements XMLGrammarPool {

    private final GrammarPool delegate;
    private volatile boolean dtdReuse = false;

    public ReaderGrammarPool(final GrammarPool delegate) {
        this.delegate = delegate;
    }

    /**
     * Get the shared pool.
     *
     * @return the shared pool
     */
    public GrammarPool getDelegate() {
        return delegate;
    }

    /**
     * Let the parser of this reader use cached DTD grammars. The caller is responsible for
     * making the entities of such a grammar available to the document.
     */
    public void enableDtdReuse() {
        this.dtdReuse = true;
    }

    @Override
    public Grammar[] retrieveInitialGrammarSet(final String grammarType) {
        if (dtdReuse && XMLGrammarDescription.XML_DTD.equals(grammarType)) {
            return delegate.retrieveCachedDtdGrammars();
        }
        return delegate.retrieveInitialGrammarSet(grammarType);
    }

    @Override
    public void cacheGrammars(final String grammarType, final Grammar[] grammars) {
        delegate.cacheGrammars(grammarType, grammars);
    }

    @Override
    public Grammar retrieveGrammar(final XMLGrammarDescription desc) {
        return delegate.retrieveGrammar(desc);
    }

    @Override
    public void lockPool() {
        delegate.lockPool();
    }

    @Override
    public void unlockPool() {
        delegate.unlockPool();
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
