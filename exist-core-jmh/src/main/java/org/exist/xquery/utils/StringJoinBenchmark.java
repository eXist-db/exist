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
package org.exist.xquery.utils;

import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Benchmarks on variations of Java String Join operations.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@State(Scope.Benchmark)
public class StringJoinBenchmark {

    private final int maxStringLength = 20;

    @Param({ "1", "2", "5", "10", "100", "1000", "10000" })
    private int numOfStrings;

    private final List<String> strings = new ArrayList();

//    @State(Scope.Thread)
//    public static class BuilderState {
//        StringBuilder builder;
//
//        @Setup(Level.Invocation)  //TODO(AR) check that Level.Invocation is correct
//        public void setUp() {
//            builder = new StringBuilder();
//        }
//    }

    @Setup(Level.Trial)
    public void setUp() {
        final byte[] strData = new byte[maxStringLength];
        final Random random = new Random();
        for (int i = 0; i < numOfStrings; i++) {
            final int strLen = random.nextInt(maxStringLength) + 1;
            random.nextBytes(strData);
            strings.add(new String(strData, 0, strLen, StandardCharsets.UTF_8));
        }
    }

    @Benchmark
    public StringBuilder forApproach(/*final BuilderState builderState*/) {
//        final StringBuilder builder = builderState.builder;
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < strings.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(strings.get(i));
        }
        return builder;
    }

    @Benchmark
    public StringBuilder forApproachRadek(/*final BuilderState builderState*/) {
//        final StringBuilder builder = builderState.builder;
        final StringBuilder builder = new StringBuilder();

        for (String string : strings) {
            builder.append(string);
            builder.append(", ");
        }

        builder.substring(0, builder.length() - 3);

        return builder;
    }

    @Benchmark
    public StringBuilder forEachApproach(/*final BuilderState builderState*/) {
//        final StringBuilder builder = builderState.builder;
        final StringBuilder builder = new StringBuilder();

        boolean firstArgument = true;
        for (final String str : strings) {
            builder.append(str);
            if (firstArgument) {
                firstArgument = false;
            } else {
                builder.append(", ");
            }
        }
        return builder;
    }

    @Benchmark
    public StringBuilder forEachApproachRadek(/*final BuilderState builderState*/) {
//        final StringBuilder builder = builderState.builder;
        final StringBuilder builder = new StringBuilder();

        boolean firstArgument = true;
        for (final String str : strings) {
            builder.append(str);
            builder.append(", ");
        }

        builder.substring(0, builder.length() - 3);

        return builder;
    }

    @Benchmark
    public String jdkApproach() {
        return String.join(", ", strings);
    }

    public static void main(final String args[]) {
        // NOTE: just for running with the java debugger
        final StringJoinBenchmark stringJoinBenchmark = new StringJoinBenchmark();
        // stringJoinBenchmark.forApproach();
        // stringJoinBenchmark.forEachApproach();
        stringJoinBenchmark.jdkApproach();
    }
}
