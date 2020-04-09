/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 The eXist-db Authors
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.QName;

/**
 * Used to uniquely identify a function by its function name and arity.
 *
 * @author wolf
 */
public class FunctionId implements Comparable<FunctionId> {

    private final QName qname;
    private final int argCount;

    public FunctionId(final QName qname, final int arguments) {
        this.qname = qname;
        this.argCount = arguments;
    }

    @Override
    public int compareTo(final FunctionId other) {
        final int cmp = qname.compareTo(other.qname);
        if (cmp == 0) {
            if (argCount == other.argCount || argCount == -1 || other.argCount == -1) {
                return Constants.EQUAL;
            } else if (argCount < other.argCount) {
                return Constants.INFERIOR;
            } else {
                return Constants.SUPERIOR;
            }
        } else {
            return cmp;
        }
    }

    @Override
    public String toString() {
        return qname.getStringValue() + "#" + argCount;
    }
}
