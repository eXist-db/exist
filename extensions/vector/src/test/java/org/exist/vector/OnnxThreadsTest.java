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
package org.exist.vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ONNX Runtime's threads are limited to the JVM's CPUs under a CPU limit, and left to ONNX Runtime otherwise.
 */
class OnnxThreadsTest {

    private static final int KEEP_DEFAULT = 0;

    @Test
    void underALimitOneThreadPerCpu() {
        assertEquals(4, OnnxVectorProvider.intraOpThreads(4, 10));
    }

    @Test
    void withoutALimitTheDefaultStays() {
        assertEquals(KEEP_DEFAULT, OnnxVectorProvider.intraOpThreads(10, 10));
    }

    @Test
    void anUnknownHostKeepsTheDefault() {
        assertEquals(KEEP_DEFAULT, OnnxVectorProvider.intraOpThreads(10, 0));
    }

    @Test
    void theHostsCpuListDetectsALimit() {
        assertEquals(4, OnnxVectorProvider.intraOpThreads(4, OnnxVectorProvider.countCpus("0-9\n")));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0,           1
            '0-3,6-7,9', 7
            """)
    void rangesAndSingleCpusAddUp(final String cpuList, final int cpus) {
        assertEquals(cpus, OnnxVectorProvider.countCpus(cpuList));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0-a", "3-1", "0-1-2"})
    void anUnreadableListCountsNone(final String cpuList) {
        assertEquals(0, OnnxVectorProvider.countCpus(cpuList));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void linuxListsAtLeastTheJvmsCpus() {
        final int hostCpus = OnnxVectorProvider.hostCpus();
        assertTrue(hostCpus >= Runtime.getRuntime().availableProcessors(),
                "host CPUs " + hostCpus + ", JVM CPUs " + Runtime.getRuntime().availableProcessors());
    }
}
