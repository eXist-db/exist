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
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple factory for thread groups, where you
 * may want multiple groups with similar names.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class NamedThreadGroupFactory {

    private final String threadGroupNameBase;
    private final AtomicLong threadGroupId = new AtomicLong();

    /**
     * @param threadGroupNameBase the base name for the thread group.
     */
    public NamedThreadGroupFactory(final String threadGroupNameBase) {
        this.threadGroupNameBase = threadGroupNameBase;
    }

    /**
     * Produces a thread group named like:
     *     "${threadGroupNameBase}-${id}"
     *
     * Where id is a global monontonically increasing identifier.
     *
     * @param parent the parent thread group, or null to use the current threads thread group.
     *
     * @return the new thread group
     */
    public ThreadGroup newThreadGroup(@Nullable final ThreadGroup parent) {
        final String threadGroupName = threadGroupNameBase + "-" + threadGroupId.getAndIncrement();
        if (parent != null) {
            return new ThreadGroup(parent, threadGroupName);
        } else {
            return new ThreadGroup(threadGroupName);
        }
    }
}
