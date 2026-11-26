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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.impl.XMLEntityManager;
import org.apache.xerces.util.URI;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.exist.Namespaces;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A view of the shared {@link GrammarPool} for one XML reader.
 *
 * <p>The shared pool never gives DTD grammars to a parser, because Xerces does not make the entities
 * that a cached DTD declares available to a later document. A reader whose consumer restores those
 * entities itself, see {@link DtdEntityRestorer}, enables DTD reuse here, and only then does its
 * parser see the cached DTD grammars. Every other parser that uses the same reader still parses
 * a DTD each time, as before.</p>
 *
 * <p>The DTD grammars that are cached are those of an external DTD alone. Xerces caches the grammar that a
 * parse has built when the DOCTYPE ends, and that grammar holds the declarations of the internal subset of the
 * document as well: the next document that names the same DTD would take them for the DTD, the attribute types
 * and the entities of the one before. So this view does not cache the DTD grammars of the documents that are
 * parsed through it. A consumer asks for the grammar of a DTD with {@link #requestSeed}, and it is built
 * by {@link #seed(XMLReader)}, from a document that is nothing but the DOCTYPE.</p>
 */
public class ReaderGrammarPool implements XMLGrammarPool {

    private static final Logger LOG = LogManager.getLogger(ReaderGrammarPool.class);

    /** The name under which SAX reports the external subset of a DTD as an entity. */
    private static final String EXTERNAL_SUBSET = "[dtd]";

    private final GrammarPool delegate;
    private volatile boolean dtdReuse = false;

    /** The DTD that a consumer has asked a grammar for, or null. */
    @Nullable private DtdSeed pendingSeed = null;

    /** Whether the parse in progress is the one that builds the grammar of a DTD, see {@link #seed}. */
    private boolean seeding = false;
    private final List<Grammar> seeded = new ArrayList<>();

    private record DtdSeed(@Nullable String publicId, String systemId, @Nullable String baseSystemId, String rootName) {
    }

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

    /**
     * Ask for the grammar of the external DTD that the document names, to be built when the reader is returned
     * to its pool, see {@link #seed}.
     *
     * @param publicId the public id of the DOCTYPE, or null
     * @param systemId the system id of the DOCTYPE as written in the document
     * @param baseSystemId the system id of the document, used to expand a relative system id, or null
     * @param rootName the name of the DOCTYPE
     */
    public void requestSeed(@Nullable final String publicId, final String systemId, @Nullable final String baseSystemId,
            final String rootName) {
        try {
            if (delegate.isKnownNotReusableDtd(publicId, XMLEntityManager.expandSystemId(systemId, baseSystemId, false))) {
                // read before and not kept, a grammar built now would be discarded again
                return;
            }
        } catch (final URI.MalformedURIException e) {
            // no identity to look it up by, ask as usual
        }
        this.pendingSeed = new DtdSeed(publicId, systemId, baseSystemId, rootName);
    }

    /**
     * Builds the grammar of the DTD that was asked for with {@link #requestSeed}, if any, and caches it. This is
     * called when the reader is idle, with the configuration that the document was parsed with: it parses a
     * document that is nothing but the same DOCTYPE, so that the DTD is found and read as it was for the
     * document, and the grammar holds that DTD and nothing else. A parser can not be called from one of its own
     * handlers, which is why this is not done while the document is parsed.
     *
     * <p>The grammar is only kept if the parser read the external subset: a reader that does not, for the
     * validation mode it was set to, would otherwise cache a grammar that a reader which does would use.</p>
     *
     * @param reader the reader, which is not parsing
     */
    public void seed(final XMLReader reader) {
        final DtdSeed seed = pendingSeed;
        pendingSeed = null;
        if (seed == null) {
            return;
        }

        final SeedingHandler handler = new SeedingHandler();
        seeded.clear();
        seeding = true;
        try {
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, handler);
            reader.setProperty(Namespaces.SAX_DECLARATION_HANDLER, handler);

            final InputSource source = new InputSource(new StringReader(doctypeOnly(seed)));
            source.setSystemId(seed.baseSystemId());
            reader.parse(source);

        } catch (final SAXException | IOException | RuntimeException e) {
            LOG.debug("The grammar of the DTD {} {} was not built: {}", seed.publicId(), seed.systemId(), e.getMessage());
            seeded.clear();
        } finally {
            seeding = false;
        }

        if (handler.readExternalSubset) {
            delegate.cacheGrammars(XMLGrammarDescription.XML_DTD, seeded.toArray(Grammar[]::new));
        }
        seeded.clear();
    }

    private static String doctypeOnly(final DtdSeed seed) {
        final StringBuilder doctype = new StringBuilder("<!DOCTYPE ").append(seed.rootName());
        if (seed.publicId() != null) {
            doctype.append(" PUBLIC ").append(literal(seed.publicId())).append(' ').append(literal(seed.systemId()));
        } else {
            doctype.append(" SYSTEM ").append(literal(seed.systemId()));
        }
        return doctype.append("><x/>").toString();
    }

    /** A literal for a public or system id: they can not contain the quote that delimits them. */
    private static String literal(final String value) {
        final char quote = value.indexOf('"') < 0 ? '"' : '\'';
        return quote + value + quote;
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
        if (XMLGrammarDescription.XML_DTD.equals(grammarType)) {
            // only the grammar of the parse that is nothing but a DOCTYPE, see seed()
            if (seeding) {
                seeded.addAll(List.of(grammars));
            }
            return;
        }
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

    /** Takes in the parse of {@link #doctypeOnly} and notes whether the external subset was read. */
    private static final class SeedingHandler extends DefaultHandler2 {
        private boolean readExternalSubset = false;

        @Override
        public void startEntity(final String name) {
            if (EXTERNAL_SUBSET.equals(name)) {
                readExternalSubset = true;
            }
        }
    }
}
