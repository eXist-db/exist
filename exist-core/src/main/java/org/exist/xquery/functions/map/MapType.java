package org.exist.xquery.functions.map;

import com.github.krukow.clj_lang.IPersistentMap;
import com.github.krukow.clj_lang.ITransientMap;
import com.github.krukow.clj_lang.PersistentHashMap;
import com.github.krukow.clj_lang.PersistentTreeMap;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Iterator;
import java.util.Map;

/**
 * Full implementation of the map type based on a persistent,
 * immutable tree map.
 */
public class MapType extends AbstractMapType {

    // underlying map: a persistent, immutable tree map
    private IPersistentMap<AtomicValue, Sequence> map;
    private int type = Type.ANY_TYPE;

    public MapType(XQueryContext context)
            throws XPathException {
        this(context, (String) null);
    }

    public MapType(XQueryContext context, String collation) throws XPathException {
        super(context);
        // if there's no collation, we'll use a hash map for better performance
        if (collation == null)
            {this.map = PersistentHashMap.EMPTY;}
        else
            {this.map = PersistentTreeMap.create(getComparator(collation), null);}
    }

    public MapType(XQueryContext context, String collation, AtomicValue key, Sequence value) throws XPathException {
        super(context);
        if (collation == null)
            {this.map = PersistentHashMap.EMPTY;}
        else
            {this.map = PersistentTreeMap.create(getComparator(collation), null);}
        this.type = key.getType();
        this.map = this.map.assoc(key, value);
    }

    protected MapType(XQueryContext context, IPersistentMap<AtomicValue, Sequence> other, int type) {
        super(context);
        this.map = other;
        this.type = type;
    }

    public void add(AbstractMapType other) {
        if (other.size() == 1) {
            setKeyType(other.getKey().getType());
            map = map.assoc(other.getKey(), other.getValue());
        } else if (other.size() > 0) {
            setKeyType(other.getKeyType());
            if (map instanceof PersistentHashMap) {
                ITransientMap<AtomicValue, Sequence> tmap = ((PersistentHashMap)map).asTransient();
                for (final Map.Entry<AtomicValue, Sequence> entry : other) {
                    tmap = tmap.assoc(entry.getKey(), entry.getValue());
                }
                map = tmap.persistentMap();
            } else {
                for (final Map.Entry<AtomicValue, Sequence> entry : other)
                    map = map.assoc(entry.getKey(), entry.getValue());
            }
        }
    }

    public void add(AtomicValue key, Sequence value) {
        setKeyType(key.getType());
        this.map = this.map.assoc(key, value);
    }

    public Sequence get(AtomicValue key) {
        key = convert(key);
        if (key == null)
            {return Sequence.EMPTY_SEQUENCE;}
        final Map.Entry<AtomicValue, Sequence> e = this.map.entryAt(key);
        return e == null ? Sequence.EMPTY_SEQUENCE : e.getValue();
    }

    @Override
    public AbstractMapType put(AtomicValue key, final Sequence value) throws XPathException {
        return new MapType(this.context, this.map.assoc(key, value), type);
    }

    public boolean contains(AtomicValue key) {
        key = convert(key);
        if (key == null)
            {return false;}
        return this.map.containsKey(key);
    }

    public Sequence keys() {
        final ValueSequence seq = new ValueSequence();
        for (final Map.Entry<AtomicValue, Sequence> entry: this.map) {
            seq.add(entry.getKey());
        }
        return seq;
    }

    public AbstractMapType remove(AtomicValue key) {
        try {
            return new MapType(this.context, this.map.without(key), type);
        } catch (final Exception e) {
            return this;
        }
    }

    @Override
    public int size() {
        return map.count();
    }

    @Override
    public Iterator<Map.Entry<AtomicValue, Sequence>> iterator() {
        return map.iterator();
    }

    @Override
    public AtomicValue getKey() {
        if (map.count() == 0)
            {return null;}
        final Iterator<Map.Entry<AtomicValue,Sequence>> iter = this.map.iterator();
        return iter.next().getKey();
    }

    @Override
    public Sequence getValue() {
        return mapToSequence(this.map);
    }

    /**
     * Get a Sequence from an internal map representation
     */
    private Sequence mapToSequence(final IPersistentMap<AtomicValue, Sequence> map) {
        if (map.count() == 0) {
            return null;
        }
        final Iterator<Map.Entry<AtomicValue,Sequence>> iter = map.iterator();
        return iter.next().getValue();
    }

    private void setKeyType(int newType) {
        if (type == Type.ANY_TYPE)
            {type = newType;}
        else if (type != newType) {
            type = Type.ITEM;
            if (map instanceof PersistentHashMap) {
                try {
                    PersistentTreeMap tmap = PersistentTreeMap.create(getComparator(null), null);
                    for (final Map.Entry<AtomicValue, Sequence> entry : map) {
                        tmap = tmap.assoc(entry.getKey(), entry.getValue());
                    }
                    map = tmap;
                } catch (final XPathException e) {
                }
            }
        }
    }

    private AtomicValue convert(AtomicValue key) {
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