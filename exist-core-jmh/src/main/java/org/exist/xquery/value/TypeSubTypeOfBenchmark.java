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
package org.exist.xquery.value;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark for {@link Type#subTypeOf(int, int)} — the engine-wide hot path
 * exercised by every atomic comparison. See issue #6322.
 *
 * Shapes covered:
 * - identical:        STRING vs STRING (early-return at the first equality check)
 * - directSuper:      STRING vs ANY_ATOMIC_TYPE (single recursion, two map lookups)
 * - deepSuper:        INT vs ANY_ATOMIC_TYPE (multi-step recursion)
 * - unionMember:      DOUBLE vs NUMERIC (supertype is a registered union)
 * - unionSubtype:     NUMERIC vs ANY_ATOMIC_TYPE (subtype is a registered union)
 * - notSubType:       STRING vs INTEGER (walks superTypes, returns false)
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TypeSubTypeOfBenchmark {

    @Param({"identical", "directSuper", "deepSuper", "unionMember", "unionSubtype", "notSubType"})
    public String shape;

    private int subtype;
    private int supertype;

    @Setup(Level.Trial)
    public void setUp() {
        switch (shape) {
            case "identical" -> { subtype = Type.STRING; supertype = Type.STRING; }
            case "directSuper" -> { subtype = Type.STRING; supertype = Type.ANY_ATOMIC_TYPE; }
            case "deepSuper" -> { subtype = Type.INT; supertype = Type.ANY_ATOMIC_TYPE; }
            case "unionMember" -> { subtype = Type.DOUBLE; supertype = Type.NUMERIC; }
            case "unionSubtype" -> { subtype = Type.NUMERIC; supertype = Type.ANY_ATOMIC_TYPE; }
            case "notSubType" -> { subtype = Type.STRING; supertype = Type.INTEGER; }
            default -> throw new IllegalStateException("Unknown shape: " + shape);
        }
    }

    @Benchmark
    public boolean subTypeOf() {
        return Type.subTypeOf(subtype, supertype);
    }
}
