/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util;

import java.util.Arrays;

/**
 * Utility methods for dealing with stack traces
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class Stacktrace {

    public final static int DEFAULT_STACK_TOP = 10;

    /**
     * Gets the top N frames from the stack trace and
     * returns them as a string
     *
     * Excludes the callee and self stack frames
     *
     * @param stack The stack
     * @param top The number of frames to examine
     *
     * @return String representation of the top frames of the stack
     */
    public static String top(final StackTraceElement[] stack, final int top) {
        final int from = 2;
        final int until = stack.length - from < top ? stack.length - from : top + from;
        return asString(stack, from, until);
    }

    /**
     * Formats the stack trace as a String
     *
     * @param stack the stack trace
     *
     * @return A formatted string showing the stack trace
     */
    public static String asString(final StackTraceElement[] stack) {
        return asString(stack, 0, stack.length);
    }

    /**
     * Formats the stack trace as a String
     *
     * @param stack the stack trace
     * @param from The most recent frame to start from
     * @param until The least recent frame to format until
     *
     * @return A formatted string showing the stack trace
     */
    public static String asString(final StackTraceElement[] stack, final int from, final int until) {
        final StringBuilder builder = new StringBuilder();
        for(int i = from; i < until; i++) {
            builder
                    .append(" <- ")
                    .append(stack[i]);
        }
        return builder.toString();
    }

    /**
     * Get a subset of the StackTraceElements
     *
     * @param stack The stack trace
     * @param from Starting from HEAD
     * @param max The maximum number of elements to take
     *
     * @return The stack trace elements between from and from+max (or less)
     */
    public static StackTraceElement[] substack(final StackTraceElement[] stack, final int from, final int max) {
        final int to = stack.length - from  < max ? stack.length - from : from + max;
        return Arrays.copyOfRange(stack, from, to);
    }
}
