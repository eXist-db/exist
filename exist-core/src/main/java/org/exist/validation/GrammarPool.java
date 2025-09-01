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

        grammarCache = cafeineBuilder.build();
    }

    @Override
    public Grammar[] retrieveInitialGrammarSet(final String grammarType) {
        final List<Grammar> grammars = new ArrayList<>();
        grammarCache.asMap().forEach((key, value) -> {
            if (key.grammarType.equals(grammarType)) {
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
            grammarCache.put(new GrammarKey(desc), grammar);
        });
    }

    @Override
    public Grammar retrieveGrammar(final XMLGrammarDescription desc) {
        return grammarCache.getIfPresent(new GrammarKey(desc));
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
    }

    @Override
    public String toString() {
        return grammarCache.stats().toString();
    }

    // Helper class for cache keys
    private static class GrammarKey {
        private final String grammarType;
        private final String targetNamespace;

        public GrammarKey(final XMLGrammarDescription desc) {
            this.grammarType = desc.getGrammarType();
            this.targetNamespace = desc.getNamespace();
        }

        @Override
        public int hashCode() {
            return Objects.hash(grammarType, targetNamespace);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof final GrammarKey other)) return false;
            return Objects.equals(grammarType, other.grammarType) &&
                    Objects.equals(targetNamespace, other.targetNamespace);
        }

        @Override
        public String toString() {
            return "grammarType='%s', targetNamespace='%s'".formatted(grammarType, targetNamespace);
        }
    }
}
