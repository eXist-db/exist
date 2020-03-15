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
package org.exist.util.hashtable;

import net.jcip.annotations.NotThreadSafe;

import java.util.Iterator;

/**
 * Abstract base class for all hashtable implementations.
 *
 * @author Stephan Körnig
 * @author Wolfgang Meier
 */
@NotThreadSafe
abstract class AbstractHashtable<K, V> extends AbstractHashSet<K> {

    /**
     * Create a new hashtable with default size (1031).
     */
    AbstractHashtable() {
        super();
    }

    /**
     * Create a new hashtable using the specified size.
     *
     * The actual size will be next prime number following
     * iSize * 1.5.
     *
     * @param iSize The initial size of the hash table
     */
    AbstractHashtable(final int iSize) {
        super(iSize);
    }

    public abstract Iterator<V> valueIterator();
}
