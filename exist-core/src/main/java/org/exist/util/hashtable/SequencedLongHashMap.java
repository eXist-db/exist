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
package org.exist.util.hashtable;

/**
 * A double-linked hash map additionally providing access to entries in the order in which
 * they were added.
 *
 * If a duplicate entry is added, the old entry is removed from the list and appended to the end. The
 * map thus implements a "Last Recently Used" (LRU) behaviour.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import net.jcip.annotations.NotThreadSafe;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.NoSuchElementException;

@NotThreadSafe
public class SequencedLongHashMap<V> {

    private static final int DEFAULT_SIZE = 1031; // prime number

    private final Long2ObjectLinkedOpenHashMap<V> map;

    SequencedLongHashMap() {
        this.map = new Long2ObjectLinkedOpenHashMap<>(DEFAULT_SIZE);
    }

    public SequencedLongHashMap(final int iSize) {
        this.map = new Long2ObjectLinkedOpenHashMap<>(iSize);
    }

    /**
     * Adds a pair to the map; if the key is already present, it is moved to the
     * last position of the iteration order.
     *
     * @param key The key
     * @param value The value
     *
     * @return the old value, or null if no value was present for the given key.
     */
    public @Nullable V put(final long key, final V value) {
        return map.putAndMoveToLast(key, value);
    }

    /**
     * Returns the value for key or null if the key
     * is not in the map.
     *
     * @param key The key to retrieve the value for
     *
     * @return the value associated with the key, or null if the key is absent
     */
    public @Nullable V get(final long key) {
        return map.get(key);
    }

    /**
     * Returns the first entry added to the map.
     *
     * @return the first entry
     */
//    public Entry<V> getFirstEntry() {
//        return first;
//    }

    /**
     * Remove the entry specified by key from the map.
     *
     * @param key The key
     *
     * @return the previous value, or null if there was no previous value
     */
    public @Nullable V remove(final long key) {
        return map.remove(key);
    }

    /**
     * Removes the mapping associated with the first key in iteration order.
     *
     * @return the value previously associated with the first key in iteration
     *         order, or null if the map is empty
     */
    public @Nullable V removeFirst() {
        if (map.isEmpty()) {
            return null;
        }
        return map.removeFirst();
    }

    /**
     * Clear the map.
     */
    public void clear() {
        map.clear();
        map.trim();  // also resize the map to reduce memory use!
    }

    /**
     * Returns an iterator over all keys in the
     * order in which they were inserted.
     *
     * @return an iterator over the keys in the map
     */
    public LongIterator iterator() {
       return map.keySet().iterator();
    }

    /**
     * Returns an iterator over all values in the order
     * in which they were inserted.
     *
     * @return an iterator over the values in the map
     */
    public Iterator<V> valueIterator() {
        return map.values().iterator();
    }

    /**
     * Returns an iterator over all entries in the order
     * in which they were inserted.
     *
     * NOTE: The iterator might return always the same
     * entry instance, suitably mutated.
     *
     * @return an iterator over the entries in the map
     */
    public Iterator<Long2ObjectMap.Entry<V>> fastEntrySetIterator() {
        return map.long2ObjectEntrySet().fastIterator();
    }

    /**
     * Returns the number of key/value mappings in this map. If the map contains
     * more than {@link Integer#MAX_VALUE} elements, returns
     * {@link Integer#MAX_VALUE}.
     *
     * @return the number of key-value mappings in this map.
     */
    public int size() {
        return map.size();
    }

    /**
     * Add all the entries from the other map to this map.
     *
     * @param other the other map.
     */
    public void putAll(final SequencedLongHashMap<V> other) {
        map.putAll(other.map);
    }
}
