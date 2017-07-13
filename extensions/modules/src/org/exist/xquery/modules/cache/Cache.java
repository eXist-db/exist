package org.exist.xquery.modules.cache;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cache itself.
 *
 * Just a small wrapper around {@link ConcurrentHashMap} to manage
 * translating to/from sequences
 */
class Cache {

	private final CacheConfig config;
	private final Map<String, Sequence> store = new ConcurrentHashMap<>();

	public Cache(final CacheConfig config) {
		this.config = config;
	}

	public CacheConfig getConfig() {
		return config;
	}

    public Sequence put(final String key, final Sequence value) {
	    return store.put(key, value);
    }

    public Sequence list(final String[] keys) throws XPathException {
	    final ValueSequence values = new ValueSequence();

	    if(keys.length == 0) {
	        // all keys
            for(final Sequence value : store.values()) {
                values.addAll(value);
            }
        } else {
	        // just the specified keys
            for (final String key : keys) {
                final Sequence value = store.get(key);
                if (value != null) {
                    values.addAll(value);
                }
            }
        }

        return values;
    }

    public Sequence get(final String key) {
	    final Sequence value = store.get(key);
	    if(value == null) {
	        return Sequence.EMPTY_SEQUENCE;
        } else {
	        return value;
        }
    }

    public Sequence remove(final String key) {
        final Sequence prevValue = store.remove(key);
        if(prevValue == null) {
            return Sequence.EMPTY_SEQUENCE;
        } else {
            return prevValue;
        }
    }

    public void clear() {
        store.clear();
    }
}
