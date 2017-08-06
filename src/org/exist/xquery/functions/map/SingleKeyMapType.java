package org.exist.xquery.functions.map;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

public class SingleKeyMapType extends AbstractMapType {

    private AtomicValue key;
    private Sequence value;
    private Comparator<AtomicValue> comparator;

    public SingleKeyMapType(XQueryContext context, String collation, AtomicValue key, Sequence value) throws XPathException {
        super(context);
        this.key = key;
        this.value = value;
        this.comparator = getComparator(collation);
    }

    @Override
    public int getKeyType() {
        return key.getType();
    }

    @Override
    public Sequence get(AtomicValue key) {
        if (comparator.compare(this.key, key) == Constants.EQUAL)
            {return this.value;}
        return null;
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) throws XPathException {
        final MapType map = new MapType(context);
        map.add(this);
        return map.put(key, value);
    }

    @Override
    public boolean contains(AtomicValue key) {
        return (comparator.compare(this.key, key) == Constants.EQUAL);
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
    public AbstractMapType remove(AtomicValue key) {
        try {
            return new MapType(context);
        } catch (final XPathException e) {
            return null;
        }
    }

    @Override
    public AtomicValue getKey() {
        return key;
    }

    @Override
    public Sequence getValue() {
        return value;
    }

    @Override
    public Iterator<Map.Entry<AtomicValue, Sequence>> iterator() {
        return new SingleKeyMapIterator();
    }

    private class SingleKeyMapIterator implements Iterator<Map.Entry<AtomicValue, Sequence>> {

        boolean hasMore = true;

        @Override
        public boolean hasNext() {
            return hasMore;
        }

        @Override
        public Map.Entry<AtomicValue, Sequence> next() {
            if (!hasMore) {
                return null;
            }
            hasMore = false;
            return new AbstractMap.SimpleEntry<AtomicValue, Sequence>(key, value);
        }
    }
}