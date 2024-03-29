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
package org.exist.xquery;

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.value.Sequence;

/**
 *
 */
public interface Optimizable extends Expression {

    /**
     * Given a sequence of Items, test each to see if they are optimizable,
     * and return only those Items that are optimizable.
     *
     * @param contextSequence the sequence of items that should be tested to see if each is optimizable.
     * @return a sequence containing only the items from the {@code contextSequence} that
     *      can be optimized, if there are no items that can be optimized, the result is the empty sequence.
     */
    Sequence canOptimizeSequence(Sequence contextSequence);

    boolean optimizeOnSelf();

    boolean optimizeOnChild();
    
    NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException;

    int getOptimizeAxis();
}
