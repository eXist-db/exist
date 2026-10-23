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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.impl.dtd.DTDGrammar;
import org.apache.xerces.impl.dtd.XMLEntityDecl;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Smart grammar-cache for xerces based on the Caffeine library.
 */
public class GrammarPool implements XMLGrammarPool {

    private static final Logger LOGGER = LogManager.getLogger(GrammarPool.class);

    public static final String GRAMMAR_POOL_ELEMENT = "grammar-cache";

    public static final String ATTRIBUTE_MAXIMUM_SIZE = "size";
    public static final String PROPERTY_MAXIMUM_SIZE = "validation.grammar-cache.size";

    public static final String ATTRIBUTE_EXPIRE_AFTER_ACCESS = "expire";
    public static final String PROPERTY_EXPIRE_AFTER_ACCESS = "validation.grammar-cache.expire";

    private final Cache<GrammarKey, Grammar> grammarCache;

    /** Schemas registered on purpose, to be found by namespace alone (see {@link #preloadGrammars}). */
    private final Set<GrammarKey> preloaded = ConcurrentHashMap.newKeySet();
    private boolean isLocked = false;

    /**
     * Constructor. Default 128 entries, lifetime after last access is 60 minutes.
     */
    public GrammarPool() {
        this(-1, -1L);
    }

    /**
     * Constructor.
     *
     * @param maxSize Maximum nr of cached elements.
     * @param seconds Maximum expiration time in seconds after last access.
     */
    public GrammarPool(final long maxSize, final long seconds) {

        Caffeine<Object, Object> cafeineBuilder = Caffeine.newBuilder();

        if (maxSize > 0) {
            cafeineBuilder = cafeineBuilder.maximumSize(maxSize);
        }

        if (seconds > 0L) {
            cafeineBuilder.expireAfterAccess(seconds, TimeUnit.SECONDS);
        }

        final String sizeTxt =maxSize > 0 ? String.valueOf(maxSize) : "unlimited";
        final String expireTxt = seconds > 0L ? seconds + " seconds" : "infinite";

        final  Level level = seconds > 0L && maxSize > 0 ? Level.INFO : Level.WARN;
        LOGGER.log(level, "Grammar cache: size={}, lifetime={}", sizeTxt, expireTxt);

        // forget a registration together with the grammar it belongs to, when the cache drops it by itself
        cafeineBuilder = cafeineBuilder.evictionListener((key, value, cause) -> preloaded.remove(key));

        grammarCache = cafeineBuilder.build();
    }

    @Override
    public Grammar[] retrieveInitialGrammarSet(final String grammarType) {
        // DTD grammars are not handed out here, see #retrieveCachedDtdGrammars()
        if (XMLGrammarDescription.XML_DTD.equals(grammarType)) {
            return new Grammar[0];
        }

        // Xerces preloads this set into a bucket keyed by target namespace alone and consults it before the
        // schema location of the document. Handing out every cached schema would make the first one cached
        // for a namespace win over a different schema that a document names for the same namespace, so
        // schemas are only handed out here when they were registered on purpose. All others are found
        // through retrieveGrammar(), which sees the location.
        final boolean onlyRegistered = XMLGrammarDescription.XML_SCHEMA.equals(grammarType);

        final List<Grammar> grammars = new ArrayList<>();
        grammarCache.asMap().forEach((key, value) -> {
            if (key.grammarType.equals(grammarType) && (!onlyRegistered || preloaded.contains(key))) {
                grammars.add(value);
            }
        });
        return grammars.toArray(Grammar[]::new);
    }

    /**
     * Get the cached DTD grammars.
     *
     * Xerces does not make the entities declared by a cached DTD available to later documents,
     * so DTD grammars are never given to a parser through the {@link XMLGrammarPool} methods.
     * They are only for a reader that restores those entities itself, see {@link ReaderGrammarPool}
     * and {@link DtdEntityRestorer}.
     *
     * @return the cached DTD grammars
     */
    public Grammar[] retrieveCachedDtdGrammars() {
        final List<Grammar> grammars = new ArrayList<>();
        grammarCache.asMap().forEach((key, value) -> {
            if (XMLGrammarDescription.XML_DTD.equals(key.grammarType)) {
                grammars.add(value);
            }
        });
        return grammars.toArray(Grammar[]::new);
    }

