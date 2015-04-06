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
package org.exist.xquery.modules.ngram.query;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.ngram.utils.NodeProxies;
import org.exist.xquery.modules.ngram.utils.NodeSets;

public class WildcardedExpressionTriple implements EvaluatableExpression {
    /**
     *
     */
    private final EvaluatableExpression head;
    private final Wildcard wildcard;
    private final EvaluatableExpression tail;

    protected static Logger LOG = LogManager.getLogger(WildcardedExpressionTriple.class);


    public WildcardedExpressionTriple(final EvaluatableExpression head,
        final Wildcard wildcard,
        final EvaluatableExpression tail) {
        this.head = head;
        this.wildcard = wildcard;
        this.tail = tail;
    }

    @Override
    public NodeSet eval(
        final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames, final NodeSet nodeSet,
        final int axis, final int expressionId) throws XPathException {

        final NodeSet headNodes = head.eval(index, docs, qnames, nodeSet, axis, expressionId);
        if (headNodes.isEmpty()) {
            return headNodes;
        }

        final NodeSet tailNodes = tail.eval(index, docs, qnames, nodeSet, axis, expressionId);
        if (tailNodes.isEmpty()) {
            return tailNodes;
        }

        final NodeSet result = NodeSets.transformNodes(headNodes, headNode ->
                Optional.ofNullable(tailNodes.get(headNode))
                        .map(tailNode -> getMatchingNode(headNode, tailNode, expressionId))
                        .orElse(null));

        return result;
    }

    private NodeProxy getMatchingNode(NodeProxy headNode, NodeProxy tailNode, final int expressionId) {
        NodeProxy result = null;

        Match match = null;
        boolean found = false;

        for (Match headMatch = headNode.getMatches(); headMatch != null && !found; headMatch = headMatch
            .getNextMatch()) {

            for (Match tailMatch = tailNode.getMatches(); tailMatch != null && !found; tailMatch = tailMatch
                .getNextMatch()) {

                match = headMatch.followedBy(tailMatch, wildcard.getMinimumLength(), wildcard.getMaximumLength());
                found = (match != null);

            }
        }

        // preserve other matches, add new match

        if (found) {
            // Remove own (partial) matches and add new complete match
            NodeProxies.filterMatches(tailNode, a -> a.getContextId() != expressionId);

            tailNode.addMatch(match);
            result = tailNode;
        }


        return result;
    }

    @Override
    public String toString() {
        return "WildcardedExpressionTriple(" + head + ", " + wildcard + ", " + tail + ")";
    }

}
