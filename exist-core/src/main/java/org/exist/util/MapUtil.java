/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.util;

import com.evolvedbinary.j8fu.tuple.Tuple2;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple utility functions for working with Java Maps.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface MapUtil {

    /**
     * Create a Hash Map from a List of Tuples.
     *
     * @param <K> the type of the keys in the map.
     * @param <V> the types of the values in the map.
     *
     * @param entries the entries for the map.
     *
     * @return The HashMap
     */
    @SafeVarargs
    static <K, V> Map<K,V> hashMap(final Tuple2<K, V>... entries) {
        return hashMap(Math.max(entries.length, 16), entries);
    }

    /**
     * Create a Hash Map from a List of Tuples.
     *
     * @param <K> the type of the keys in the map.
     * @param <V> the types of the values in the map.
     *
     * @param initialCapacity allows you to oversize the map if you plan to add more entries.
     * @param entries the entries for the map.
     *
     * @return The HashMap
     */
    @SafeVarargs
    static <K, V> Map<K,V> hashMap(final int initialCapacity, final Tuple2<K, V>... entries) {
        final Map<K, V> map = new HashMap<>(initialCapacity);
        for (final Tuple2<K, V> entry : entries) {
            map.put(entry._1, entry._2);
        }
        return map;
    }
}
