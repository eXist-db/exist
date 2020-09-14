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
package org.exist.indexing.range;

import org.exist.xquery.*;
import org.exist.xquery.modules.range.Lookup;
import org.w3c.dom.Node;


/**
 *
 * Base class for conditions that can be defined for complex range config elements.
 *
 * @author Marcel Schaeben
 */
public abstract class RangeIndexConfigCondition {


    /**
     * Test if a node matches this condition. Used by the indexer.
     * @param node The node to test.
     * @return true if the node is an element node and an attribute matches this condition.
     */
    public abstract boolean matches(Node node);

    /**
     * Test if an expression defined by the arguments matches this condition. Used by the query rewriter.
     * @param predicate The predicate to test.
     * @return true if the predicate matches this condition.
     */
    public boolean find(Predicate predicate) {

        return false;
    }

    /**
     * Get the inner expression of a predicate. Will unwrap the original expression if it has previously
     * been rewritten into an index function call.
     * @param predicate The predicate to test.
     * @return The fallback expression from a rewritten function call or the original inner expression.
     */
    protected Expression getInnerExpression(Predicate predicate) {
        Expression inner = predicate.getExpression(0);
        if (inner instanceof InternalFunctionCall) {
            Function function = ((InternalFunctionCall)inner).getFunction();
            if (function instanceof Lookup) {
                return ((Lookup)function).getFallback();
            }
        }

        return inner;
    }

}