    @Override
    public void cacheGrammars(final String grammarType, final Grammar[] grammars) {
        if (isLocked || grammars == null) return;

        Arrays.stream(grammars).forEach(grammar -> {
            final XMLGrammarDescription desc = grammar.getGrammarDescription();

            if (XMLGrammarDescription.XML_DTD.equals(desc.getGrammarType()) && !isReusableDtd(grammar)) {
                return;
            }

            grammarCache.put(new GrammarKey(desc), grammar);
        });
    }

    /**
     * Is it safe to parse a later document with this DTD grammar, without scanning the DTD again?
     *
     * A parser that is given a cached DTD grammar expands the entities that it declares without
     * regard to the settings of the parser. That is not acceptable for an external or unparsed
     * entity, as the parser would read the file or location of the entity even though
     * external general entities are disabled. Such a DTD is parsed each time instead. A DTD that
     * only consists of an internal subset has no identity to reuse it by.
     */
    private static boolean isReusableDtd(final Grammar grammar) {
        if (!(grammar instanceof DTDGrammar dtd)) {
            return false;
        }

        final XMLGrammarDescription desc = grammar.getGrammarDescription();
        if (desc.getExpandedSystemId() == null && desc.getLiteralSystemId() == null) {
            return false;
        }

        final XMLEntityDecl decl = new XMLEntityDecl();
        for (int i = 0; dtd.getEntityDecl(i, decl); i++) {
            if (!decl.isPE && (decl.systemId != null || decl.notation != null)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Grammar retrieveGrammar(final XMLGrammarDescription desc) {
        // see #retrieveCachedDtdGrammars()
        if (XMLGrammarDescription.XML_DTD.equals(desc.getGrammarType())) {
            return null;
        }
        return grammarCache.getIfPresent(new GrammarKey(desc));
    }

    /**
     * Caches XML Schema grammars and registers them to be found by their target namespace alone, as
     * {@code validation:pre-parse-grammar} promises, even for a document that names no schema location.
     *
     * @param grammars the XML Schema grammars to cache and register.
     */
    public void preloadGrammars(final Grammar[] grammars) {
        if (isLocked || grammars == null) return;

        cacheGrammars(XMLGrammarDescription.XML_SCHEMA, grammars);
        Arrays.stream(grammars).forEach(grammar -> preloaded.add(new GrammarKey(grammar.getGrammarDescription())));
    }

    /**
     * @return the number of grammars currently cached, of any type.
     */
    int cachedGrammarCount() {
        return grammarCache.asMap().size();
    }

    @Override
    public void lockPool() {
        isLocked = true;
    }

    @Override
    public void unlockPool() {
        isLocked = false;
    }

    @Override
    public void clear() {
        if (isLocked) return;

        grammarCache.invalidateAll();
        preloaded.clear();
    }

    @Override
    public String toString() {
        return grammarCache.stats().toString();
    }

    // Helper class for cache keys
    private static class GrammarKey {
        private final String grammarType;
        private final String targetNamespace;
        private final String publicId;
        private final String systemId;

        public GrammarKey(final XMLGrammarDescription desc) {
            this.grammarType = desc.getGrammarType();
            this.targetNamespace = desc.getNamespace();

            // Different schemas can share a target namespace, so the namespace alone does not identify one.
            this.systemId = desc.getExpandedSystemId() != null ? desc.getExpandedSystemId() : desc.getLiteralSystemId();
            // A DTD has no target namespace either, so its public id takes part in identifying it.
            this.publicId = XMLGrammarDescription.XML_DTD.equals(grammarType) ? desc.getPublicId() : null;
        }

        @Override
        public int hashCode() {
            return Objects.hash(grammarType, targetNamespace, publicId, systemId);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof final GrammarKey other)) return false;
            return Objects.equals(grammarType, other.grammarType) &&
                    Objects.equals(targetNamespace, other.targetNamespace) &&
                    Objects.equals(publicId, other.publicId) &&
                    Objects.equals(systemId, other.systemId);
        }

        @Override
        public String toString() {
            return "grammarType='%s', targetNamespace='%s', publicId='%s', systemId='%s'"
                    .formatted(grammarType, targetNamespace, publicId, systemId);
        }
    }
}
