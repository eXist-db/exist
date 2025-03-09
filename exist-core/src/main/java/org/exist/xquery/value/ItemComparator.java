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
package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import net.jcip.annotations.NotThreadSafe;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.Constants;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * Comparator for comparing instances of Item
 * apart from the XQuery atomic types there are
 * two Node types in eXist org.exist.dom.persistent.*
 * and org.exist.dom.memtree.* this class is
 * used so that both types can be compared to each other
 * as Item even though they have quite different inheritance
 * hierarchies.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class ItemComparator implements Comparator<Item> {

    @Nullable private final Collator collator;
    @Nullable private AtomicValueComparator atomicValueComparator = null;

    public ItemComparator() {
        this(null);
    }

    public ItemComparator(@Nullable final Collator collator) {
        this.collator = collator;
    }

    @Override
    public int compare(final Item left, final Item right) {
        return switch (left) {
            case AtomicValue atomicValue when right instanceof AtomicValue -> {
                if (atomicValueComparator == null) {
                    atomicValueComparator = new AtomicValueComparator(collator);
                }
                yield atomicValueComparator.compare(atomicValue, (AtomicValue) right);
            }
            case Comparable comparable -> comparable.compareTo(right);
            case null, default -> Constants.INFERIOR;
        };
    }
}
