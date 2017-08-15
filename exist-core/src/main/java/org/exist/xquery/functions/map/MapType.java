package org.exist.xquery.functions.map;

import io.usethesource.capsule.core.PersistentTrieMap;
import io.usethesource.capsule.util.EqualityComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Comparator;
import java.util.Iterator;

import io.usethesource.capsule.Map;

/**
 * Full implementation of the map type based on a persistent,
 * immutable tree map.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Rettter</a>
 */
public class MapType extends AbstractMapType {

    protected final static Logger LOG = LogManager.getLogger(MapType.class);

    // underlying map: a persistent, immutable tree map
    private Map.Immutable<AtomicValue, Sequence> map;
    private final Comparator<AtomicValue> comparator;
    private int type = Type.ANY_TYPE;

    public MapType(final XQueryContext context)
            throws XPathException {
        this(context,null);
    }

    public MapType(final XQueryContext context, final String collation) throws XPathException {
        super(context);
        // if there's no collation, we'll use a hash map for better performance
        if (collation == null) {
            this.comparator = null;
        } else {
            this.comparator = getComparator(collation);
        }
        this.map = PersistentTrieMap.of();
    }

    public MapType(final XQueryContext context, final String collation, final AtomicValue key, final Sequence value) throws XPathException {
        super(context);
        if (collation == null) {
            this.comparator = null;
        } else {
            this.comparator = getComparator(collation);
        }
        this.map = PersistentTrieMap.of(key, value);
        this.type = key.getType();
    }

    protected MapType(final XQueryContext context, final Comparator<AtomicValue> comparator, final Map.Immutable<AtomicValue, Sequence> other, final int type) {
        super(context);
        this.comparator = comparator;
        this.map = other;
        this.type = type;
    }

    public void add(final AbstractMapType other) {
        if (other.size() == 1) {
            setKeyType(other.getKey().getType());
            if(comparator != null) {
                map = map.__putEquivalent(other.getKey(), other.getValue(), EqualityComparator.fromComparator((Comparator)comparator));
            } else {
                map = map.__put(other.getKey(), other.getValue());
            }
        } else if (other.size() > 0) {
            setKeyType(other.getKeyType());
            final Map.Transient<AtomicValue, Sequence> transientMap = map.asTransient();

            if(other instanceof MapType) {
                if (comparator != null) {
                    transientMap.__putAllEquivalent(((MapType) other).map, EqualityComparator.fromComparator((Comparator)comparator));
                } else {
                    transientMap.__putAll(((MapType) other).map);
                }
            } else {
                for (final Map.Entry<AtomicValue, Sequence> entry : other) {
                    if (comparator != null) {
                        transientMap.__putEquivalent(entry.getKey(), entry.getValue(), EqualityComparator.fromComparator((Comparator)comparator));
                    } else {
                        transientMap.__put(entry.getKey(), entry.getValue());
                    }
                }
            }
            map = transientMap.freeze();
        }
    }

    public void add(final AtomicValue key, final Sequence value) {
        setKeyType(key.getType());
        if (comparator != null) {
            map = map.__putEquivalent(key, value, EqualityComparator.fromComparator((Comparator)comparator));
        } else {
            map = map.__put(key, value);
        }
    }

    @Override
    public Sequence get(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Sequence result;
        if (comparator != null) {
            result = map.getEquivalent(key, EqualityComparator.fromComparator((Comparator)comparator));
        } else {
            result = map.get(key);
        }
        return result == null ? Sequence.EMPTY_SEQUENCE : result;
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) throws XPathException {
        final Map.Immutable<AtomicValue, Sequence> newMap;
        if (comparator != null) {
            newMap = map.__putEquivalent(key, value, EqualityComparator.fromComparator((Comparator)comparator));
        } else {
            newMap = map.__put(key, value);
        }
        return new MapType(this.context, comparator, newMap, type);
    }

    @Override
    public boolean contains(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return false;
        }
        if (comparator != null) {
            return map.containsKeyEquivalent(key, EqualityComparator.fromComparator((Comparator)comparator));
        } else {
            return map.containsKey(key);
        }
    }

    @Override
    public Sequence keys() {
        final ValueSequence seq = new ValueSequence();
        for (final AtomicValue key: map.keySet()) {
            seq.add(key);
        }
        return seq;
    }

    public AbstractMapType remove(final AtomicValue[] keysAtomicValues) {
        final Map.Transient<AtomicValue, Sequence> newMap = map.asTransient();
        try {
            for (final AtomicValue key: keysAtomicValues) {
                if (comparator != null) {
                    newMap.__removeEquivalent(key, EqualityComparator.fromComparator((Comparator)comparator));
                } else {
                    newMap.__remove(key);
                }
            }
            return new MapType(this.context, comparator, newMap.freeze(), type);
        } catch (final Exception e) {
            return this;
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Iterator<Map.Entry<AtomicValue, Sequence>> iterator() {
        return map.entryIterator();
    }

    @Override
    public AtomicValue getKey() {
        if (map.size() == 0) {
            return null;
        }
        final Iterator<Map.Entry<AtomicValue, Sequence>> iter = this.map.entryIterator();
        return iter.next().getKey();
    }

    @Override
    public Sequence getValue() {
        return mapToSequence(this.map);
    }

    /**
     * Get a Sequence from an internal map representation
     */
    private Sequence mapToSequence(final Map.Immutable<AtomicValue, Sequence> map) {
        if (map.size() == 0) {
            return null;
        }
        final Iterator<Map.Entry<AtomicValue,Sequence>> iter = map.entryIterator();
        return iter.next().getValue();
    }

    private void setKeyType(final int newType) {
        if (type == Type.ANY_TYPE) {
            type = newType;
        }
        else if (type != newType) {
            type = Type.ITEM;
            try {
                final Map.Transient<AtomicValue, Sequence> newTransientMap = PersistentTrieMap.<AtomicValue, Sequence>of().asTransient();
                newTransientMap.__putAllEquivalent(map, EqualityComparator.fromComparator((Comparator)getComparator(null)));   //NOTE: getComparator(null) returns a default distinct values comparator
                map = newTransientMap.freeze();
            } catch (final XPathException e) {
                LOG.error(e);
            }
        }
    }

    private AtomicValue convert(final AtomicValue key) {
        if (type != Type.ANY_TYPE && type != Type.ITEM) {
            try {
                return key.convertTo(type);
            } catch (final XPathException e) {
                return null;
            }
        }
        return key;
    }

    @Override
    public int getKeyType() {
        return type;
    }
}
