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

import net.jcip.annotations.GuardedBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

/**
 * IP Utilities
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class IPUtil {

    @GuardedBy("class")
    private static final Random random = new Random();

    /**
     * Attempts to get the next random free IP port in the range {@code from} and {@code to}.
     *
     * @param from start of the port range
     * @param to end of the port range
     * @param maxAttempts the number of attempts to make to find a free port
     *
     * @return a potentially free IP port. This is done on a best effort basis! It is possible that the port returned
     *     is free, but by time you come to use it, it is then in-use; if so, just try calling this again.
     *
     * @throws IllegalStateException if maxAttempts is exceeded
     */
    public static int nextFreePort(final int from, final int to, final int maxAttempts) {
        for (int attempts = 0; attempts < maxAttempts; attempts++) {
            final int port = random(from, to);
            if (isLocalPortFree(port)) {
                return port;
            }
        }

        throw new IllegalStateException("Exceeded MAX_RANDOM_PORT_ATTEMPTS");
    }

    private synchronized static int random(final int min, final int max) {
        return random.nextInt((max - min) + 1) + min;
    }

    private static boolean isLocalPortFree(final int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (final IOException e) {
            return false;
        }
    }
}
