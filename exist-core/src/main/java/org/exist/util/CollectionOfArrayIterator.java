/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator over a Collection of array.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CollectionOfArrayIterator<T> implements Iterator<T> {

    private final Iterator<T[]> itArrays;

    public CollectionOfArrayIterator(final Collection<T[]> collectionOfArrays) {
        if (collectionOfArrays == null) {
            this.itArrays = Collections.emptyIterator();
        } else {
            this.itArrays = collectionOfArrays.iterator();
        }
    }

    private int arrayIdx = -1;  // -1 indicates BoF state
    private T[] array = null;

    @Override
    public boolean hasNext() {
        if (arrayIdx == -1) {
            while (itArrays.hasNext()) {
                array = itArrays.next();
                arrayIdx = 0;
                if (arrayIdx < array.length) {
                    return true;
                }
            }
            return false;
        }

        if (array == null && itArrays.hasNext()) {
            array = itArrays.next();
            arrayIdx = 0;
        }

        if (arrayIdx < array.length) {
            return true;
        } else {
            while (itArrays.hasNext()) {
                array = itArrays.next();
                arrayIdx = 0;
                if (arrayIdx < array.length) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public T next() {
        if (arrayIdx == -1) {
            array = itArrays.next();
            arrayIdx = 0;
        }

        if (arrayIdx < array.length) {
            return array[arrayIdx++];
        }

        while (itArrays.hasNext()) {
            array = itArrays.next();
            arrayIdx = 0;
            if (arrayIdx < array.length) {
                return array[arrayIdx++];
            }
        }

        throw new NoSuchElementException();
    }
}
