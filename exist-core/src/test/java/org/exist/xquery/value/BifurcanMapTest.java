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
package org.exist.xquery.value;

import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.LinearMap;
import org.junit.Test;

import javax.annotation.Nullable;

import static org.exist.xquery.functions.map.MapType.newLinearMap;
import static org.junit.Assert.*;

/**
 * Tests to demonstrate Bifurcan Map behaviour
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class BifurcanMapTest {

    /**
     * Reproduces the XQSuite Test `mt:immutable-remove-then-remove()` from `maps.xql`:
     *
     * <code>
     *     let $removed := map:remove(map { 1: true(), 2: true() }, 2)
     *     let $expected := $removed(1)
     *     let $result := map:remove($removed, 1)
     *     return
     *         (
     *              $expected eq $removed(1),
     *              $expected ne $result(1)
     *         )
     * </code>
     */
    @Test
    public void immutableRemoveThenRemove() {

        /*
          1. Create the initial map: `map { 1: true(), 2: true() }`
         */
        final IMap<AtomicValue, Sequence> map = createMap();
        checkMapIsForked(map);

        /*
          2. Remove the entry in the initial map with key `2`: `let $removed := map:remove(..., 2)`
         */
        final IMap<AtomicValue, Sequence> removed = removeFromMap(map, new IntegerValue(null, 2));
        // taken from MapType.java constructor
        checkMapIsForked(removed);

        /*
          3. Get the entry in the removed map with key `1`: `$expected := $removed(1)`
         */
        final Sequence expected = getFromMap(removed, new IntegerValue(null, 1));

        /*
         4. Remove the entry in the removed map with key `1`: `let $result := map:remove($removed, 1)`
         */
        final IMap<AtomicValue, Sequence> result = removeFromMap(removed, new IntegerValue(null, 1));
        // taken from MapType.java constructor
        checkMapIsForked(result);

        /*
         `$expected eq $removed(1)`
        */
        assertEquals(expected, getFromMap(removed, new IntegerValue(null, 1)));

        /*
         `$expected ne $result(1)`
         */
        assertNotEquals(expected, getFromMap(result, new IntegerValue(null, 1)));
    }

    /*
    Taken from MapExpr.java
     */
    private static IMap<AtomicValue, Sequence> createMap() {
        final IMap<AtomicValue, Sequence> map = newLinearMap(null);
        map.put(new IntegerValue(null, 1), BooleanValue.TRUE);
        map.put(new IntegerValue(null, 2), BooleanValue.TRUE);

        // return an immutable map
        return map.forked();
    }

    /*
    Taken from MapFunction.java MapFunction#remove(Sequence[])
     */
    private static IMap<AtomicValue, Sequence> removeFromMap(final IMap<AtomicValue, Sequence> map, final AtomicValue... keys) {
        // create a transient map
        IMap<AtomicValue, Sequence> newMap = map.linear();

        for (final AtomicValue key: keys) {
            newMap = newMap.remove(key);
        }

        // return an immutable map
        return newMap.forked();
    }

    /*
    Taken from MapType.java MapType#get(AtomicValue)
     */
    private static @Nullable Sequence getFromMap(final IMap<AtomicValue, Sequence> map, final AtomicValue key) {
        return map.get(key, null);
    }

    @Test
    public void bifurcanImmutableRemoveThenRemove() {

        /*
          1. Create the initial map: `map { 1: true(), 2: true() }`
         */
        IMap<Integer, Boolean> map = new LinearMap<>();
        map.put(1, true);
        map.put(2, true);
        map = map.forked();  // make the map immutable
        checkMapIsForked(map);

        /*
          2. Remove the entry in the initial map with key `2`: `let $removed := map:remove(..., 2)`
         */
        IMap<Integer, Boolean> removed = map.linear();  // create a transient map for modifications
        assertFalse(removed == map);
        removed = removed.remove(2);
        removed = removed.forked();  // make the map immutable
        checkMapIsForked(removed);

        /*
          3. Get the entry in the removed map with key `1`: `$expected := $removed(1)`
         */
        final Boolean expected = removed.get(1, null);

        /*
         4. Remove the entry in the removed map with key `1`: `let $result := map:remove($removed, 1)`
         */
        IMap<Integer, Boolean> result = removed.linear();  // create a transient map for modifications
        assertFalse(result == removed);
        result = result.remove(1);
        result = result.forked();  // make the map immutable
        checkMapIsForked(result);

        /*
         `$expected eq $removed(1)`
        */
        assertEquals(expected, removed.get(1, null));

        /*
         `$expected ne $result(1)`
         */
        assertNotEquals(expected, result.get(1, null));
    }

    /*
     Taken from MapType.java constructor
     */
    private static <K,V> void checkMapIsForked(final IMap<K, V> map) throws IllegalArgumentException {
        if (map.isLinear()) {
            throw new IllegalArgumentException("Map must be immutable, but linear Map was provided");
        }
    }
}
