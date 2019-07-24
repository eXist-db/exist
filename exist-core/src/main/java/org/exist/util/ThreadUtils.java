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

import org.exist.Database;

/**
 * Simple utility functions for creating named threads
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ThreadUtils {

    public static String nameInstanceThreadGroup(final String instanceId) {
        return "exist.db." + instanceId;
    }

    public static ThreadGroup newInstanceSubThreadGroup(final Database database, final String subThreadGroupName) {
        return new ThreadGroup(database.getThreadGroup(), subThreadGroupName);
    }

    public static String nameInstanceThread(final Database database, final String threadName) {
        return "db." + database.getId() + "." + threadName;
    }

    public static String nameInstanceThread(final String instanceId, final String threadName) {
        return "db." + instanceId + "." + threadName;
    }

    public static String nameInstanceSchedulerThread(final Database database, final String threadName) {
        return "db." + database.getId() + ".scheduler." + threadName;
    }

    public static Thread newInstanceThread(final Database database, final String threadName, final Runnable runnable) {
        return new Thread(database.getThreadGroup(), runnable, nameInstanceThread(database, threadName));
    }

    public static Thread newInstanceThread(final ThreadGroup threadGroup, final String instanceId, final String threadName, final Runnable runnable) {
        return new Thread(threadGroup, runnable, nameInstanceThread(instanceId, threadName));
    }

    public static String nameGlobalThread(final String threadName) {
        return "global." + threadName;
    }

    public static Thread newGlobalThread(final String threadName, final Runnable runnable) {
        return new Thread(runnable, nameGlobalThread(threadName));
    }
}
