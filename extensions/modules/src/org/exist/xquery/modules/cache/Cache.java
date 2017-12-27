package org.exist.xquery.modules.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.ValueSequence;

import java.util.Arrays;
import java.util.Map;
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

        config.getMaximumSize().map(cacheBuilder::maximumSize);
        config.getExpireAfterAccess().map(ms -> cacheBuilder.expireAfterAccess(ms, TimeUnit.MILLISECONDS));

        this.store = cacheBuilder.build();
	}

	public CacheConfig getConfig() {
		return config;
	}

    public Sequence put(final String key, final Sequence value) {
	    return store.asMap().put(key, value);
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
	    if(value == null) {
	        return Sequence.EMPTY_SEQUENCE;
        } else {
	        return value;
        }
    }

    public Sequence remove(final String key) {
        final Sequence prevValue = store.asMap().remove(key);
        if(prevValue == null) {
            return Sequence.EMPTY_SEQUENCE;
        } else {
            return prevValue;
        }
    }

    public void clear() {
        store.invalidateAll();
    }

    public void cleanup() {
	    store.cleanUp();
    }
}
