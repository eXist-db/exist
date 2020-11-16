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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.http.RESTServer;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class SequenceComparator implements Comparator<Sequence> {

    protected static final Logger LOG = LogManager.getLogger(RESTServer.class);

    private final @Nullable Collator collator;
    private @Nullable ItemComparator itemComparator = null;

    public SequenceComparator() {
        this(null);
    }

    public SequenceComparator(@Nullable final Collator collator) {
        this.collator = collator;
    }

    @Override
    public int compare(final Sequence o1, final Sequence o2) {
        if (o1 == null && o2 == null) {
            return 0;
        } else if (o1 == null) {
            return -1;
        } else if (o2 == null) {
            return 1;
        }

        final int o1Count = o1.getItemCount();
        final int o2Count = o1.getItemCount();

        if (o1Count < o2Count) {
            return -1;
        } else if(o1Count > o2Count) {
            return 1;
        } else if (o1Count == 0 && o1Count == o2Count) {
            return 0;
        }

        for (int i = 0; i < o1Count; i++) {
            if (itemComparator == null) {
                itemComparator = new ItemComparator(collator);
            }

            final Item i1 = o1.itemAt(i);
            final Item i2 = o2.itemAt(i);
            final int result = itemComparator.compare(i1, i2);
            if (result != 0) {
                return result;
            }
        }

        return 0;
    }
}
