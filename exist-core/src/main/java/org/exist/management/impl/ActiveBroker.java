/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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

package org.exist.management.impl;

/**
 * Owner and process information on a broker.
 */
public class ActiveBroker {

    private final String owner;
    private final int referenceCount;
    private final String stack;
    private final String stackAcquired;

    public ActiveBroker(final String owner, final int referenceCount, final String stack, final String stackAcquired) {

        this.owner = owner;
        this.referenceCount = referenceCount;
        this.stack = stack;
        this.stackAcquired = stackAcquired;
    }

    public String getOwner() {
        return owner;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public String getStack() {
        return stack;
    }

    public String getStackAcquired() {
        return stackAcquired;
    }
}
