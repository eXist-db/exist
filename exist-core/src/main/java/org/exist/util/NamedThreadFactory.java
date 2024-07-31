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

package org.exist.util;

import org.exist.Database;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import static org.exist.util.ThreadUtils.nameGlobalThread;
import static org.exist.util.ThreadUtils.nameInstanceThread;

/**
 * A simple thread factory that provides a standard naming convention
 * for threads.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class NamedThreadFactory implements ThreadFactory {

    private final ThreadGroup threadGroup;
    @Nullable private final String instanceId;
    private final String nameBase;
    private final AtomicLong threadId = new AtomicLong();

    /**
     * A factory who will produce threads named like either:
     *     "instance.${instanceId}.${nameBase}-${id}".
     *
     * @param instanceId the id of the database instance
     * @param nameBase The name base for the thread name
     *
     * @deprecated use {@link #NamedThreadFactory(Database, String)}.
     */
    @Deprecated
    public NamedThreadFactory(final String instanceId, final String nameBase) {
        this(null, instanceId, nameBase);
    }

    /**
     * A factory who will produce threads named like either:
     *     "instance.${instanceId}.${nameBase}-${id}".
     *
     * @param database the database instance which the threads are created for
     * @param nameBase The name base for the thread name
     *
     * @deprecated use {@link #NamedThreadFactory(Database, String)}.
     */
    @Deprecated
    public NamedThreadFactory(final Database database, final String nameBase) {
        this(database.getThreadGroup(), database.getId(), nameBase);
    }

    /**
     * A factory who will produce threads named like either:
     *
     *    1. "instance.${instanceId}.${nameBase}-${id}".
     *    2. "global.${nameBase}-${id}".
     *
     * @param threadGroup The thread group for the created threads, or null
     *     to use the same group as the calling thread.
     * @param instanceId the id of the database instance, or null if the
     *     thread is a global thread i.e. shared between instances.
     * @param nameBase The name base for the thread name.
     */
    public NamedThreadFactory(@Nullable final ThreadGroup threadGroup, @Nullable final String instanceId, final String nameBase) {
        Objects.requireNonNull(nameBase);
        this.threadGroup = threadGroup;
        this.instanceId = instanceId;
        this.nameBase = nameBase;
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        final String localName = nameBase + "-" + threadId.getAndIncrement();
        if (instanceId == null) {
            return new Thread(threadGroup, runnable, nameGlobalThread(localName));
        } else {
            return new Thread(threadGroup, runnable, nameInstanceThread(instanceId, localName));
        }
    }
}
