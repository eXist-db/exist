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
package org.exist.xquery.modules.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.ValueSequence;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * The cache itself.
 *
 * Just a small wrapper around {@link com.github.benmanes.caffeine.cache.Cache} to manage
 * translating to/from sequences
 */
class Cache {

	private final CacheConfig config;
	private final com.github.benmanes.caffeine.cache.Cache<String, Sequence> store;

	public Cache(final CacheConfig config) {
		this.config = config;
        final Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();

        config.maximumSize().map(cacheBuilder::maximumSize);
        config.expireAfterAccess().map(ms -> cacheBuilder.expireAfterAccess(ms, TimeUnit.MILLISECONDS));
        config.expireAfterWrite().map(ms -> cacheBuilder.expireAfterWrite(ms, TimeUnit.MILLISECONDS));

        this.store = cacheBuilder.build();
	}

	public CacheConfig getConfig() {
		return config;
	}

    public Sequence put(final String key, final Sequence value) {
	    final Sequence previous = store.asMap().put(key, value);
	    if (previous != null) {
	        return previous;
        }
	    return Sequence.EMPTY_SEQUENCE;
    }

    public Sequence list(final String[] keys) throws XPathException {
	    final ValueSequence values = new ValueSequence();

	    if(keys.length == 0) {
	        // all keys
            for(final Sequence value : store.asMap().values()) {
                values.addAll(value);
            }
        } else {
	        // just the specified keys
            final Map<String, Sequence> entries = store.getAllPresent(Arrays.asList(keys));

            for (final Sequence value : entries.values()) {
                values.addAll(value);
            }
        }

        return values;
    }

    public Sequence listKeys() throws XPathException {
        final ValueSequence keys = new ValueSequence();
	    for(final String key : store.asMap().keySet()) {
            keys.add(new StringValue(key));
        }
        return keys;
    }

    public Sequence get(final String key) {
	    final Sequence value = store.getIfPresent(key);
        return Objects.requireNonNullElse(value, Sequence.EMPTY_SEQUENCE);
    }

    public Sequence remove(final String key) {
        final Sequence prevValue = store.asMap().remove(key);
        return Objects.requireNonNullElse(prevValue, Sequence.EMPTY_SEQUENCE);
    }

    public void clear() {
        store.invalidateAll();
    }

    public void cleanup() {
	    store.cleanUp();
    }
}
