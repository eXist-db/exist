/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
 * Simple Ring Buffer implementation.
 *
 * @author Adam Retter adam.retter@googlemail.com
 */
@NotThreadSafe
public class RingBuffer<T> {
    private final int capacity;
    private final T[] elements;

    private int writePos;
    private int available;

    @SuppressWarnings("unchecked")
    public RingBuffer(final int capacity, final Supplier<T> constructor) {
        this.capacity = capacity;
        this.elements = (T[])new Object[capacity];
        for (int i = 0; i < capacity; i++) {
            elements[i] = constructor.get();
        }

        this.available = capacity;
        this.writePos = capacity;
    }

    public @Nullable T takeEntry() {
        if(available == 0){
            return null;
        }
        int nextSlot = writePos - available;
        if(nextSlot < 0){
            nextSlot += capacity;
        }
        final T nextObj = elements[nextSlot];
        available--;
        return nextObj;
    }

    public void returnEntry(final T element) {
        if(available < capacity){
            if(writePos >= capacity){
                writePos = 0;
            }
            elements[writePos] = element;
            writePos++;
            available++;
        }
    }
}
