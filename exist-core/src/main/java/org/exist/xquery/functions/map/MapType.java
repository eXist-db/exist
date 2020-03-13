package org.exist.xquery.functions.map;

import com.ibm.icu.text.Collator;
import io.lacuna.bifurcan.IEntry;
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.function.ToIntFunction;

/**
 * Full implementation of the XDM map() type based on an
 * immutable hash-map.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Rettter</a>
 */
public class MapType extends AbstractMapType {

    private static final ToIntFunction<AtomicValue> KEY_HASH_FN = AtomicValue::hashCode;

    protected final static Logger LOG = LogManager.getLogger(MapType.class);

    private IMap<AtomicValue, Sequence> map;
    private int type = Type.ANY_TYPE;

    private static IMap<AtomicValue, Sequence> newMap(@Nullable final Collator collator) {
        return new Map<>(KEY_HASH_FN, (k1, k2) -> keysEqual(collator, k1, k2));
    }

    public MapType(final XQueryContext context) {
        this(context,null);
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator) {
        super(context);
        // if there's no collation, we'll use a hash map for better performance
        this.map = newMap(collator);
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator, final AtomicValue key, final Sequence value) {
        super(context);
        this.map = newMap(collator).put(key, value);
        this.type = key.getType();
    }

    private MapType(final XQueryContext context, final IMap<AtomicValue, Sequence> other, final int type) {
        super(context);
        this.map = other;
        this.type = type;
    }

    public void add(final AbstractMapType other) {
        setKeyType(other.key() != null ? other.key().getType() : Type.ANY_TYPE);

        if(other instanceof MapType) {
            //TODO(AR) is the union in the correct direction i.e. keys from `other` overwrite `this`
            map = map.union(((MapType)other).map);
        } else {

            // TODO(AR) could the class member `map` remain `linear` ?

            // create a transient map
            final IMap<AtomicValue, Sequence> newMap = map.linear();

            for (final IEntry<AtomicValue, Sequence> entry : other) {
                newMap.put(entry.key(), entry.value());
            }

            // return to immutable map
            map = newMap.forked();
        }
    }

    public void add(final AtomicValue key, final Sequence value) {
        setKeyType(key.getType());
        map = map.put(key, value);
    }

    @Override
    public Sequence get(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Sequence result = map.get(key, null);
        return result == null ? Sequence.EMPTY_SEQUENCE : result;
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) {
        final IMap<AtomicValue, Sequence> newMap = map.put(key, value);
        return new MapType(this.context, newMap, type == key.getType() ? type : Type.ITEM);
    }

    @Override
    public boolean contains(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return false;
        }

        return map.contains(key);
    }

    @Override
    public Sequence keys() {
        final ValueSequence seq = new ValueSequence();
        for (final AtomicValue key: map.keys()) {
            seq.add(key);
        }
        return seq;
    }

    public AbstractMapType remove(final AtomicValue[] keysAtomicValues) {

        // create a transient map
        IMap<AtomicValue, Sequence> newMap = map.linear();

        for (final AtomicValue key: keysAtomicValues) {
            newMap = newMap.remove(key);
        }

        // return an immutable map
        return new MapType(context, newMap.forked(), type);
    }

    @Override
    public int size() {
        return (int)map.size();
    }

    @Override
    public Iterator<IEntry<AtomicValue, Sequence>> iterator() {
        return map.iterator();
    }

    @Override
    public AtomicValue key() {
        if (map.size() > 0) {
            final IEntry<AtomicValue, Sequence> entry = map.nth(0);
            if (entry != null) {
                return entry.key();
            }
        }

        return null;
    }

    @Override
    public Sequence value() {
        if (map.size() > 0) {
            final IEntry<AtomicValue, Sequence> entry = map.nth(0);
            if (entry != null) {
                return entry.value();
            }
        }

        return null;
    }

    private void setKeyType(final int newType) {
        if (type == Type.ANY_TYPE) {
            type = newType;
        }
        else if (type != newType) {
            type = Type.ITEM;
//            try {
//                final Map.Transient<AtomicValue, Sequence> newTransientMap = PersistentTrieMap.<AtomicValue, Sequence>of().asTransient();
//                newTransientMap.__putAllEquivalent(map, EqualityComparator.fromComparator((Comparator)getComparator(null)));   //NOTE: getComparator(null) returns a default distinct values comparator
//                map = newTransientMap.freeze();
//            } catch (final XPathException e) {
//                LOG.error(e);
//            }
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
