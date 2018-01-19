/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple thread factory that provides a standard naming convention
 * for threads.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class NamedThreadFactory implements ThreadFactory {

    private static final String DEFAULT_THREAD_NAME_PREFIX = "exist";

    private final AtomicLong threadId = new AtomicLong();
    private final String threadNamePrefix;

    /**
     * A factory who will produce threads names "exist-${nameBase}-${id}".
     *
     * @param nameBase The name base for the thread name
     */
    public NamedThreadFactory(final String nameBase) {
        this(DEFAULT_THREAD_NAME_PREFIX, nameBase);
    }

    /**
     * A factory who will produce threads names "${prefix}-${nameBase}-${id}".
     *
     * @param prefix A common prefix for the thread names
     * @param nameBase The name base for the thread name
     */
    public NamedThreadFactory(@Nullable final String prefix, final String nameBase) {
        Objects.requireNonNull(nameBase);
        this.threadNamePrefix = (prefix == null ? "" : prefix + "-") + nameBase + "-";
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        return new Thread(runnable, threadNamePrefix + threadId.getAndIncrement());
    }
}
