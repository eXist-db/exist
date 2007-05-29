/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.dom;

import java.util.Iterator;

import org.exist.util.hashtable.AbstractHashtable;


/**
 * A pool for QNames. This is a temporary pool for QName objects to avoid
 * allocating the same QName multiple times. If the pool is full, it will just be
 * cleared.
 * 
 * @author wolf
 */
public class QNamePool extends AbstractHashtable {

    private QName[] values;
    private QName temp = new QName("", "");
    
    public QNamePool() {
        super(512);
        values = new QName[tabSize];
    }

    public QNamePool(int iSize) {
        super(iSize);
        values = new QName[tabSize];
    }

    /**
     * Return a QName object for the given local name, namespace and
     * prefix. Return null if the QName has not yet been added to the pool.
     *
     * @param type
     * @param namespaceURI
     * @param localName
     * @param prefix
     * @return QName object
     */
    public QName get(byte type, String namespaceURI, String localName, String prefix) {
        temp.setLocalName(localName);
        temp.setNamespaceURI(namespaceURI);
        temp.setPrefix(prefix);
        temp.setNameType(type);
        int idx = temp.hashCode() % tabSize;
		if (idx < 0)
			idx *= -1;
		if (values[idx] == null)
			return null; // key does not exist
		else if (values[idx].equals(temp)) {
			return values[idx];
		}
		int rehashVal = rehash(idx);
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == null) {
				return null; // key not found
			} else if (values[idx].equals(temp)) {
				return values[idx];
			}
		}
		return null;
    }

    /**
	 * Add a QName, consisting of namespace, local name and prefix, to the
	 * pool.
	 */
    public QName add(byte type, String namespaceURI, String localName, String prefix) {
        temp.setLocalName(localName);
        temp.setNamespaceURI(namespaceURI);
        temp.setPrefix(prefix);
        temp.setNameType(type);
		try {
			return insert(temp);
		} catch(HashtableOverflowException e) {
		    // just clear the pool and try again
		    values = new QName[tabSize];
		    items = 0;
		    try {
                return insert(temp);
            } catch (HashtableOverflowException e1) {
            }
            // should never happen, but just to be sure
            return new QName(temp);
		}
	}
    
    protected QName insert(QName value) throws HashtableOverflowException {
		if (value == null)
			throw new IllegalArgumentException("Illegal value: null");
		int idx = value.hashCode() % tabSize;
		if (idx < 0)
			idx *= -1;
		int bucket = -1;
		// look for an empty bucket
		if (values[idx] == null) {
			values[idx] = new QName(value);
			++items;
			return values[idx];
		} else if (values[idx] == REMOVED) {
			// remember the bucket, but continue to check
			// for duplicate keys
			bucket = idx;
		} else if (values[idx].equals(value)) {
			// duplicate value
			return values[idx];
		}
//		System.out.println("Hash collision: " + value + " with " + values[idx]);
		int rehashVal = rehash(idx);
		int rehashCnt = 1;
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == REMOVED) {
				bucket = idx;
			} else if (values[idx] == null) {
				if (bucket > -1) {
					// store key into the empty bucket first found
					idx = bucket;
				}
				values[idx] = new QName(value);
				++items;
				return values[idx];
			} else if (values[idx].equals(value)) {
				// duplicate value
				return values[idx];
			}
			++rehashCnt;
		}
		// should never happen, but just to be sure:
		// if the key has not been inserted yet, do it now
		if (bucket > -1) {
			values[bucket] = new QName(value);
			++items;
			return values[bucket];
		}
		throw new HashtableOverflowException();
	}
    
    protected int rehash(int iVal) {
		int retVal = (iVal + iVal / 2) % tabSize;
		if (retVal == 0)
			retVal = 1;
		return retVal;
	}
	
	public Iterator iterator() {
		return null;
	}

	public Iterator valueIterator() {
		return null;
	}
}
