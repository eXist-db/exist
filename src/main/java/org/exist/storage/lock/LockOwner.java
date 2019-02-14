/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.storage.lock;

/**
 * Used to track acquired locks, mainly for debugging.
 */
public class LockOwner {

    /**
     * Global flag: set to true to receive debugging output, in particular,
     * to see where a lock was acquired. Note: it adds some considerable
     * processing overhead.
     */
    public static boolean DEBUG = false;

    private final Thread owner;
    private Throwable stack = null;

    public LockOwner(Thread owner) {
        this.owner = owner;
        if (DEBUG)
            {this.stack = new Throwable().fillInStackTrace();}
    }

    public final Thread getOwner() {
        return owner;
    }

    public final Throwable getStack() {
        return stack;
    }
}