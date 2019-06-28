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

import net.jcip.annotations.NotThreadSafe;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * A timestamped reference of which
 * updates to are conditional on the
 * timestamp.
 *
 * @param <V> The type of the object reference.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class TimestampedReference<V> {
    private long timestamp;
    @Nullable
    private V reference;

    /**
     * Creates a timestamped reference with
     * an initial null reference and millisecond
     * resolution.
     */
    public TimestampedReference() {
        this(false, null);
    }

    /**
     * Creates a timestamped reference with
     * an initial null reference.
     *
     * @param nanoResolution true for nanosecond resolution
     *     or, false for millisecond resolution.
     */
    public TimestampedReference(final boolean nanoResolution) {
        this(nanoResolution, null);
    }

    /**
     * Creates a timestamped reference.
     *
     * @param nanoResolution true for nanosecond resolution
     *     or, false for millisecond resolution.
     * @param reference the initial object reference.
     */
    public TimestampedReference(final boolean nanoResolution, @Nullable final V reference) {
        this.reference = reference;
        this.timestamp = nanoResolution ? System.nanoTime() : System.currentTimeMillis();
    }

    /**
     * Set the reference if it is older than the provided timestamp.
     *
     * @param timestamp The new/current timestamp
     * @param supplier A provider of a new object reference.
     *
     * @return the existing reference if not expired, otherwise the new
     *     reference after it is set.
     */
    @Nullable public V setIfExpired(final long timestamp, final Supplier<V> supplier) {
        if(timestamp > this.timestamp) {
            this.reference = supplier.get();
            this.timestamp = timestamp;
        }
        return this.reference;
    }

    /**
     * Set the reference if it is older than the provided timestamp or null.
     *
     * @param timestamp The new/current timestamp
     * @param supplier A provider of a new object reference.
     *
     * @return the existing reference if not expired, otherwise the new
     *     reference after it is set.
     */
    @Nullable public V setIfExpiredOrNull(final long timestamp, final Supplier<V> supplier) {
        if(timestamp > this.timestamp || this.reference == null) {
            this.reference = supplier.get();
            this.timestamp = timestamp;
        }
        return this.reference;
    }

    /**
     * Get the reference.
     *
     * @return the object reference
     */
    @Nullable public V get() {
        return this.reference;
    }
}
