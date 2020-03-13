package org.exist.xquery.functions.map;

import com.ibm.icu.text.Collator;
import io.lacuna.bifurcan.IEntry;
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.Maps;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;
import java.util.Iterator;

import static org.exist.xquery.functions.map.MapType.newLinearMap;

/**
 * Implementation of the XDM map() type for a map that only
 * contains a single key and value.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SingleKeyMapType extends AbstractMapType {

    private AtomicValue key;
    private Sequence value;
    private @Nullable Collator collator;

    public SingleKeyMapType(final XQueryContext context, final @Nullable Collator collator, final AtomicValue key, final Sequence value) {
        super(context);
        this.key = key;
        this.value = value;
        this.collator = collator;
    }

    @Override
    public int getKeyType() {
        return key.getType();
    }

    @Override
    public Sequence get(final AtomicValue key) {
        if (keysEqual(collator, this.key, key)) {
            return this.value;
        }
        return null;
    }

    @Override
    public AbstractMapType merge(final Iterable<AbstractMapType> others) {
        final MapType map = new MapType(context, collator, key, value);
        return map.merge(others);
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) {
        final IMap<AtomicValue, Sequence> map = newLinearMap(collator);
        int type = Type.ANY_TYPE;
        if (this.key != null) {
            map.put(this.key, this.value);
            type = this.key.getType();
        }
        map.put(key, value);
        if (type != key.getType()) {
            type = Type.ITEM;
        }

        return new MapType(context, map.forked(), type);
    }

    @Override
    public boolean contains(final AtomicValue key) {
        return keysEqual(collator, this.key, key);
    }

    @Override
    public Sequence keys() {
        return key == null ? Sequence.EMPTY_SEQUENCE : key;
    }

    @Override
    public int size() {
        return key == null ? 0 : 1;
    }

    @Override
    public AbstractMapType remove(final AtomicValue[] keysAtomicValues) throws XPathException {
        for (final AtomicValue key: keysAtomicValues) {
            if (key != this.key) {
                continue;
            }
            this.key = null;
            value = null;
            return new MapType(context);
        }
        final MapType map = new MapType(context);
        map.add(this);
        return map;
    }

    @Override
    public AtomicValue key() {
        return key;
    }

    @Override
    public Sequence value() {
        return value;
    }

    @Override
    public Iterator<IEntry<AtomicValue, Sequence>> iterator() {
        return new SingleKeyMapIterator();
    }

    private class SingleKeyMapIterator implements Iterator<IEntry<AtomicValue, Sequence>> {

        boolean hasMore = true;

        @Override
        public boolean hasNext() {
            return hasMore;
        }

        @Override
        public IEntry<AtomicValue, Sequence> next() {
            if (!hasMore) {
                return null;
            }
            hasMore = false;
            return new Maps.Entry<>(key, value);
        }
    }
}