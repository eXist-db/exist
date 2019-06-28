/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2014 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.dom;

import org.exist.xquery.Constants;

import java.util.Comparator;

/**
 * Comparator for comparing two QNames which takes their
 * nameType into account
 *
 * Should be able to be removed in future when we further refactor
 * to decouple QName from nameType.
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class TypedQNameComparator implements Comparator<QName> {

    @Override
    public int compare(final QName q1, final QName q2) {
        if(q1.getNameType() != q2.getNameType()) {
        return q1.getNameType() < q2.getNameType() ? Constants.INFERIOR : Constants.SUPERIOR;
        }
        final int c = q1.getNamespaceURI().compareTo(q2.getNamespaceURI());
        return c == Constants.EQUAL ? q1.getLocalPart().compareTo(q2.getLocalPart()) : c;
    }
}
