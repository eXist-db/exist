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

/**
 * Utility methods for dealing with stacktraces
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class Stacktrace {

    /**
     * Gets the top N frames from the stack returns
     * them as a string
     *
     * Excludes the callee and self stack frames
     *
     * @param stack The stack
     * @param top The number of frames to examine
     *
     * @return String representation of the top frames of the stack
     */
    public static String top(final StackTraceElement[] stack, final int top) {
        final StringBuilder builder = new StringBuilder();
        final int start = 2;

        for(int i = start; i < start + top && i < stack.length; i++) {
            builder
                    .append(" <- ")
                    .append(stack[i]);
        }

        return builder.toString();
    }
}
