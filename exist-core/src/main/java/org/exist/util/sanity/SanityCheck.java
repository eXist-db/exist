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
package org.exist.util.sanity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

/**
 * Utility class for sanity checks. Provides static methods which can be used in the
 * code to react to unexpected conditions.
 *
 * {@link #ASSERT(boolean)} and {@link #ASSERT(boolean, String)} log a stack trace
 * to the Log4J log output at the {@link org.apache.logging.log4j.Level#ERROR} level.
 *
 * {@link #THROW_ASSERT(boolean)} and {@link #THROW_ASSERT(boolean)} log a stack trace
 * to the Log4J log output at the {@link org.apache.logging.log4j.Level#ERROR} level,
 * and throws an additional runtime exception of {@link AssertFailure}.
 *
 * {@link #PRINT_STACK(int)}  and {@link #TRACE(String)} log a stack trace to the Log4J
 * log output at the {@link org.apache.logging.log4j.Level#TRACE} level.
 *
 * @author wolf
 */
public class SanityCheck {
    private static final Logger LOG = LogManager.getLogger(SanityCheck.class);
    private static final String EOL = System.lineSeparator();

    public static void ASSERT(final boolean mustBeTrue) {
        ASSERT(mustBeTrue, null);
    }

    public static void ASSERT(final boolean mustBeTrue, @Nullable final String failureMsg) {
        if (!mustBeTrue && LOG.isErrorEnabled()) {
            final AssertFailure failure;
            if (failureMsg == null) {
                 failure = new AssertFailure("ASSERT FAILED");
            } else {
                failure = new AssertFailure("ASSERT FAILED: " + failureMsg);
            }

            LOG.error(failure);
        }
    }

    public static void THROW_ASSERT(final boolean mustBeTrue) {
        THROW_ASSERT(mustBeTrue, null);
    }

    public static void THROW_ASSERT(final boolean mustBeTrue, @Nullable final String failureMsg) {
        if (!mustBeTrue) {
            final AssertFailure failure;
            if (failureMsg == null) {
                failure = new AssertFailure("ASSERT FAILED");
            } else {
                failure = new AssertFailure("ASSERT FAILED: " + failureMsg);
            }
            LOG.error(failure);
            throw failure;
        }
    }

    public static void TRACE(final String msg) {
        if (LOG.isTraceEnabled()) {
            final AssertFailure failure = new AssertFailure("TRACE: " + msg);
            LOG.trace(failure);
        }
    }

    public static void PRINT_STACK(final int level) {
        if (LOG.isTraceEnabled()) {

            final StackTraceElement[] elements = new Exception("Trace").getStackTrace();
            final StringBuilder buf = new StringBuilder();
            final int depth = Math.min(level, elements.length);
            for (int i = 1; i < depth; i++) {
                buf.append(elements[i].toString());
                if (i < depth - 1) {
                    buf.append(EOL);
                }
            }

            LOG.trace(buf.toString());
        }
    }
}
