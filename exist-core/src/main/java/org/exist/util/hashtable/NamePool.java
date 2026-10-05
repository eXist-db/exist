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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.jcip.annotations.ThreadSafe;
import org.exist.dom.QName;
import org.exist.xquery.Constants;

/**
 * @author Pieter Deelen
 */
@ThreadSafe
public class NamePool {

    private final ConcurrentMap<WrappedQName, QName> pool;

    public NamePool() {
        pool = new ConcurrentHashMap<>();
    }

    public QName getSharedName(final QName name) {
        final WrappedQName wrapped = new WrappedQName(name);
        final QName sharedName = pool.putIfAbsent(wrapped, name);
        if (sharedName == null) {
            // The name was not in the pool, return the name just added.
            return name;
        } else {
            // The name was in the pool, return the shared name.
            return sharedName;
        }
    }

    /**
     * QName ignores nameType and prefix when testing for equality.
     * Wrap it to compare all four parts instead, as {@link QName#isIdenticalTo(QName)} does.
     */
    private static class WrappedQName implements Comparable<WrappedQName> {
        private final QName qname;

        public WrappedQName(final QName qname) {
            this.qname = qname;
        }

        @Override
        public int compareTo(final WrappedQName other) {
            if (qname.getNameType() != other.qname.getNameType()) {
                return qname.getNameType() < other.qname.getNameType() ? Constants.INFERIOR : Constants.SUPERIOR;
            }
            return qname.compareTo(other.qname);
        }

        @Override
        public int hashCode() {
            return qname.identicalHashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof WrappedQName other && qname.isIdenticalTo(other.qname);
        }
    }
}
