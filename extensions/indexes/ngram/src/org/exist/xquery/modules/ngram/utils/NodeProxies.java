/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xquery.modules.ngram.utils;

import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;

import java.util.function.Function;
import java.util.function.Predicate;

public final class NodeProxies {

    private NodeProxies() {
    }

    /**
     * Filters the full-text matches on the supplied node, leaving only those for which the predicate is true.
     *
     * @param node
     *            the node whose full-text matches are to be filtered
     * @param predicate
     *            the predicate based on which the full-text matches are filtered: If the predicate returns true the
     *            match stays, if not the match is removed.
     */
    public static void filterMatches(final NodeProxy node, final Predicate<Match> predicate) {
        Match m = node.getMatches();
        node.setMatches(null);
        while (m != null) {
            if (predicate.test(m)) {
                node.addMatch(m);
            }
            m = m.getNextMatch();
        }
    }

    /**
     * Applies a supplied function to all matches with the supplied expression id on the supplied NodeProxy and returns
     * the NodeProxy with the modified matches if at least one match with the supplied expression id was not transformed
     * to null or null otherwise.
     *
     * @param node
     *            the NodeProxy to modify
     * @param transform
     *            the function to apply to all matches with the supplied expression id
     * @param ownExpressionId
     *            the expression id of the matches to be transformed
     * @return the modified node if at least one match with the supplied expression id was not transformed to null or
     *         null otherwise
     */
    public static NodeProxy transformOwnMatches(final NodeProxy node, final Function<Match, Match> transform, int ownExpressionId) {
        Match m = node.getMatches();
        node.setMatches(null);
        boolean ownMatch = false;

        while (m != null) {
            if (m.getContextId() != ownExpressionId) {
                node.addMatch(m);
            } else {
                final Match nm = transform.apply(m);
                if (nm != null) {
                    node.addMatch(nm);
                    ownMatch = true;
                }
            }
            m = m.getNextMatch();
        }

        return ownMatch ? node : null;
    }
}
