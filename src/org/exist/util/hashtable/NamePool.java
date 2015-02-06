/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
package org.exist.util.hashtable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.exist.dom.QName;
import org.exist.xquery.Constants;

/**
 * @author Pieter Deelen
 */
public class NamePool {

    private ConcurrentMap<WrappedQName, QName> pool;

    public NamePool() {
        pool = new ConcurrentHashMap<WrappedQName, QName>();
    }
    
    public QName getSharedName(QName name) {
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
     * Wrap it to overwrite those methods.
     */
    private static class WrappedQName implements Comparable<WrappedQName> {

        private QName qname = null;

        public WrappedQName(final QName qname) {
            this.qname = qname;
        }

        @Override
        public int compareTo(WrappedQName other) {
            if(qname.getNameType() != other.qname.getNameType()) {
                return qname.getNameType() < other.qname.getNameType() ? Constants.INFERIOR : Constants.SUPERIOR;
            }
            int c;
            if (qname.getNamespaceURI() == null)
            {c = other.qname.getNamespaceURI() == null ? Constants.EQUAL : Constants.INFERIOR;}
            else if (other.qname.getNamespaceURI() == null)
            {c = Constants.SUPERIOR;}
            else
            {c = other.qname.getNamespaceURI().compareTo(other.qname.getNamespaceURI());}
            return c == Constants.EQUAL ? qname.getLocalPart().compareTo(other.qname.getLocalPart()) : c;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null || !(obj instanceof WrappedQName)) {
                return false;
            }

            final WrappedQName other = (WrappedQName) obj;
            final int cmp = compareTo(other);
            if(cmp != 0) {
                return false;
            }

            if(qname.getPrefix() == null) {
                return other.qname.getPrefix() == null;
            } else if(other.qname.getPrefix() == null) {
                return false;
            } else {
                return qname.getPrefix().equals(other.qname.getPrefix());
            }
        }
    }
}
