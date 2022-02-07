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
package com.ettrema.cache;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple guess implementation of a class
 * that is missing from Milton but is required by Milton Client.
 */
public class MemoryCache<K,V> implements Cache<K, V> {
    private final String name;
    private final int max;
    private final int min;

    private final Map<K, V> storage = new HashMap<>();

    public MemoryCache(final String name, final int max, final int min) {
        this.name = name;
        this.max = max;
        this.min = min;
    }

    @Override
    public @Nullable V get(final K key) {
        return storage.get(key);
    }

    @Override
    public void put(final K key, final V value) {
        storage.put(key, value);
    }

    @Override
    public @Nullable void remove(final K key) {
        storage.remove(key);
    }
}
