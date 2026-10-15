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
        return grammars.toArray(new Grammar[0]);
    }

    @Override
    public void cacheGrammars(final String grammarType, final Grammar[] grammars) {
        if (isLocked || grammars == null) return;

        Arrays.stream(grammars).forEach(grammar -> {
            final XMLGrammarDescription desc = grammar.getGrammarDescription();

            // A DTD grammar must not be cached. Xerces does not make the entities that a cached DTD
            // declares available to later documents, so these would fail with "The entity was
            // referenced, but not declared", depending on the order in which documents are stored.
            // The cache key is also not specific enough to tell DTDs apart, as they have no namespace.
            if (XMLGrammarDescription.XML_DTD.equals(desc.getGrammarType())) {
                return;
            }

            grammarCache.put(new GrammarKey(desc), grammar);
        });
    }

    @Override
    public Grammar retrieveGrammar(final XMLGrammarDescription desc) {
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
        private final String expandedSystemId;

        public GrammarKey(final XMLGrammarDescription desc) {
            this.grammarType = desc.getGrammarType();
            this.targetNamespace = desc.getNamespace();
            // Different schemas can share a target namespace, so the namespace alone does not identify one.
            this.expandedSystemId = desc.getExpandedSystemId() != null ? desc.getExpandedSystemId() : desc.getLiteralSystemId();
        }

        @Override
        public int hashCode() {
            return Objects.hash(grammarType, targetNamespace, expandedSystemId);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof final GrammarKey other)) return false;
            return Objects.equals(grammarType, other.grammarType) &&
                    Objects.equals(targetNamespace, other.targetNamespace) &&
                    Objects.equals(expandedSystemId, other.expandedSystemId);
        }

        @Override
        public String toString() {
            return "grammarType='%s', targetNamespace='%s', expandedSystemId='%s'".formatted(grammarType, targetNamespace, expandedSystemId);
        }
    }
}
