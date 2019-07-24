/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.dom.persistent;

import net.jcip.annotations.NotThreadSafe;
import org.exist.dom.QName;
import org.exist.util.hashtable.AbstractHashSet;

import java.util.Iterator;
import java.util.Objects;

/**
 * A pool for QNames. This is a temporary pool for QName objects to avoid
 * allocating the same QName multiple times. If the pool is full, it will just be
 * cleared.
 *
 * @author wolf
 */
@NotThreadSafe
public class QNamePool extends AbstractHashSet<QName> {

    private static final int DEFAULT_POOL_SIZE = 512;
    private QName[] values;

    public QNamePool() {
        super(DEFAULT_POOL_SIZE);
        values = new QName[tabSize];
    }

    /**
     * @param size The size of the QName pool
     */
    public QNamePool(final int size) {
        super(size);
        values = new QName[tabSize];
    }

    /**
     * Return a QName object for the given local name, namespace and
     * prefix. Return null if the QName has not yet been added to the pool.
     *
     * @param type qname type
     * @param namespaceURI qname namespace uri
     * @param localName qname local name
     * @param prefix qname prefix
     * @return QName object
     */
    public final QName get(final byte type, final String namespaceURI, final String localName, final String prefix) {
        int idx = hashCode(localName, namespaceURI, prefix, type) % tabSize;
        if(idx < 0) {
            idx *= -1;
        }

        if(values[idx] == null) {
            return null;  // key does not exist
        } else if(equals(values[idx], localName, namespaceURI, prefix, type)) {
            return values[idx]; //no hash-collision
        } else {

            //hash-collision rehash
            final int rehashVal = rehash(idx);
            for(int i = 0; i < tabSize; i++) {
                idx = (idx + rehashVal) % tabSize;
                if(values[idx] == null) {
                    return null; // key not found
                } else if(equals(values[idx], localName, namespaceURI, prefix, type)) {
                    return values[idx];
                }
            }
            return null;
        }
    }

    /**
     * Add a QName, consisting of namespace, local name and prefix, to the
     * pool.
     * @param namespaceURI qname namespace uri
     * @param localName qname local name
     * @param prefix qname prefix
     * @param type qname type
     * @return QName object
     */
    public final QName add(final byte type, final String namespaceURI, final String localName, final String prefix) {
        final QName qn = new QName(localName, namespaceURI, prefix, type);
        try {
            return insert(qn);
        } catch(final HashSetOverflowException e) {
            clear();
            try {
                return insert(qn);
            } catch(final HashSetOverflowException e1) {
                throw new RuntimeException(e1);
            }
        }
    }

    private void clear() {
        // just clear the pool and try again
        values = new QName[tabSize];
        items = 0;
    }

    private QName insert(final QName value) throws HashSetOverflowException {
        if(value == null) {
            throw new IllegalArgumentException("Illegal value: null");
        }

        int idx = hashCode(value.getLocalPart(), value.getNamespaceURI(), value.getPrefix(), value.getNameType()) % tabSize;
        if(idx < 0) {
            idx *= -1;
        }

        int bucket = -1;
        // look for an empty bucket
        if(values[idx] == null) {
            values[idx] = value;
            ++items;
            return values[idx];
        } else if(values[idx] == REMOVED) {
            // remember the bucket, but continue to check
            // for duplicate keys
            bucket = idx;
        } else if(values[idx].equals(value)) {
            // duplicate value
            return values[idx];
        }

        //System.out.println("Hash collision: " + value + " with " + values[idx]);
        final int rehashVal = rehash(idx);
        for(int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if(values[idx] == REMOVED) {
                bucket = idx;
            } else if(values[idx] == null) {
                if(bucket > -1) {
                    // store key into the empty bucket first found
                    idx = bucket;
                }
                values[idx] = value;
                ++items;
                return values[idx];
            } else if(values[idx].equals(value)) {
                // duplicate value
                return values[idx];
            }
        }
        // should never happen, but just to be sure:
        // if the key has not been inserted yet, do it now
        if(bucket > -1) {
            values[bucket] = value;
            ++items;
            return values[bucket];
        } else {
            throw new HashSetOverflowException();
        }
    }

    private int rehash(final int iVal) {
        int retVal = (iVal + iVal / 2) % tabSize;
        if(retVal == 0) {
            retVal = 1;
        }
        return retVal;
    }

    /**
     * Used to calculate a hashCode for a QName
     *
     * This varies from {@see org.exist.dom.QName#hashCode()} in so far
     * as it also includes the prefix in the hash calculation
     *
     * @param localPart
     * @param namespaceURI
     * @param prefix
     * @param nameType
     */
    private static int hashCode(final String localPart, final String namespaceURI, final String prefix, final byte nameType) {
        int h = nameType + 31 + localPart.hashCode();
        h += 31 * h + namespaceURI.hashCode();
        h += 31 * h + (prefix == null ? 1 : prefix.hashCode());
        return h;
    }

    /**
     * Used to calculate equality for a QName and it's constituent components
     *
     * This varies from {@see org.exist.dom.QName#equals(Object)} in so far
     * as it also includes the prefix in the equality test
     *
     * @param qname             The QName to check equality against the other*
     * @param otherLocalPart
     * @param otherNamespaceURI
     * @param otherPrefix
     * @param otherNameType
     */
    private static boolean equals(final QName qname, final String otherLocalPart, final String otherNamespaceURI, final String otherPrefix, final byte otherNameType) {
        return qname.getNameType() == otherNameType
            && qname.getNamespaceURI().equals(otherNamespaceURI)
            && qname.getLocalPart().equals(otherLocalPart)
            && Objects.equals(qname.getPrefix(), otherPrefix);
    }


    @Override
    public Iterator<QName> iterator() {
        throw new UnsupportedOperationException();
    }
}
